package com.netra.library.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: PersistentRequest)

    @Query("SELECT * FROM offline_queue ORDER BY timestamp ASC")
    suspend fun getAllRequests(): List<PersistentRequest>

    @Query("DELETE FROM offline_queue WHERE id = :id")
    suspend fun deleteRequest(id: String)
}