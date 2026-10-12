package com.sonance.musicplayer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.video.VideoItem
import com.sonance.musicplayer.video.VideoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoPlayerScreen(
    theme: ThemeConfig,
    onBack: () -> Unit,
    onPauseMusic: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoRepo = remember { VideoRepository.getInstance(context) }
    val videos by videoRepo.videos.collectAsState()
    val isScanning by videoRepo.isScanning.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeVideo by remember { mutableStateOf<VideoItem?>(null) }

    // Launcher to pick any offline video file from storage
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val title = uri.lastPathSegment?.substringAfterLast("/") ?: "Imported Video"
            videoRepo.addCustomVideo(uri, title)
            activeVideo = VideoItem(
                id = System.currentTimeMillis(),
                title = title,
                path = uri.toString(),
                uri = uri,
                durationMs = 0L,
                sizeBytes = 0L,
                width = 1920,
                height = 1080,
                dateAdded = System.currentTimeMillis() / 1000
            )
            onPauseMusic()
        }
    }

    // Permission launcher for videos
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

    BackHandler {
        if (activeVideo != null) {
            activeVideo = null
        } else {
            onBack()
        }
    }

    val filteredVideos = remember(videos, searchQuery) {
        if (searchQuery.isBlank()) videos
        else videos.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
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
                                .size(8.dp)
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Import Video Button
                IconButton(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    modifier = Modifier
                        .size(40.dp)
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

                // Rescan Button
                IconButton(
                    onClick = { scope.launch { videoRepo.scanDeviceVideos() } },
                    modifier = Modifier
                        .size(40.dp)
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

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search videos...",
                        color = Color(0xFF8E8E93),
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFF8E8E93)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF22C55E),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = Color(0xFF14161C),
                    unfocusedContainerColor = Color(0xFF14161C),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("input_search_videos")
            )

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    color = Color(0xFF22C55E),
                    trackColor = Color(0xFF1C1E24)
                )
            }

            // Video Grid List
            if (filteredVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1B1E26)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching videos" else "No videos found",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+ Import' above to browse and watch any video from your device",
                            color = Color(0xFF8E8E93),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Video", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredVideos, key = { it.id }) { video ->
                        VideoCard(
                            video = video,
                            onClick = {
                                activeVideo = video
                                onPauseMusic()
                            }
                        )
                    }
                }
            }
        }

        // In-App Fullscreen Video Player Modal
        activeVideo?.let { currentVideo ->
            InAppVideoPlayerView(
                video = currentVideo,
                allVideos = videos,
                onClose = { activeVideo = null },
                onSelectVideo = { next -> activeVideo = next }
            )
        }
    }
}

@Composable
private fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .testTag("video_card_${video.id}"),
        color = Color(0xFF14161C),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            // Thumbnail Area with Realistic Video Slate Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF1E2330),
                                Color(0xFF0F121A)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Video Graphic Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E).copy(alpha = 0.22f))
                        .border(1.5.dp, Color(0xFF22C55E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Resolution Badge (Top Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.resolution,
                        color = Color(0xFF22C55E),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Duration Badge (Bottom Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Info Area
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 13.sp,
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
                        text = video.formattedSize,
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
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun InAppVideoPlayerView(
    video: VideoItem,
    allVideos: List<VideoItem>,
    onClose: () -> Unit,
    onSelectVideo: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(video.durationMs) }
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }

    val exoPlayer = remember(video.uri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(video.uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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
            exoPlayer.release()
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
            delay(500)
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    val formatTime: (Long) -> String = { ms ->
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        String.format("%02d:%02d", m, s)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls }
                )
            }
            .testTag("in_app_video_player")
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
            ) {
                // Top Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .testTag("btn_close_player")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Video",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${video.resolution} • Video",
                            color = Color(0xFF22C55E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Resize / Fit Mode Toggle
                    IconButton(
                        onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Speed Toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable {
                                playbackSpeed = when (playbackSpeed) {
                                    1.0f -> 1.25f
                                    1.25f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.75f
                                    else -> 1.0f
                                }
                                exoPlayer.setPlaybackSpeed(playbackSpeed)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = Color(0xFF22C55E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Center Controls (Rewind 10s, Play/Pause, Forward 10s)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                            exoPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Main Big Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                            .clickable {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                            .testTag("btn_player_toggle_play"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(durationMs)
                            exoPlayer.seekTo(newPos)
                            currentPositionMs = newPos
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Control Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Time and Progress Bar
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
                            .height(28.dp)
                            .testTag("video_progress_slider")
                    )

                    // Secondary Bottom Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous / Next in playlist
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currentIndex = allVideos.indexOf(video)
                            IconButton(
                                onClick = {
                                    if (currentIndex > 0) {
                                        onSelectVideo(allVideos[currentIndex - 1])
                                    }
                                },
                                enabled = currentIndex > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Video",
                                    tint = if (currentIndex > 0) Color.White else Color.Gray
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (currentIndex in 0 until allVideos.size - 1) {
                                        onSelectVideo(allVideos[currentIndex + 1])
                                    }
                                },
                                enabled = currentIndex in 0 until allVideos.size - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Video",
                                    tint = if (currentIndex in 0 until allVideos.size - 1) Color.White else Color.Gray
                                )
                            }
                        }

                        // Mute button
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Toggle Audio",
                                tint = if (isMuted) Color(0xFFEF4444) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
