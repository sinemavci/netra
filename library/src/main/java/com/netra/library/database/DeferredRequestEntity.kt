package com.netra.library.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.netra.library.enums.DeferredOrigin
import com.netra.library.enums.DeferredStatus

@Entity(tableName = "deferred_request")
data class DeferredRequestEntity(
    @PrimaryKey val id: String,
    val url: String,
    val method: String,
    val body: String?,
    val headersJson: String,
    val converter: String?,
    val deferredOrigin: DeferredOrigin?,
    val status: DeferredStatus?,
    val timestamp: Long = System.currentTimeMillis(),
    )