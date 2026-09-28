package com.sonance.musicplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import com.sonance.musicplayer.data.MusicRepository
import com.sonance.musicplayer.model.*
import com.sonance.musicplayer.player.PlaybackManager
import com.sonance.musicplayer.ui.components.*
import com.sonance.musicplayer.ui.screens.*
import com.sonance.musicplayer.ui.theme.SonanceTheme
import com.sonance.musicplayer.util.MusicFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: MusicRepository
    private lateinit var playbackManager: PlaybackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = MusicRepository.getInstance(applicationContext)
        playbackManager = PlaybackManager.getInstance(applicationContext, repository)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (isRunningOnEmulator()) {
                    android.util.Log.i("MainActivity", "Emulator environment detected: skipping MobileAds init")
                    return@launch
                }
                val gmsAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                val resultCode = gmsAvailability.isGooglePlayServicesAvailable(applicationContext)
                if (resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS) {
                    com.google.android.gms.ads.MobileAds.initialize(applicationContext) {}
                }
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "MobileAds init safe catch: ${t.message}")
            }
        }

        setContent {
            val scope = rememberCoroutineScope()

            // State from repository & playback manager
            val tracks by repository.tracksFlow.collectAsState()

            LaunchedEffect(tracks) {
                if (tracks.isNotEmpty()) {
                    playbackManager.restoreLastPlaybackState(tracks)
                }
            }
            val playlists by repository.playlistsFlow.collectAsState()
            val appTheme by repository.themeFlow.collectAsState()
            val unlockedThemeIds by repository.unlockedThemeIdsFlow.collectAsState()
            val customWallpaperUri by repository.customWallpaperUriFlow.collectAsState()
            val eqSettings by repository.equalizerFlow.collectAsState()
            val playerSettings by repository.settingsFlow.collectAsState()
            val remoteSettings by repository.remoteSettingsFlow.collectAsState()
            val userSubscription by repository.userSubscriptionFlow.collectAsState()
            val userProfile by repository.userProfileFlow.collectAsState()

            val currentTrack by playbackManager.currentTrack.collectAsState()
            val isPlaying by playbackManager.isPlaying.collectAsState()
            val currentPosMs by playbackManager.currentPositionMs.collectAsState()
            val durationMs by playbackManager.durationMs.collectAsState()
            val repeatMode by playbackManager.repeatMode.collectAsState()
            val isShuffle by playbackManager.isShuffle.collectAsState()
            val playbackSpeed by playbackManager.playbackSpeed.collectAsState()
            val audioEffect by playbackManager.activeEffect.collectAsState()
            val isKaraokeMode by playbackManager.isKaraokeMode.collectAsState()
            val sleepTimerSec by playbackManager.sleepTimerSeconds.collectAsState()
            val playQueue by playbackManager.playQueue.collectAsState()

            // Navigation state
            var activeView by remember { mutableStateOf(ActiveView.HOME) }
            var activePlaylistId by remember { mutableStateOf<String?>(null) }
            var searchQuery by remember { mutableStateOf("") }
            var currentSortBy by remember { mutableStateOf("title") }

            // Modals and dialogs
            var isSidebarOpen by remember { mutableStateOf(false) }
            var isFullPlayerOpen by remember { mutableStateOf(false) }
            var isQueueOpen by remember { mutableStateOf(false) }
            var isEqualizerOpen by remember { mutableStateOf(false) }
            var isSleepTimerOpen by remember { mutableStateOf(false) }
            var isThemePickerOpen by remember { mutableStateOf(false) }
            var isSettingsOpen by remember { mutableStateOf(false) }
            var isScanModalOpen by remember { mutableStateOf(false) }
            var isWebBrowserOpen by remember { mutableStateOf(false) }
            var isCreatePlaylistOpen by remember { mutableStateOf(false) }
            var isDriveModeOpen by remember { mutableStateOf(false) }
            var isLyricsModeOpen by remember { mutableStateOf(false) }
            var isProUpgradeOpen by remember { mutableStateOf(false) }
            var trimmingTrack by remember { mutableStateOf<Track?>(null) }
            var isKaraokeStudioOpen by remember { mutableStateOf(false) }
            var isBeatInstrumentalOpen by remember { mutableStateOf(false) }
            var isInterstitialAdOpen by remember { mutableStateOf(false) }
            var pendingPostAdAction by remember { mutableStateOf<(() -> Unit)?>(null) }

            // Effective Pro check (supports remote override or local subscription)
            val isProEffective = remember(userSubscription.isPro, remoteSettings.forceProOverride) {
                when (remoteSettings.forceProOverride) {
                    "force_true" -> true
                    "force_false" -> false
                    else -> userSubscription.isPro
                }
            }

            fun executeWithInterstitialAd(action: () -> Unit) {
                if (InterstitialAdController.shouldTriggerInterstitial(isProEffective, remoteSettings.admobEnabled)) {
                    InterstitialAdController.recordAdImpression()
                    pendingPostAdAction = action
                    isInterstitialAdOpen = true
                } else {
                    action()
                }
            }

            // Keep screen on setting
            LaunchedEffect(playerSettings.keepScreenOn) {
                if (playerSettings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Permissions request
            val permissionsToRequest = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                }
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                val granted = perms[Manifest.permission.READ_MEDIA_AUDIO] == true ||
                        perms[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
                        perms.values.any { it }
                if (granted) {
                    scope.launch {
                        repository.scanMediaStore()
                    }
                }
            }

            LaunchedEffect(Unit) {
                val hasAudioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.READ_MEDIA_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }

                if (!hasAudioPerm) {
                    permissionLauncher.launch(permissionsToRequest)
                } else {
                    scope.launch {
                        repository.scanMediaStore()
                    }
                }

                // Preload production AdMob Interstitial ad for free tier on real devices
                if (!isProEffective && remoteSettings.admobEnabled && !isRunningOnEmulator()) {
                    com.sonance.musicplayer.ui.components.InterstitialAdController.loadInterstitial(
                        this@MainActivity,
                        remoteSettings.admobInterstitialUnitId.ifBlank { com.sonance.musicplayer.ui.components.PRODUCTION_INTERSTITIAL_UNIT_ID }
                    )
                }
            }

            LaunchedEffect(playerSettings) {
                playbackManager.updateSettings(playerSettings)
            }

            SonanceTheme(appTheme = appTheme) { theme ->
                val activeViewTitle: String = remember(activeView, activePlaylistId, playlists) {
                    when (activeView) {
                        ActiveView.HOME -> "Music Player"
                        ActiveView.LIBRARY -> "LIBRARY"
                        ActiveView.FOLDER -> "Folder View"
                        ActiveView.FAVORITE -> "Favorite Tracks"
                        ActiveView.RECENT_PLAY -> "Recent Play"
                        ActiveView.RECENT_ADD -> "Recent Add"
                        ActiveView.MOST_PLAY -> "Most Played"
                        ActiveView.DRIVE_MODE -> "Drive Mode"
                        ActiveView.LYRICS_MODE -> "Lyrics"
                        ActiveView.PLAYLIST_DETAIL -> {
                            val pl = playlists.find { it.id == activePlaylistId }
                            pl?.name ?: "Playlist"
                        }
                    }
                }

                // Filtered legitimate music tracks
                val musicOnlyTracks: List<Track> = remember(tracks) {
                    tracks.filter { MusicFilter.isMusicTrack(it) }
                }

                // Filtered tracks for current view & search query
                val viewTracks: List<Track> = remember(musicOnlyTracks, activeView, activePlaylistId, playlists, searchQuery, playerSettings.smartPlaylistTrackLimit) {
                    val cutoffTime = when (playerSettings.smartPlaylistTrackLimit) {
                        "Past month" -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                        "Past 3 months" -> System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                        "Past 6 months" -> System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000
                        "Past year" -> System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
                        else -> 0L
                    }

                    val base: List<Track> = when (activeView) {
                        ActiveView.HOME -> musicOnlyTracks
                        ActiveView.LIBRARY -> musicOnlyTracks.sortedWith(com.sonance.musicplayer.util.TrackComparators.TitleComparator)
                        ActiveView.FOLDER -> musicOnlyTracks
                        ActiveView.DRIVE_MODE -> musicOnlyTracks
                        ActiveView.LYRICS_MODE -> musicOnlyTracks
                        ActiveView.FAVORITE -> musicOnlyTracks.filter { it.isFavorite }
                        ActiveView.RECENT_PLAY -> {
                            musicOnlyTracks.filter { it.playCount > 0 || it.lastPlayed > 0L }
                                .sortedByDescending { it.lastPlayed }
                        }
                        ActiveView.RECENT_ADD -> {
                            // Show tracks ordered by MediaStore DATE_ADDED (or DATE_MODIFIED fallback), newest first
                            musicOnlyTracks.sortedByDescending { it.dateAdded }
                        }
                        ActiveView.MOST_PLAY -> {
                            // Show tracks that have been played, ordered by play count descending
                            musicOnlyTracks.filter { it.playCount > 0 }
                                .sortedByDescending { it.playCount }
                        }
                        ActiveView.PLAYLIST_DETAIL -> {
                            val pl = playlists.find { it.id == activePlaylistId }
                            if (pl != null) {
                                musicOnlyTracks.filter { pl.trackIds.contains(it.id) }
                            } else emptyList()
                        }
                    }

                    if (searchQuery.isBlank()) {
                        base
                    } else {
                        val q = searchQuery.trim().lowercase()
                        base.filter {
                            MusicFilter.isMusicTrack(it) && (
                                it.title.lowercase().contains(q) ||
                                it.artist.lowercase().contains(q) ||
                                it.album.lowercase().contains(q)
                            )
                        }
                    }
                }

                var showSplashScreen by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1200L)
                    showSplashScreen = false
                }

                // Drive mode screen replaces the normal UI
                if (showSplashScreen) {
                    SplashScreen()
                } else if (isDriveModeOpen) {
                    DriveModeScreen(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        onTogglePlay = { playbackManager.togglePlayPause() },
                        onPrev = { playbackManager.skipToPrevious() },
                        onNext = { playbackManager.skipToNext() },
                        onExit = { isDriveModeOpen = false },
                        theme = theme,
                        isPro = isProEffective,
                        admobEnabled = remoteSettings.admobEnabled,
                        onOpenProUpgrade = { isProUpgradeOpen = true }
                    )
                } else if (isLyricsModeOpen) {
                    LyricsModeScreen(
                        track = currentTrack,
                        currentPosMs = currentPosMs,
                        onSeek = { playbackManager.seekTo(it) },
                        onClose = { isLyricsModeOpen = false },
                        theme = theme,
                        isPro = isProEffective,
                        admobEnabled = remoteSettings.admobEnabled,
                        onOpenProUpgrade = { isProUpgradeOpen = true },
                        onUpdateLyrics = { lyrics ->
                            currentTrack?.let { repository.updateLyrics(it.id, lyrics) }
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(theme.bgCanvas)) {
                        // Live Wallpaper Background: Custom User Photo OR Template Picture
                        if (customWallpaperUri != null) {
                            coil.compose.AsyncImage(
                                model = customWallpaperUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
                        } else if (theme.coverDrawableRes != null) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = theme.coverDrawableRes),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
                        }

                        Scaffold(
                            topBar = {
                                HeaderBar(
                                    title = activeViewTitle,
                                    isHome = activeView == ActiveView.HOME,
                                    onOpenSidebar = { isSidebarOpen = true },
                                    onBack = {
                                        activeView = ActiveView.HOME
                                        activePlaylistId = null
                                        searchQuery = ""
                                    },
                                    searchQuery = searchQuery,
                                    onSearchChange = { searchQuery = it },
                                    isPro = isProEffective,
                                    onOpenPro = { isProUpgradeOpen = true },
                                    onOpenScanModal = { isScanModalOpen = true },
                                    onOpenEqualizer = { isEqualizerOpen = true },
                                    onOpenSettings = { isSettingsOpen = true },
                                    onSortSelected = { sortKey -> currentSortBy = sortKey },
                                    onPlayAll = {
                                        if (viewTracks.isNotEmpty()) {
                                            playbackManager.setQueue(viewTracks, 0)
                                        }
                                    },
                                    onShuffleAll = {
                                        if (viewTracks.isNotEmpty()) {
                                            playbackManager.setQueue(viewTracks.shuffled(), 0)
                                        }
                                    },
                                    repeatMode = repeatMode,
                                    onToggleRepeat = { playbackManager.cycleRepeatMode() },
                                    theme = theme
                                )
                            },
                            bottomBar = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(theme.miniPlayerBg.copy(alpha = 0.92f))
                                        .navigationBarsPadding()
                                ) {
                                    // Mini Player (Lifted on top of AdMobBanner)
                                    AnimatedVisibility(
                                        visible = currentTrack != null,
                                        enter = slideInVertically(initialOffsetY = { it }),
                                        exit = slideOutVertically(targetOffsetY = { it })
                                    ) {
                                        MiniPlayerBar(
                                            track = currentTrack,
                                            isPlaying = isPlaying,
                                            currentPosMs = currentPosMs,
                                            durationMs = durationMs,
                                            onTogglePlay = { playbackManager.togglePlayPause() },
                                            onNext = { playbackManager.skipToNext() },
                                            onOpenQueue = { isQueueOpen = true },
                                            onOpenFullPlayer = { isFullPlayerOpen = true },
                                            theme = theme
                                        )
                                    }

                                    // AdMob Banner (positioned under the playing music bar at area 2)
                                    AdMobBanner(
                                        isPro = isProEffective,
                                        admobEnabled = remoteSettings.admobEnabled,
                                        onOpenProUpgrade = { isProUpgradeOpen = true },
                                        theme = theme
                                    )
                                }
                            },
                            containerColor = Color.Transparent
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (activeView == ActiveView.HOME && searchQuery.isEmpty()) {
                                HomeScreen(
                                    tracks = musicOnlyTracks,
                                    playlists = playlists,
                                    theme = theme,
                                    showShuffleButton = playerSettings.showShuffleButton,
                                    remoteSettings = remoteSettings,
                                    onSelectView = { selected ->
                                        when (selected) {
                                            ActiveView.RECENT_ADD,
                                            ActiveView.MOST_PLAY,
                                            ActiveView.RECENT_PLAY,
                                            ActiveView.FOLDER,
                                            ActiveView.FAVORITE -> {
                                                executeWithInterstitialAd { activeView = selected }
                                            }
                                            else -> activeView = selected
                                        }
                                    },
                                    onSelectPlaylist = { plId ->
                                        activePlaylistId = plId
                                        activeView = ActiveView.PLAYLIST_DETAIL
                                    },
                                    onOpenCreatePlaylist = { isCreatePlaylistOpen = true },
                                    onShuffleAll = {
                                        if (tracks.isNotEmpty()) {
                                            playbackManager.setQueue(tracks.shuffled(), 0)
                                        }
                                    },
                                    onOpenMusicTrim = {
                                        executeWithInterstitialAd {
                                            trimmingTrack = currentTrack ?: tracks.firstOrNull()
                                        }
                                    },
                                    onOpenKaraoke = {
                                        executeWithInterstitialAd { isKaraokeStudioOpen = true }
                                    },
                                    onOpenBeatInstrumental = {
                                        executeWithInterstitialAd { isBeatInstrumentalOpen = true }
                                    },
                                    onOpenEqualizer = {
                                        executeWithInterstitialAd { isEqualizerOpen = true }
                                    }
                                )
                            } else {
                                TrackListScreen(
                                    view = activeView,
                                    title = activeViewTitle,
                                    tracks = viewTracks,
                                    allPlaylists = playlists,
                                    currentTrackId = currentTrack?.id,
                                    isPlaying = isPlaying,
                                    theme = theme,
                                    showShuffleButton = playerSettings.showShuffleButton,
                                    repeatMode = repeatMode,
                                    onToggleRepeat = { playbackManager.cycleRepeatMode() },
                                    isShuffle = isShuffle,
                                    onToggleShuffle = { playbackManager.toggleShuffle() },
                                    isPro = isProEffective,
                                    admobEnabled = remoteSettings.admobEnabled,
                                    onOpenProUpgrade = { isProUpgradeOpen = true },
                                    onPlayTrack = { track, list ->
                                        if (currentTrack?.id == track.id) {
                                            // Tapping currently playing music brings up the full player interface directly
                                            isFullPlayerOpen = true
                                        } else {
                                            if (searchQuery.isNotBlank()) {
                                                when (playerSettings.queueAfterSearching) {
                                                    "Play immediately & replace queue" -> playbackManager.setQueue(listOf(track), 0)
                                                    "Add to current queue" -> playbackManager.addToQueue(track)
                                                    "Play next" -> playbackManager.playNext(track)
                                                    else -> {
                                                        val idx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                                        playbackManager.setQueue(list, idx)
                                                    }
                                                }
                                            } else if (playerSettings.clickTracksAddToCurrentQueue) {
                                                playbackManager.addToQueue(track)
                                            } else {
                                                val idx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                                playbackManager.setQueue(list, idx)
                                            }
                                            // Open the full player interface directly
                                            isFullPlayerOpen = true
                                        }
                                    },
                                    onToggleFavorite = { trId ->
                                        repository.toggleFavorite(trId)
                                    },
                                    onAddToPlaylist = { trId, plId ->
                                        repository.addTrackToPlaylist(trId, plId)
                                    },
                                    onDeleteTrack = { trId ->
                                        val tr = repository.tracks.value.find { it.id == trId }
                                        val duplicateIds = repository.tracks.value.filter { tr != null && repository.areTracksDuplicate(it, tr) }.map { it.id }
                                        val allIds = (setOf(trId) + duplicateIds).toSet()
                                        playbackManager.removeTracksFromQueue(allIds)
                                        repository.deleteTrack(trId)
                                        android.widget.Toast.makeText(applicationContext, "Music deleted permanently from device", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onPlayTracks = { selTracks ->
                                        if (selTracks.isNotEmpty()) {
                                            playbackManager.setQueue(selTracks, 0)
                                            isFullPlayerOpen = true
                                        }
                                    },
                                    onAddTracksToPlaylist = { trackIds, plId ->
                                        repository.addTracksToPlaylist(trackIds, plId)
                                    },
                                    onAddTracksToFavorites = { trackIds ->
                                        repository.addTracksToFavorites(trackIds)
                                    },
                                    onDeleteTracks = { trackIds ->
                                        val idSet = trackIds.toSet()
                                        val targets = repository.tracks.value.filter { it.id in idSet }
                                        val allMatchingIds = repository.tracks.value.filter { tr ->
                                            tr.id in idSet || targets.any { target -> repository.areTracksDuplicate(tr, target) }
                                        }.map { it.id }.toSet()
                                        playbackManager.removeTracksFromQueue(allMatchingIds)
                                        repository.deleteTracks(trackIds)
                                        android.widget.Toast.makeText(applicationContext, "${trackIds.size} songs deleted permanently from device", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onEnqueueTracks = { selTracks ->
                                        playbackManager.addTracksToQueue(selTracks)
                                        android.widget.Toast.makeText(applicationContext, "Added ${selTracks.size} songs to queue", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onOpenMusicTrim = { tr ->
                                        executeWithInterstitialAd { trimmingTrack = tr }
                                    },
                                    onOpenLyrics = { tr ->
                                        executeWithInterstitialAd { isLyricsModeOpen = true }
                                    },
                                    onShuffleAll = { list ->
                                        if (list.isNotEmpty()) {
                                            playbackManager.setQueue(list.shuffled(), 0)
                                            isFullPlayerOpen = true
                                        }
                                    },
                                    onSelectView = { targetView ->
                                        activeView = targetView
                                        activePlaylistId = null
                                    },
                                    onPlayNext = { tr -> playbackManager.playNext(tr) },
                                    activeSortBy = currentSortBy
                                )
                            }
                        }
                    }
                    }
                }

                // Sidebar Navigation Drawer
                SidebarDrawer(
                    isOpen = isSidebarOpen,
                    onClose = { isSidebarOpen = false },
                    playlists = playlists,
                    repeatMode = repeatMode,
                    sleepTimerSec = sleepTimerSec,
                    onSelectPlaylist = { plId ->
                        activePlaylistId = plId
                        activeView = ActiveView.PLAYLIST_DETAIL
                    },
                    onOpenCreatePlaylist = { isCreatePlaylistOpen = true },
                    onOpenScanModal = { isScanModalOpen = true },
                    onOpenEqualizer = {
                        executeWithInterstitialAd { isEqualizerOpen = true }
                    },
                    onToggleRepeat = { playbackManager.cycleRepeatMode() },
                    onOpenThemes = { isThemePickerOpen = true },
                    onOpenSleepTimer = { isSleepTimerOpen = true },
                    onEnterDriveMode = {
                        executeWithInterstitialAd { isDriveModeOpen = true }
                    },
                    onEnterLyricsMode = {
                        executeWithInterstitialAd { isLyricsModeOpen = true }
                    },
                    onOpenWebBrowser = { isWebBrowserOpen = true },
                    onOpenSettings = { isSettingsOpen = true },
                    isPro = isProEffective,
                    onOpenPro = { isProUpgradeOpen = true },
                    customWallpaperUri = customWallpaperUri,
                    theme = theme
                )

                // Full Screen Player Sheet
                FullPlayerSheet(
                    isOpen = isFullPlayerOpen,
                    onClose = { isFullPlayerOpen = false },
                    track = currentTrack,
                    isPlaying = isPlaying,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    repeatMode = repeatMode,
                    isShuffle = isShuffle,
                    playbackSpeed = playbackSpeed,
                    activeEffect = audioEffect,
                    isKaraokeMode = isKaraokeMode,
                    theme = theme,
                    showForwardBackward = playerSettings.forwardAndBackward,
                    customWallpaperUri = customWallpaperUri,
                    onTogglePlay = { playbackManager.togglePlayPause() },
                    onPrev = { playbackManager.skipToPrevious() },
                    onNext = { playbackManager.skipToNext() },
                    onSeek = { playbackManager.seekTo(it) },
                    onToggleRepeat = { playbackManager.cycleRepeatMode() },
                    onToggleShuffle = { playbackManager.toggleShuffle() },
                    onToggleFavorite = { trId -> repository.toggleFavorite(trId) },
                    onSetSpeed = { playbackManager.setSpeed(it) },
                    onSetAudioEffect = { playbackManager.setAudioEffect(it) },
                    onToggleKaraoke = { playbackManager.toggleKaraokeMode() },
                    onOpenEqualizer = { isEqualizerOpen = true },
                    onOpenSleepTimer = { isSleepTimerOpen = true },
                    onOpenLyrics = {
                        isFullPlayerOpen = false
                        isLyricsModeOpen = true
                    },
                    onOpenQueue = { isQueueOpen = true },
                    onOpenMusicTrim = { tr -> trimmingTrack = tr }
                )

                // Queue Sheet
                QueueBottomSheet(
                    isOpen = isQueueOpen,
                    onDismiss = { isQueueOpen = false },
                    queue = playQueue,
                    currentTrackId = currentTrack?.id,
                    onSelectTrack = { tr ->
                        val idx = playQueue.indexOfFirst { it.id == tr.id }.coerceAtLeast(0)
                        playbackManager.setQueue(playQueue, idx)
                    },
                    theme = theme
                )

                // Equalizer Dialog
                EqualizerDialog(
                    isOpen = isEqualizerOpen,
                    onClose = { isEqualizerOpen = false },
                    settings = eqSettings,
                    onApplySettings = { newSettings ->
                        repository.saveEqualizer(newSettings)
                        playbackManager.applyEqualizerSettings(newSettings)
                    },
                    theme = theme
                )

                // Ringtone Trimmer Dialog
                RingtoneTrimmerDialog(
                    isOpen = trimmingTrack != null,
                    onClose = { trimmingTrack = null },
                    track = trimmingTrack,
                    theme = theme
                )

                // Karaoke Studio Dialog
                KaraokeStudioDialog(
                    isOpen = isKaraokeStudioOpen,
                    onClose = { isKaraokeStudioOpen = false },
                    track = currentTrack,
                    isKaraokeActive = isKaraokeMode,
                    onToggleKaraoke = { playbackManager.toggleKaraokeMode() },
                    theme = theme
                )

                // Beat Instrumental Dialog
                BeatInstrumentalDialog(
                    isOpen = isBeatInstrumentalOpen,
                    onClose = { isBeatInstrumentalOpen = false },
                    track = currentTrack,
                    theme = theme,
                    playbackManager = playbackManager,
                    repository = repository,
                    onConversionFinished = {
                        executeWithInterstitialAd {}
                    }
                )

                // Sleep Timer Dialog
                SleepTimerDialog(
                    isOpen = isSleepTimerOpen,
                    onClose = { isSleepTimerOpen = false },
                    sleepTimerSec = sleepTimerSec,
                    onSetTimerMinutes = { minutes ->
                        playbackManager.setSleepTimer(minutes)
                    },
                    theme = theme
                )

                // Theme Dialog
                ThemeDialog(
                    isOpen = isThemePickerOpen,
                    onClose = { isThemePickerOpen = false },
                    currentTheme = appTheme,
                    onSelectTheme = { repository.saveTheme(it) },
                    theme = theme,
                    isPro = isProEffective,
                    onOpenProUpgrade = { isProUpgradeOpen = true },
                    unlockedThemeIds = unlockedThemeIds,
                    onUnlockThemeByAd = { repository.unlockThemeByAd(it) },
                    customWallpaperUri = customWallpaperUri,
                    onSetCustomWallpaperUri = { repository.setCustomWallpaperUri(it) }
                )

                // Settings Dialog
                SettingsDialog(
                    isOpen = isSettingsOpen,
                    onClose = { isSettingsOpen = false },
                    settings = playerSettings,
                    onUpdateSettings = {
                        repository.saveSettings(it)
                        playbackManager.updateSettings(it)
                    },
                    tracks = tracks,
                    onSelectTheme = { repository.saveTheme(it) },
                    onDeleteDuplicateTrack = { trId -> repository.deleteTrack(trId) },
                    remoteSettings = remoteSettings,
                    onSyncFirebase = { repository.syncFirebaseSettings() },
                    onUpdateFirebaseSettings = { updated ->
                        scope.launch {
                            repository.updateFirebaseSettings(updated)
                        }
                    },
                    userSubscription = userSubscription.copy(isPro = isProEffective),
                    onOpenProUpgrade = { isProUpgradeOpen = true },
                    onSetDevProState = { isPro -> repository.setDevProState(isPro) },
                    theme = theme
                )

                // Scan Library Dialog
                ScanLibraryDialog(
                    isOpen = isScanModalOpen,
                    onClose = { isScanModalOpen = false },
                    totalTrackCount = tracks.size,
                    onScan = { repository.scanMediaStore() },
                    theme = theme
                )

                // Web Browser Dialog
                WebBrowserDialog(
                    isOpen = isWebBrowserOpen,
                    onClose = { isWebBrowserOpen = false },
                    theme = theme
                )

                // Create Playlist Dialog
                CreatePlaylistDialog(
                    isOpen = isCreatePlaylistOpen,
                    onClose = { isCreatePlaylistOpen = false },
                    onCreate = { name -> repository.createPlaylist(name) },
                    theme = theme
                )

                // Pro Upgrade & Subscription Dialog
                ProUpgradeDialog(
                    isOpen = isProUpgradeOpen,
                    onClose = { isProUpgradeOpen = false },
                    subscription = userSubscription,
                    onSubscribe = { plan, price, email, provider ->
                        repository.subscribePro(plan, price, email, provider)
                    },
                    onRestorePurchases = { onRes ->
                        repository.restorePurchases(onRes)
                    },
                    theme = theme,
                    defaultYearlyPrice = remoteSettings.proYearlyPrice.ifBlank { "$1.00" },
                    defaultOneTimePrice = remoteSettings.proLifetimePrice.ifBlank { "$5.00" }
                )

                // Full-Screen Interstitial Ad Dialog (non-blocking for background music)
                InterstitialAdDialog(
                    isOpen = isInterstitialAdOpen,
                    onDismiss = {
                        isInterstitialAdOpen = false
                        val pending = pendingPostAdAction
                        pendingPostAdAction = null
                        pending?.invoke()
                    },
                    onOpenPro = {
                        isInterstitialAdOpen = false
                        val pending = pendingPostAdAction
                        pendingPostAdAction = null
                        pending?.invoke()
                        isProUpgradeOpen = true
                    },
                    theme = theme
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            repository.scanMediaStore()
        }
    }

    private fun isRunningOnEmulator(): Boolean {
        val finger = android.os.Build.FINGERPRINT.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val brand = android.os.Build.BRAND.lowercase()
        val device = android.os.Build.DEVICE.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        val hardware = android.os.Build.HARDWARE.lowercase()
        return finger.startsWith("generic")
            || finger.startsWith("unknown")
            || model.contains("google_sdk")
            || model.contains("emulator")
            || model.contains("android sdk built for x86")
            || hardware.contains("goldfish")
            || hardware.contains("ranchu")
            || product.contains("sdk_gphone")
            || product.contains("google_sdk")
            || product.contains("sdk")
            || product.contains("vbox86p")
            || (brand.startsWith("generic") && device.startsWith("generic"))
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do NOT stop playback if music is currently active so background playback continues even when user swipes away app!
        if (!playbackManager.isPlaying.value) {
            playbackManager.release()
        }
    }
}
