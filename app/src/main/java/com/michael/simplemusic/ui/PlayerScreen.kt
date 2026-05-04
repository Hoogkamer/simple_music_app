package com.michael.simplemusic.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.michael.simplemusic.data.AudioChannel
import com.michael.simplemusic.data.ChannelType
import com.michael.simplemusic.data.PodcastEpisode
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import kotlinx.coroutines.delay

enum class NavigationDestination {
    MUSIC, RADIO, PODCASTS
}

enum class PodcastNavigation {
    DASHBOARD, SHOW_DETAIL, EPISODE_DETAIL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = viewModel()
) {
    val allChannels by viewModel.allChannels.collectAsStateWithLifecycle()
    val appConfig by viewModel.appConfig.collectAsStateWithLifecycle()
    
    val activeMusicChannel by viewModel.activeMusicChannel.collectAsStateWithLifecycle()
    val activeRadioChannel by viewModel.activeRadioChannel.collectAsStateWithLifecycle()
    val activePodcastChannel by viewModel.activePodcastChannel.collectAsStateWithLifecycle()
    
    val activeChannelId by viewModel.activeChannelId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val currentTrackName by viewModel.currentTrackName.collectAsStateWithLifecycle()
    val streamMetadata by viewModel.streamMetadata.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val audioFiles by viewModel.audioFiles.collectAsStateWithLifecycle()
    val podcastEpisodes by viewModel.podcastEpisodes.collectAsStateWithLifecycle()
    val recentEpisodes by viewModel.recentEpisodes.collectAsStateWithLifecycle()
    val podcastView by viewModel.podcastView.collectAsStateWithLifecycle()
    val activePodcastEpisode by viewModel.activeEpisode.collectAsStateWithLifecycle()
    val radioSearchResults by viewModel.radioSearchResults.collectAsStateWithLifecycle()

    var showAddStation by remember { mutableStateOf(false) }
    var showBulkImport by remember { mutableStateOf(false) }
    var showAddPodcast by remember { mutableStateOf(false) }
    var showAddMusic by remember { mutableStateOf(false) }
    var showRadioSearch by remember { mutableStateOf(false) }
    val pendingMarkPlayed by viewModel.pendingMarkPlayed.collectAsStateWithLifecycle()
    
    var currentDestination by remember { 
        mutableStateOf(
            when(appConfig.lastCategory) {
                "RADIO" -> NavigationDestination.RADIO
                "PODCASTS" -> NavigationDestination.PODCASTS
                else -> NavigationDestination.MUSIC
            }
        )
    }

    var podcastNav by remember { 
        mutableStateOf(
            if (appConfig.lastCategory == "PODCASTS" && appConfig.activePodcastEpisodeId != null) 
                PodcastNavigation.EPISODE_DETAIL 
            else PodcastNavigation.DASHBOARD
        ) 
    }
    var cameFromRecent by remember { mutableStateOf(false) }
    var selectedPodcastId by remember { mutableIntStateOf(appConfig.activePodcastChannelId ?: -1) }

    var isPlayerVisible by remember { 
        mutableStateOf(
            when (appConfig.lastCategory) {
                "MUSIC" -> appConfig.activeMusicChannelId != null
                "RADIO" -> appConfig.activeRadioChannelId != null
                "PODCASTS" -> appConfig.activePodcastChannelId != null
                else -> false
            }
        ) 
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { folderUri ->
            val context = viewModel.getApplication<android.app.Application>()
            val docFile = DocumentFile.fromTreeUri(context, folderUri)
            viewModel.setFolder(folderUri, docFile?.name ?: "Unknown Folder")
            isPlayerVisible = true
        }
    }

    val importRadioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { fileUri ->
            val context = viewModel.getApplication<android.app.Application>()
            try {
                val content = context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.use { it.readText() }
                content?.let { viewModel.bulkAddRadioStations(it) }
            } catch (e: Exception) {
                // Handle error if needed
            }
        }
    }

    BackHandler(enabled = isPlayerVisible || (currentDestination == NavigationDestination.PODCASTS && podcastNav != PodcastNavigation.DASHBOARD)) {
        if (isPlayerVisible) {
            isPlayerVisible = false
        } else if (currentDestination == NavigationDestination.PODCASTS) {
            podcastNav = when (podcastNav) {
                PodcastNavigation.SHOW_DETAIL -> PodcastNavigation.DASHBOARD
                PodcastNavigation.EPISODE_DETAIL -> if (cameFromRecent) PodcastNavigation.DASHBOARD else PodcastNavigation.SHOW_DETAIL
                else -> PodcastNavigation.DASHBOARD
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isPlayerVisible && currentDestination == NavigationDestination.MUSIC) {
                            activeMusicChannel?.name ?: "Player"
                        } else {
                            when(currentDestination) {
                                NavigationDestination.MUSIC -> "Music Decks"
                                NavigationDestination.RADIO -> "Radio Stations"
                                NavigationDestination.PODCASTS -> {
                                    if (isPlayerVisible && activePodcastEpisode != null) {
                                        "Now Playing"
                                    } else {
                                        when(podcastNav) {
                                            PodcastNavigation.DASHBOARD -> "Podcasts"
                                            PodcastNavigation.SHOW_DETAIL -> allChannels.find { it.id == selectedPodcastId }?.name ?: "Show"
                                            PodcastNavigation.EPISODE_DETAIL -> "Episode Detail"
                                        }
                                    }
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    if (isPlayerVisible && currentDestination == NavigationDestination.MUSIC) {
                        IconButton(onClick = { isPlayerVisible = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else if (currentDestination == NavigationDestination.PODCASTS && podcastNav != PodcastNavigation.DASHBOARD) {
                        IconButton(onClick = { 
                            if (isPlayerVisible) {
                                isPlayerVisible = false
                            } else {
                                podcastNav = when(podcastNav) {
                                    PodcastNavigation.SHOW_DETAIL -> PodcastNavigation.DASHBOARD
                                    PodcastNavigation.EPISODE_DETAIL -> if (cameFromRecent) PodcastNavigation.DASHBOARD else PodcastNavigation.SHOW_DETAIL
                                    else -> PodcastNavigation.DASHBOARD
                                }
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (currentDestination == NavigationDestination.PODCASTS && podcastNav == PodcastNavigation.SHOW_DETAIL) {
                        IconButton(onClick = { viewModel.markAllAsPlayed(selectedPodcastId) }) {
                            Icon(Icons.Default.DoneAll, "Mark all played")
                        }
                        IconButton(onClick = { viewModel.toggleHidePlayed() }) {
                            Icon(if (appConfig.hidePlayedEpisodes) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle hide played")
                        }
                    }
                    if (currentDestination == NavigationDestination.RADIO) {
                        IconButton(onClick = { showRadioSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Radio")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Column {
                if (!isPlayerVisible || currentDestination != NavigationDestination.MUSIC) {
                    AnimatedVisibility(
                        visible = activeChannelId != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Surface(
                            tonalElevation = 8.dp,
                            modifier = Modifier.clickable { 
                                val channel = allChannels.find { it.id == activeChannelId }
                                if (channel?.type == ChannelType.FOLDER) {
                                    currentDestination = NavigationDestination.MUSIC
                                    isPlayerVisible = true
                                } else if (channel?.type == ChannelType.PODCAST) {
                                    currentDestination = NavigationDestination.PODCASTS
                                    isPlayerVisible = true
                                } else if (channel?.type == ChannelType.RADIO) {
                                    currentDestination = NavigationDestination.RADIO
                                    isPlayerVisible = false
                                }
                            },
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(currentTrackName.ifEmpty { streamMetadata ?: "Total Audio Hub" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (durationMs > 0) LinearProgressIndicator(progress = { (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp))
                                }
                                IconButton(onClick = { 
                                    if (currentDestination == NavigationDestination.PODCASTS && activePodcastEpisode != null && activeChannelId != activePodcastEpisode?.channelId) {
                                        viewModel.playPodcastEpisode(activePodcastEpisode!!)
                                    } else {
                                        viewModel.playPause()
                                    }
                                }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                            }
                        }
                    }
                }
                NavigationBar {
                    NavigationBarItem(selected = currentDestination == NavigationDestination.MUSIC, onClick = { currentDestination = NavigationDestination.MUSIC }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("Music") })
                    NavigationBarItem(selected = currentDestination == NavigationDestination.RADIO, onClick = { currentDestination = NavigationDestination.RADIO }, icon = { Icon(Icons.Default.Radio, null) }, label = { Text("Radio") })
                    NavigationBarItem(selected = currentDestination == NavigationDestination.PODCASTS, onClick = { currentDestination = NavigationDestination.PODCASTS }, icon = { Icon(Icons.Default.Podcasts, null) }, label = { Text("Podcasts") })
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (currentDestination) {
                NavigationDestination.MUSIC -> {
                    MusicDashboard(
                        channels = allChannels.filter { it.type == ChannelType.FOLDER },
                        activeChannelId = activeChannelId,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        currentDurationMs = durationMs,
                        isPlayerVisible = isPlayerVisible,
                        activeChannel = activeMusicChannel,
                        audioFiles = audioFiles,
                        onChannelClick = { viewModel.selectChannel(it); isPlayerVisible = true },
                        onDeleteChannel = { viewModel.deleteChannel(it) },
                        onRenameChannel = { id, name -> viewModel.renameChannel(id, name) },
                        onCreateChannel = { showAddMusic = true },
                        onFolderPick = { folderPickerLauncher.launch(null) },
                        onPlayPause = { 
                            val channel = activeMusicChannel
                            if (channel != null && activeChannelId != channel.id) {
                                viewModel.selectChannel(channel.id, autoPlay = true)
                            } else {
                                viewModel.playPause()
                            }
                        },
                        onNext = { if (activeChannelId == activeMusicChannel?.id) viewModel.next() },
                        onPrevious = { if (activeChannelId == activeMusicChannel?.id) viewModel.previous() },
                        onSeek = { if (activeChannelId == activeMusicChannel?.id) viewModel.seekTo(it) },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onToggleRepeat = { viewModel.toggleRepeat() }
                    )
                }
                NavigationDestination.RADIO -> {
                    RadioDashboard(
                        channels = allChannels.filter { it.type == ChannelType.RADIO },
                        activeChannelId = activeChannelId,
                        isPlaying = isPlaying,
                        streamMetadata = streamMetadata,
                        onChannelClick = { if (activeChannelId == it && isPlaying) viewModel.stopRadio() else viewModel.selectChannel(it) },
                        onDeleteChannel = { viewModel.deleteChannel(it) },
                        onRenameChannel = { id, name -> viewModel.renameChannel(id, name) },
                        onUrlUpdate = { id, url -> viewModel.setRadioUrl(id, url) },
                        onCreateChannel = { showAddStation = true },
                        onBulkImport = { showBulkImport = true }
                    )
                }
                NavigationDestination.PODCASTS -> {
                    if (isPlayerVisible && activePodcastEpisode != null) {
                        PodcastPlayer(
                            activeEpisode = activePodcastEpisode!!,
                            isPlaying = isPlaying && activeChannelId == activePodcastEpisode?.channelId,
                            playbackSpeed = playbackSpeed,
                            currentPositionMs = if (activeChannelId == activePodcastEpisode?.channelId && durationMs > 0) currentPositionMs else activePodcastEpisode!!.playbackPositionMs,
                            durationMs = if (activeChannelId == activePodcastEpisode?.channelId && durationMs > 0) durationMs else activePodcastEpisode!!.durationMs,
                            onPlayPause = {
                                if (activeChannelId == activePodcastEpisode?.channelId) {
                                    viewModel.playPause()
                                } else {
                                    activePodcastEpisode?.let { viewModel.playPodcastEpisode(it) }
                                }
                            },
                            onSkipBack = { if (activeChannelId == activePodcastEpisode?.channelId) viewModel.skipBack() },
                            onSkipForward = { if (activeChannelId == activePodcastEpisode?.channelId) viewModel.skipForward() },
                            onSeek = { if (activeChannelId == activePodcastEpisode?.channelId) viewModel.seekTo(it) },
                            onSpeedChange = { viewModel.setPlaybackSpeed(it) }
                        )
                    } else {
                        when (podcastNav) {
                            PodcastNavigation.DASHBOARD -> {
                                PodcastDashboard(
                                    channels = allChannels.filter { it.type == ChannelType.PODCAST },
                                    recentEpisodes = recentEpisodes,
                                    activeView = podcastView,
                                    onChannelClick = { id -> selectedPodcastId = id; viewModel.selectChannel(id); podcastNav = PodcastNavigation.SHOW_DETAIL },
                                    onEpisodeClick = { viewModel.setActiveEpisode(it); cameFromRecent = true; podcastNav = PodcastNavigation.EPISODE_DETAIL },
                                    onMarkPlayed = { viewModel.markEpisodeAsPlayed(it) },
                                    onSwitchView = { viewModel.setPodcastView(it) },
                                    onCreateChannel = { showAddPodcast = true },
                                    onDownloadAllNew = { viewModel.downloadAllNew() },
                                    onDeleteChannel = { viewModel.deleteChannel(it) },
                                    onUrlUpdate = { id, url -> viewModel.setRadioUrl(id, url) },
                                    pendingMarkPlayed = pendingMarkPlayed,
                                    onSwipeMarkPlayed = { viewModel.markAsPlayedWithUndo(it) },
                                    onUndoMarkPlayed = { viewModel.undoMarkAsPlayed(it) }
                                )
                            }
                        PodcastNavigation.SHOW_DETAIL -> {
                            PodcastShowDetail(
                                episodes = if (appConfig.hidePlayedEpisodes) podcastEpisodes.filter { !it.isFinished } else podcastEpisodes,
                                onEpisodeClick = { viewModel.setActiveEpisode(it); cameFromRecent = false; podcastNav = PodcastNavigation.EPISODE_DETAIL },
                                onPlayEpisode = { viewModel.playPodcastEpisode(it) },
                                onDownload = { viewModel.downloadEpisode(it) },
                                onDelete = { viewModel.deleteEpisodeFile(it) },
                                onMarkPlayed = { viewModel.markAsPlayedWithUndo(it) },
                                pendingMarkPlayed = pendingMarkPlayed,
                                onUndoMarkPlayed = { viewModel.undoMarkAsPlayed(it) }
                            )
                        }
                        PodcastNavigation.EPISODE_DETAIL -> {
                            activePodcastEpisode?.let { episode ->
                                EpisodeDetailScreen(
                                    episode = episode,
                                    isActive = activeChannelId == episode.channelId && activePodcastEpisode?.id == episode.id,
                                    isPlaying = isPlaying,
                                    playbackSpeed = playbackSpeed,
                                    currentPositionMs = currentPositionMs,
                                    durationMs = durationMs,
                                    onPlay = { viewModel.playPodcastEpisode(it) },
                                    onPause = { viewModel.playPause() },
                                    onSeek = { viewModel.seekTo(it) },
                                    onSkipBack = { viewModel.skipBack() },
                                    onSkipForward = { viewModel.skipForward() },
                                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                                    onDownload = { viewModel.downloadEpisode(it) },
                                    onDelete = { viewModel.deleteEpisodeFile(it) },
                                    onMarkPlayed = { viewModel.markEpisodeAsPlayed(it) },
                                    onMarkUnplayed = { viewModel.markEpisodeAsUnplayed(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
            
        // Overlays
        if (showAddStation) AddChannelScreen(title = "Add Radio Station", nameLabel = "Station Name", urlLabel = "Stream URL (http/https)", onSave = { name, url -> viewModel.createChannel(name, ChannelType.RADIO, url); showAddStation = false }, onDismiss = { showAddStation = false })
        if (showAddPodcast) AddPodcastScreen(onSave = { url -> viewModel.createChannel("", ChannelType.PODCAST, url); showAddPodcast = false }, onDismiss = { showAddPodcast = false })
        if (showAddMusic) AddMusicDeckScreen(onSave = { name -> viewModel.createChannel(name, ChannelType.FOLDER); showAddMusic = false }, onDismiss = { showAddMusic = false })
        if (showRadioSearch) RadioSearchDialog(results = radioSearchResults, onSearch = { viewModel.searchRadioStations(it) }, onSelect = { result -> viewModel.createChannel(result.name, ChannelType.RADIO, result.url); showRadioSearch = false }, onDismiss = { showRadioSearch = false })
        
        if (showBulkImport) BulkImportDialog(
            onSave = { viewModel.bulkAddRadioStations(it); showBulkImport = false },
            onFilePick = { importRadioLauncher.launch(arrayOf("text/*", "application/*")); showBulkImport = false },
            onDismiss = { showBulkImport = false }
        )
        }
    }
}

@Composable
fun MusicDashboard(channels: List<AudioChannel>, activeChannelId: Int?, isPlaying: Boolean, currentPositionMs: Long, currentDurationMs: Long, isPlayerVisible: Boolean, activeChannel: AudioChannel?, audioFiles: List<com.michael.simplemusic.scanner.AudioFile>, onChannelClick: (Int) -> Unit, onDeleteChannel: (Int) -> Unit, onRenameChannel: (Int, String) -> Unit, onCreateChannel: () -> Unit, onFolderPick: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit, onToggleShuffle: () -> Unit, onToggleRepeat: () -> Unit) {
    if (isPlayerVisible && activeChannel != null) {
        Box(modifier = Modifier.padding(16.dp)) {
            FolderPlayer(activeChannel, audioFiles, isPlaying && activeChannelId == activeChannel.id, if (activeChannelId == activeChannel.id) "" else activeChannel.currentTrackTitle ?: "", 0, if (activeChannelId == activeChannel.id) currentPositionMs else activeChannel.currentPositionMs, if (activeChannelId == activeChannel.id) currentDurationMs else activeChannel.currentTrackDurationMs, false, true, onFolderPick, onPlayPause, onNext, onPrevious, onSeek, onToggleShuffle, onToggleRepeat)
        }
    } else if (channels.isEmpty()) {
        EmptyState("No music decks yet", Icons.Default.LibraryMusic, onCreateChannel)
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(channels) { channel ->
                val isActive = channel.id == activeChannelId
                DeckCard(channel, isActive, isActive && isPlaying, if (isActive) currentPositionMs else channel.currentPositionMs, if (isActive && currentDurationMs > 0) currentDurationMs else channel.currentTrackDurationMs, { onChannelClick(channel.id) }, { onDeleteChannel(channel.id) }, { onRenameChannel(channel.id, it) })
            }
            item { AddButton("Add New Music Deck", onCreateChannel) }
        }
    }
}

@Composable
fun RadioDashboard(channels: List<AudioChannel>, activeChannelId: Int?, isPlaying: Boolean, streamMetadata: String?, onChannelClick: (Int) -> Unit, onDeleteChannel: (Int) -> Unit, onRenameChannel: (Int, String) -> Unit, onUrlUpdate: (Int, String) -> Unit, onCreateChannel: () -> Unit, onBulkImport: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (channels.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Radio, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("No radio stations yet", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(channels) { channel ->
                    val isActive = channel.id == activeChannelId
                    RadioCard(
                        channel = channel,
                        isActive = isActive,
                        isPlaying = isActive && isPlaying,
                        status = if (isActive && isPlaying) streamMetadata ?: "Streaming..." else "Live Station",
                        onClick = { onChannelClick(channel.id) },
                        onDelete = { onDeleteChannel(channel.id) },
                        onRename = { newName -> onRenameChannel(channel.id, newName) },
                        onUrlUpdate = { newUrl -> onUrlUpdate(channel.id, newUrl) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddButton("Add New", onCreateChannel, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBulkImport, modifier = Modifier.weight(1f).height(80.dp)) { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudUpload, null)
                    Text("Bulk Import", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun DeckCard(channel: AudioChannel, isActive: Boolean, isPlaying: Boolean, pos: Long, dur: Long, onClick: () -> Unit, onDelete: () -> Unit, onRename: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(channel.name ?: "") }

    Card(modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { onClick() }, onLongPress = { showMenu = true }) }, elevation = CardDefaults.cardElevation(if (isActive) 8.dp else 2.dp), colors = CardDefaults.cardColors(if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null, tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(channel.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(channel.folderDisplayName ?: "No folder", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(channel.currentTrackTitle ?: "No track playing", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(progress = { if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(pos), style = MaterialTheme.typography.bodySmall); Text(formatDuration(dur), style = MaterialTheme.typography.bodySmall)
            }
        }
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { showRenameDialog = true; showMenu = false }, leadingIcon = { Icon(Icons.Default.Edit, null) })
            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
        }
    }
    
    if (showRenameDialog) {
        AlertDialog(onDismissRequest = { showRenameDialog = false }, title = { Text("Rename Deck") }, text = { OutlinedTextField(value = renameInput, onValueChange = { renameInput = it }) }, confirmButton = { Button(onClick = { onRename(renameInput); showRenameDialog = false }) { Text("Save") } })
    }
}

@Composable
fun RadioCard(channel: AudioChannel, isActive: Boolean, isPlaying: Boolean, status: String, onClick: () -> Unit, onDelete: () -> Unit, onRename: (String) -> Unit, onUrlUpdate: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(channel.name ?: "") }
    var urlInput by remember { mutableStateOf(channel.streamUrl ?: "") }

    Card(modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { onClick() }, onLongPress = { showMenu = true }) }, elevation = CardDefaults.cardElevation(if (isActive) 8.dp else 2.dp), colors = CardDefaults.cardColors(if (isActive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isActive && isPlaying) Icons.Default.StopCircle else Icons.Default.PlayCircle, null, tint = if (isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(channel.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = if (isActive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { showRenameDialog = true; showMenu = false })
            DropdownMenuItem(text = { Text("Edit URL") }, onClick = { showUrlDialog = true; showMenu = false })
            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false })
        }
    }
    if (showRenameDialog) {
        AlertDialog(onDismissRequest = { showRenameDialog = false }, title = { Text("Rename Station") }, text = { OutlinedTextField(value = renameInput, onValueChange = { renameInput = it }, label = { Text("Station Name") }) }, confirmButton = { Button(onClick = { onRename(renameInput); showRenameDialog = false }) { Text("Save") } })
    }
    if (showUrlDialog) {
        AlertDialog(onDismissRequest = { showUrlDialog = false }, title = { Text("Edit Stream URL") }, text = { OutlinedTextField(value = urlInput, onValueChange = { urlInput = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { onUrlUpdate(urlInput); showUrlDialog = false }) { Text("Save") } })
    }
}

@Composable
fun PodcastCard(channel: AudioChannel, onClick: () -> Unit, onDelete: () -> Unit, onUrlUpdate: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf(channel.streamUrl ?: "") }

    Card(modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { onClick() }, onLongPress = { showMenu = true }) }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Podcasts, null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Text(channel.name, style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Edit RSS URL") }, onClick = { showUrlDialog = true; showMenu = false })
            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false })
        }
    }
    if (showUrlDialog) {
        AlertDialog(onDismissRequest = { showUrlDialog = false }, title = { Text("Edit RSS URL") }, text = { OutlinedTextField(value = urlInput, onValueChange = { urlInput = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { onUrlUpdate(urlInput); showUrlDialog = false }) { Text("Save") } })
    }
}

@Composable
fun AddPodcastScreen(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text("Subscribe to Podcast", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("RSS Feed URL") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
            Button(onClick = { onSave(url) }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = url.isNotBlank()) { Text("Subscribe") }
        }
    }
}

@Composable
fun AddChannelScreen(title: String, nameLabel: String, urlLabel: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(title, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(nameLabel) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(urlLabel) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
            Button(onClick = { onSave(name, url) }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = name.isNotBlank() && url.isNotBlank()) { Text("Save") }
        }
    }
}

@Composable
fun AddMusicDeckScreen(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text("New Music Deck", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Deck Name (e.g. Gym Mix)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
            Button(onClick = { onSave(name) }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = name.isNotBlank()) { Text("Create Deck") }
        }
    }
}

@Composable
fun RadioSearchDialog(results: List<com.michael.simplemusic.ui.RadioStationResult>, onSearch: (String) -> Unit, onSelect: (com.michael.simplemusic.ui.RadioStationResult) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, title = { Text("Search Global Radio") }, text = {
        Column {
            OutlinedTextField(value = query, onValueChange = { query = it; onSearch(it) }, label = { Text("Search...") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(results) { result ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(result) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = result.favicon, contentDescription = null, modifier = Modifier.size(32.dp).background(Color.LightGray))
                        Column(modifier = Modifier.padding(start = 12.dp)) { Text(result.name, maxLines = 1); Text(result.country ?: "", style = MaterialTheme.typography.bodySmall) }
                    }
                    HorizontalDivider()
                }
            }
        }
    })
}

@Composable
fun FolderPlayer(activeChannel: AudioChannel, audioFiles: List<com.michael.simplemusic.scanner.AudioFile>, isPlaying: Boolean, currentTrackName: String, currentTrackIndex: Int, currentPositionMs: Long, durationMs: Long, shuffleEnabled: Boolean, repeatEnabled: Boolean, onFolderPick: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit, onToggleShuffle: () -> Unit, onToggleRepeat: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(activeChannel.folderDisplayName ?: "No folder", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    if (audioFiles.isNotEmpty()) Text("${audioFiles.size} tracks", style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = onFolderPick) { Text("Change") }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(currentTrackName.ifEmpty { activeChannel.currentTrackTitle ?: "Ready" }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
        Slider(value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f, onValueChange = { onSeek((it * durationMs).toLong()) })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatDuration(currentPositionMs)); Text(formatDuration(durationMs)) }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleShuffle) { Icon(Icons.Default.Shuffle, null, tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(48.dp)) }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(80.dp)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(48.dp)) }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(48.dp)) }
            IconButton(onClick = onToggleRepeat) { Icon(Icons.Default.Repeat, null, tint = if (repeatEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun EmptyState(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
            Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) { Text("Add New") }
        }
    }
}

@Composable
fun BulkImportDialog(onSave: (String) -> Unit, onFilePick: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk Import Radio Stations") },
        text = {
            Column {
                Text("Paste stream URLs (one per line or comma-separated) or load a text file.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("http://stream.url/1\nhttp://stream.url/2") },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onFilePick, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Load from File (Google Drive/Local)")
                }
            }
        },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onSave(text) }) { Text("Import Pasted") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().height(80.dp)) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(text)
    }
}

@Composable
fun PodcastDashboard(channels: List<AudioChannel>, recentEpisodes: List<com.michael.simplemusic.data.PodcastEpisode>, activeView: String, onChannelClick: (Int) -> Unit, onEpisodeClick: (com.michael.simplemusic.data.PodcastEpisode) -> Unit, onMarkPlayed: (com.michael.simplemusic.data.PodcastEpisode) -> Unit, onSwitchView: (String) -> Unit, onCreateChannel: () -> Unit, onDownloadAllNew: () -> Unit, onDeleteChannel: (Int) -> Unit, onUrlUpdate: (Int, String) -> Unit, pendingMarkPlayed: Set<Int>, onSwipeMarkPlayed: (com.michael.simplemusic.data.PodcastEpisode) -> Unit, onUndoMarkPlayed: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (activeView == "SHOWS") 0 else 1) {
            Tab(selected = activeView == "SHOWS", onClick = { onSwitchView("SHOWS") }, text = { Text("Shows") })
            Tab(selected = activeView == "RECENT", onClick = { onSwitchView("RECENT") }, text = { Text("Recent") })
        }
        
        if (activeView == "SHOWS") {
            if (channels.isEmpty()) EmptyState("No podcasts yet", Icons.Default.Podcasts, onCreateChannel)
            else LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(channels) { channel ->
                    PodcastCard(
                        channel = channel,
                        onClick = { onChannelClick(channel.id) },
                        onDelete = { onDeleteChannel(channel.id) },
                        onUrlUpdate = { newUrl -> onUrlUpdate(channel.id, newUrl) }
                    )
                }
                item { AddButton("Subscribe to RSS", onCreateChannel) }
            }
        } else {
            if (recentEpisodes.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No recent episodes", color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDownloadAllNew) { Text("Download All New") }
                }
            }
            else LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Button(onClick = onDownloadAllNew, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download New (Last 2 Weeks)")
                    }
                }
                items(recentEpisodes, key = { it.id }) { episode ->
                    if (episode.id in pendingMarkPlayed) {
                        UndoItem(episode.title, onUndo = { onUndoMarkPlayed(episode.id) })
                    } else {
                        EpisodeListItem(
                            episode = episode,
                            onClick = { onEpisodeClick(episode) },
                            onPlay = { onEpisodeClick(episode) },
                            onDownload = {},
                            onDelete = {},
                            onSwipe = { onSwipeMarkPlayed(episode) },
                            isPending = false,
                            onUndo = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PodcastShowDetail(episodes: List<com.michael.simplemusic.data.PodcastEpisode>, onEpisodeClick: (com.michael.simplemusic.data.PodcastEpisode) -> Unit, onPlayEpisode: (com.michael.simplemusic.data.PodcastEpisode) -> Unit, onDownload: (com.michael.simplemusic.data.PodcastEpisode) -> Unit, onDelete: (com.michael.simplemusic.data.PodcastEpisode) -> Unit, onMarkPlayed: (com.michael.simplemusic.data.PodcastEpisode) -> Unit, pendingMarkPlayed: Set<Int>, onUndoMarkPlayed: (Int) -> Unit) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    
    val filteredEpisodes = remember(searchQuery, episodes) {
        if (searchQuery.isBlank()) episodes
        else episodes.filter { it.title.contains(searchQuery, ignoreCase = true) || it.description?.contains(searchQuery, ignoreCase = true) == true }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Search Episodes") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredEpisodes, key = { it.id }) { episode ->
                if (episode.id in pendingMarkPlayed) {
                    UndoItem(episode.title, onUndo = { onUndoMarkPlayed(episode.id) })
                } else {
                    EpisodeListItem(
                        episode = episode,
                        onClick = { onEpisodeClick(episode) },
                        onPlay = { onPlayEpisode(episode) },
                        onDownload = { onDownload(episode) },
                        onDelete = { onDelete(episode) },
                        onSwipe = { onMarkPlayed(episode) },
                        isPending = false,
                        onUndo = {}
                    )
                }
            }
        }
    }
}

@Composable
fun UndoItem(title: String, onUndo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Marked played", style = MaterialTheme.typography.labelLarge)
                Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onUndo, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                Text("UNDO")
            }
        }
    }
}

@Composable
fun EpisodeListItem(episode: com.michael.simplemusic.data.PodcastEpisode, onClick: () -> Unit, onPlay: () -> Unit, onDownload: () -> Unit, onDelete: () -> Unit, onSwipe: () -> Unit, isPending: Boolean, onUndo: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                onSwipe()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    ) {
        val alpha = if (episode.isFinished) 0.5f else 1.0f
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onClick() }) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Play button with circular progress
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp).clickable { onPlay() }) {
                    val progress = if (episode.durationMs > 0) (1f - (episode.playbackPositionMs.toFloat() / episode.durationMs.toFloat())).coerceIn(0f, 1f) else 1f
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize().rotate(-90f), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(24.dp))
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f).alpha(alpha)) {
                    episode.podcastTitle?.let { title ->
                        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                    Text(episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (episode.isDownloaded) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        val statusText = when {
                            episode.isFinished -> "Played"
                            episode.isDownloading -> "Downloading..."
                            episode.isQueued -> "Queued"
                            else -> if (episode.isDownloaded) "" else "Available"
                        }
                        if (statusText.isNotEmpty()) {
                            Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(formatDateToDaysAgo(episode.pubDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                if (!episode.isFinished && episode.durationMs > 0) {
                    Text(formatTimeRemaining(episode.durationMs - episode.playbackPositionMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            if (episode.isDownloading || episode.isQueued) {
                LinearProgressIndicator(
                    progress = { if (episode.isDownloading) episode.downloadProgress / 100f else 0f },
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }
        }
    }
}

@Composable
fun EpisodeDetailScreen(
    episode: com.michael.simplemusic.data.PodcastEpisode,
    isActive: Boolean,
    isPlaying: Boolean,
    playbackSpeed: Float,
    currentPositionMs: Long,
    durationMs: Long,
    onPlay: (com.michael.simplemusic.data.PodcastEpisode) -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onDownload: (com.michael.simplemusic.data.PodcastEpisode) -> Unit,
    onDelete: (com.michael.simplemusic.data.PodcastEpisode) -> Unit,
    onMarkPlayed: (com.michael.simplemusic.data.PodcastEpisode) -> Unit,
    onMarkUnplayed: (com.michael.simplemusic.data.PodcastEpisode) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(episode.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(formatDateToDaysAgo(episode.pubDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))

        if (isActive) {
            PodcastPlayer(
                activeEpisode = episode,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed,
                currentPositionMs = currentPositionMs,
                durationMs = if (durationMs > 0) durationMs else episode.durationMs,
                onPlayPause = { if (isPlaying) onPause() else onPlay(episode) },
                onSkipBack = onSkipBack,
                onSkipForward = onSkipForward,
                onSeek = onSeek,
                onSpeedChange = onSpeedChange
            )
            Spacer(Modifier.height(16.dp))
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (episode.isDownloaded) {
                if (!isActive) {
                    Button(onClick = { onPlay(episode) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play") }
                } else {
                    OutlinedButton(onClick = { onDelete(episode) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete") }
                }
            } else if (episode.isDownloading || episode.isQueued) {
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { 
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (episode.isQueued) "Queued" else "Downloading ${episode.downloadProgress}%")
                }
            } else {
                if (!isActive) {
                    Button(onClick = { onDownload(episode) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download") }
                    OutlinedButton(onClick = { onPlay(episode) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Stream") }
                } else {
                    Button(onClick = { onDownload(episode) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download") }
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { if (episode.isFinished) onMarkUnplayed(episode) else onMarkPlayed(episode) },
            modifier = Modifier.fillMaxWidth()
        ) { 
            Icon(if (episode.isFinished) Icons.Default.Replay else Icons.Default.Check, null)
            Spacer(Modifier.width(8.dp))
            Text(if (episode.isFinished) "Mark Unplayed" else "Mark Played") 
        }

        Spacer(Modifier.height(24.dp))
        Text("Show Notes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        HtmlText(episode.description ?: "No description available")
    }
}

@Composable
fun PodcastListScreen(channels: List<AudioChannel>, onChannelClick: (Int) -> Unit, onCreateChannel: () -> Unit) {
    // Deprecated in favor of PodcastDashboard
}

@Composable
fun PodcastPlayer(activeEpisode: com.michael.simplemusic.data.PodcastEpisode, isPlaying: Boolean, playbackSpeed: Float, currentPositionMs: Long, durationMs: Long, onPlayPause: () -> Unit, onSkipBack: () -> Unit, onSkipForward: () -> Unit, onSeek: (Long) -> Unit, onSpeedChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(activeEpisode.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Slider(value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f, onValueChange = { onSeek((it * durationMs).toLong()) })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(currentPositionMs)); Text(formatDuration(durationMs))
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = {
                val nextSpeed = when {
                    playbackSpeed < 0.8f -> 0.8f
                    playbackSpeed < 1.0f -> 1.0f
                    playbackSpeed < 1.2f -> 1.2f
                    playbackSpeed < 1.5f -> 1.5f
                    playbackSpeed < 2.0f -> 2.0f
                    else -> 0.5f
                }
                onSpeedChange(nextSpeed)
            }) {
                Text("${playbackSpeed}x", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onSkipBack) { Icon(Icons.Default.Replay10, null, modifier = Modifier.size(48.dp)) } // Using Replay10 as proxy for 15
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(40.dp)) }
            IconButton(onClick = onSkipForward) { Icon(Icons.Default.Forward30, null, modifier = Modifier.size(48.dp)) }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatTimeRemaining(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatDateToDaysAgo(pubDate: Long?): String {
    if (pubDate == null) return "Unknown"
    val diff = System.currentTimeMillis() - pubDate
    val days = diff / (24 * 60 * 60 * 1000)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        else -> "$days days ago"
    }
}

@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(context.getColor(android.R.color.tab_indicator_text))
                textSize = 16f
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { it.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT) }
    )
}
