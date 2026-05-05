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

data class MusicState(
    val activeChannel: AudioChannel? = null,
    val audioFiles: List<AudioFile> = emptyList(),
    val isLoading: Boolean = false,
    val currentTrackName: String = "",
    val currentTrackArtist: String? = null,
    val currentTrackAlbum: String? = null,
    val currentTrackIndex: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatEnabled: Boolean = true
)

data class RadioState(
    val activeChannel: AudioChannel? = null,
    val streamMetadata: String? = null,
    val searchResults: List<RadioStationResult> = emptyList()
)

data class PodcastState(
    val activeChannel: AudioChannel? = null,
    val activeEpisode: PodcastEpisode? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val recentEpisodes: List<PodcastEpisode> = emptyList(),
    val currentView: String = "RECENT",
    val currentTrackName: String = "",
    val currentTrackArtist: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlayingActiveEpisode: Boolean = false
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
    private var playerLoadJob: Job? = null
    private var podcastEpisodesJob: Job? = null
    private var isRefreshing = false

    // --- Unified Category States ---
    private val _musicState = MutableStateFlow(MusicState())
    val musicState: StateFlow<MusicState> = _musicState.asStateFlow()

    private val _radioState = MutableStateFlow(RadioState())
    val radioState: StateFlow<RadioState> = _radioState.asStateFlow()

    private val _podcastState = MutableStateFlow(PodcastState())
    val podcastState: StateFlow<PodcastState> = _podcastState.asStateFlow()

    private val _appConfig = MutableStateFlow(AppConfig())
    val appConfig: StateFlow<AppConfig> = _appConfig.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeChannelId = MutableStateFlow<Int?>(null)
    val activeChannelId: StateFlow<Int?> = _activeChannelId.asStateFlow()

    private val _pendingMarkPlayed = MutableStateFlow<Set<Int>>(emptySet())
    val pendingMarkPlayed: StateFlow<Set<Int>> = _pendingMarkPlayed.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

    val allChannels: StateFlow<List<AudioChannel>>

    init {
        viewModelScope.launch { podcastRepository.clearDownloadingState() }
        allChannels = repository.allChannels
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Sync recent episodes into podcastState
        podcastRepository.getRecentEpisodes().onEach { episodes ->
            _podcastState.update { it.copy(recentEpisodes = episodes) }
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            val channels = allChannels.first { it.isNotEmpty() }
            podcastRepository.refreshAllFeeds(channels)
        }

        viewModelScope.launch {
            configDao.getConfig().collect { config ->
                val actualConfig = config ?: AppConfig()
                _appConfig.value = actualConfig
                
                val musicChannel = actualConfig.activeMusicChannelId?.let { repository.getChannelById(it) }
                _musicState.update { it.copy(activeChannel = musicChannel) }

                val radioChannel = actualConfig.activeRadioChannelId?.let { repository.getChannelById(it) }
                _radioState.update { it.copy(activeChannel = radioChannel) }

                val podcastChannel = actualConfig.activePodcastChannelId?.let { repository.getChannelById(it) }
                val podcastEpisode = actualConfig.activePodcastEpisodeId?.let { podcastRepository.getEpisodeById(it) }
                _podcastState.update { it.copy(activeChannel = podcastChannel, activeEpisode = podcastEpisode) }
            }
        }


        viewModelScope.launch {
            configDao.getConfig().first()?.let { config ->
                val activeId = when (config.lastCategory) {
                    "MUSIC" -> config.activeMusicChannelId
                    "RADIO" -> config.activeRadioChannelId
                    "PODCASTS" -> config.activePodcastChannelId
                    else -> null
                }
                if (activeId != null) {
                    selectChannel(activeId, autoPlay = false)
                }
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
            
            // Sync play state to podcast if it's a podcast
            _podcastState.update { 
                it.copy(isPlayingActiveEpisode = isPlaying && it.activeEpisode?.id?.toString() == mediaController?.currentMediaItem?.mediaId) 
            }

            if (!isRefreshing) viewModelScope.launch { saveCurrentPlaybackState() }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString()
            val artist = mediaMetadata.artist?.toString()
            val album = mediaMetadata.albumTitle?.toString()
            val station = mediaMetadata.station?.toString() ?: album

            // Update Radio State specifically
            if (_appConfig.value.lastCategory == "RADIO") {
                _radioState.update { it.copy(streamMetadata = if (artist != null && title != null) "$artist - $title" else title) }
                
                // Auto-discovery for Radio Station names
                val activeRadio = _radioState.value.activeChannel
                if (activeRadio != null && !station.isNullOrBlank() && 
                    (activeRadio.name.isBlank() || activeRadio.name.contains("Loading") || activeRadio.name == "New Radio")) {
                    viewModelScope.launch {
                        val updated = activeRadio.copy(name = station)
                        repository.updateChannel(updated)
                        _radioState.update { it.copy(activeChannel = updated) }
                    }
                }
            }
            
            // Update Music State specifically
            if (_appConfig.value.lastCategory == "MUSIC") {
                _musicState.update { it.copy(
                    currentTrackName = title ?: "",
                    currentTrackArtist = artist,
                    currentTrackAlbum = album
                ) }
            }
            
            // Update Podcast State specifically
            if (_appConfig.value.lastCategory == "PODCASTS") {
                _podcastState.update { it.copy(
                    currentTrackName = title ?: it.activeEpisode?.title ?: "",
                    currentTrackArtist = artist ?: it.activeEpisode?.podcastTitle
                ) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isRefreshing) return
            updateCurrentTrackInfo()
            viewModelScope.launch { saveCurrentPlaybackState() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (_appConfig.value.lastCategory == "RADIO") {
                        _radioState.update { it.copy(streamMetadata = "Buffering...") }
                    }
                }
                Player.STATE_READY -> {
                    if (!isRefreshing) {
                        val dur = mediaController?.duration?.coerceAtLeast(0) ?: 0L
                        val cat = _appConfig.value.lastCategory
                        if (cat == "MUSIC") _musicState.update { it.copy(durationMs = dur) }
                        if (cat == "PODCASTS") _podcastState.update { it.copy(durationMs = dur) }
                        updateCurrentTrackInfo()
                    }
                }
                else -> {}
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            if (_appConfig.value.lastCategory == "RADIO") {
                _radioState.update { it.copy(streamMetadata = "Error: ${error.localizedMessage ?: "Playback failed"}") }
            }
            _isPlaying.value = false
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (!isRefreshing) viewModelScope.launch { saveCurrentPlaybackState() }
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _playbackSpeed.value = playbackParameters.speed
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
                        val cat = _appConfig.value.lastCategory
                        
                        if (cat == "MUSIC") _musicState.update { s -> s.copy(positionMs = pos, durationMs = if (dur > 0) dur else s.durationMs) }
                        if (cat == "PODCASTS") _podcastState.update { s -> s.copy(positionMs = pos, durationMs = if (dur > 0) dur else s.durationMs) }
                        
                        _podcastState.value.activeEpisode?.let { episode ->
                            val isCorrectItem = it.currentMediaItem?.mediaId == episode.id.toString()
                            val isReady = it.playbackState == Player.STATE_READY || it.playbackState == Player.STATE_BUFFERING
                            
                            if (cat == "PODCASTS" && isCorrectItem && isReady) {
                                if (!(pos == 0L && episode.playbackPositionMs > 1000 && !it.isPlaying)) {
                                    podcastRepository.updatePlaybackPosition(episode, pos, dur)
                                }
                            }
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
            val artist = item.mediaMetadata.artist?.toString()
            val album = item.mediaMetadata.albumTitle?.toString()
            val index = controller.currentMediaItemIndex
            
            _currentMediaId.value = item.mediaId
            
            when(_appConfig.value.lastCategory) {
                "MUSIC" -> _musicState.update { it.copy(
                    currentTrackName = title,
                    currentTrackArtist = artist,
                    currentTrackAlbum = album,
                    currentTrackIndex = index
                ) }
                "PODCASTS" -> {
                    // We don't update podcast titles here as they are driven by the ActiveEpisode state
                }
            }
        }
    }

    fun selectChannel(channelId: Int, autoPlay: Boolean = true) {
        // Optimization: If this channel is already active and loaded, just return
        if (_activeChannelId.value == channelId && _musicState.value.audioFiles.isNotEmpty()) {
            return
        }
        
        playerLoadJob?.cancel()
        playerLoadJob = viewModelScope.launch {
            if (!isRefreshing) saveCurrentPlaybackState()
            
            val channel = repository.getChannelById(channelId) ?: return@launch
            repository.updateChannel(channel.copy(lastPlayedTime = System.currentTimeMillis()))
            _activeChannelId.value = channelId
            
            when (channel.type) {
                ChannelType.FOLDER -> {
                    _musicState.update { it.copy(activeChannel = channel) }
                    updateConfig { it.copy(activeMusicChannelId = channelId, lastCategory = "MUSIC") }
                    _podcastState.update { it.copy(activeEpisode = null) } // Explicitly clear podcast spillover
                }
                ChannelType.RADIO -> {
                    _radioState.update { it.copy(activeChannel = channel, streamMetadata = "Connecting...") }
                    updateConfig { it.copy(activeRadioChannelId = channelId, lastCategory = "RADIO") }
                    _podcastState.update { it.copy(activeEpisode = null) }
                }
                ChannelType.PODCAST -> {
                    _podcastState.update { it.copy(activeChannel = channel) }
                    updateConfig { it.copy(activePodcastChannelId = channelId, lastCategory = "PODCASTS") }
                }
            }
            loadChannelIntoPlayer(channel, autoPlay)
        }
    }

    private suspend fun loadChannelIntoPlayer(channel: AudioChannel, autoPlay: Boolean) {
        isRefreshing = true
        
        when(channel.type) {
            ChannelType.FOLDER -> _musicState.update { it.copy(
                currentTrackName = channel.currentTrackTitle ?: "Loading...",
                currentTrackArtist = channel.currentTrackArtist,
                currentTrackAlbum = channel.currentTrackAlbum,
                positionMs = channel.currentPositionMs,
                durationMs = channel.currentTrackDurationMs
            ) }
            ChannelType.RADIO -> _radioState.update { it.copy(streamMetadata = "Connecting...") }
            else -> {}
        }

        try {
            val controller = mediaController ?: return
            controller.stop()
            controller.clearMediaItems()
            _musicState.update { it.copy(isLoading = true) }
            _currentMediaId.value = null

            when (channel.type) {
                ChannelType.FOLDER -> {
                    if (channel.folderUri != null) {
                        val files = withContext(Dispatchers.IO) { folderScanner.scanFolder(Uri.parse(channel.folderUri)) }
                        _musicState.update { it.copy(audioFiles = files, isLoading = false) }
                        if (files.isNotEmpty()) {
                            val mediaItems = files.map { file ->
                                MediaItem.Builder()
                                    .setMediaId(file.uri.toString())
                                    .setUri(file.uri)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(file.title ?: file.displayName.removeSuffix(".mp3"))
                                            .setArtist(file.artist)
                                            .setAlbumTitle(file.album)
                                            .build()
                                    )
                                    .build()
                            }
                            val startIndex = files.indexOfFirst { it.uri.toString() == channel.currentTrackUri }.let { if (it != -1) it else channel.currentTrackIndex }.coerceIn(0, files.size - 1)
                            controller.setMediaItems(mediaItems, startIndex, channel.currentPositionMs)
                            controller.shuffleModeEnabled = channel.shuffleEnabled
                            controller.repeatMode = if (channel.repeatEnabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                            controller.prepare()
                            delay(1000)
                            if (autoPlay) controller.play()
                        }
                    }
                }
                ChannelType.RADIO -> {
                    if (channel.streamUrl != null) {
                        try {
                            val item = MediaItem.Builder().setUri(channel.streamUrl).setMediaMetadata(MediaMetadata.Builder().setTitle(channel.name).build()).build()
                            controller.setMediaItem(item)
                            controller.prepare()
                            if (autoPlay) controller.play()
                        } catch (e: Exception) {
                            _radioState.update { it.copy(streamMetadata = "Error: ${e.message}") }
                        }
                    }
                }
                ChannelType.PODCAST -> {
                    podcastEpisodesJob?.cancel()
                    podcastEpisodesJob = podcastRepository.getEpisodesForChannel(channel.id)
                        .onEach { episodes -> _podcastState.update { it.copy(episodes = episodes) } }
                        .launchIn(viewModelScope)
                    podcastRepository.refreshFeed(channel.id, channel.streamUrl!!)
                }
            }
        } finally {
            delay(1000)
            isRefreshing = false
            updateCurrentTrackInfo()
            saveCurrentPlaybackState()
            _musicState.update { it.copy(isLoading = false) }
        }
    }

    suspend fun saveCurrentPlaybackState() {
        val channelId = _activeChannelId.value ?: return
        val controller = mediaController ?: return
        val item = controller.currentMediaItem ?: return

        val currentPosition = controller.currentPosition.coerceAtLeast(0L)
        val duration = controller.duration
        val mediaId = item.mediaId
        val title = item.mediaMetadata.title?.toString()
        val artist = item.mediaMetadata.artist?.toString()
        val album = item.mediaMetadata.albumTitle?.toString()
        val currentIndex = controller.currentMediaItemIndex

        if (currentPosition == 0L && !isRefreshing) {
            val activeEp = _podcastState.value.activeEpisode
            if (activeEp != null && activeEp.id.toString() == mediaId && activeEp.playbackPositionMs > 1000) {
                return
            }
        }

        val updatedChannel = withContext(Dispatchers.IO) {
            val channel = repository.getChannelById(channelId) ?: return@withContext null
            val updated = channel.copy(
                currentTrackIndex = currentIndex,
                currentTrackUri = mediaId,
                currentTrackTitle = title ?: channel.currentTrackTitle,
                currentTrackArtist = artist ?: channel.currentTrackArtist,
                currentTrackAlbum = album ?: channel.currentTrackAlbum,
                currentPositionMs = currentPosition,
                currentTrackDurationMs = if (duration > 0) duration else channel.currentTrackDurationMs
            )
            repository.updateChannel(updated)
            updated
        }

        if (updatedChannel != null) {
            when (updatedChannel.type) {
                ChannelType.FOLDER -> _musicState.update { it.copy(activeChannel = updatedChannel) }
                ChannelType.RADIO -> _radioState.update { it.copy(activeChannel = updatedChannel) }
                ChannelType.PODCAST -> _podcastState.update { it.copy(activeChannel = updatedChannel) }
            }
        }
    }

    fun playPause() = mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    fun skipBack() = mediaController?.let { it.seekTo((it.currentPosition - 15000).coerceAtLeast(0)) }
    fun skipForward() = mediaController?.let { it.seekTo(it.currentPosition + 30000) }
    fun stopRadio() = mediaController?.stop()

    fun stopAllPlayback() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _activeChannelId.value = null
        
        _musicState.update { it.copy(activeChannel = null) }
        _radioState.update { it.copy(activeChannel = null) }
        _podcastState.update { it.copy(activeChannel = null, activeEpisode = null) }
        
        updateConfig { it.copy(
            activeMusicChannelId = null, 
            activeRadioChannelId = null, 
            activePodcastChannelId = null, 
            activePodcastEpisodeId = null
        ) }
    }
    
    fun next() = mediaController?.seekToNext()
    fun previous() = mediaController?.seekToPrevious()
    fun seekTo(pos: Long) = mediaController?.seekTo(pos)
    
    fun toggleShuffle() { 
        mediaController?.let { 
            it.shuffleModeEnabled = !it.shuffleModeEnabled 
            _musicState.update { s -> s.copy(shuffleEnabled = it.shuffleModeEnabled) }
        } 
    }
    
    fun toggleRepeat() { 
        mediaController?.let { 
            val newMode = if (it.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
            it.repeatMode = newMode
            _musicState.update { s -> s.copy(repeatEnabled = newMode == Player.REPEAT_MODE_ALL) }
        } 
    }
    fun setPlaybackSpeed(speed: Float) { mediaController?.setPlaybackSpeed(speed) }

    fun createChannel(name: String, type: ChannelType = ChannelType.FOLDER, streamUrl: String? = null) {
        viewModelScope.launch {
            val initialName = if (type == ChannelType.PODCAST || (type == ChannelType.RADIO && name.isBlank())) "Loading..." else name
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
            val channel = _musicState.value.activeChannel ?: return@launch
            getApplication<SimpleMusicApp>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val updated = channel.copy(folderUri = uri.toString(), folderDisplayName = name, currentTrackIndex = 0, currentTrackUri = null, currentTrackTitle = null, currentPositionMs = 0L)
            repository.updateChannel(updated)
            _musicState.update { it.copy(activeChannel = updated) }
            loadChannelIntoPlayer(updated, autoPlay = true)
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
                    _radioState.update { s ->
                        s.copy(searchResults = results.take(20).map {
                            RadioStationResult(
                                name = it["name"] as? String ?: "Unknown",
                                url = it["url_resolved"] as? String ?: it["url"] as? String ?: "",
                                favicon = it["favicon"] as? String,
                                country = it["country"] as? String
                            )
                        })
                    }
                } catch (e: Exception) {
                    _radioState.update { it.copy(searchResults = emptyList()) }
                }
            }
        }
    }

    fun bulkAddRadioStations(urlsText: String) {
        viewModelScope.launch {
            val existingUrls = allChannels.value.filter { it.type == ChannelType.RADIO }.mapNotNull { it.streamUrl }.toSet()
            val lines = urlsText.split(Regex("[\\n\\r,;\\s]+"))
            val newUrls = lines.map { it.trim() }
                .filter { it.isNotEmpty() && it.startsWith("http") && it !in existingUrls }
                .distinct()
            
            newUrls.forEach { url ->
                repository.insertChannel(AudioChannel(name = "Loading...", type = ChannelType.RADIO, streamUrl = url))
            }
        }
    }

    fun bulkAddPodcasts(urlsText: String) {
        viewModelScope.launch {
            val existingUrls = allChannels.value.filter { it.type == ChannelType.PODCAST }.mapNotNull { it.streamUrl }.toSet()
            val lines = urlsText.split(Regex("[\\n\\r,;\\s]+"))
            val newUrls = lines.map { it.trim() }
                .filter { it.isNotEmpty() && it.startsWith("http") && it !in existingUrls }
                .distinct()
            
            newUrls.forEach { url ->
                val id = repository.insertChannel(AudioChannel(name = "Loading...", type = ChannelType.PODCAST, streamUrl = url))
                val title = podcastRepository.refreshFeed(id.toInt(), url)
                if (!title.isNullOrBlank()) {
                    repository.updateChannel(repository.getChannelById(id.toInt())?.copy(name = title) ?: return@forEach)
                }
            }
        }
    }

    fun setPodcastView(view: String) {
        _podcastState.update { it.copy(currentView = view) }
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
            val channel = _podcastState.value.activeChannel ?: return@launch
            repository.updateChannel(channel.copy(streamUrl = url))
            selectChannel(channel.id)
        }
    }

    private fun updateConfig(update: (AppConfig) -> AppConfig) {
        viewModelScope.launch { 
            val current = configDao.getConfigSync() ?: _appConfig.value
            configDao.saveConfig(update(current)) 
        }
    }

    fun setCategory(category: String) {
        updateConfig { it.copy(lastCategory = category) }
    }

    fun setActiveEpisode(episode: PodcastEpisode?) {
        _podcastState.update { it.copy(activeEpisode = episode) }
        updateConfig { it.copy(activePodcastEpisodeId = episode?.id) }
    }

    fun playPodcastEpisode(episode: PodcastEpisode) {
        playerLoadJob?.cancel()
        playerLoadJob = viewModelScope.launch {
            val channel = repository.getChannelById(episode.channelId) ?: return@launch

            isRefreshing = true
            try {
                // 1. Update State to point to this Podcast Channel
                _activeChannelId.value = episode.channelId
                setActiveEpisode(episode)
                
                updateConfig { it.copy(
                    activePodcastChannelId = episode.channelId,
                    lastCategory = "PODCASTS"
                ) }

                // 2. Update UI tracking
                _podcastState.update { it.copy(
                    positionMs = episode.playbackPositionMs,
                    durationMs = episode.durationMs,
                    isPlayingActiveEpisode = true
                ) }

                // 3. Load into ExoPlayer
                mediaController?.let { controller ->
                    controller.stop()
                    controller.clearMediaItems()
                    val uri = episode.localPath ?: episode.streamUrl
                    val item = MediaItem.Builder()
                        .setMediaId(episode.id.toString())
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(episode.title)
                                .setArtist(episode.podcastTitle)
                                .build()
                        )
                        .build()
                    controller.setMediaItems(listOf(item), 0, episode.playbackPositionMs)
                    controller.prepare()
                    controller.play()
                }

                // 4. Update the Podcast Channel in DB
                repository.updateChannel(channel.copy(
                    currentTrackUri = episode.id.toString(),
                    currentTrackTitle = episode.title,
                    currentPositionMs = episode.playbackPositionMs
                ))
            } finally {
                delay(1000)
                isRefreshing = false
                updateCurrentTrackInfo()
                saveCurrentPlaybackState()
            }
        }
    }

    fun playEpisodeById(episodeId: Int) {
        viewModelScope.launch {
            podcastRepository.getEpisodeById(episodeId)?.let { episode ->
                playPodcastEpisode(episode)
            }
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

    fun launchClock() {
        SystemApps.launchClock(app)
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
