package com.michael.simplemusic.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.michael.simplemusic.MainActivity
import com.michael.simplemusic.widget.PlayerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var widgetUpdateJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Default: repeat the whole folder
        player.repeatMode = Player.REPEAT_MODE_ALL

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_PLAY_WHEN_READY_CHANGED, 
                        Player.EVENT_MEDIA_METADATA_CHANGED, 
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED
                    )) {
                    updateWidget(player)
                }
            }
        })
    }

    private fun updateWidget(player: Player) {
        broadcastWidgetUpdate(player)
        
        if (player.isPlaying) {
            if (widgetUpdateJob == null || widgetUpdateJob?.isActive != true) {
                widgetUpdateJob = serviceScope.launch {
                    while (true) {
                        delay(1000)
                        mediaSession?.player?.let { p ->
                            if (p.isPlaying) {
                                broadcastWidgetUpdate(p)
                            } else {
                                widgetUpdateJob?.cancel()
                            }
                        }
                    }
                }
            }
        } else {
            widgetUpdateJob?.cancel()
            widgetUpdateJob = null
        }
    }

    private fun broadcastWidgetUpdate(player: Player) {
        val intent = Intent(this, com.michael.simplemusic.widget.PlayerWidgetProvider::class.java)
        intent.action = com.michael.simplemusic.widget.PlayerWidgetProvider.ACTION_UPDATE_WIDGET
        
        // Capture metadata on main thread
        val metadata = player.mediaMetadata
        val isPlaying = player.isPlaying
        val position = player.currentPosition
        val duration = player.duration

        serviceScope.launch(Dispatchers.IO) {
            val db = (application as com.michael.simplemusic.SimpleMusicApp).database
            val config = db.appConfigDao().getConfigSync() ?: com.michael.simplemusic.data.AppConfig()
            val category = config.lastCategory

            val title: String
            val subtitle: String
            val iconRes: Int

            when (category) {
                "PODCASTS" -> {
                    title = metadata.albumTitle?.toString() ?: "Podcast"
                    subtitle = metadata.title?.toString() ?: "Unknown Episode"
                    iconRes = com.michael.simplemusic.R.drawable.ic_widget_podcast
                }
                "RADIO" -> {
                    title = metadata.title?.toString() ?: "Radio"
                    subtitle = metadata.artist?.toString() ?: "Live Stream"
                    iconRes = com.michael.simplemusic.R.drawable.ic_widget_radio
                }
                else -> { // MUSIC
                    title = metadata.title?.toString() ?: "Total Audio Hub"
                    subtitle = metadata.artist?.toString() ?: "Unknown Artist"
                    iconRes = com.michael.simplemusic.R.drawable.ic_widget_music
                }
            }
            
            withContext(Dispatchers.Main) {
                intent.putExtra(com.michael.simplemusic.widget.PlayerWidgetProvider.EXTRA_TITLE, title)
                intent.putExtra(com.michael.simplemusic.widget.PlayerWidgetProvider.EXTRA_SUBTITLE, subtitle)
                intent.putExtra(com.michael.simplemusic.widget.PlayerWidgetProvider.EXTRA_IS_PLAYING, isPlaying)
                intent.putExtra(com.michael.simplemusic.widget.PlayerWidgetProvider.EXTRA_POSITION, position)
                intent.putExtra(com.michael.simplemusic.widget.PlayerWidgetProvider.EXTRA_DURATION, duration)
                intent.putExtra(com.michael.simplemusic.widget.PlayerWidgetProvider.EXTRA_CATEGORY, category)
                intent.putExtra(com.michael.simplemusic.widget.PlayerWidgetProvider.EXTRA_ICON_RES, iconRes)
                
                sendBroadcast(intent)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PlayerWidgetProvider.ACTION_PLAY_PAUSE -> {
                mediaSession?.player?.let { player ->
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                    }
                    if (player.mediaItemCount > 0) {
                        if (player.isPlaying) player.pause() else player.play()
                    } else {
                        // If empty, it might have been killed and restarted.
                        // We should ideally restore state here, but for now let's at least try to update the widget.
                        updateWidget(player)
                    }
                }
            }
            PlayerWidgetProvider.ACTION_REWIND -> {
                mediaSession?.player?.let { player ->
                    player.seekTo((player.currentPosition - 15000).coerceAtLeast(0))
                }
            }
            PlayerWidgetProvider.ACTION_FAST_FORWARD -> {
                mediaSession?.player?.let { player ->
                    player.seekTo(player.currentPosition + 30000)
                }
            }
            PlayerWidgetProvider.ACTION_SKIP_NEXT -> {
                handleSkip(true)
            }
            PlayerWidgetProvider.ACTION_SKIP_PREVIOUS -> {
                handleSkip(false)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleSkip(forward: Boolean) {
        val player = mediaSession?.player ?: return
        serviceScope.launch(Dispatchers.IO) {
            val db = (application as com.michael.simplemusic.SimpleMusicApp).database
            val config = db.appConfigDao().getConfigSync() ?: return@launch
            
            when (config.lastCategory) {
                "MUSIC" -> {
                    withContext(Dispatchers.Main) {
                        if (forward) player.seekToNext() else player.seekToPrevious()
                    }
                }
                "RADIO" -> {
                    val channels = db.audioChannelDao().getAllChannelsSync().filter { it.type == com.michael.simplemusic.data.ChannelType.RADIO }
                    if (channels.isNotEmpty()) {
                        val currentIndex = channels.indexOfFirst { it.id == config.activeRadioChannelId }
                        val nextIndex = if (forward) (currentIndex + 1) % channels.size else (currentIndex - 1 + channels.size) % channels.size
                        val nextChannel = channels[nextIndex]
                        
                        // We need to notify the UI to load this channel
                        // For now, let's just update the DB and broadcast a "LOAD" intent or similar
                        // Actually, the simplest is to just send an intent that MainActivity/ViewModel can handle
                        val intent = Intent(this@MusicService, MainActivity::class.java).apply {
                            action = "com.michael.simplemusic.ACTION_LOAD_CHANNEL"
                            putExtra("channel_id", nextChannel.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    }
                }
                "PODCASTS" -> {
                    val recentThreshold = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000L)
                    val episodes = db.podcastEpisodeDao().getRecentEpisodesSync(recentThreshold)
                    if (episodes.isNotEmpty()) {
                        val currentIndex = episodes.indexOfFirst { it.id == config.activePodcastEpisodeId }
                        val nextIndex = if (forward) (currentIndex + 1) % episodes.size else (currentIndex - 1 + episodes.size) % episodes.size
                        val nextEpisode = episodes[nextIndex]
                        
                        val intent = Intent(this@MusicService, MainActivity::class.java).apply {
                            action = "com.michael.simplemusic.ACTION_LOAD_EPISODE"
                            putExtra("episode_id", nextEpisode.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    }
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
