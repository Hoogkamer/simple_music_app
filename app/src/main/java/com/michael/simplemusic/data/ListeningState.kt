package com.michael.simplemusic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_states")
data class ListeningState(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val folderUri: String? = null,
    val folderDisplayName: String? = null,
    val currentTrackUri: String? = null,
    val currentTrackIndex: Int = 0,
    val currentPositionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatEnabled: Boolean = true
)
