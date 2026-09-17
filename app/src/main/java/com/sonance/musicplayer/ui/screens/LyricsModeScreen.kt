package com.sonance.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
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
    onOpenProUpgrade: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val parsedLyrics = remember(track?.lyrics) {
        val raw = track?.lyrics ?: ""
        if (raw.isBlank()) emptyList()
        else {
            val lines = mutableListOf<LyricLine>()
            val regex = Regex("""\[(\d{2}):(\d{2}(?:\.\d+)?)\](.*)""")
            raw.lines().forEach { line ->
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
            var found = -1
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
            .padding(20.dp)
            .testTag("lyrics_mode_screen")
    ) {
        // Top row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        text = track?.artist ?: "",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
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

        Spacer(modifier = Modifier.height(24.dp))

        if (parsedLyrics.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No synchronized lyrics available for this track",
                        color = theme.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 40.dp)
            ) {
                itemsIndexed(parsedLyrics) { index, line ->
                    val isActive = index == activeIndex

                    Text(
                        text = line.text,
                        color = if (isActive) theme.accentColor else theme.textSecondary.copy(alpha = 0.5f),
                        fontSize = if (isActive) 24.sp else 18.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (line.timeMs > 0) {
                                    onSeek(line.timeMs)
                                }
                            }
                            .padding(vertical = 4.dp)
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
