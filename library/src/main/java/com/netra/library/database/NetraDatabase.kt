package com.netra.library.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DeferredRequestEntity::class], version = 1)
abstract class NetraDatabase : RoomDatabase() {
    abstract fun deferredDao(): DeferredDao

    companion object {
        @Volatile
        private var INSTANCE: NetraDatabase? = null

        fun getDatabase(context: Context): NetraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NetraDatabase::class.java,
                    "netra_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}