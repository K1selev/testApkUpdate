package com.example.box.logs

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LogEntity::class], version = 1)
abstract class LogDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao

    companion object {
        @Volatile private var INSTANCE: LogDatabase? = null
        @Volatile private var instance: LogDatabase? = null

        fun getDatabase(context: Context): LogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LogDatabase::class.java,
                    "log_database"
                ).build().also { instance = it }
            }

        fun getInstance(context: Context): LogDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    LogDatabase::class.java,
                    "logs.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}