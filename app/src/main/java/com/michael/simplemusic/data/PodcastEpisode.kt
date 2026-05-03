package com.michael.simplemusic.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcast_episodes",
    foreignKeys = [
        ForeignKey(
            entity = AudioChannel::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("channelId"),
        Index(value = ["channelId", "guid"], unique = true)
    ]
)
data class PodcastEpisode(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val channelId: Int,
    val title: String,
    val description: String? = null,
    val pubDate: Long? = null,
    val streamUrl: String, // Remote audio URL
    val localPath: String? = null, // Path if downloaded
    val downloadStatus: Int = STATUS_IDLE,
    val downloadProgress: Int = 0,
    val durationMs: Long = 0L,
    val playbackPositionMs: Long = 0L,
    val isFinished: Boolean = false,
    val guid: String // Unique ID from RSS to prevent duplicates
) {
    companion object {
        const val STATUS_IDLE = 0
        const val STATUS_QUEUED = 1
        const val STATUS_DOWNLOADING = 2
        const val STATUS_DOWNLOADED = 3
    }

    val isDownloaded: Boolean get() = downloadStatus == STATUS_DOWNLOADED
    val isDownloading: Boolean get() = downloadStatus == STATUS_DOWNLOADING
    val isQueued: Boolean get() = downloadStatus == STATUS_QUEUED
}
