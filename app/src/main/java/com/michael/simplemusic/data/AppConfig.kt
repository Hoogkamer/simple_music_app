package com.michael.simplemusic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_config")
data class AppConfig(
    @PrimaryKey val id: Int = 1,
    val activeMusicChannelId: Int? = null,
    val activeRadioChannelId: Int? = null,
    val activePodcastChannelId: Int? = null,
    val activePodcastEpisodeId: Int? = null,
    val lastCategory: String = "CHANNELS",
    val hidePlayedEpisodes: Boolean = false,
    val showOnlyInProgressPodcasts: Boolean = false
)
