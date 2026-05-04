package com.michael.simplemusic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ChannelType {
    FOLDER, RADIO, PODCAST
}

@Entity(tableName = "audio_channels")
data class AudioChannel(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: ChannelType = ChannelType.FOLDER,
    
    // For FOLDER and PODCAST
    val folderUri: String? = null,
    val folderDisplayName: String? = null,
    
    // For RADIO and PODCAST (streaming)
    val streamUrl: String? = null,
    
    // Playback state
    val currentTrackUri: String? = null,
    val currentTrackIndex: Int = 0,
    val currentTrackTitle: String? = null,
    val currentTrackArtist: String? = null,
    val currentTrackAlbum: String? = null,
    val currentPositionMs: Long = 0L,
    val currentTrackDurationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatEnabled: Boolean = true,
    
    // Metadata
    val lastPlayedTime: Long = System.currentTimeMillis()
)
