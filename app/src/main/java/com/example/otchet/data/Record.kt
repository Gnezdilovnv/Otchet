package com.example.otchet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "records")
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String,
    val direction: String,
    val point: String,
    val type: String,
    val freqVideo: String,
    val freqControl: String,
    val suppressed: String,
    val exported: Boolean = false
)
