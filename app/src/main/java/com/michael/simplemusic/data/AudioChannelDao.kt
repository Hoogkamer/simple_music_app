package com.michael.simplemusic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChannelDao {
    @Query("SELECT * FROM audio_channels ORDER BY lastPlayedTime DESC")
    fun getAllChannels(): Flow<List<AudioChannel>>

    @Query("SELECT * FROM audio_channels WHERE id = :id")
    suspend fun getChannelById(id: Int): AudioChannel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: AudioChannel): Long

    @Update
    suspend fun updateChannel(channel: AudioChannel)

    @Delete
    suspend fun deleteChannel(channel: AudioChannel)

    @Query("SELECT COUNT(*) FROM audio_channels")
    suspend fun getChannelCount(): Int
}
