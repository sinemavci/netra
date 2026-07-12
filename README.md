# Netra Android SDK

A flexible Android networking SDK built with Kotlin.
Netra provides:

- ⚡ Simple and fluent API
- 📦 GET, POST, PUT, PATCH, DELETE support
- 🧠 Smart caching system (memory + disk)
- 📡 Offline request queueing
- 🐢 Slow network handling strategies
- 👀 Request lifecycle observers
- 🖼 Multipart file/image upload
- 🔄 Automatic queued request restoration
- 🧩 Converter support (`Kotlinx Serialization`, `Gson`, `Moshi`)
- 🛑 Request cancellation
- 🔥 Circuit breaker support
- 🧵 Coroutine-friendly
- 🛜 Custom headers support

---

## Requirements

Add the following permission to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
```

Netra initializes automatically via `ContentProvider` — no `Application` class setup required.

---

## Basic Usage

### Create Client

```kotlin
val client = NetraClient.Builder(applicationContext)
    .baseUrl("https://api.example.com")
    .build()
```

---

## Typed Responses with `asObject<T>`

Netra uses Kotlin reified generics to deserialize responses directly into your model — no casting required.

```kotlin
data class User(val id: Int, val name: String)

client.get("/users/1")
    .asObject<User>()
    .enqueue { result, exception ->
        val user: User? = result?.data  // direkt User, cast yok
    }
```

For lists:

```kotlin
client.get("/users")
    .asList<User>()
    .enqueue { result, exception ->
        val users: List<User>? = result?.data
    }
```

---

## GET Request

```kotlin
client.get("/users")
    .asObject<Any>()
    .enqueue { result, exception ->
        println(result?.data)
        println(exception?.message)
    }
```

Synchronous:

```kotlin
val response = client.get("/users")
    .asObject<Any>()
    .execute()

println(response.statusCode)
```

---

## POST Request

```kotlin
val json = """
{
   "name": "Sinem",
   "job": "developer"
}
""".trimIndent()

val body = NetraRequestBody.create(json)

client.post("/users", body)
    .asObject<Any>()
    .enqueue { result, exception ->
        println(result?.statusCode)
        println(exception?.message)
    }
```

---

## PUT Request

```kotlin
client.put("/users/1", body)
    .asObject<Any>()
    .enqueue { result, exception ->
        println(result?.statusCode)
    }
```

---

## PATCH Request

```kotlin
client.patch("/users/1", body)
    .asObject<Any>()
    .enqueue { result, exception ->
        println(result?.statusCode)
    }
```

---

## DELETE Request

```kotlin
client.delete("/users/1")
    .asObject<Any>()
    .enqueue { result, exception ->
        println(result?.statusCode)
    }
```

---

## Example Response

```kotlin
result?.statusCode      // Int
result?.statusMessage   // String?
result?.headers         // Map<String, String>?
result?.data            // T?
result?.isCache         // Boolean? — true if served from cache
```

### Exception Types

| Exception | Description |
|---|---|
| `NetraTimeoutException` | Request timed out |
| `NetraDnsException` | DNS resolution failed |
| `NetraConnectionException` | Could not connect to server |
| `NetraSslException` | SSL/TLS error |
| `NetraSocketException` | Socket-level error |
| `NetraNetworkException` | General network error |

```kotlin
client.get("/users")
    .asObject<Any>()
    .enqueue { result, exception ->
        when (exception) {
            is NetraTimeoutException -> println("Timeout!")
            is NetraConnectionException -> println("No connection!")
            else -> println(exception?.message)
        }
    }
```

---

## Cache Support

```kotlin
client.get("/users")
    .asObject<Any>()
    .withCache(Cache())
    .enqueue { result, exception ->
        println(result?.isCache)  // true if from cache
        println(result?.data)
    }
```

Custom TTL (milliseconds):

```kotlin
.withCache(Cache(ttl = 60_000L))  // 1 minute
```

---

## Offline Support

Netra can automatically handle requests when the device is offline.

```kotlin
client.get("/users")
    .asObject<Any>()
    .whenOffline(OfflinePolicyAction.QUEUE)
    .enqueue { result, exception -> }
```

Available offline actions:

| Action | Description |
|---|---|
| `QUEUE` | Stores request and retries automatically when connection is restored |
| `USE_CACHE` | Returns cached response if available |
| `RETRY(retries, retryInterval)` | Retries the request N times with a given interval |
| `THROW_ERROR` | Immediately throws a network error |

### RETRY example:

```kotlin
client.get("/users")
    .asObject<Any>()
    .whenOffline(OfflinePolicyAction.RETRY(retries = 3, retryInterval = 2.seconds))
    .enqueue { result, exception -> }
```

---

## Slow Network Strategies

```kotlin
client.get("/users")
    .asObject<Any>()
    .whenSlowNetwork(SlowNetworkPolicyAction.USE_CACHE)
    .enqueue { result, exception -> }
```

Available actions:

| Action | Description |
|---|---|
| `USE_CACHE` | Returns cached response immediately |
| `WAIT(delay)` | Waits the given duration before sending the request |
| `TIMEOUT(duration)` | Applies a strict timeout; throws error if exceeded |

### TIMEOUT example:

```kotlin
.whenSlowNetwork(SlowNetworkPolicyAction.TIMEOUT(2000.milliseconds))
```

---

## Observers

Attach observers to monitor request, cache, queue, and network lifecycle events.

```kotlin
client.get("/users")
    .asObject<Any>()
    .addObserver(object : INetraObserver {

        override fun onRequestChanged(event: RequestEvent) {
            when (event) {
                is RequestEvent.RequestExecuted -> println("Executing: ${event.request.command.url}")
                is RequestEvent.RequestSuccess -> println("Success: ${event.response.statusCode}")
                is RequestEvent.RequestFailed -> println("Failed: ${event.exception?.message}")
            }
        }

        override fun onCacheChanged(event: CacheEvent) {
            when (event) {
                is CacheEvent.CacheHit -> println("Cache hit: age ${event.ageMs}ms")
                is CacheEvent.CacheMiss -> println("Cache miss")
                is CacheEvent.CacheStored -> println("Cache stored")
                is CacheEvent.CacheExpired -> println("Cache expired")
                is CacheEvent.StaleCacheUsed -> println("Stale cache used: expired by ${event.expiredByMs}ms")
            }
        }

        override fun onQueueChanged(event: QueueEvent) {
            when (event) {
                is QueueEvent.RequestQueued -> println("Queued: ${event.url} order: ${event.queueOrder}")
                is QueueEvent.QueuedRequestExecuted -> println("Queue executing: ${event.url}")
                is QueueEvent.QueuedRequestSuccess -> println("Queue success: ${event.response.statusCode}")
                is QueueEvent.QueuedRequestFailed -> println("Queue failed: ${event.url}")
            }
        }

        override fun onNetworkChanged(event: NetworkEvent) {
            when (event) {
                is NetworkEvent.Offline -> println("Offline")
                is NetworkEvent.SlowNetwork -> println("Slow network")
                is NetworkEvent.ConnectionRestored -> println("Connection restored")
            }
        }
    })
    .enqueue { result, exception -> }
```

All methods have default (empty) implementations — override only what you need:

```kotlin
.addObserver(object : INetraObserver {
    override fun onRequestChanged(event: RequestEvent) {
        // only this, others are no-ops
    }
})
```

### Cache Events

| Event | Description |
|---|---|
| `CacheHit` | Valid cached data found and returned |
| `CacheMiss` | No cache entry exists |
| `CacheStored` | Response saved to cache |
| `CacheExpired` | Cache exists but TTL has passed |
| `StaleCacheUsed` | Expired cache returned as fallback |

### Queue Events

| Event | Description |
|---|---|
| `RequestQueued` | Request stored in offline queue |
| `QueuedRequestExecuted` | Queued request is being retried |
| `QueuedRequestSuccess` | Queued request succeeded |
| `QueuedRequestFailed` | Queued request failed on retry |

### Network Events

| Event | Description |
|---|---|
| `Offline` | Device lost internet connection |
| `SlowNetwork` | Network is congested or degraded |
| `ConnectionRestored` | Internet connection is back |

---

## Streaming

For large responses (images, files, audio), use `executeStream` to consume data chunk by chunk without loading it all into memory.

```kotlin
coroutineScope.launch {
    client.get("/video")
        .asObject<ByteArray>()
        .executeStream(
            onStreamReady = { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val chunk = buffer.copyOfRange(0, bytesRead)
                    // process chunk
                }
            },
            onFailure = { exception ->
                println("Stream failed: ${exception.message}")
            }
        )
}
```

---

## Download Image

```kotlin
client.get("/image")
    .asObject<ByteArray>()
    .enqueue { result, exception ->
        val bytes: ByteArray? = result?.data
        if (bytes != null) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
```

---

## Multipart Upload

```kotlin
val part = NetraPart.file(
    name = "image",
    fileName = "photo.jpg",
    bytes = byteArray,
    mimeType = "image/jpeg"
)

val body = NetraRequestBody.multipart(listOf(part))

client.post("/upload", body)
    .asObject<Any>()
    .enqueue { result, exception ->
        println(result?.statusCode)
    }
```

---

## Custom Headers

### Global Headers

```kotlin
val client = NetraClient.Builder(applicationContext)
    .baseUrl("https://api.example.com")
    .addHeaders(mapOf("Authorization" to "Bearer token"))
    .build()
```

### Per-Request Headers

```kotlin
client.get("/users")
    .addHeaders(mapOf("X-Custom-Header" to "value"))
    .asObject<Any>()
    .enqueue { result, exception -> }
```

---

## Converter Support

| Converter | Class |
|---|---|
| Gson | `NetraGsonConverter()` |
| Kotlinx Serialization | `NetraKotlinxConverter()` |
| Moshi | `NetraMoshiConverter()` |

```kotlin
val client = NetraClient.Builder(applicationContext)
    .baseUrl("https://api.example.com")
    .addConverterFactory(NetraKotlinxConverter())
    .build()
```

---

## Circuit Breaker

Automatically stops sending requests after a threshold of consecutive failures.

```kotlin
val client = NetraClient.Builder(applicationContext)
    .baseUrl("https://api.example.com")
    .circuitBreaker(failureThreshold = 5, retryDelayMs = 1000L)
    .build()
```

---

## Custom Interceptor

Global interceptors are applied to all requests created by the client.

```kotlin
val client = NetraClient.Builder(applicationContext)
    .baseUrl("https://api.example.com")
    .addInterceptor(object : NetraInterceptor {
        override fun intercept(chain: NetraInterceptor.NetraChain): NetraResponse<*> {
            val request = chain.request()
            println("Request URL: ${request.url}")

            val modifiedRequest = request.newBuilder()
                .addHeader("X-App-Version", "1.0.0")
                .build()

            val response = chain.proceed(modifiedRequest)
            println("Response Code: ${response.statusCode}")
            return response
        }
    })
    .build()
```

---

## Cancel Request

### Manual cancel

```kotlin
val request = client.get("/users").asObject<Any>()
request.cancel()
```

### Auto-cancel on Activity destroy

```kotlin
client.get("/users")
    .asObject<Any>()
    .cancelWhenDestroyed()
    .enqueue { result, exception -> }
```

---

## Roadmap

- [ ] WebSocket support
- [ ] Request deduplication
- [ ] `executeStream` offline/slow network handling

---

## Why Netra?

Netra focuses on real-world mobile networking problems:

- Unreliable connections
- Offline-first architecture
- Slow networks
- Cache consistency
- Request recovery
- Developer observability

Instead of only being an HTTP client, Netra aims to provide a resilient networking layer for modern Android applications.

---

## License

```
MIT License
```