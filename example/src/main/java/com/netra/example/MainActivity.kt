package com.netra.example

import android.os.Bundle
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
import com.netra.library.NetraClient
import com.netra.library.converter.NetraGsonConverter

data class Repo(
    val id: Int,
    val name: String
)
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
                                val client = NetraClient.Builder()
                                    .baseUrl("https://api.github.com/users/octocat/repos")
                                    .addConverterFactory(
                                        NetraGsonConverter()
                                    ).build()

                                client.get("https://api.github.com/users/octocat/repos")
                                    .asList<Repo>().enqueue { result ->
                                        print("result: ${result?.get(0)?.name}}")
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