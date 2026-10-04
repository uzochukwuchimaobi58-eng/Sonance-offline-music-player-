package com.sonance.musicplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.data.LyricLine
import com.sonance.musicplayer.data.LyricsRepository
import com.sonance.musicplayer.data.LyricsState
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LyricsModeScreen(
    track: Track?,
    currentPosMs: Long,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    theme: ThemeConfig,
    isPro: Boolean = false,
    admobEnabled: Boolean = false,
    onOpenProUpgrade: () -> Unit = {},
    onUpdateLyrics: ((String) -> Unit)? = null,
    isKaraokeMode: Boolean = false,
    onToggleKaraoke: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val lyricsRepo = remember { LyricsRepository(context) }

    var lyricsState by remember { mutableStateOf<LyricsState>(LyricsState.None) }
    var lyricsJob by remember { mutableStateOf<Job?>(null) }

    fun loadLyricsForTrack(tr: Track?) {
        lyricsJob?.cancel()
        if (tr == null) {
            lyricsState = LyricsState.None
            return
        }
        lyricsJob = coroutineScope.launch {
            lyricsState = LyricsState.Loading
            val state = lyricsRepo.load(tr)
            lyricsState = state
            if (state is LyricsState.Found) {
                val reconstructedLrc = state.lines.joinToString("\n") { line ->
                    if (state.synced && line.timeMs >= 0) {
                        val min = (line.timeMs / 1000) / 60
                        val sec = (line.timeMs / 1000) % 60
                        val frac = (line.timeMs % 1000) / 10
                        String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]%s", min, sec, frac, line.text)
                    } else {
                        line.text
                    }
                }
                onUpdateLyrics?.invoke(reconstructedLrc)
            }
        }
    }

    LaunchedEffect(track?.id) {
        loadLyricsForTrack(track)
    }

    // Determine current active lyric line based on playback position
    val activeIndex = remember(currentPosMs, lyricsState) {
        when (val state = lyricsState) {
            is LyricsState.Found -> {
                if (state.synced && state.lines.isNotEmpty()) {
                    var found = -1
                    for (i in state.lines.indices) {
                        if (currentPosMs >= state.lines[i].timeMs) {
                            found = i
                        } else {
                            break
                        }
                    }
                    found
                } else -1
            }
            else -> -1
        }
    }

    // Auto-scroll list smoothly with song playback
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bgCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("lyrics_mode_screen")
    ) {
        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = null,
                    tint = theme.accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = track?.title ?: "Lyrics",
                        color = theme.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = when (lyricsState) {
                            is LyricsState.Loading -> "Fetching synchronized lyrics online..."
                            is LyricsState.Found -> {
                                val s = lyricsState as LyricsState.Found
                                if (s.synced) "Synchronized Lyrics (LRCLIB)" else "Plain Lyrics"
                            }
                            is LyricsState.NotFound -> "No lyrics matched"
                            else -> track?.artist ?: "Music Lyrics"
                        },
                        color = if (lyricsState is LyricsState.Loading) theme.accentColor else theme.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Karaoke Mode Toggle Chip in Lyrics Screen
                if (onToggleKaraoke != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isKaraokeMode) Color(0xFFFB7185) else theme.headerBg.copy(alpha = 0.8f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isKaraokeMode) Color(0xFFFB7185) else theme.headerBorder
                        ),
                        modifier = Modifier
                            .clickable { onToggleKaraoke() }
                            .padding(end = 6.dp)
                            .testTag("chip_lyrics_karaoke")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isKaraokeMode) Color.White else theme.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isKaraokeMode) "Karaoke ON" else "Karaoke",
                                color = if (isKaraokeMode) Color.White else theme.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Refresh / Re-fetch Button
                IconButton(
                    onClick = { loadLyricsForTrack(track) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Lyrics",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Download Lyrics (.lrc to Downloads folder)
                IconButton(
                    onClick = {
                        val state = lyricsState
                        if (track != null && state is LyricsState.Found) {
                            val content = state.lines.joinToString("\n") { line ->
                                if (state.synced && line.timeMs >= 0) {
                                    val min = (line.timeMs / 1000) / 60
                                    val sec = (line.timeMs / 1000) % 60
                                    val frac = (line.timeMs % 1000) / 10
                                    String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]%s", min, sec, frac, line.text)
                                } else {
                                    line.text
                                }
                            }
                            coroutineScope.launch {
                                com.sonance.musicplayer.util.AudioExportHelper.exportLyrics(
                                    context = context,
                                    track = track,
                                    lyricsContent = content
                                )
                            }
                        } else {
                            android.widget.Toast.makeText(context, "No lyrics available to download", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Lyrics",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(theme.headerBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Lyrics",
                        tint = theme.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main Lyrics Body depending on LyricsState
        when (val state = lyricsState) {
            is LyricsState.Loading, is LyricsState.None -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = theme.accentColor,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading synchronized lyrics...",
                            color = theme.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Searching LRCLIB → Lyrics.ovh → Karalyr",
                            color = theme.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            is LyricsState.NotFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = theme.textSecondary.copy(alpha = 0.45f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Lyrics not available",
                            color = theme.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Checked LRCLIB, Lyrics.ovh, and Karalyr",
                            color = theme.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { loadLyricsForTrack(track) },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                        ) {
                            Text("Retry Search", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            is LyricsState.Found -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 32.dp, horizontal = 8.dp)
                ) {
                    itemsIndexed(state.lines) { index, line ->
                        val isActive = index == activeIndex

                        Text(
                            text = line.text,
                            color = if (isActive) theme.accentColor else theme.textSecondary.copy(alpha = if (state.synced) 0.5f else 0.85f),
                            fontSize = if (isActive) 23.sp else 18.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            lineHeight = if (isActive) 30.sp else 24.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (state.synced && line.timeMs >= 0) {
                                        onSeek(line.timeMs)
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 10.dp)
                        )
                    }
                }
            }
        }

        // AdMob banner on bottom of Lyrics Mode if enabled
        if (!isPro && admobEnabled) {
            com.sonance.musicplayer.ui.components.AdMobBanner(
                isPro = isPro,
                admobEnabled = admobEnabled,
                onOpenProUpgrade = onOpenProUpgrade,
                theme = theme
            )
        }
    }
}
