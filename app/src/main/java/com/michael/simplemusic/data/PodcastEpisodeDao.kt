package com.michael.simplemusic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastEpisodeDao {
    @Query("SELECT * FROM podcast_episodes WHERE channelId = :channelId ORDER BY pubDate DESC")
    fun getEpisodesForChannel(channelId: Int): Flow<List<PodcastEpisode>>

    @Query("SELECT * FROM podcast_episodes WHERE id = :id")
    suspend fun getEpisodeById(id: Int): PodcastEpisode?

    @Query("SELECT * FROM podcast_episodes WHERE channelId = :channelId AND guid = :guid")
    suspend fun getEpisodeByGuid(channelId: Int, guid: String): PodcastEpisode?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisode(episode: PodcastEpisode): Long

    @Update
    suspend fun updateEpisode(episode: PodcastEpisode)

    @Delete
    suspend fun deleteEpisode(episode: PodcastEpisode)

    @Query("DELETE FROM podcast_episodes WHERE channelId = :channelId AND downloadStatus != 3")
    suspend fun deleteNonDownloadedEpisodes(channelId: Int)

    @Query("SELECT * FROM podcast_episodes WHERE (downloadStatus IN (1, 2, 3)) AND isFinished = 0 ORDER BY pubDate DESC")
    fun getDownloadedEpisodes(): Flow<List<PodcastEpisode>>

    @Query("UPDATE podcast_episodes SET downloadStatus = 0 WHERE downloadStatus = 2")
    suspend fun clearDownloadingState()

    @Query("SELECT * FROM podcast_episodes")
    fun getAllEpisodes(): Flow<List<PodcastEpisode>>
 
    @Query("SELECT COUNT(*) FROM podcast_episodes WHERE downloadStatus = 2")
    suspend fun getActiveDownloadCount(): Int
}
