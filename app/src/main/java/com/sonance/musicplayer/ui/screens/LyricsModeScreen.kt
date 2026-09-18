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
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.util.LyricsScanner
import kotlinx.coroutines.launch

data class LyricLine(
    val timeMs: Long,
    val text: String
)

@Composable
fun LyricsModeScreen(
    track: Track?,
    currentPosMs: Long,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    theme: ThemeConfig,
    isPro: Boolean = false,
    admobEnabled: Boolean = true,
    onOpenProUpgrade: () -> Unit = {},
    onUpdateLyrics: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var activeLyricsText by remember(track?.id, track?.lyrics) {
        mutableStateOf(track?.lyrics ?: "")
    }
    var isScanning by remember { mutableStateOf(false) }

    // Automatically scan & fetch/generate lyrics or music captions if empty
    LaunchedEffect(track?.id) {
        if (track != null) {
            if (activeLyricsText.isBlank()) {
                isScanning = true
                val result = LyricsScanner.scanOrGenerateLyrics(context, track)
                activeLyricsText = result
                isScanning = false
                onUpdateLyrics?.invoke(result)
            }
        }
    }

    val parsedLyrics = remember(activeLyricsText) {
        if (activeLyricsText.isBlank()) emptyList()
        else {
            val lines = mutableListOf<LyricLine>()
            val regex = Regex("""\[(\d{2}):(\d{2}(?:\.\d+)?)\](.*)""")
            activeLyricsText.lines().forEach { line ->
                val match = regex.find(line.trim())
                if (match != null) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toFloatOrNull() ?: 0f
                    val text = match.groupValues[3].trim()
                    val totalMs = (min * 60 * 1000L) + (sec * 1000L).toLong()
                    if (text.isNotEmpty()) {
                        lines.add(LyricLine(totalMs, text))
                    }
                } else if (line.trim().isNotEmpty() && !line.startsWith("[")) {
                    lines.add(LyricLine(0L, line.trim()))
                }
            }
            lines
        }
    }

    val activeIndex = remember(currentPosMs, parsedLyrics) {
        if (parsedLyrics.isEmpty()) -1
        else {
            var found = 0
            for (i in parsedLyrics.indices) {
                if (currentPosMs >= parsedLyrics[i].timeMs) {
                    found = i
                } else {
                    break
                }
            }
            found
        }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && parsedLyrics.isNotEmpty()) {
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
        // Top row
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
                        text = track?.title ?: "Lyrics & Captions",
                        color = theme.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (isScanning) "Auto-scanning synchronized lyrics..." else (track?.artist ?: "Music Captions"),
                        color = if (isScanning) theme.accentColor else theme.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Rescan / Refresh Button
                IconButton(
                    onClick = {
                        if (track != null) {
                            coroutineScope.launch {
                                isScanning = true
                                val result = LyricsScanner.scanOrGenerateLyrics(context, track)
                                activeLyricsText = result
                                isScanning = false
                                onUpdateLyrics?.invoke(result)
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(CircleShape)
                        .background(theme.headerBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh lyrics",
                        tint = theme.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.headerBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Lyrics",
                        tint = theme.textPrimary
                    )
                }
            }
        }

        // Scanning indicator banner
        AnimatedVisibility(
            visible = isScanning,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = theme.accentColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = theme.accentColor,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Scanning & matching synchronized lyrics...",
                        color = theme.accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (parsedLyrics.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = theme.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No lyrics found for this music",
                        color = theme.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap below to generate automatic music captions",
                        color = theme.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (track != null) {
                                val generated = LyricsScanner.generateSynchronizedMusicCaptions(track)
                                activeLyricsText = generated
                                onUpdateLyrics?.invoke(generated)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                    ) {
                        Text("Generate Music Captions", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 32.dp)
            ) {
                itemsIndexed(parsedLyrics) { index, line ->
                    val isActive = index == activeIndex

                    Text(
                        text = line.text,
                        color = if (isActive) theme.accentColor else theme.textSecondary.copy(alpha = 0.55f),
                        fontSize = if (isActive) 23.sp else 17.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (line.timeMs > 0) {
                                    onSeek(line.timeMs)
                                }
                            }
                            .padding(vertical = 6.dp, horizontal = 12.dp)
                    )
                }
            }

            // AdMob banner on bottom of Lyrics Mode
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
}
