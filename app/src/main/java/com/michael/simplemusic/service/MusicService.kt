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

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

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
        val intent = Intent(this, com.michael.simplemusic.widget.PlayerWidgetProvider::class.java)
        intent.action = com.michael.simplemusic.widget.PlayerWidgetProvider.ACTION_UPDATE_WIDGET
        
        val title = player.mediaMetadata.title?.toString() ?: "Total Audio Hub"
        val subtitle = player.mediaMetadata.artist?.toString() ?: if (player.mediaMetadata.title != null) "Playing" else "Not playing"
        
        intent.putExtra(com.michael.simplemusic.widget.PlayerWidgetProvider.EXTRA_TITLE, title)
        intent.putExtra(com.michael.simplemusic.widget.PlayerWidgetProvider.EXTRA_SUBTITLE, subtitle)
        intent.putExtra(com.michael.simplemusic.widget.PlayerWidgetProvider.EXTRA_IS_PLAYING, player.isPlaying)
        
        sendBroadcast(intent)
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
                    player.seekTo(player.currentPosition - 15000)
                }
            }
            PlayerWidgetProvider.ACTION_FAST_FORWARD -> {
                mediaSession?.player?.let { player ->
                    player.seekTo(player.currentPosition + 30000)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
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
