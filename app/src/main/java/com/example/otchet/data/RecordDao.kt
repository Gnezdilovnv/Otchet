package com.example.otchet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: Record)

    @Query("SELECT * FROM records ORDER BY id DESC")
    fun getAllRecords(): Flow<List<Record>>

    @Query("UPDATE records SET exported = 1 WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>)
}
