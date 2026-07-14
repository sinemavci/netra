package com.netra.library.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_queue")
data class PersistentRequest(
    @PrimaryKey val id: String,
    val url: String,
    val method: String,
    val body: String?,
    val headersJson: String,
    val converter: String?,
    val timestamp: Long = System.currentTimeMillis(),

)