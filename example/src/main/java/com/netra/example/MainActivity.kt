package com.netra.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.netra.example.ui.theme.NetraTheme
import com.netra.library.Cache
import com.netra.library.observers.CacheEvent
import com.netra.library.observers.INetraObserver
import com.netra.library.NetraClient
import com.netra.library.NetraPart
import com.netra.library.NetraRequestBody
import com.netra.library.converter.NetraGsonConverter
import com.netra.library.observers.NetworkEvent
import com.netra.library.observers.RequestEvent
import com.netra.library.enums.OfflinePolicyAction
import com.netra.library.enums.SlowNetworkPolicyAction
import com.netra.library.converter.NetraKotlinxConverter
import com.netra.library.exceptions.NetraException
import com.netra.library.observers.QueueEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class Repo(
    val id: Int,
    val name: String
)

//val client = NetraClient.kt.Builder()
//    .baseUrl("https://api.github.com")
//    .addConverterFactory(
//        NetraGsonConverter()
//    ).build()
//
//client.get("/users/octocat/repos")
//.asList<Repo>().enqueue { result ->
//    print("result: ${result?.get(0)?.name}}")
//}
//}
class MainActivity : ComponentActivity() {
    var _bitmap = mutableStateOf<Bitmap?>(null)

    private lateinit var client: NetraClient

    val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val byteArray = uriToByteArray(it)
            if(byteArray != null) {
                val netraPart = NetraPart.file("image", "exampleImage", byteArray, "image/jpeg")
                val body = NetraRequestBody.multipart(listOf(
                    netraPart
                ))

                val client = NetraClient.Builder(applicationContext)
                    .baseUrl("http://10.0.2.2:3001")
                    .build()
                client.post("/upload", body)
                    .asObject<Any>()
                    .withCache(Cache())
                    .whenOffline(OfflinePolicyAction.THROW_ERROR)
                    .whenSlowNetwork(SlowNetworkPolicyAction.USE_CACHE)
                    .enqueue { result, exception ->
                        Log.e("result", result?.statusCode.toString() )
                        Log.e("exception", exception?.message.toString())
                    }
            }
        }
    }
    fun handleGetImage() {
        val client = NetraClient.Builder(applicationContext)
            .baseUrl("http://10.0.2.2:3001")
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            client.get("/image")
                .asObject<ByteArray>()
                .withCache(Cache())
                .whenOffline(OfflinePolicyAction.USE_CACHE)
                .cancelWhenDestroyed()
                .executeStream(
                    onStreamReady = { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var response = ByteArray(0)

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            val chunk = buffer.copyOfRange(0, bytesRead)
                            response = response.plus(chunk)
                            Log.d(
                                "NetraSDK",
                                "Received a native chunk of size: ${chunk.size} bytes"
                            )
                        }
                        Log.d("NetraSDK", "Stream fully consumed auto-closed.")
                        _bitmap.value = BitmapFactory.decodeByteArray(response, 0, response.size)
                    },
                    onFailure = { exception ->
                        Log.e("NetraSDK", "Streaming failed: ${exception.message}")
                    }
                )
//            .enqueue { result ->
//                 _bitmap.value = BitmapFactory.decodeByteArray(result?.data?.get("data") as ByteArray, 0, (result.data?.get("data") as ByteArray).size)
//            }
        }
    }

    fun handlePostImage() {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun uriToByteArray(uri: Uri): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    fun handleGet() {
//        val client = NetraClient.Builder(applicationContext)
//            .baseUrl("http://10.0.2.2:3001")                                                                                                                                                                                                                                 
//            .circuitBreaker()
//            .addHeaders(mapOf("headercustom1" to "custom"))
//            .addConverterFactory(
//                NetraKotlinxConverter()
//            )
//            .build()

        val request = client!!.get("/?status=200&delay=3000")
            .slowMode()
            .addHeaders(mapOf("headercustom2" to "custom"))
            .asObject<Any>()
//            .withCache(Cache())
            .cancelWhenDestroyed()
            .whenSlowNetwork(SlowNetworkPolicyAction.TIMEOUT(2000.milliseconds))
            .whenOffline(OfflinePolicyAction.RETRY(4, 3.seconds))
            .addObserver(object : INetraObserver {
                override fun onNetworkChanged(event: NetworkEvent) {
                    Log.e(
                        "",
                        "request NetworkEvent observer here: ${event}}"
                    )
                }

                override fun onCacheChanged(event: CacheEvent) {
                    when (event) {
                        is CacheEvent.StaleCacheUsed -> {
                            Log.e(
                                "",
                                "StaleCacheUsed: ${event.request.command.url} ${event.ageMs} ${event.expiredByMs}}"
                            )
                        }

                        is CacheEvent.CacheMiss -> {
                            Log.e(
                                "",
                                "CacheMiss: ${event.request.command.url}"
                            )
                        }

                        is CacheEvent.CacheExpired -> {
                            Log.e(
                                "",
                                "CacheExpired: ${event.request.command.url} ${event.ageMs} ${event.expiredByMs}}"
                            )
                        }

                        is CacheEvent.CacheStored -> {
                            Log.e(
                                "",
                                "CacheStored: ${event.request.command} ${event.ageMs}}"
                            )
                        }

                        is CacheEvent.CacheHit -> {
                            Log.e(
                                "",
                                "CacheHit: ${event.request.command.url} ${event.ageMs}"
                            )
                        }
                    }
                }

                override fun onRequestChanged(event: RequestEvent) {
                    when (event) {
                        is RequestEvent.RequestExecuted -> {
                            Log.e(
                                "",
                                "RequestExecuted: ${event.request.command.url} "
                            )
                        }
                        is RequestEvent.RequestSuccess -> {
                            Log.e(
                                "",
                                "RequestSuccess: ${event.request.command.url} response: ${event.response.statusCode} ${event.response.data}"
                            )
                        }
                        is RequestEvent.RequestFailed -> {
                            Log.e(
                                "",
                                "RequestFailed: ${event.request.command.url} response: ${event.response?.statusCode} ${event.response?.data}"
                            )
                        }
                    }
                }

                override fun onQueueChanged(event: QueueEvent) {
                    when(event) {
                        is QueueEvent.RequestQueued -> {
                            Log.e(
                                "",
                                "RequestQueued: ${event.url} queueOrder: ${event.queueOrder} createdAt: ${event.createdAt}"
                            )
                        }

                        is QueueEvent.QueuedRequestSuccess -> {
                            Log.e(
                                "",
                                "QueuedRequestSuccess: ${event.url} statusCode: ${event.response.statusCode} data: ${event.response.data}"
                            )
                        }

                        is QueueEvent.QueuedRequestExecuted -> {
                            Log.e(
                                "",
                                "QueuedRequestExecuted: ${event.url}}"
                            )
                        }

                        is QueueEvent.QueuedRequestFailed -> {
                            Log.e(
                                "",
                                "QueuedRequestFailed: ${event.url}}"
                            )
                        }
                    }
                }
            })

//        try {
//            CoroutineScope(Dispatchers.IO).launch {
//                val result = request.execute()
//                Log.e("", " execute: ${result.statusCode} ${result.data}")
//            }
//        } catch (e: NetraException) {
//            Log.e("", "error execute: ${e.message}")
//        }
//
        try {
            request.enqueue { result, exception ->
                Log.e(
                    "result is success",
                    "code: ${result?.statusCode.toString()} message: ${result?.statusMessage.toString()} data: ${result?.data.toString()}"
                )
                Log.e("", "exeption: ${exception?.message} --- ${exception?.cause}")
            }
        } catch (e: NetraException) {
            Log.e("", "error execute: ${e.message}")
        }

        Handler(Looper.getMainLooper()).postDelayed({
         //   Log.e("", "cancelled")
           // request.cancel()
        }, (2000))
    }

    fun handlePost() {
        val client = NetraClient.Builder(applicationContext)
            //.baseUrl("http://10.0.2.2:3001")
            .baseUrl("https://jsonplaceholder.typicode.com")
            .addConverterFactory(
                NetraKotlinxConverter()
            )
            .build()
        val json = """
                                    {
                                    "name": "Sinem",
                                    "job": "developer"
                                    }
                                    """.trimIndent()
//        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val body = NetraRequestBody.create(json)
        val call = client.post("/users", body)
            .asObject<Any>()
            .withCache(Cache())
        try {
            CoroutineScope(Dispatchers.IO).launch {
                val response = call.execute()
                Log.e(
                    "response in main kt",
                    "${response.statusCode} ${response.data} message: ${response.statusMessage} "
                )
            }
        } catch (e: NetraException) {
            Log.e("", "error execute: ${e.message}")
        }

//            .enqueue { result ->
//                Log.e("result", "${result?.statusCode} ${result?.data} message: ${result?.statusMessage} " )
//            }
    }

    fun handlePut() {
        val client = NetraClient.Builder(applicationContext)
            //.baseUrl("http://10.0.2.2:3001")
            .baseUrl("https://jsonplaceholder.typicode.com")
            .addConverterFactory(
                NetraKotlinxConverter()
            )
            .build()
        val json = """
                                    {
                                    "name": "Sinem",
                                    "job": "not developer"
                                    }
                                    """.trimIndent()
        val body = NetraRequestBody.create(json)
        //client.get("/?status=200")
        client.put("/users/1", body)
            .asObject<Any>()
            .withCache(Cache())
            .enqueue { result, exception ->
            Log.e(
                "result is success",
                "code: ${result?.statusCode.toString()} message: ${result?.statusMessage.toString()} data: ${result?.data.toString()}"
            )
            Log.e("", "exeption: ${exception?.message}")
        }
    }

    fun handlePatch() {
        val client = NetraClient.Builder(applicationContext)
            .baseUrl("https://jsonplaceholder.typicode.com")
            .addConverterFactory(
                NetraKotlinxConverter()
            )
            .build()
        val json = """
                                    {
                                    "name": "Selin",
                                    }
                                    """.trimIndent()
        val body = NetraRequestBody.create(json)
        //client.get("/?status=200")
        client.patch("/users/1", body)
            .asObject<Any>()
            .withCache(Cache())
            .enqueue { result, exception ->
                Log.e(
                    "result is success",
                    "code: ${result?.statusCode.toString()} message: ${result?.statusMessage.toString()} data: ${result?.data.toString()}"
                )
                Log.e("", "exeption: ${exception?.message}")
            }
    }

    fun handleDelete() {
        val client = NetraClient.Builder(applicationContext)
            .baseUrl("https://jsonplaceholder.typicode.com")
            .addConverterFactory(
                NetraKotlinxConverter()
            )
            .build()
        //client.get("/?status=200")
        client.delete("/users/1")
            .asObject<Any>()
            .withCache(Cache())
            .enqueue { result, exception ->
                Log.e(
                    "result is success",
                    "code: ${result?.statusCode.toString()} message: ${result?.statusMessage.toString()} data: ${result?.data.toString()}"
                )
                Log.e("", "exeption: ${exception?.message}")
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = NetraClient.Builder(applicationContext)
            .addConverterFactory(NetraGsonConverter())
            .baseUrl("http://10.0.2.2:3001")
//            .circuitBreaker()
//            .addInterceptor(object : NetraInterceptor {
//                override fun intercept(chain: NetraInterceptor.NetraChain): NetraResponse {
////                    var attempt = 0
////                    var lastException: IOException? = null
////
////                    while (attempt < 5) {
////                        try {
//                    Log.e("", "custom interceptor")
//                           chain.proceed(chain.request())
////                            Log.e("", "attempt: ${attempt} response here: ${response.statusCode}")
////                            return response
////                        } catch (e: IOException) {
////                            lastException = e
////                            attempt++
////                            Thread.sleep(2000L * attempt)
////                        }
////                    }
////                    throw lastException!!
//                    return NetraResponse(
//                        data = mapOf("data" to {
//                            "here" to "here"
//                        }),
//                        statusCode = 204,
//                        statusMessage = "custom interceptor response",
//                        isCache = false,
//                    )
//                }
//
//            })
            .build()
        enableEdgeToEdge()
        setContent {
            NetraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Text(
                            text = "press",
                        )
                        Button(
                            onClick = {
                                handleGet()
                            }
                        ) {
                            Text(
                                text = "get",
                            )
                        }
                        Button(
                            onClick = {
                                handlePost()
                            }
                        ) {
                            Text(
                                text = "post",
                            )
                        }
                        Button(
                            onClick = {
                                handlePut()
                            }
                        ) {
                            Text(
                                text = "put",
                            )
                        }
                        Button(
                            onClick = {
                                handlePatch()
                            }
                        ) {
                            Text(
                                text = "patch",
                            )
                        }
                        Button(
                            onClick = {
                                handleDelete()
                            }
                        ) {
                            Text(
                                text = "delete",
                            )
                        }
                        Button(
                            onClick = {
                                handleGetImage()
                            }
                        ) {
                            Text(
                                text = "get image",
                            )
                        }
                        Button(
                            onClick = {
                                  handlePostImage ()
                            }
                        ) {
                            Text(
                                text = "post image",
                            )
                        }

                        if (_bitmap.value !== null) {
                            androidx.compose.foundation.Image(
                                bitmap = (_bitmap.value as Bitmap).asImageBitmap(),
                                contentDescription = "My Bitmap Image"
                            )
                        }
                    }
                }
            }
        }
    }
}