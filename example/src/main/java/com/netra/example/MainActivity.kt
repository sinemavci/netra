package com.netra.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.netra.example.ui.theme.NetraTheme
import com.netra.library.Cache
import com.netra.library.NetraClient
import com.netra.library.Status
import com.netra.library.converter.NetraGsonConverter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class Repo(
    val id: Int,
    val name: String
)

//val client = NetraClient.Builder()
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
        client.get("/?status=404")
       // client.get("/users")
            .slowMode()
            .addHeader("headercustom", "custom")
            .asObject<Any>()
            .withCache(Cache(null))
            .enqueue { result ->
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
                    }
                }
            }
        }
    }
}