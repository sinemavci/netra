package com.netra.library

import okhttp3.RequestBody

sealed class Command(val url: String) {
    data class Get(val u: String) : Command(u)

    data class Post(val u: String, val body: RequestBody) : Command(u)

    data class Put(val u: String, val body: RequestBody) : Command(u)

    data class Patch(val u: String, val body: RequestBody) : Command(u)

    data class Delete(val u: String, val body: RequestBody?) : Command(u)

    data class PostImage(val u: String, val imageBytes: ByteArray, val fileName: String) : Command(u) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PostImage

            if (u != other.u) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
            if (fileName != other.fileName) return false

            return true
        }

        override fun hashCode(): Int {
            var result = u.hashCode()
            result = 31 * result + imageBytes.contentHashCode()
            result = 31 * result + fileName.hashCode()
            return result
        }
    }
}