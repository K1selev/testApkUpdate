package com.example.box.logs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: LogEntity)

    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs WHERE message LIKE :query ORDER BY timestamp DESC")
    fun searchLogs(query: String): Flow<List<LogEntity>>

    @Query("DELETE FROM logs")
    suspend fun deleteAllLogs()
}
