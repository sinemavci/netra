package com.netra.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import com.netra.library.observers.NetworkEvent
import com.netra.library.observers.RequestQueuedEvent
import com.netra.library.enums.OfflinePolicyAction
import com.netra.library.enums.SlowNetworkPolicyAction
import com.netra.library.converter.NetraKotlinxConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                    .withCache(Cache(null))
                    .whenOffline(OfflinePolicyAction.THROW_ERROR)
                    .whenSlowNetwork(SlowNetworkPolicyAction.USE_CACHE)
                    .enqueue { result ->
                        Log.e("result", result?.statusCode.toString() )
                    }
            }
        }
    }
    fun handleGetImage() {
        val client = NetraClient.Builder(applicationContext)
            .baseUrl("http://10.0.2.2:3001")
            .build()
        client.get("/image")
            .asObject<ByteArray>()
            .withCache(Cache(null))
            .whenOffline(OfflinePolicyAction.USE_CACHE)
            .enqueue { result ->
                 _bitmap.value = BitmapFactory.decodeByteArray(result?.data?.get("data") as ByteArray, 0, (result.data?.get("data") as ByteArray).size)
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
        val client = NetraClient.Builder(applicationContext)
            .baseUrl("http://10.0.2.2:3001")
            .circuitBreaker()
            .addHeaders(mapOf("headercustom1" to "custom"))
            .addConverterFactory(
                NetraKotlinxConverter()
            )
            .build()

        val request = client.get("/?status=200&delay=2000")
            .slowMode()
            .addHeaders(mapOf("headercustom2" to "custom"))
            .asObject<Any>()
            .withCache(Cache())
            .whenOffline(OfflinePolicyAction.QUEUE)
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
                                "request CacheEvent.StaleCacheUsed observer here: ${event.key} ${event.ageMs} ${event.expiredByMs}}"
                            )
                        }

                        is CacheEvent.CacheMiss -> {
                            Log.e(
                                "",
                                "request CacheEvent.CacheMiss observer here: ${event.key}"
                            )
                        }

                        is CacheEvent.CacheExpired -> {
                            Log.e(
                                "",
                                "request CacheEvent.CacheExpired observer here: ${event.key} ${event.ageMs} ${event.expiredByMs}}"
                            )
                        }

                        is CacheEvent.CacheStored -> {
                            Log.e(
                                "",
                                "request CacheEvent.CacheStored observer here: ${event.key} ${event.ageMs}}"
                            )
                        }

                        is CacheEvent.CacheHit -> {
                            Log.e(
                                "",
                                "request CacheEvent.CacheHit observer here: ${event.key} ${event.ageMs}"
                            )
                        }
                    }
                }

                override fun onQueueChanged(event: RequestQueuedEvent) {
                    when (event) {
                        is RequestQueuedEvent.RequestQueued -> {
                            Log.e(
                                "",
                                "request RequestQueuedEvent.RequestQueued observer here: ${event.key} queueOrder: ${event.queueOrder}"
                            )
                        }
                        is RequestQueuedEvent.QueuedRequestRestored -> {
                            Log.e(
                                "",
                                "request RequestQueuedEvent.QueuedRequestRestored observer here: ${event.key}"
                            )
                        }
                        is RequestQueuedEvent.QueuedRequestExecuted -> {
                            Log.e(
                                "",
                                "request RequestQueuedEvent.QueuedRequestExecuted observer here: ${event.key} statusCode: ${event.response.statusCode}"
                            )
                        }
                        is RequestQueuedEvent.QueuedRequestFailed -> {
                            Log.e(
                                "",
                                "request RequestQueuedEvent.QueuedRequestFailed observer here: ${event.key}"
                            )
                        }
                    }
                }
            })

           val response = request.execute()

            request.enqueue { result ->
                result?.headers?.forEach { string, string1 ->
                    Log.e("", "netra enqueue: headers: ${string} ${string1}")
                }
                Log.e(
                    "result is success",
                    "code: ${result?.statusCode.toString()} message: ${result?.statusMessage.toString()} data: ${result?.data.toString()}"
                )
//
        }
//        Handler(Looper.getMainLooper()).postDelayed({
//            Log.e("", "cancelled")
//            request.cancel()
//        }, (2000))
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
            .withCache(Cache(null))
        val response = call.execute()
        Log.e(
            "response in main kt",
            "${response.statusCode} ${response.data} message: ${response.statusMessage} "
        )
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
            .withCache(Cache(null))
            .enqueue { result ->
                Log.e("result", result?.statusCode.toString() )
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
            .withCache(Cache(null))
            .enqueue { result ->
                Log.e("result", result?.statusCode.toString() )
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
            .withCache(Cache(null))
            .enqueue { result ->
                Log.e("result", result?.statusCode.toString() )
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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