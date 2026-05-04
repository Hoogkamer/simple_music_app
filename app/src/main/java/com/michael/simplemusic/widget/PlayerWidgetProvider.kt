package com.michael.simplemusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.michael.simplemusic.MainActivity
import com.michael.simplemusic.R
import com.michael.simplemusic.service.MusicService

class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "Total Audio Hub"
            val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: "Not playing"
            val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
            val position = intent.getLongExtra(EXTRA_POSITION, 0L)
            val duration = intent.getLongExtra(EXTRA_DURATION, 0L)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
            for (id in appWidgetIds) {
                val views = getRemoteViews(context)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_subtitle, subtitle)
                views.setImageViewResource(R.id.widget_btn_play_pause, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                
                if (duration > 0) {
                    views.setViewVisibility(R.id.widget_progress, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_current_time, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_total_time, android.view.View.VISIBLE)
                    views.setProgressBar(R.id.widget_progress, duration.toInt(), position.toInt(), false)
                    views.setTextViewText(R.id.widget_current_time, formatTime(position))
                    views.setTextViewText(R.id.widget_total_time, formatTime(duration))
                } else {
                    views.setViewVisibility(R.id.widget_progress, android.view.View.INVISIBLE)
                    views.setViewVisibility(R.id.widget_current_time, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_total_time, android.view.View.GONE)
                }
                
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.michael.simplemusic.ACTION_PLAY_PAUSE"
        const val ACTION_REWIND = "com.michael.simplemusic.ACTION_REWIND"
        const val ACTION_FAST_FORWARD = "com.michael.simplemusic.ACTION_FAST_FORWARD"
        const val ACTION_UPDATE_WIDGET = "com.michael.simplemusic.ACTION_UPDATE_WIDGET"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_DURATION = "extra_duration"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = getRemoteViews(context)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_player)

            // Open app on click
            val openAppIntent = Intent(context, MainActivity::class.java)
            val pendingOpenApp = PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_title, pendingOpenApp)
            views.setOnClickPendingIntent(R.id.widget_subtitle, pendingOpenApp)

            // Play/Pause
            val playIntent = Intent(context, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }
            val pendingPlay = PendingIntent.getService(context, 1, playIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, pendingPlay)

            // Rewind
            val rewindIntent = Intent(context, MusicService::class.java).apply { action = ACTION_REWIND }
            val pendingRewind = PendingIntent.getService(context, 2, rewindIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_btn_rewind, pendingRewind)

            // Fast Forward
            val ffIntent = Intent(context, MusicService::class.java).apply { action = ACTION_FAST_FORWARD }
            val pendingFF = PendingIntent.getService(context, 3, ffIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_btn_fast_forward, pendingFF)

            return views
        }
    }
}
