package com.sonance.musicplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.data.LyricLine
import com.sonance.musicplayer.data.LyricsState
import com.sonance.musicplayer.data.Song
import kotlinx.coroutines.delay

/** Full lyrics view. Highlights the current line and scrolls with the song. */
@Composable
fun LyricsScreen(
    song: Song,
    state: LyricsState,
    position: () -> Long,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                TextButton(onClick = onClose) {
                    Text("Close", fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 1
                    )
                }
            }

            if (onRetry != null && state is LyricsState.NotFound) {
                IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = Color(0xFF4ADE80)
                    )
                }
            }
        }

        when (state) {
            is LyricsState.Loading, is LyricsState.None ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4ADE80))
                }

            is LyricsState.NotFound ->
                Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Lyrics not available",
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFFB8C7BE)
                        )
                        if (onRetry != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80))
                            ) {
                                Text("Retry Search", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

            is LyricsState.Found -> {
                var pos by remember { mutableLongStateOf(0L) }
                LaunchedEffect(Unit) {
                    while (true) {
                        pos = position()
                        delay(250)
                    }
                }
                val active = if (state.synced) state.lines.indexOfLast { it.timeMs <= pos } else -1
                val listState = rememberLazyListState()
                LaunchedEffect(active) {
                    if (active >= 0) listState.animateScrollToItem((active - 3).coerceAtLeast(0))
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    itemsIndexed(state.lines) { i, line ->
                        val isActive = i == active
                        Text(
                            text = line.text.ifBlank { "♪" },
                            fontSize = if (isActive) 24.sp else 20.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) Color(0xFF4ADE80) else Color(0xFFB8C7BE),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .then(if (state.synced) Modifier.clickable { onSeek(line.timeMs) } else Modifier)
                        )
                    }
                }
            }
        }
    }
}
