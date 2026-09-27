package com.netra.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.netra.library.database.DeferredDao
import com.netra.library.database.DeferredRequestEntity
import com.netra.library.database.NetraDatabase
import com.netra.library.enums.DeferredStatus
import com.netra.library.managers.DeferredRequestManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeferredRequestManagerTest {

    private lateinit var db: NetraDatabase
    private lateinit var dao: DeferredDao
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NetraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.deferredDao()

        DeferredRequestManager.init(context, database = db)

        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun teardown() = db.close()

    private fun mockNetraCall(url: String = "https://api.example.com/test"): NetraCall {
        val request = Request.Builder().url(url).build()
        val call = OkHttpClient().newCall(request)
        return NetraCall(call = call, converter = null, isCancelledWhenDestroy = false)
    }

    private suspend fun insertNRequests(dao: DeferredDao, count: Int) {
        repeat(count) { index ->
            dao.insertRequest(
                DeferredRequestEntity(
                    id = "seed-$index",
                    url = "https://api.example.com/seed/$index",
                    method = "GET",
                    body = null,
                    headersJson = "{}",
                    converter = null,
                    status = DeferredStatus.PENDING,
                )
            )
        }
    }

    private suspend fun insertSucceededEntity(dao: DeferredDao): DeferredRequestEntity {
        val entity = DeferredRequestEntity(
            id = UUID.randomUUID().toString(),
            url = "https://api.example.com/succeeded",
            method = "GET",
            body = null,
            headersJson = "{}",
            converter = null,
            status = DeferredStatus.SUCCEEDED,
        )
        dao.insertRequest(entity)
        return entity
    }

    private suspend fun insertPendingEntity(dao: DeferredDao, url: String): DeferredRequestEntity {
        val entity = DeferredRequestEntity(
            id = UUID.randomUUID().toString(),
            url = url,
            method = "GET",
            body = null,
            headersJson = "{}",
            converter = null,
            status = DeferredStatus.PENDING,
        )
        dao.insertRequest(entity)
        return entity
    }


    //"ayni anda 50 istek gonderilirse her biri benzersiz bir kayit olusturur"
    @Test
    fun test1() = runTest {
        // NOT: bu test "her enqueue benzersiz/kayıpsız bir kayıt oluşturuyor mu" sorusuna bakıyor —
        // "sunucuya iki kez istek gitmiyor mu" sorusunu değil. O senaryo aşağıdaki
        // "background isaretliyken..." testinde server.requestCount ile ayrıca doğrulanıyor.
        val server = MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(200)
        }
        server.start()

        val urls = (1..50).map { server.url("/item/$it").toString() }

        coroutineScope {
            urls.map { url ->
                async { DeferredRequestManager.enqueueDeferredRequest(mockNetraCall(url)) }
            }.awaitAll()
        }

        val all = dao.getAllRequests()
        assertThat(all).hasSize(50)

        server.shutdown()
    }

    //worker Room'da olmayan bir id ile calisirsa crash olmaz, Result_failure doner
    @Test
    fun test2() = runTest {
        val worker = TestListenableWorkerBuilder<DeferredRequestManager.NetraTransferWorker>(context)
            .setInputData(workDataOf("deferredWorkId" to "olmayan-id"))
            .build()

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }

    //basarili istek sonrasi worker ikinci kez calisirsa NPE atmaz, sessizce success doner
    @Test
    fun test3() = runTest {
        val entity = insertSucceededEntity(dao)  // status = SUCCEEDED, satır silinmemiş

        val worker = TestListenableWorkerBuilder<DeferredRequestManager.NetraTransferWorker>(context)
            .setInputData(workDataOf("deferredWorkId" to entity.id))
            .build()

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
    }

    //enqueueDeferredRequest gercek queueOrder sayisini doner, hep 0 donmez
    @Test
    fun test4() = runTest {
        insertNRequests(dao, count = 3)

        val queueOrder = DeferredRequestManager.enqueueDeferredRequest(mockNetraCall())

        assertThat(queueOrder).isEqualTo(4)
    }


    //background isaretliyken online olsa bile senkron istek atilmiyor
    @Test
    fun test6() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))  // hiç tüketilmemeli
        server.start()

        val client = NetraClient.Builder(context).baseUrl(server.url("/").toString()).build()

        val result = client.get("/test").asObject<Any>().background().execute()

        assertThat(result).isInstanceOf(NetraResponse.ResponseQueued::class.java)
        assertThat(server.requestCount).isEqualTo(0)  // <-- kritik: senkron path hiç tetiklenmedi

        server.shutdown()
    }

    //runAttemptCount 1 iken PENDING entity normal calisir, SUCCEEDED entity tekrar calismaz
    @Test
    fun test7() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()

        val entity = insertPendingEntity(dao, url = server.url("/retry-test").toString())

        val worker = TestListenableWorkerBuilder<DeferredRequestManager.NetraTransferWorker>(context)
            .setInputData(workDataOf("deferredWorkId" to entity.id))
            .setRunAttemptCount(1)   // "bu ikinci deneme" simülasyonu
            .build()

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(server.requestCount).isEqualTo(1)  // PENDING entity gerçekten isteği attı

        server.shutdown()
    }
}