package com.michael.simplemusic.podcast

import com.michael.simplemusic.data.PodcastEpisode
import com.michael.simplemusic.data.PodcastEpisodeDao
import com.michael.simplemusic.data.AudioChannel
import com.michael.simplemusic.data.ChannelType
import androidx.work.*
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PodcastRepository(private val context: android.content.Context, private val dao: PodcastEpisodeDao, private val baseDir: File) {
    private val parser = PodcastParser()
    private val podcastDir = File(baseDir, "podcasts").apply { if (!exists()) mkdirs() }

    fun getEpisodesForChannel(channelId: Int): Flow<List<PodcastEpisode>> {
        return dao.getEpisodesForChannel(channelId)
    }

    fun getRecentEpisodes(): Flow<List<PodcastEpisode>> {
        val twoWeeksAgo = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
        return dao.getRecentEpisodes(twoWeeksAgo)
    }

    fun getEpisodeFlowById(id: Int): Flow<PodcastEpisode?> {
        return dao.getEpisodeFlowById(id)
    }

    suspend fun refreshFeed(channelId: Int, feedUrl: String, sinceDate: Long? = null): Pair<String?, Int> {
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(15000) { // 15s timeout
                    val connection = URL(feedUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    
                    val inputStream = connection.inputStream
                    val (title, episodes) = parser.parse(inputStream, channelId, sinceDate)
                    
                    var newCount = 0
                    episodes.forEach { episode ->
                        if (dao.insertEpisode(episode) != -1L) {
                            newCount++
                        }
                    }
                    title to newCount
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null to 0
            }
        }
    }

    suspend fun downloadEpisode(episode: PodcastEpisode) {
        withContext(Dispatchers.IO) {
            dao.updateEpisode(episode.copy(downloadStatus = PodcastEpisode.STATUS_QUEUED))
            val data = Data.Builder().putInt("episodeId", episode.id).build()
            val request = OneTimeWorkRequestBuilder<PodcastDownloadWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("podcast_download")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    suspend fun deleteEpisodeFile(episode: PodcastEpisode) {
        withContext(Dispatchers.IO) {
            episode.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            dao.updateEpisode(episode.copy(localPath = null, downloadStatus = PodcastEpisode.STATUS_IDLE, downloadProgress = 0))
        }
    }

    suspend fun markAsPlayed(episode: PodcastEpisode) {
        withContext(Dispatchers.IO) {
            // First, delete the file if it exists
            episode.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            // Then update the DB in one go to avoid race conditions
            dao.updateEpisode(episode.copy(
                isFinished = true,
                localPath = null,
                downloadStatus = PodcastEpisode.STATUS_IDLE,
                downloadProgress = 0
            ))
        }
    }

    suspend fun markAsUnplayed(episode: PodcastEpisode) {
        withContext(Dispatchers.IO) {
            episode.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            dao.updateEpisode(episode.copy(
                isFinished = false,
                localPath = null,
                downloadStatus = PodcastEpisode.STATUS_IDLE,
                downloadProgress = 0
            ))
        }
    }

    suspend fun updatePlaybackPosition(episode: PodcastEpisode, positionMs: Long, durationMs: Long) {
        withContext(Dispatchers.IO) {
            var isFinished = false
            
            // Auto-finish if < 30 seconds remaining
            if (durationMs > 0 && (durationMs - positionMs) < 30000) {
                isFinished = true
            }

            // Keep as finished if already finished, or if auto-finish triggered
            val finalFinished = episode.isFinished || isFinished

            val updated = episode.copy(playbackPositionMs = positionMs, durationMs = durationMs, isFinished = finalFinished)
            dao.updateEpisode(updated)
            
            if (isFinished && updated.isDownloaded) {
                deleteEpisodeFile(updated)
            }
        }
    }

    suspend fun updateEpisode(episode: PodcastEpisode) {
        dao.updateEpisode(episode)
    }

    suspend fun getEpisodeById(id: Int): PodcastEpisode? {
        return dao.getEpisodeById(id)
    }

    suspend fun markAllAsPlayed(channelId: Int) {
        withContext(Dispatchers.IO) {
            val episodes = dao.getEpisodesForChannel(channelId).first()
            episodes.filter { !it.isDownloaded && !it.isDownloading && !it.isQueued }.forEach { markAsPlayed(it) }
        }
    }

    suspend fun clearDownloadingState() {
        withContext(Dispatchers.IO) {
            dao.clearDownloadingState()
        }
    }

    suspend fun downloadAllNew() {
        withContext(Dispatchers.IO) {
            val twoWeeksAgo = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
            val allEpisodes = dao.getAllEpisodes().first()
            val toDownload = allEpisodes.filter { 
                !it.isFinished && !it.isDownloaded && !it.isDownloading && (it.pubDate ?: 0L) >= twoWeeksAgo
            }
            toDownload.forEach { downloadEpisode(it) }
        }
    }

    suspend fun deleteAllEpisodesForChannel(channelId: Int) {
        withContext(Dispatchers.IO) {
            val episodes = dao.getEpisodesForChannel(channelId).first()
            episodes.forEach { episode ->
                if (episode.isDownloaded) {
                    deleteEpisodeFile(episode)
                }
            }
        }
    }
    suspend fun refreshAllFeeds(channels: List<AudioChannel>, sinceDate: Long? = null, onProgress: ((Int, Int) -> Unit)? = null): Int {
        return withContext(Dispatchers.IO) {
            coroutineScope {
                val podcastChannels = channels.filter { it.type == ChannelType.PODCAST && it.streamUrl != null }
                val total = podcastChannels.size
                var completed = 0
                val deferreds = podcastChannels.map { channel ->
                    async {
                        val result = refreshFeed(channel.id, channel.streamUrl!!, sinceDate)
                        synchronized(this@coroutineScope) {
                            completed++
                            onProgress?.invoke(completed, total)
                        }
                        result.second
                    }
                }
                deferreds.awaitAll().sum()
            }
        }
    }

    fun getInProgressChannelIds(): Flow<List<Int>> = dao.getInProgressChannelIds()

    suspend fun getDownloadProgress(): Pair<Int, Int> {
        val twoWeeksAgo = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
        val pending = dao.getPendingDownloadCount(twoWeeksAgo)
        val downloaded = dao.getDownloadedCount(twoWeeksAgo)
        return pending to downloaded
    }
}
