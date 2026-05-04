package com.michael.simplemusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.michael.simplemusic.ui.PlayerScreen
import com.michael.simplemusic.ui.PlayerViewModel
import com.michael.simplemusic.ui.theme.SimpleMusicTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The app works whether granted or not */ }

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial intent
        handleIntent(intent)

        // Request notification permission (required for Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            SimpleMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlayerScreen(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intent ?: return
        
        // Handle widget click category switch
        intent.getStringExtra("extra_category")?.let { category ->
            viewModel.setCategory(category)
        }

        // Handle skip/load actions from Service
        when (intent.action) {
            "com.michael.simplemusic.ACTION_LOAD_CHANNEL" -> {
                val channelId = intent.getIntExtra("channel_id", -1)
                if (channelId != -1) {
                    viewModel.selectChannel(channelId)
                }
            }
            "com.michael.simplemusic.ACTION_LOAD_EPISODE" -> {
                val episodeId = intent.getIntExtra("episode_id", -1)
                if (episodeId != -1) {
                    // We need to fetch the episode and play it
                    // I'll add a playEpisodeById to the ViewModel
                    viewModel.playEpisodeById(episodeId)
                }
            }
        }
    }
}
