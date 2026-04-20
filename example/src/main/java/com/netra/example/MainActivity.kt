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
import java.nio.file.Path

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
                                Log.e("click", "click")
                                val client = NetraClient.Builder(applicationContext)
                                    .baseUrl("http://10.0.2.2:3001")
                                    .addConverterFactory(
                                        NetraGsonConverter()
                                    )
                                    .build()

                                client.get("/?status=200")
                                    .asList<Repo>()
                                    .withCache(Cache(null))
                                    .enqueue { result ->
                                        Log.e("result", result.toString())
                                        if (result is Status.Success<*>) {
                                            Log.e("result is success", result.response.toString())
                                        }
                                    }
                            }
                        ) {
                            Text(
                                text = "press",
                            )
                        }
                    }
                }
            }
        }
    }
}