package com.michael.simplemusic.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.michael.simplemusic.SimpleMusicApp
import com.michael.simplemusic.data.ListeningState
import com.michael.simplemusic.data.StateRepository
import com.michael.simplemusic.scanner.AudioFile
import com.michael.simplemusic.scanner.FolderScanner
import com.michael.simplemusic.service.MusicService
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StateRepository
    private val folderScanner = FolderScanner(application)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // --- Exposed state ---
    val allStates: StateFlow<List<ListeningState>>

    private val _activeStateId = MutableStateFlow<Int?>(null)
    val activeStateId: StateFlow<Int?> = _activeStateId.asStateFlow()

    private val _activeState = MutableStateFlow<ListeningState?>(null)
    val activeState: StateFlow<ListeningState?> = _activeState.asStateFlow()

    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val audioFiles: StateFlow<List<AudioFile>> = _audioFiles.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrackName = MutableStateFlow("")
    val currentTrackName: StateFlow<String> = _currentTrackName.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatEnabled = MutableStateFlow(true)
    val repeatEnabled: StateFlow<Boolean> = _repeatEnabled.asStateFlow()

    init {
        val app = application as SimpleMusicApp
        val dao = app.database.listeningStateDao()
        repository = StateRepository(dao)

        allStates = repository.allStates
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Connect to MusicService
        val sessionToken = SessionToken(
            application,
            ComponentName(application, MusicService::class.java)
        )
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.let { future ->
                if (future.isDone && !future.isCancelled) future.get() else null
            }
            mediaController?.addListener(playerListener)
            startPositionUpdates()
        }, MoreExecutors.directExecutor())
    }

    // --- Player listener ---
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrentTrackInfo()
            viewModelScope.launch { saveCurrentPlaybackState() }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
            viewModelScope.launch { saveCurrentPlaybackState() }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatEnabled.value = repeatMode == Player.REPEAT_MODE_ALL
            viewModelScope.launch { saveCurrentPlaybackState() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _durationMs.value = mediaController?.duration ?: 0L
            }
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                viewModelScope.launch { saveCurrentPlaybackState() }
            }
        }
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(30_000) // Periodic save every 30 seconds as safety net
                saveCurrentPlaybackState()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(500)
                mediaController?.let { controller ->
                    _currentPositionMs.value = controller.currentPosition
                    if (controller.duration > 0) {
                        _durationMs.value = controller.duration
                    }
                }
            }
        }
    }

    private fun updateCurrentTrackInfo() {
        mediaController?.let { controller ->
            val mediaItem = controller.currentMediaItem
            _currentTrackName.value = mediaItem?.mediaMetadata?.title?.toString() ?: ""
            _currentTrackIndex.value = controller.currentMediaItemIndex
            _durationMs.value = if (controller.duration > 0) controller.duration else 0L
        }
    }

    // --- State management ---
    fun selectState(stateId: Int) {
        viewModelScope.launch {
            // Ensure previous state is saved ATOMICALLY before switching
            saveCurrentPlaybackState()

            val state = repository.getStateById(stateId) ?: return@launch
            _activeStateId.value = stateId
            _activeState.value = state

            if (state.folderUri != null) {
                val uri = Uri.parse(state.folderUri)
                val files = withContext(Dispatchers.IO) {
                    folderScanner.scanFolder(uri)
                }
                _audioFiles.value = files

                if (files.isNotEmpty()) {
                    loadPlaylist(files, state.currentTrackIndex, state.currentPositionMs)
                    _shuffleEnabled.value = state.shuffleEnabled
                    _repeatEnabled.value = state.repeatEnabled
                    mediaController?.shuffleModeEnabled = state.shuffleEnabled
                    mediaController?.repeatMode =
                        if (state.repeatEnabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                }
            } else {
                _audioFiles.value = emptyList()
                mediaController?.stop()
                mediaController?.clearMediaItems()
            }
        }
    }

    fun setFolder(folderUri: Uri, displayName: String) {
        viewModelScope.launch {
            val stateId = _activeStateId.value ?: return@launch
            val state = repository.getStateById(stateId) ?: return@launch

            // Persist URI permission so we can access this folder after app restart
            val contentResolver = getApplication<SimpleMusicApp>().contentResolver
            contentResolver.takePersistableUriPermission(
                folderUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            val updatedState = state.copy(
                folderUri = folderUri.toString(),
                folderDisplayName = displayName,
                currentTrackIndex = 0,
                currentPositionMs = 0L
            )
            repository.updateState(updatedState)
            _activeState.value = updatedState

            val files = withContext(Dispatchers.IO) {
                folderScanner.scanFolder(folderUri)
            }
            _audioFiles.value = files
            if (files.isNotEmpty()) {
                loadPlaylist(files, 0, 0L)
            }
        }
    }

    private fun loadPlaylist(files: List<AudioFile>, startIndex: Int, startPositionMs: Long) {
        mediaController?.let { controller ->
            val mediaItems = files.map { file ->
                MediaItem.Builder()
                    .setUri(file.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(file.displayName.removeSuffix(".mp3"))
                            .build()
                    )
                    .build()
            }
            controller.setMediaItems(
                mediaItems,
                startIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0)),
                startPositionMs
            )
            controller.prepare()
        }
    }

    // --- Playback controls ---
    fun playPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
                // Save immediately on pause
                viewModelScope.launch { saveCurrentPlaybackState() }
            } else {
                controller.play()
            }
        }
    }

    fun next() {
        mediaController?.seekToNext()
    }

    fun previous() {
        mediaController?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        mediaController?.let { controller ->
            controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        }
    }

    fun toggleRepeat() {
        mediaController?.let { controller ->
            controller.repeatMode = if (controller.repeatMode == Player.REPEAT_MODE_ALL) {
                Player.REPEAT_MODE_OFF
            } else {
                Player.REPEAT_MODE_ALL
            }
        }
    }

    // --- State CRUD ---
    fun createState(name: String) {
        viewModelScope.launch {
            if (repository.getStateCount() < 5) {
                val id = repository.insertState(ListeningState(name = name))
                if (_activeStateId.value == null) {
                    selectState(id.toInt())
                }
            }
        }
    }

    fun renameState(stateId: Int, newName: String) {
        viewModelScope.launch {
            val state = repository.getStateById(stateId) ?: return@launch
            repository.updateState(state.copy(name = newName))
            if (_activeStateId.value == stateId) {
                _activeState.value = state.copy(name = newName)
            }
        }
    }

    fun deleteState(stateId: Int) {
        viewModelScope.launch {
            val state = repository.getStateById(stateId) ?: return@launch
            repository.deleteState(state)
            if (_activeStateId.value == stateId) {
                _activeStateId.value = null
                _activeState.value = null
                _audioFiles.value = emptyList()
                mediaController?.stop()
                mediaController?.clearMediaItems()
            }
        }
    }

    // --- Persistence ---
    suspend fun saveCurrentPlaybackState() {
        val stateId = _activeStateId.value ?: return
        val state = repository.getStateById(stateId) ?: return
        val controller = mediaController ?: return

        repository.updateState(
            state.copy(
                currentTrackIndex = controller.currentMediaItemIndex,
                currentPositionMs = controller.currentPosition,
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatEnabled = controller.repeatMode == Player.REPEAT_MODE_ALL
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
