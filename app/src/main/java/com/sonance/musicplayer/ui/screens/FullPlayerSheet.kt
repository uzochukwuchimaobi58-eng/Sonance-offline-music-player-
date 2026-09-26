package com.sonance.musicplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sonance.musicplayer.model.RepeatMode
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.model.TrendingAudioEffect
import com.sonance.musicplayer.ui.components.VisualizerWaveform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    isOpen: Boolean,
    onClose: () -> Unit,
    track: Track?,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    repeatMode: RepeatMode,
    isShuffle: Boolean,
    playbackSpeed: Float,
    activeEffect: TrendingAudioEffect,
    isKaraokeMode: Boolean,
    theme: ThemeConfig,
    showForwardBackward: Boolean = true,
    customWallpaperUri: String? = null,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetAudioEffect: (TrendingAudioEffect) -> Unit,
    onToggleKaraoke: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMusicTrim: (Track) -> Unit
) {
    if (!isOpen || track == null) return

    var showVisualizer by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEffectDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = theme.bgCanvas,
        dragHandle = null,
        modifier = Modifier
            .fillMaxSize()
            .testTag("full_player_sheet")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (customWallpaperUri != null) {
                AsyncImage(
                    model = customWallpaperUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.20f)
                )
            } else if (theme.coverDrawableRes != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = theme.coverDrawableRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.18f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
            // Top Bar: Minimize / Title / Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("btn_close_full_player")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize player",
                        tint = theme.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        color = theme.accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = track.album,
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = {
                            com.sonance.musicplayer.util.RingtoneHelper.setAsRingtoneImmediately(context, track)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Set as Ringtone immediately",
                            tint = theme.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = { onOpenMusicTrim(track) }) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Trim audio",
                            tint = theme.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Artwork or Visualizer Box
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1E1E24))
                    .clickable { showVisualizer = !showVisualizer },
                contentAlignment = Alignment.Center
            ) {
                if (!showVisualizer && track.coverArt.isNotEmpty()) {
                    AsyncImage(
                        model = track.coverArt,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VisualizerWaveform(
                            isPlaying = isPlaying,
                            accentColor = theme.accentColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isPlaying) "Live Audio Visualizer" else "Visualizer Paused",
                            color = theme.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Small badge to toggle visualizer
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (showVisualizer) "ARTWORK" else "WAVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Song Info & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = theme.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (track.artist == "<unknown>") "Unknown Artist" else track.artist,
                        color = theme.textSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { onToggleFavorite(track.id) },
                    modifier = Modifier.testTag("btn_full_favorite")
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) Color(0xFFF43F5E) else theme.textSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Slider
            val maxDuration = durationMs.coerceAtLeast(1000L).toFloat()
            val currentPos = currentPosMs.coerceIn(0L, durationMs.coerceAtLeast(1000L)).toFloat()

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = currentPos,
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..maxDuration,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.accentColor,
                        activeTrackColor = theme.accentColor,
                        inactiveTrackColor = theme.textSecondary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.testTag("full_player_slider")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val curSec = currentPosMs / 1000L
                    val durSec = durationMs / 1000L
                    Text(
                        text = String.format("%d:%02d", curSec / 60, curSec % 60),
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = String.format("%d:%02d", durSec / 60, durSec % 60),
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Playback Controls Row (Shuffle, Prev, Play/Pause, Next, Repeat)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                val availableWidth = maxWidth
                val isCompact = availableWidth < 350.dp
                val isMedium = availableWidth in 350.dp..390.dp

                val playSize = if (isCompact) 56.dp else if (isMedium) 62.dp else 68.dp
                val playIconSize = if (isCompact) 30.dp else if (isMedium) 34.dp else 38.dp
                val circleBtnSize = if (isCompact) 38.dp else if (isMedium) 42.dp else 44.dp
                val circleIconSize = if (isCompact) 20.dp else if (isMedium) 24.dp else 26.dp
                val skipBtnSize = if (isCompact) 28.dp else if (isMedium) 32.dp else 36.dp
                val skipIconSize = if (isCompact) 18.dp else if (isMedium) 22.dp else 24.dp
                val navIconSize = if (isCompact) 26.dp else if (isMedium) 30.dp else 34.dp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Shuffle Button with visible circle container on all devices
                    Surface(
                        shape = CircleShape,
                        color = if (isShuffle) theme.accentColor.copy(alpha = 0.22f) else theme.headerBg.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isShuffle) 1.5.dp else 1.dp,
                            color = if (isShuffle) theme.accentColor else theme.textSecondary.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.size(circleBtnSize)
                    ) {
                        IconButton(
                            onClick = onToggleShuffle,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("btn_full_shuffle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffle) theme.accentColor else theme.textSecondary,
                                modifier = Modifier.size(circleIconSize)
                            )
                        }
                    }

                    // Previous Button
                    IconButton(
                        onClick = onPrev,
                        modifier = Modifier
                            .size(if (isCompact) 36.dp else 44.dp)
                            .testTag("btn_full_prev")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = theme.textPrimary,
                            modifier = Modifier.size(navIconSize)
                        )
                    }

                    // Rewind 10s (if enabled)
                    if (showForwardBackward) {
                        IconButton(
                            onClick = { onSeek((currentPosMs - 10000L).coerceAtLeast(0L)) },
                            modifier = Modifier
                                .size(skipBtnSize)
                                .testTag("btn_full_rewind_10")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10 seconds",
                                tint = theme.textSecondary,
                                modifier = Modifier.size(skipIconSize)
                            )
                        }
                    }

                    // Big Play / Pause Button
                    Box(
                        modifier = Modifier
                            .size(playSize)
                            .clip(CircleShape)
                            .background(theme.accentColor)
                            .clickable(onClick = onTogglePlay)
                            .testTag("btn_full_play_pause"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(playIconSize)
                        )
                    }

                    // Forward 10s (if enabled)
                    if (showForwardBackward) {
                        IconButton(
                            onClick = { onSeek((currentPosMs + 10000L).coerceAtMost(durationMs)) },
                            modifier = Modifier
                                .size(skipBtnSize)
                                .testTag("btn_full_forward_10")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10 seconds",
                                tint = theme.textSecondary,
                                modifier = Modifier.size(skipIconSize)
                            )
                        }
                    }

                    // Next Button
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(if (isCompact) 36.dp else 44.dp)
                            .testTag("btn_full_next")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = theme.textPrimary,
                            modifier = Modifier.size(navIconSize)
                        )
                    }

                    // Prominent Repeat Button with guaranteed visible circle container & indicator on every device
                    Surface(
                        shape = CircleShape,
                        color = if (repeatMode != RepeatMode.OFF) theme.accentColor.copy(alpha = 0.22f) else theme.headerBg.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (repeatMode != RepeatMode.OFF) 1.5.dp else 1.dp,
                            color = if (repeatMode != RepeatMode.OFF) theme.accentColor else theme.textSecondary.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.size(circleBtnSize)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(onClick = onToggleRepeat)
                                .testTag("btn_full_repeat"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    RepeatMode.ONE -> Icons.Default.RepeatOne
                                    RepeatMode.ALL -> Icons.Default.Repeat
                                    RepeatMode.OFF -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (repeatMode != RepeatMode.OFF) theme.accentColor else theme.textSecondary,
                                modifier = Modifier.size(circleIconSize)
                            )
                            if (repeatMode != RepeatMode.OFF) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 2.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(theme.accentColor)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Actions Bar: Speed, Karaoke, FX, Equalizer, Lyrics, Queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed Chip
                PlayerPillChip(
                    label = "${playbackSpeed}x",
                    isActive = playbackSpeed != 1.0f,
                    onClick = { showSpeedDialog = true },
                    theme = theme
                )

                // Karaoke Vocal Toggle
                PlayerPillChip(
                    label = "Karaoke",
                    isActive = isKaraokeMode,
                    onClick = onToggleKaraoke,
                    theme = theme
                )

                // Audio Effect (Super Bass, etc.)
                PlayerPillChip(
                    label = activeEffect.label,
                    isActive = activeEffect != TrendingAudioEffect.OFF,
                    onClick = { showEffectDialog = true },
                    theme = theme
                )

                // Equalizer
                IconButton(onClick = onOpenEqualizer) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Equalizer",
                        tint = theme.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Sleep Timer
                IconButton(onClick = onOpenSleepTimer) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Sleep timer",
                        tint = theme.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Lyrics
                IconButton(onClick = onOpenLyrics) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "Lyrics",
                        tint = theme.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Queue
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = theme.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
    }

    // Speed Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = theme.sidebarBg,
            title = { Text("Playback Speed", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSetSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${speed}x ${if (speed == 1.0f) "(Normal)" else ""}",
                                color = theme.textPrimary,
                                fontSize = 14.sp
                            )
                            if (playbackSpeed == speed) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = theme.accentColor
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = theme.accentColor)
                }
            }
        )
    }

    // Audio Effect Dialog
    if (showEffectDialog) {
        AlertDialog(
            onDismissRequest = { showEffectDialog = false },
            containerColor = theme.sidebarBg,
            title = { Text("Sound Engine FX", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TrendingAudioEffect.entries.forEach { effect ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSetAudioEffect(effect)
                                    showEffectDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = effect.label,
                                color = theme.textPrimary,
                                fontSize = 14.sp
                            )
                            if (activeEffect == effect) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = theme.accentColor
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEffectDialog = false }) {
                    Text("Close", color = theme.accentColor)
                }
            }
        )
    }
}

@Composable
private fun PlayerPillChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    theme: ThemeConfig
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) theme.accentColor else theme.headerBg)
            .border(
                1.dp,
                if (isActive) theme.accentColor else theme.headerBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (isActive) Color.Black else theme.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
