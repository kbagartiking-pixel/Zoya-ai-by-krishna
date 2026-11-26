package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessageEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ZoyaDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: ZoyaDatabase? = null

        fun getInstance(context: Context): ZoyaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZoyaDatabase::class.java,
                    "zoya_ai_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
