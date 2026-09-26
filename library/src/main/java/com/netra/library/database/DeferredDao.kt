package com.netra.library.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netra.library.enums.DeferredStatus

@Dao
interface DeferredDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: DeferredRequestEntity)

    @Query("SELECT * FROM deferred_request WHERE status = :status ORDER BY timestamp ASC")
    suspend fun getAllRequests(status: DeferredStatus): List<DeferredRequestEntity>

    @Query("DELETE FROM deferred_request WHERE id = :id")
    suspend fun deleteRequest(id: String)

    @Query("SELECT * FROM deferred_request WHERE id = :id")
    suspend fun getRequest(id: String): DeferredRequestEntity

    @Query("UPDATE deferred_request SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: DeferredStatus)
}