package com.michael.simplemusic.podcast

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.michael.simplemusic.SimpleMusicApp
import com.michael.simplemusic.data.PodcastEpisode
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class PodcastDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val episodeId = inputData.getInt("episodeId", -1)
        if (episodeId == -1) return Result.failure()

        val app = applicationContext as SimpleMusicApp
        val dao = app.database.podcastEpisodeDao()
        val episode = dao.getEpisodeById(episodeId) ?: return Result.failure()

        val podcastDir = File(applicationContext.filesDir, "podcasts")
        if (!podcastDir.exists()) podcastDir.mkdirs()
        val file = File(podcastDir, "${episode.channelId}_${episode.guid.hashCode()}.mp3")

        return try {
            // Wait for a slot (max 3 concurrent downloads)
            var waiting = true
            while (waiting) {
                val activeDownloads = dao.getActiveDownloadCount()
                if (activeDownloads < 3) {
                    waiting = false
                } else {
                    kotlinx.coroutines.delay(5000) // Check every 5 seconds
                }
            }

            dao.updateEpisode(episode.copy(downloadStatus = PodcastEpisode.STATUS_DOWNLOADING, downloadProgress = 0))
            
            val url = URL(episode.streamUrl)
            val connection = url.openConnection()
            connection.connect()
            val fileLength = connection.contentLength
            
            url.openStream().use { input ->
                FileOutputStream(file).use { output ->
                    val data = ByteArray(8192)
                    var total = 0L
                    var count: Int
                    var lastUpdate = 0
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (fileLength > 0) {
                            val progress = (total * 100 / fileLength).toInt()
                            if (progress > lastUpdate) {
                                dao.updateEpisode(episode.copy(downloadStatus = PodcastEpisode.STATUS_DOWNLOADING, downloadProgress = progress))
                                lastUpdate = progress
                            }
                        }
                    }
                }
            }
            
            dao.updateEpisode(episode.copy(localPath = file.absolutePath, downloadStatus = PodcastEpisode.STATUS_DOWNLOADED, downloadProgress = 100))
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            dao.updateEpisode(episode.copy(downloadStatus = PodcastEpisode.STATUS_IDLE, downloadProgress = 0))
            Result.retry()
        }
    }
}
