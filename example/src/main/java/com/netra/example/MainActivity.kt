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
import com.netra.library.NetraClient
import com.netra.library.OfflinePolicyAction
import com.netra.library.SlowNetworkPolicyAction
import com.netra.library.Status
import com.netra.library.converter.NetraGsonConverter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

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
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "image",
                        "exampleImage",
                        byteArray.toRequestBody("image/jpeg".toMediaType())
                    )
                    .build()

                val client = NetraClient.Builder(applicationContext)
                    .baseUrl("http://10.0.2.2:3001")
                    .build()
                client.post("/upload", requestBody)
                    .asObject<Any>()
                    .withCache(Cache(null))
                    .whenOffline(OfflinePolicyAction.THROW_ERROR)
                    .whenSlowNetwork(SlowNetworkPolicyAction.CACHE)
                    .enqueue { result ->
                        Log.e("result", result.toString())
                        if (result is Status.Success<*>) {
                            Log.e("result is success", result.response.toString())
                        }
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
            .enqueue { result ->
                if (result is Status.Success<*>) {
                    Log.e("here", "here: ${result.response?.javaClass}")
                    _bitmap.value = BitmapFactory.decodeByteArray(result.response as ByteArray, 0, (result.response as ByteArray).size)
                } else if (result is Status.Retrying) {
                    Log.e("result is Retrying", result.code.toString())
                } else if (result is Status.Error) {
                    Log.e("result is Error", result.code.toString())
                } else {
                    Log.e("result is Failure", (result as Status.Failure).message.toString())
                }
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
//            .baseUrl("https://jsonplaceholder.typicode.com")
            .addConverterFactory(
                NetraGsonConverter()
            )
            .build()
        val json = """
                                    {
                                    "name": "Sinem",
                                    "job": "developer"
                                    }
                                    """.trimIndent()
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = client.get("/?status=200&delay=8000")
            // client.get("/users")
            .slowMode()
            .addHeader("headercustom", "custom")
            .asObject<Any>()
            .withCache(Cache(null))
            .whenOffline(OfflinePolicyAction.QUEUE)
            .whenSlowNetwork(SlowNetworkPolicyAction.TIMEOUT(timeout = 3))

        request.enqueue { result ->
            if (result is Status.Success<*>) {
                Log.e("result is success", result.response.toString())
            } else if (result is Status.Retrying) {
                Log.e("result is Retrying", result.code.toString())
            } else if (result is Status.Error) {
                Log.e("result is Error", result.code.toString())
            } else {
                Log.e("result is Failure", (result as Status.Failure).message.toString())
            }
        }

//        Handler(Looper.getMainLooper()).postDelayed({
//            Log.e("", "cancelled")
//            request.cancel()
//        }, (1000))
    }

    fun handlePost() {
        val client = NetraClient.Builder(applicationContext)
            //.baseUrl("http://10.0.2.2:3001")
            .baseUrl("https://jsonplaceholder.typicode.com")
            .addConverterFactory(
                NetraGsonConverter()
            )
            .build()
        val json = """
                                    {
                                    "name": "Sinem",
                                    "job": "developer"
                                    }
                                    """.trimIndent()
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        //client.get("/?status=200")
        client.post("/users", body)
            .asObject<Any>()
            .withCache(Cache(null))
            .enqueue { result ->
                Log.e("result", result.toString())
                if (result is Status.Success<*>) {
                    Log.e("result is success", result.response.toString())
                }
            }
    }

    fun handlePut() {
        val client = NetraClient.Builder(applicationContext)
            //.baseUrl("http://10.0.2.2:3001")
            .baseUrl("https://jsonplaceholder.typicode.com")
            .addConverterFactory(
                NetraGsonConverter()
            )
            .build()
        val json = """
                                    {
                                    "name": "Sinem",
                                    "job": "not developer"
                                    }
                                    """.trimIndent()
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        //client.get("/?status=200")
        client.put("/users/1", body)
            .asObject<Any>()
            .withCache(Cache(null))
            .enqueue { result ->
                Log.e("result", result.toString())
                if (result is Status.Success<*>) {
                    Log.e("result is success", result.response.toString())
                }
            }
    }

    fun handlePatch() {
        val client = NetraClient.Builder(applicationContext)
            //.baseUrl("http://10.0.2.2:3001")
            .baseUrl("https://jsonplaceholder.typicode.com")
            .addConverterFactory(
                NetraGsonConverter()
            )
            .build()
        val json = """
                                    {
                                    "name": "Selin",
                                    }
                                    """.trimIndent()
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        //client.get("/?status=200")
        client.patch("/users/1", body)
            .asObject<Any>()
            .withCache(Cache(null))
            .enqueue { result ->
                if (result is Status.Success<*>) {
                    Log.e("result is success", result.response.toString())
                } else if (result is Status.Retrying) {
                    Log.e("result is Retrying", result.code.toString())
                } else if (result is Status.Error) {
                    Log.e("result is Error", result.code.toString())
                }else {
                    Log.e("result is Failure", (result as Status.Failure).message.toString())
                }
            }
    }

    fun handleDelete() {
        val client = NetraClient.Builder(applicationContext)
            //.baseUrl("http://10.0.2.2:3001")
            .baseUrl("https://jsonplaceholder.typicode.com")
            .addConverterFactory(
                NetraGsonConverter()
            )
            .build()
        //client.get("/?status=200")
        client.delete("/users/1")
            .asObject<Any>()
            .withCache(Cache(null))
            .enqueue { result ->
                Log.e("result", result.toString())
                if (result is Status.Success<*>) {
                    Log.e("result is success", result.response.toString())
                }
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
                                text = "get image",
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