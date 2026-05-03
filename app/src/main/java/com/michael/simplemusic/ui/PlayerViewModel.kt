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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.michael.simplemusic.SimpleMusicApp
import com.michael.simplemusic.data.*
import com.michael.simplemusic.podcast.PodcastRepository
import com.michael.simplemusic.scanner.AudioFile
import com.michael.simplemusic.scanner.FolderScanner
import com.michael.simplemusic.service.MusicService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.URL
import java.net.URLEncoder

data class RadioStationResult(
    val name: String,
    val url: String,
    val favicon: String?,
    val country: String?
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SimpleMusicApp
    private val repository = ChannelRepository(app.database.audioChannelDao())
    private val podcastRepository = PodcastRepository(app, app.database.podcastEpisodeDao(), app.filesDir)
    private val configDao = app.database.appConfigDao()
    private val folderScanner = FolderScanner(application)
    private val gson = Gson()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var podcastEpisodesJob: kotlinx.coroutines.Job? = null

    private var isRefreshing = false

    // --- Exposed state ---
    val allChannels: StateFlow<List<AudioChannel>>
    
    private val _appConfig = MutableStateFlow(AppConfig())
    val appConfig: StateFlow<AppConfig> = _appConfig.asStateFlow()

    private val _activeMusicChannel = MutableStateFlow<AudioChannel?>(null)
    val activeMusicChannel: StateFlow<AudioChannel?> = _activeMusicChannel.asStateFlow()

    private val _activeRadioChannel = MutableStateFlow<AudioChannel?>(null)
    val activeRadioChannel: StateFlow<AudioChannel?> = _activeRadioChannel.asStateFlow()

    private val _radioSearchResults = MutableStateFlow<List<RadioStationResult>>(emptyList())
    val radioSearchResults: StateFlow<List<RadioStationResult>> = _radioSearchResults.asStateFlow()

    private val _activePodcastChannel = MutableStateFlow<AudioChannel?>(null)
    val activePodcastChannel: StateFlow<AudioChannel?> = _activePodcastChannel.asStateFlow()

    private val _podcastView = MutableStateFlow("SHOWS")
    val podcastView: StateFlow<String> = _podcastView.asStateFlow()

    private val _activeChannelId = MutableStateFlow<Int?>(null)
    val activeChannelId: StateFlow<Int?> = _activeChannelId.asStateFlow()

    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val audioFiles: StateFlow<List<AudioFile>> = _audioFiles.asStateFlow()

    private val _podcastEpisodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val podcastEpisodes: StateFlow<List<PodcastEpisode>> = _podcastEpisodes.asStateFlow()

    private val _pendingMarkPlayed = MutableStateFlow<Set<Int>>(emptySet())
    val pendingMarkPlayed: StateFlow<Set<Int>> = _pendingMarkPlayed.asStateFlow()

    private val _activeEpisodeId = MutableStateFlow<Int?>(null)
    val activeEpisodeId: StateFlow<Int?> = _activeEpisodeId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeEpisode: StateFlow<PodcastEpisode?> = _activeEpisodeId
        .flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(null)
            else podcastRepository.getEpisodeFlowById(id)
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    val recentEpisodes = podcastRepository.getRecentEpisodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrackName = MutableStateFlow("")
    val currentTrackName: StateFlow<String> = _currentTrackName.asStateFlow()

    private val _streamMetadata = MutableStateFlow<String?>(null)
    val streamMetadata: StateFlow<String?> = _streamMetadata.asStateFlow()

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
        viewModelScope.launch { podcastRepository.clearDownloadingState() }
        allChannels = repository.allChannels
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch {
            val channels = allChannels.first { it.isNotEmpty() }
            podcastRepository.refreshAllFeeds(channels)
        }

        viewModelScope.launch {
            configDao.getConfig().collect { config ->
                val actualConfig = config ?: AppConfig()
                _appConfig.value = actualConfig
                actualConfig.activeMusicChannelId?.let { id -> _activeMusicChannel.value = repository.getChannelById(id) }
                actualConfig.activeRadioChannelId?.let { id -> _activeRadioChannel.value = repository.getChannelById(id) }
                actualConfig.activePodcastChannelId?.let { id -> _activePodcastChannel.value = repository.getChannelById(id) }
            }
        }

        val sessionToken = SessionToken(application, ComponentName(application, MusicService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = try { controllerFuture?.get() } catch (e: Exception) { null }
            mediaController?.addListener(playerListener)
            startPositionUpdates()
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (!isRefreshing) viewModelScope.launch { saveCurrentPlaybackState() }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString()
            val artist = mediaMetadata.artist?.toString()
            if (artist != null && title != null) {
                _streamMetadata.value = "$artist - $title"
            } else if (title != null) {
                _streamMetadata.value = title
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isRefreshing) return
            updateCurrentTrackInfo()
            viewModelScope.launch { saveCurrentPlaybackState() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _streamMetadata.value = "Buffering..."
                Player.STATE_READY -> {
                    if (!isRefreshing) {
                        _durationMs.value = mediaController?.duration?.coerceAtLeast(0) ?: 0L
                        updateCurrentTrackInfo()
                    }
                }
                else -> {}
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _streamMetadata.value = "Error: ${error.localizedMessage ?: "Playback failed"}"
            _isPlaying.value = false
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (!isRefreshing) viewModelScope.launch { saveCurrentPlaybackState() }
        }
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (!isRefreshing) {
                    mediaController?.let {
                        val pos = it.currentPosition.coerceAtLeast(0L)
                        val dur = it.duration
                        _currentPositionMs.value = pos
                        if (dur > 0) _durationMs.value = dur
                        
                        activeEpisode.value?.let { episode ->
                            podcastRepository.updatePlaybackPosition(episode, pos, dur)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(10000)
                if (!isRefreshing && _isPlaying.value) saveCurrentPlaybackState()
            }
        }
    }

    private fun updateCurrentTrackInfo() {
        mediaController?.let { controller ->
            val item = controller.currentMediaItem ?: return
            val title = item.mediaMetadata.title?.toString() ?: ""
            if (title.isNotEmpty()) _currentTrackName.value = title
            _currentTrackIndex.value = controller.currentMediaItemIndex
        }
    }

    fun selectChannel(channelId: Int) {
        viewModelScope.launch {
            if (!isRefreshing) saveCurrentPlaybackState()
            _activeEpisodeId.value = null // Clear active episode if switching channels
            val channel = repository.getChannelById(channelId) ?: return@launch
            repository.updateChannel(channel.copy(lastPlayedTime = System.currentTimeMillis()))
            _activeChannelId.value = channelId
            
            when (channel.type) {
                ChannelType.FOLDER -> {
                    _activeMusicChannel.value = channel
                    updateConfig { it.copy(activeMusicChannelId = channelId, lastCategory = "MUSIC") }
                }
                ChannelType.RADIO -> {
                    _activeRadioChannel.value = channel
                    updateConfig { it.copy(activeRadioChannelId = channelId, lastCategory = "RADIO") }
                }
                ChannelType.PODCAST -> {
                    _activePodcastChannel.value = channel
                    updateConfig { it.copy(activePodcastChannelId = channelId, lastCategory = "PODCASTS") }
                }
            }
            loadChannelIntoPlayer(channel)
        }
    }

    private suspend fun loadChannelIntoPlayer(channel: AudioChannel) {
        isRefreshing = true
        _currentTrackName.value = channel.currentTrackTitle ?: "Loading..."
        _currentPositionMs.value = channel.currentPositionMs
        _durationMs.value = channel.currentTrackDurationMs

        try {
            val controller = mediaController ?: return
            controller.stop()
            controller.clearMediaItems()

            when (channel.type) {
                ChannelType.FOLDER -> {
                    if (channel.folderUri != null) {
                        val files = withContext(Dispatchers.IO) { folderScanner.scanFolder(Uri.parse(channel.folderUri)) }
                        _audioFiles.value = files
                        if (files.isNotEmpty()) {
                            val mediaItems = files.map { file ->
                                MediaItem.Builder()
                                    .setMediaId(file.uri.toString())
                                    .setUri(file.uri)
                                    .setMediaMetadata(MediaMetadata.Builder().setTitle(file.displayName.removeSuffix(".mp3")).build())
                                    .build()
                            }
                            val startIndex = files.indexOfFirst { it.uri.toString() == channel.currentTrackUri }.let { if (it != -1) it else channel.currentTrackIndex }.coerceIn(0, files.size - 1)
                            controller.setMediaItems(mediaItems, startIndex, channel.currentPositionMs)
                            controller.shuffleModeEnabled = channel.shuffleEnabled
                            controller.repeatMode = if (channel.repeatEnabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                            controller.prepare()
                            delay(1000)
                            controller.play()
                        }
                    }
                }
                ChannelType.RADIO -> {
                    _streamMetadata.value = "Connecting..."
                    if (channel.streamUrl != null) {
                        try {
                            val item = MediaItem.Builder().setUri(channel.streamUrl).setMediaMetadata(MediaMetadata.Builder().setTitle(channel.name).build()).build()
                            controller.setMediaItem(item)
                            controller.prepare()
                            controller.play()
                        } catch (e: Exception) {
                            _streamMetadata.value = "Error: ${e.message}"
                        }
                    }
                }
                ChannelType.PODCAST -> {
                    podcastEpisodesJob?.cancel()
                    podcastEpisodesJob = podcastRepository.getEpisodesForChannel(channel.id).onEach { _podcastEpisodes.value = it }.launchIn(viewModelScope)
                    podcastRepository.refreshFeed(channel.id, channel.streamUrl!!)
                }
            }
        } finally {
            delay(1000)
            isRefreshing = false
            updateCurrentTrackInfo()
            saveCurrentPlaybackState()
        }
    }

    suspend fun saveCurrentPlaybackState() {
        val channelId = _activeChannelId.value ?: return
        val controller = mediaController ?: return
        val index = controller.currentMediaItemIndex
        if (index < 0) return

        // Read all MediaController properties on the main thread (required by Media3)
        val item = controller.currentMediaItem
        val currentPosition = controller.currentPosition.coerceAtLeast(0L)
        val duration = controller.duration
        val mediaId = item?.mediaId
        val title = item?.mediaMetadata?.title?.toString()

        withContext(Dispatchers.IO) {
            val channel = repository.getChannelById(channelId) ?: return@withContext
            repository.updateChannel(channel.copy(
                currentTrackIndex = index,
                currentTrackUri = mediaId ?: channel.currentTrackUri,
                currentTrackTitle = title ?: channel.currentTrackTitle,
                currentPositionMs = currentPosition,
                currentTrackDurationMs = if (duration > 0) duration else channel.currentTrackDurationMs
            ))
        }
    }

    fun playPause() = mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    fun skipBack() = mediaController?.let { it.seekTo((it.currentPosition - 15000).coerceAtLeast(0)) }
    fun skipForward() = mediaController?.let { it.seekTo(it.currentPosition + 30000) }
    fun stopRadio() = mediaController?.stop()
    
    fun next() = mediaController?.seekToNext()
    fun previous() = mediaController?.seekToPrevious()
    fun seekTo(pos: Long) = mediaController?.seekTo(pos)
    fun toggleShuffle() { mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun toggleRepeat() { mediaController?.let { it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL } }

    fun createChannel(name: String, type: ChannelType = ChannelType.FOLDER, streamUrl: String? = null) {
        viewModelScope.launch {
            val initialName = if (type == ChannelType.PODCAST) "Loading..." else name
            val id = repository.insertChannel(AudioChannel(name = initialName, type = type, streamUrl = streamUrl))
            if (type == ChannelType.PODCAST && streamUrl != null) {
                val title = podcastRepository.refreshFeed(id.toInt(), streamUrl)
                if (!title.isNullOrBlank()) {
                    repository.updateChannel(repository.getChannelById(id.toInt())?.copy(name = title) ?: return@launch)
                }
            }
            selectChannel(id.toInt())
        }
    }

    fun renameChannel(channelId: Int, newName: String) {
        viewModelScope.launch { repository.updateChannel(repository.getChannelById(channelId)?.copy(name = newName) ?: return@launch) }
    }

    fun deleteChannel(channelId: Int) {
        viewModelScope.launch { 
            val channel = repository.getChannelById(channelId) ?: return@launch
            if (channel.type == ChannelType.PODCAST) {
                podcastRepository.deleteAllEpisodesForChannel(channelId)
            }
            repository.deleteChannel(channel) 
        }
    }

    fun setFolder(uri: Uri, name: String) {
        viewModelScope.launch {
            val channel = _activeMusicChannel.value ?: return@launch
            getApplication<SimpleMusicApp>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val updated = channel.copy(folderUri = uri.toString(), folderDisplayName = name, currentTrackIndex = 0, currentTrackUri = null, currentTrackTitle = null, currentPositionMs = 0L)
            repository.updateChannel(updated)
            _activeMusicChannel.value = updated
            loadChannelIntoPlayer(updated)
        }
    }

    fun setRadioUrl(channelId: Int, url: String) {
        viewModelScope.launch {
            val channel = repository.getChannelById(channelId) ?: return@launch
            repository.updateChannel(channel.copy(streamUrl = url))
            if (channel.type == ChannelType.PODCAST) {
                val title = podcastRepository.refreshFeed(channelId, url)
                if (!title.isNullOrBlank()) {
                    repository.updateChannel(repository.getChannelById(channelId)?.copy(name = title) ?: return@launch)
                }
            }
            if (_activeChannelId.value == channelId) {
                selectChannel(channelId)
            }
        }
    }

    fun searchRadioStations(query: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val encodedQuery = URLEncoder.encode(query, "UTF-8")
                    val url = "https://de1.api.radio-browser.info/json/stations/byname/$encodedQuery"
                    val json = URL(url).readText()
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    val results: List<Map<String, Any>> = gson.fromJson(json, type)
                    _radioSearchResults.value = results.take(20).map {
                        RadioStationResult(
                            name = it["name"] as? String ?: "Unknown",
                            url = it["url_resolved"] as? String ?: it["url"] as? String ?: "",
                            favicon = it["favicon"] as? String,
                            country = it["country"] as? String
                        )
                    }
                } catch (e: Exception) {
                    _radioSearchResults.value = emptyList()
                }
            }
        }
    }

    fun setPodcastView(view: String) {
        _podcastView.value = view
    }

    fun downloadEpisode(episode: PodcastEpisode) = viewModelScope.launch { podcastRepository.downloadEpisode(episode) }
    fun deleteEpisodeFile(episode: PodcastEpisode) = viewModelScope.launch { podcastRepository.deleteEpisodeFile(episode) }
    fun markAsPlayedWithUndo(episode: PodcastEpisode) {
        viewModelScope.launch {
            _pendingMarkPlayed.value = _pendingMarkPlayed.value + episode.id
            delay(5000)
            if (episode.id in _pendingMarkPlayed.value) {
                podcastRepository.markAsPlayed(episode)
                _pendingMarkPlayed.value = _pendingMarkPlayed.value - episode.id
            }
        }
    }

    fun undoMarkAsPlayed(episodeId: Int) {
        _pendingMarkPlayed.value = _pendingMarkPlayed.value - episodeId
    }

    fun markEpisodeAsPlayed(episode: PodcastEpisode) = viewModelScope.launch { podcastRepository.markAsPlayed(episode) }
    fun markEpisodeAsUnplayed(episode: PodcastEpisode) = viewModelScope.launch { podcastRepository.markAsUnplayed(episode) }

    fun setPodcastFeed(url: String) {
        viewModelScope.launch {
            val channel = _activePodcastChannel.value ?: return@launch
            repository.updateChannel(channel.copy(streamUrl = url))
            selectChannel(channel.id)
        }
    }

    private fun updateConfig(update: (AppConfig) -> AppConfig) {
        viewModelScope.launch { configDao.saveConfig(update(_appConfig.value)) }
    }

    fun setActiveEpisode(episode: PodcastEpisode?) {
        _activeEpisodeId.value = episode?.id
    }

    fun playPodcastEpisode(episode: PodcastEpisode) {
        mediaController?.let { controller ->
            setActiveEpisode(episode)
            val uri = episode.localPath ?: episode.streamUrl
            val item = MediaItem.Builder()
                .setMediaId(episode.id.toString())
                .setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(episode.title).build())
                .build()
            controller.setMediaItem(item)
            controller.seekTo(episode.playbackPositionMs)
            controller.prepare()
            controller.play()
        }
    }

    fun markAllAsPlayed(channelId: Int) {
        viewModelScope.launch {
            podcastRepository.markAllAsPlayed(channelId)
        }
    }

    fun toggleHidePlayed() {
        updateConfig { it.copy(hidePlayedEpisodes = !it.hidePlayedEpisodes) }
    }

    fun downloadAllNew() {
        viewModelScope.launch {
            podcastRepository.downloadAllNew()
        }
    }

    override fun onCleared() {
        viewModelScope.launch {
            saveCurrentPlaybackState()
            mediaController?.removeListener(playerListener)
            controllerFuture?.let { MediaController.releaseFuture(it) }
        }
        super.onCleared()
    }
}
