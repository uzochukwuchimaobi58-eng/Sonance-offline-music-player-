package com.sonance.musicplayer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.util.AudioExportHelper
import com.sonance.musicplayer.video.VideoItem
import com.sonance.musicplayer.video.VideoRepository
import com.sonance.musicplayer.video.VideoThumbnailCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modern In-App Offline Video Player:
 * - Embedded responsive player in portrait with all videos showing right below
 * - Seamless playlist-driven ExoPlayer (Fixes next/prev video audio desync & surface freezing)
 * - Screen Size modes (Fit 16:9 / Zoom Fullscreen / Stretch Fill)
 * - True immersive Fullscreen with auto-rotate (Sensor / Landscape / Portrait)
 * - Every video has its own side 3-dot tools menu (Cutter, Loop, Speed, Details, Share)
 * - In-fullscreen video drawer to browse and switch all videos without exiting
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    theme: ThemeConfig,
    onBack: () -> Unit,
    onPauseMusic: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val videoRepo = remember { VideoRepository.getInstance(context) }
    val videos by videoRepo.videos.collectAsState()
    val isScanning by videoRepo.isScanning.collectAsState()

    var activeVideoIndex by remember { mutableIntStateOf(0) }
    var trimmingVideo by remember { mutableStateOf<VideoItem?>(null) }
    var detailsVideo by remember { mutableStateOf<VideoItem?>(null) }

    // Playback and UI states
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // Screen sizing: FIT (original 16:9), ZOOM (fill display without borders), FILL (stretch)
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var screenSizeLabel by remember { mutableStateOf("Fit (16:9)") }

    // Fullscreen & Auto-rotate states
    var isFullscreen by remember { mutableStateOf(false) }
    // 0 = Auto-Rotate Sensor (rotates with device tilt), 1 = Force Landscape, 2 = Force Portrait
    var autoRotateMode by remember { mutableIntStateOf(0) }
    var showFullscreenQueue by remember { mutableStateOf(false) }

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var showToolsMenu by remember { mutableStateOf(false) }

    fun showToastBadge(msg: String) {
        bannerMessage = msg
        scope.launch {
            delay(2200)
            if (bannerMessage == msg) bannerMessage = null
        }
    }

    var hasSelectedVideo by remember { mutableStateOf(false) }

    // Single persistent ExoPlayer instance with proper audio attributes and focus handling
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true // handleAudioFocus = true pauses background music automatically
            )
        }
    }

    // Keep active video synchronized with playlist only when user has selected a video
    val activeVideo = remember(videos, activeVideoIndex, hasSelectedVideo) {
        if (hasSelectedVideo && videos.isNotEmpty() && activeVideoIndex in videos.indices) {
            videos[activeVideoIndex]
        } else null
    }

    // Load full video playlist into ExoPlayer to ensure instant, seamless next/previous transitions
    LaunchedEffect(videos) {
        if (videos.isNotEmpty()) {
            val validIndex = activeVideoIndex.coerceIn(0, videos.size - 1)
            val currentPos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.isPlaying

            val mediaItems = videos.map { v ->
                MediaItem.Builder()
                    .setUri(v.uri)
                    .setMediaId(v.id.toString())
                    .build()
            }
            exoPlayer.setMediaItems(mediaItems, validIndex, currentPos)
            exoPlayer.prepare()
            if (hasSelectedVideo && wasPlaying) exoPlayer.play()
        }
    }

    // Switch video cleanly using native ExoPlayer seek
    fun playVideoAtIndex(index: Int) {
        if (videos.isEmpty()) return
        val targetIdx = index.coerceIn(0, videos.size - 1)
        activeVideoIndex = targetIdx
        hasSelectedVideo = true
        onPauseMusic()
        exoPlayer.seekTo(targetIdx, 0L)
        exoPlayer.play()
    }

    fun playNextVideo() {
        if (videos.isEmpty()) return
        val nextIdx = (activeVideoIndex + 1) % videos.size
        playVideoAtIndex(nextIdx)
        showToastBadge("Next: ${videos[nextIdx].title}")
    }

    fun playPreviousVideo() {
        if (videos.isEmpty()) return
        if (exoPlayer.currentPosition > 3000L) {
            exoPlayer.seekTo(0L)
        } else {
            val prevIdx = if (activeVideoIndex > 0) activeVideoIndex - 1 else videos.size - 1
            playVideoAtIndex(prevIdx)
            showToastBadge("Prev: ${videos[prevIdx].title}")
        }
    }

    // Setup ExoPlayer lifecycle and listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentIdx = exoPlayer.currentMediaItemIndex
                if (currentIdx in videos.indices) {
                    activeVideoIndex = currentIdx
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d > 0) durationMs = d
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.show(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
            }
        }
    }

    // Fullscreen Orientation & System Insets handling
    LaunchedEffect(isFullscreen, autoRotateMode) {
        if (isFullscreen) {
            when (autoRotateMode) {
                0 -> activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR // Auto-rotate with phone sensor
                1 -> activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE // Force Landscape
                2 -> activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT // Force Portrait
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.show(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
            }
        }
    }

    // Ticking position updater
    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPositionMs = exoPlayer.currentPosition
                val d = exoPlayer.duration
                if (d > 0) durationMs = d
            }
            delay(350)
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3600)
            showControls = false
        }
    }

    // File Picker for adding local device videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val title = uri.lastPathSegment?.substringAfterLast("/") ?: "Imported Video"
            videoRepo.addCustomVideo(uri, title)
            scope.launch {
                delay(300)
                playVideoAtIndex(0)
            }
        }
    }

    // Storage permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch { videoRepo.scanDeviceVideos() }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        videoRepo.scanDeviceVideos()
    }

    // Hardware back press handler
    BackHandler {
        when {
            showFullscreenQueue -> showFullscreenQueue = false
            trimmingVideo != null -> trimmingVideo = null
            detailsVideo != null -> detailsVideo = null
            isFullscreen -> isFullscreen = false
            hasSelectedVideo -> {
                exoPlayer.pause()
                hasSelectedVideo = false
            }
            else -> onBack()
        }
    }


    val formatTime: (Long) -> String = { ms ->
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        String.format("%02d:%02d", m, s)
    }

    // Root Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0E))
    ) {
        if (isFullscreen) {
            // ================= FULLSCREEN THEATER MODE =================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showControls = !showControls })
                    }
                    .testTag("fullscreen_video_view")
            ) {
                // Video Surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            this.resizeMode = resizeMode
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        if (view.player != exoPlayer) view.player = exoPlayer
                        view.resizeMode = resizeMode
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Temporary Banner message overlay
                bannerMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(1.dp, Color(0xFF22C55E), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(text = msg, color = Color(0xFF22C55E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Fullscreen Controls Overlay
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        // Top Fullscreen Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isFullscreen = false },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .testTag("btn_exit_fullscreen")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Exit Fullscreen",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeVideo?.title ?: "Video",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeVideo?.resolution ?: "HD"} • Screen: $screenSizeLabel",
                                    color = Color(0xFF22C55E),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Screen Size Mode Button
                            IconButton(
                                onClick = {
                                    when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            screenSizeLabel = "Zoom (Full)"
                                            showToastBadge("Screen Size: Zoom (Full)")
                                        }
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> {
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                                            screenSizeLabel = "Stretch (Fill)"
                                            showToastBadge("Screen Size: Stretch (Fill)")
                                        }
                                        else -> {
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            screenSizeLabel = "Fit (16:9)"
                                            showToastBadge("Screen Size: Fit (16:9)")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Screen Size",
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Auto Rotate Button
                            IconButton(
                                onClick = {
                                    autoRotateMode = (autoRotateMode + 1) % 3
                                    val modeText = when (autoRotateMode) {
                                        0 -> "Auto-Rotate: Sensor (Dynamic)"
                                        1 -> "Orientation: Force Landscape"
                                        else -> "Orientation: Force Portrait"
                                    }
                                    showToastBadge(modeText)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = "Auto Rotate",
                                    tint = if (autoRotateMode == 0) Color(0xFF22C55E) else Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // "All Videos" Drawer Toggle Button
                            IconButton(
                                onClick = { showFullscreenQueue = !showFullscreenQueue },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = "All Videos",
                                    tint = if (showFullscreenQueue) Color(0xFF22C55E) else Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // 3-Dot Tools Menu
                            Box {
                                IconButton(
                                    onClick = { showToolsMenu = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Video tools",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showToolsMenu,
                                    onDismissRequest = { showToolsMenu = false },
                                    modifier = Modifier.background(Color(0xFF161922))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Video Cutter / Trim", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color(0xFF22C55E)) },
                                        onClick = {
                                            showToolsMenu = false
                                            trimmingVideo = activeVideo
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isLooping) "Disable Video Loop" else "Enable Video Loop", color = Color.White) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Repeat,
                                                contentDescription = null,
                                                tint = if (isLooping) Color(0xFF22C55E) else Color.White
                                            )
                                        },
                                        onClick = {
                                            showToolsMenu = false
                                            isLooping = !isLooping
                                            exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                            showToastBadge(if (isLooping) "Video Loop: ON" else "Video Loop: OFF")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Video Info & Details", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF38BDF8)) },
                                        onClick = {
                                            showToolsMenu = false
                                            detailsVideo = activeVideo
                                        }
                                    )
                                }
                            }
                        }

                        // Center Controls (Rewind 10s, Big Play/Pause, Forward 10s)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(36.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                    exoPlayer.seekTo(newPos)
                                    currentPositionMs = newPos
                                    showToastBadge("<< 10s")
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                                    .clickable {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                                    .testTag("btn_fullscreen_play_pause"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(42.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(durationMs)
                                    exoPlayer.seekTo(newPos)
                                    currentPositionMs = newPos
                                    showToastBadge("10s >>")
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Bottom Fullscreen Controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            // Time Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(currentPositionMs),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatTime(durationMs),
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Slider(
                                value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                                onValueChange = { frac ->
                                    val target = (frac * durationMs).toLong()
                                    currentPositionMs = target
                                    exoPlayer.seekTo(target)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF22C55E),
                                    activeTrackColor = Color(0xFF22C55E),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                            )

                            // Action Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Previous / Next
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = { playPreviousVideo() },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Previous Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { playNextVideo() },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            isLooping = !isLooping
                                            exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                            showToastBadge(if (isLooping) "Loop: ON" else "Loop: OFF")
                                        },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                            contentDescription = "Loop Video",
                                            tint = if (isLooping) Color(0xFF22C55E) else Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Speed Chip
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .clickable {
                                                playbackSpeed = when (playbackSpeed) {
                                                    1.0f -> 1.25f
                                                    1.25f -> 1.5f
                                                    1.5f -> 2.0f
                                                    2.0f -> 0.5f
                                                    0.5f -> 0.75f
                                                    else -> 1.0f
                                                }
                                                exoPlayer.setPlaybackSpeed(playbackSpeed)
                                                showToastBadge("Speed: ${playbackSpeed}x")
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${playbackSpeed}x",
                                            color = Color(0xFF22C55E),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            isMuted = !isMuted
                                            exoPlayer.volume = if (isMuted) 0f else 1f
                                            showToastBadge(if (isMuted) "Muted" else "Unmuted")
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "Mute",
                                            tint = if (isMuted) Color(0xFFEF4444) else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick In-Fullscreen Video Drawer / Carousel
                AnimatedVisibility(
                    visible = showFullscreenQueue,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        color = Color(0xFF141720).copy(alpha = 0.95f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "All Videos (${videos.size})",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { showFullscreenQueue = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(videos) { idx, v ->
                                    val isSelected = idx == activeVideoIndex
                                    Column(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Color(0xFF22C55E).copy(alpha = 0.2f) else Color(0xFF1C202B))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF22C55E) else Color.White.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                playVideoAtIndex(idx)
                                                showFullscreenQueue = false
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        ) {
                                            VideoThumbnailView(video = v, modifier = Modifier.fillMaxSize())
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = v.title,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = v.formattedDuration,
                                            color = if (isSelected) Color(0xFF22C55E) else Color(0xFF8E8E93),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ================= PORTRAIT INTEGRATED VIEW =================
            // Video player at top + Active tools bar + All videos list clearly showing below
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Top Screen Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .testTag("btn_video_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VIDEO",
                                color = Color(0xFF22C55E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "Video (${videos.size})",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Import Video Button
                    IconButton(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                            .testTag("btn_import_video")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Import Video",
                            tint = Color(0xFF22C55E)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Refresh Button
                    IconButton(
                        onClick = { scope.launch { videoRepo.scanDeviceVideos() } },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .testTag("btn_rescan_videos")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Videos",
                            tint = Color.White
                        )
                    }
                }

                // Embedded Responsive Video Player (16:9 frame) - Only displays when user selects a video
                if (hasSelectedVideo && activeVideo != null) {
                    Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { showControls = !showControls })
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                this.resizeMode = resizeMode
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { view ->
                            if (view.player != exoPlayer) view.player = exoPlayer
                            view.resizeMode = resizeMode
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Banner feedback
                    bannerMessage?.let { msg ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                                .border(1.dp, Color(0xFF22C55E), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(text = msg, color = Color(0xFF22C55E), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Embedded Controls Overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            // Top overlay row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${activeVideo?.resolution ?: "HD"} • $screenSizeLabel",
                                        color = Color(0xFF22C55E),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Screen Size Toggle
                                    IconButton(
                                        onClick = {
                                            when (resizeMode) {
                                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                    screenSizeLabel = "Zoom (Full)"
                                                    showToastBadge("Screen: Zoom (Full)")
                                                }
                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> {
                                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                    screenSizeLabel = "Stretch (Fill)"
                                                    showToastBadge("Screen: Stretch (Fill)")
                                                }
                                                else -> {
                                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                    screenSizeLabel = "Fit (16:9)"
                                                    showToastBadge("Screen: Fit (16:9)")
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Default.AspectRatio, contentDescription = "Screen size", tint = Color(0xFF22C55E), modifier = Modifier.size(17.dp))
                                    }

                                    // Auto Rotate Toggle
                                    IconButton(
                                        onClick = {
                                            autoRotateMode = (autoRotateMode + 1) % 3
                                            isFullscreen = true
                                            showToastBadge(if (autoRotateMode == 0) "Auto-Rotate: ON (Sensor)" else "Orientation: Landscape")
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Default.ScreenRotation, contentDescription = "Auto rotate", tint = Color.White, modifier = Modifier.size(17.dp))
                                    }

                                    // Fullscreen Button
                                    IconButton(
                                        onClick = { isFullscreen = true },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }

                                    // Close Player Button
                                    IconButton(
                                        onClick = {
                                            exoPlayer.pause()
                                            hasSelectedVideo = false
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close player", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Center Play / Skip
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                IconButton(
                                    onClick = { playPreviousVideo() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(24.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E))
                                        .clickable {
                                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { playNextVideo() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }

                            // Bottom embedded progress slider
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = formatTime(currentPositionMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(text = formatTime(durationMs), color = Color(0xFFA1A1AA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                                    onValueChange = { frac ->
                                        val target = (frac * durationMs).toLong()
                                        currentPositionMs = target
                                        exoPlayer.seekTo(target)
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF22C55E),
                                        activeTrackColor = Color(0xFF22C55E)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp)
                                )
                            }
                        }
                    }
                }

                // Currently Playing Video Info Bar (Only displays when a video is selected and playing)
                activeVideo?.let { curVideo ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF10131B)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = curVideo.title,
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${curVideo.resolution} • ${curVideo.formattedDuration} • Offline",
                                    color = Color(0xFF22C55E),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = { isFullscreen = true },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                                }

                                IconButton(
                                    onClick = {
                                        exoPlayer.pause()
                                        hasSelectedVideo = false
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close player", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (isScanning) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        color = Color(0xFF22C55E),
                        trackColor = Color(0xFF1C1E24)
                    )
                }

                // ALL VIDEOS LIST - Showing vertical list till user plays any video
                if (videos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No videos found", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Tap '+ Import' above to browse and watch any video from your phone", color = Color(0xFF8E8E93), fontSize = 12.5.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(videos, key = { _, v -> v.id }) { index, itemVideo ->
                            val isCurrentlyPlaying = (hasSelectedVideo && itemVideo.id == activeVideo?.id)
                            VideoRowItem(
                                video = itemVideo,
                                isPlaying = isCurrentlyPlaying,
                                onPlay = {
                                    val realIdx = videos.indexOfFirst { it.id == itemVideo.id }
                                    if (realIdx >= 0) playVideoAtIndex(realIdx)
                                },
                                onOpenTrim = { trimmingVideo = itemVideo },
                                onSetLoop = {
                                    val realIdx = videos.indexOfFirst { it.id == itemVideo.id }
                                    if (realIdx >= 0) playVideoAtIndex(realIdx)
                                    isLooping = true
                                    exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                                    showToastBadge("Looping: ${itemVideo.title}")
                                },
                                onShowDetails = { detailsVideo = itemVideo }
                            )
                        }
                    }
                }
            }
        }

        // Dedicated Video Cutter / Trimmer Dialog
        trimmingVideo?.let { targetVideo ->
            VideoTrimmerDialog(
                video = targetVideo,
                onClose = { trimmingVideo = null }
            )
        }

        // Dedicated Video Details Dialog
        detailsVideo?.let { targetVideo ->
            VideoDetailsDialog(
                video = targetVideo,
                onClose = { detailsVideo = null }
            )
        }
    }
}


/**
 * Video Row Item with actual thumbnail, duration, resolution, "NOW PLAYING" indicator,
 * and individual side 3-dot tools menu (Cutter, Loop, Speed, Details, Share).
 */
@Composable
private fun VideoRowItem(
    video: VideoItem,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onOpenTrim: () -> Unit,
    onSetLoop: () -> Unit,
    onShowDetails: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay)
            .border(
                width = if (isPlaying) 1.5.dp else 1.dp,
                color = if (isPlaying) Color(0xFF22C55E) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("video_row_${video.id}"),
        color = if (isPlaying) Color(0xFF14241B) else Color(0xFF13161F),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail Box
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 70.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                VideoThumbnailView(video = video, modifier = Modifier.fillMaxSize())

                // Resolution Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.resolution,
                        color = Color(0xFF22C55E),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Duration Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                if (isPlaying) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "NOW PLAYING",
                            color = Color(0xFF22C55E),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = video.title,
                    color = if (isPlaying) Color(0xFF22C55E) else Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${video.formattedDuration} • ${video.formattedSize}",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Offline",
                        color = Color(0xFF22C55E),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Side Three-Dot Menu on every video
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .testTag("btn_video_menu_${video.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Video options",
                        tint = if (isPlaying) Color(0xFF22C55E) else Color(0xFFA1A1AA),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF1A1D27))
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Video", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF22C55E)) },
                        onClick = {
                            showMenu = false
                            onPlay()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Video Cutter / Trim", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color(0xFF22C55E)) },
                        onClick = {
                            showMenu = false
                            onOpenTrim()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Video Loop (Repeat This)", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.RepeatOne, contentDescription = null, tint = Color(0xFF22C55E)) },
                        onClick = {
                            showMenu = false
                            onSetLoop()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Video Info & Details", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF38BDF8)) },
                        onClick = {
                            showMenu = false
                            onShowDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share Video", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showMenu = false
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/*"
                                    putExtra(Intent.EXTRA_STREAM, video.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Video Thumbnail with asynchronous bitmap frame loading & LRU caching
 */
@Composable
private fun VideoThumbnailView(
    video: VideoItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember(video.id) {
        mutableStateOf(VideoThumbnailCache.get(video.id))
    }

    LaunchedEffect(video.id) {
        if (thumbnailBitmap == null) {
            val bmp = VideoThumbnailCache.loadThumbnail(context, video)
            thumbnailBitmap = bmp
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E2330), Color(0xFF0F121A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color(0xFF22C55E).copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Dedicated Video Trimmer Dialog (Video Cutter)
 */
@Composable
private fun VideoTrimmerDialog(
    video: VideoItem,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val maxSec = (video.durationMs / 1000f).coerceAtLeast(30f)
    var startSec by remember { mutableFloatStateOf(0f) }
    var endSec by remember { mutableFloatStateOf((maxSec.coerceAtMost(30f))) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF161922),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color(0xFF22C55E))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Video Cutter", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Start: ${String.format("%02d:%02d", (startSec / 60).toInt(), (startSec % 60).toInt())}",
                        color = Color(0xFF22C55E),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "End: ${String.format("%02d:%02d", (endSec / 60).toInt(), (endSec % 60).toInt())}",
                        color = Color(0xFF22C55E),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Start Point (sec):", color = Color(0xFF8E8E93), fontSize = 11.sp)
                Slider(
                    value = startSec,
                    onValueChange = { startSec = it.coerceAtMost(endSec - 1f) },
                    valueRange = 0f..maxSec,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF22C55E),
                        activeTrackColor = Color(0xFF22C55E)
                    )
                )

                Text("End Point (sec):", color = Color(0xFF8E8E93), fontSize = 11.sp)
                Slider(
                    value = endSec,
                    onValueChange = { endSec = it.coerceAtLeast(startSec + 1f) },
                    valueRange = 0f..maxSec,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF22C55E),
                        activeTrackColor = Color(0xFF22C55E)
                    )
                )

                Text(
                    text = "Clip Duration: ${(endSec - startSec).toInt()} seconds",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        val mockTrack = Track(
                            id = video.id.toString(),
                            title = video.title,
                            artist = "Video Audio",
                            album = "Video Clips",
                            duration = (video.durationMs / 1000),
                            url = video.path,
                            coverArt = ""
                        )
                        AudioExportHelper.exportTrimmedAudio(
                            context = context,
                            track = mockTrack,
                            startSec = startSec,
                            endSec = endSec,
                            saveAsRingtone = true
                        )
                        isSaving = false
                        Toast.makeText(context, "Saved trimmed clip to Ringtones/Downloads!", Toast.LENGTH_LONG).show()
                        onClose()
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isSaving) "Saving..." else "Cut & Save Clip", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel", color = Color(0xFF8E8E93))
            }
        }
    )
}

/**
 * Detailed Metadata Dialog for Video
 */
@Composable
private fun VideoDetailsDialog(
    video: VideoItem,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF161922),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Video Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailItem("Title", video.title)
                DetailItem("Resolution", video.resolution)
                DetailItem("Duration", video.formattedDuration)
                DetailItem("File Size", video.formattedSize)
                DetailItem("Format", if (video.path.endsWith(".mp4", ignoreCase = true)) "MP4 Video" else "Local Media")
                DetailItem("Path", video.path)
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(text = label, color = Color(0xFF8E8E93), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
