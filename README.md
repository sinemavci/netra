# Netra Android SDK

A flexible Android networking SDK built with Kotlin.
Netra provides:

- ⚡ Simple and fluent API
- 📦 GET, POST, PUT, PATCH, DELETE support
- 🧠 Smart caching system
- 📡 Offline request queueing
- 🐢 Slow network handling strategies
- 👀 Request lifecycle observers
- 🖼 Multipart file/image upload
- 🔄 Automatic queued request restoration
- 🧩 Converter support (`Kotlinx Serialization`, Gson, etc.)
- 🛑 Request cancellation
- 🔥 Circuit breaker support
- 🧵 Coroutine-friendly
- 🛜 Custom headers support
---

# Basic Usage

## Create Client

```kotlin
val client = NetraClient.Builder(applicationContext)
    .baseUrl("https://api.example.com")
    .build()
```

---

# GET Request

```kotlin
client.get("/users")
    .asObject<Any>()
    .enqueue { result ->
        println(result?.data)
    }
```
or
```kotlin
val request = client.get("/users").asObject<Any>()
val response = request.execute()
```

---

# POST Request

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
    .enqueue { result ->
        println(result?.statusCode)
    }
```

---

# PUT Request

```kotlin
client.put("/users/1", body)
    .asObject<Any>()
    .enqueue { result ->
        println(result?.statusCode)
    }
```

---

# PATCH Request

```kotlin
client.patch("/users/1", body)
    .asObject<Any>()
    .enqueue { result ->
        println(result?.statusCode)
    }
```

---

# DELETE Request

```kotlin
client.delete("/users/1")
    .asObject<Any>()
    .enqueue { result ->
        println(result?.statusCode)
    }
```

---

# Synchronous Request

```kotlin
val response = client.get("/users")
    .asObject<Any>()
    .execute()

println(response.statusCode)
```

---

# Cache Support

## Enable Cache

```kotlin
client.get("/users")
    .withCache(Cache())
    .enqueue { result ->
        println(result?.data)
    }
```

---

## Cache Events

Netra provides detailed cache lifecycle events to help developers monitor cache behavior and debug networking flows.

| Event | Description | Typical Scenario |
|---|---|---|
| `CacheHit` | Triggered when valid cached data is found and returned successfully. | User opens a screen and data is loaded directly from cache without a network request. |
| `CacheMiss` | Triggered when no cache entry exists for the requested resource. | First request to an endpoint before any cache has been stored. |
| `CacheStored` | Triggered after a successful network response is saved into cache storage. | Fresh API response is persisted for future offline or fast access usage. |
| `CacheExpired` | Triggered when cached data exists but its expiration time has passed. | SDK checks cache age and determines it is no longer valid. |
| `StaleCacheUsed` | Triggered when expired cache data is intentionally returned as a fallback strategy. | Network is slow/offline and SDK serves expired cache to improve UX. |

Example:

```kotlin
override fun onCacheChanged(event: CacheEvent) {
    when(event) {
        is CacheEvent.CacheHit -> {
            Log.e("", "Cache hit")
        }

        is CacheEvent.CacheMiss -> {
            Log.e("", "Cache miss")
        }

        is CacheEvent.CacheExpired -> {
            Log.e("", "Cache expired")
        }

        is CacheEvent.StaleCacheUsed -> {
            Log.e("", "Using stale cache")
        }

        is CacheEvent.CacheStored -> {
            Log.e("", "Cache stored")
        }
    }
}
```

---

# Offline Support

Netra can automatically queue requests while the device is offline.

```kotlin
client.get("/users")
    .whenOffline(OfflinePolicyAction.QUEUE)
```

Available offline actions:

| Action | Description                                        |
|---|----------------------------------------------------|
| `QUEUE` | Stores request and retries later                   |
| `USE_CACHE` | Uses cached response                               |
| `RETRY` | Client send request retries times until successful |
| `THROW_ERROR` | Throws network error                               |


---

# Slow Network Strategies

```kotlin
client.get("/users")
    .whenSlowNetwork(SlowNetworkPolicyAction.USE_CACHE)
```

Available actions:

| Action | Description                                                                                                                          |
|---|--------------------------------------------------------------------------------------------------------------------------------------|
| `USE_CACHE` | Returns cache immediately                                                                                                            |
| `WAIT` | Waits for network response                                                                                                           |
| `TIMEOUT` | Set callTimeout to client and waits until timeout. If the client can not continue receive data within timeout duration, throws error |

---

# Queue Events

Netra exposes detailed queue lifecycle events.

Available events:

- `RequestQueued`
- `QueuedRequestRestored`
- `QueuedRequestExecuted`
- `QueuedRequestFailed`

Example:

```kotlin
override fun onQueueChanged(event: RequestQueuedEvent) {
    when(event) {

        is RequestQueuedEvent.RequestQueued -> {
            Log.e("", "Request added to queue")
        }

        is RequestQueuedEvent.QueuedRequestRestored -> {
            Log.e("", "Queued request restored")
        }

        is RequestQueuedEvent.QueuedRequestExecuted -> {
            Log.e("", "Queued request executed")
        }

        is RequestQueuedEvent.QueuedRequestFailed -> {
            Log.e("", "Queued request failed")
        }
    }
}
```

---

# Observers

Attach observers to monitor request lifecycle events.

```kotlin
client.get("/users")
    .addObserver(object : INetraObserver {

        override fun onNetworkChanged(event: NetworkEvent) {
            Log.e("", "Network changed")
        }

        override fun onCacheChanged(event: CacheEvent) {
            Log.e("", "Cache event")
        }

        override fun onQueueChanged(event: RequestQueuedEvent) {
            Log.e("", "Queue event")
        }
    })
```

---

# Multipart Upload

## Upload Image

```kotlin
val part = NetraPart.file(
    name = "image",
    fileName = "photo.jpg",
    bytes = byteArray,
    mimeType = "image/jpeg"
)

val body = NetraRequestBody.multipart(
    listOf(part)
)

client.post("/upload", body)
    .asObject<Any>()
    .enqueue { result ->
        println(result?.statusCode)
    }
```

---

# Download Image

```kotlin
client.get("/image")
    .asObject<ByteArray>()
    .enqueue { result ->

        val bytes = result?.data?.get("data") as ByteArray

        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size
        )
    }
```

---

# Custom Headers

## Global Headers

```kotlin
val client = NetraClient.Builder(applicationContext)
    .addHeaders(
        mapOf(
            "Authorization" to "Bearer token"
        )
    )
    .build()
```

---

## Request Headers

```kotlin
client.get("/users")
    .addHeaders(
        mapOf(
            "Custom-Header" to "value"
        )
    )
```

---

# Converter Support

## Kotlinx Serialization

```kotlin
val client = NetraClient.Builder(applicationContext)
    .addConverterFactory(
        NetraKotlinxConverter()
    )
    .build()
```

---

# Circuit Breaker

```kotlin
val client = NetraClient.Builder(applicationContext)
    .circuitBreaker()
    .build()
```

---

# Cancel Request

```kotlin
val request = client.get("/users")

request.cancel()
```

---

# Example Response

```kotlin
result?.statusCode
result?.statusMessage
result?.headers
result?.data
```

---

# Roadmap

- [ ] WebSocket support
- [ ] Logging interceptor
- [ ] Request deduplication
---

# Why Netra?

Netra focuses on real-world mobile networking problems:

- unreliable connections
- offline-first architecture
- slow networks
- cache consistency
- request recovery
- developer observability

Instead of only being an HTTP client, Netra aims to provide a resilient networking layer for modern Android applications.

---

# License

```text
MIT License
```