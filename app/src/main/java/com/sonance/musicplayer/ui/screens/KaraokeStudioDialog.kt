package com.sonance.musicplayer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.player.PlaybackManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokeStudioDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    track: Track?,
    isKaraokeActive: Boolean,
    onToggleKaraoke: () -> Unit,
    theme: ThemeConfig,
    playbackManager: PlaybackManager? = null,
    allTracks: List<Track> = emptyList(),
    isPlaying: Boolean = false,
    onPlayTrack: ((Track) -> Unit)? = null,
    onOpenLyrics: (() -> Unit)? = null
) {
    if (!isOpen) return

    val context = LocalContext.current
    var localIsKaraokeActive by remember(isKaraokeActive) { mutableStateOf(isKaraokeActive) }
    var vocalReduction by remember { mutableFloatStateOf(85f) }

    // Live preview update while slider moves if Karaoke is active
    LaunchedEffect(localIsKaraokeActive, vocalReduction) {
        if (localIsKaraokeActive) {
            playbackManager?.setKaraokeMode(true, vocalReduction)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = theme.sidebarBg,
        modifier = Modifier.testTag("karaoke_studio_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Row with Title and Persistent Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFB7185).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Karaoke Studio",
                            tint = Color(0xFFFB7185),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Karaoke Studio",
                            color = theme.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Center Vocal Remover & Sing-Along",
                            color = theme.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Global Karaoke Toggle Switch
                Switch(
                    checked = localIsKaraokeActive,
                    onCheckedChange = { checked ->
                        localIsKaraokeActive = checked
                        playbackManager?.setKaraokeMode(checked, vocalReduction)
                        Toast.makeText(
                            context,
                            if (checked) "🎤 Karaoke Mode ON: Vocals attenuated" else "Karaoke Mode turned OFF",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFB7185),
                        checkedTrackColor = Color(0xFFFB7185).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("switch_karaoke_mode")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status message badge
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = if (localIsKaraokeActive) Color(0xFFFB7185).copy(alpha = 0.15f) else theme.headerBg.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (localIsKaraokeActive) Color(0xFFFB7185).copy(alpha = 0.4f) else theme.headerBorder.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (localIsKaraokeActive) Icons.Default.Check else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (localIsKaraokeActive) Color(0xFFFB7185) else theme.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (localIsKaraokeActive) {
                            "✓ Karaoke Active: Center lead vocals suppressed across all songs"
                        } else {
                            "Turn toggle ON or tap Apply to isolate instrumental backing"
                        },
                        color = if (localIsKaraokeActive) Color(0xFFFB7185) else theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Now Playing Track or Song Picker Card
            val activeSong = track ?: allTracks.firstOrNull()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = theme.headerBg.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.headerBorder.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = theme.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeSong?.title ?: "No Song Selected",
                                    color = theme.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (track != null) {
                                        if (isPlaying) "Now Playing • Sing along live" else "Paused • Tap play to test"
                                    } else {
                                        "Tap play below to start singing"
                                    },
                                    color = theme.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // Play/Pause Action Button
                        IconButton(
                            onClick = {
                                if (track != null) {
                                    if (isPlaying) {
                                        playbackManager?.pause()
                                    } else {
                                        playbackManager?.resume()
                                    }
                                } else if (activeSong != null) {
                                    onPlayTrack?.invoke(activeSong)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFB7185)),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Synced lyrics button
                    if (onOpenLyrics != null && activeSong != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                localIsKaraokeActive = true
                                playbackManager?.setKaraokeMode(true, vocalReduction)
                                if (!isPlaying) {
                                    if (track != null) playbackManager?.resume()
                                    else onPlayTrack?.invoke(activeSong)
                                }
                                onClose()
                                onOpenLyrics()
                            },
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFB7185).copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFB7185))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sing with Synced Lyrics",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Vocal Reduction Level Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vocal Attenuation",
                        color = theme.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Suppresses mid-range vocal frequencies (200Hz - 4500Hz)",
                        color = theme.textSecondary,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "${vocalReduction.toInt()}%",
                    color = Color(0xFFFB7185),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = vocalReduction,
                onValueChange = {
                    vocalReduction = it
                },
                valueRange = 40f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFB7185),
                    activeTrackColor = Color(0xFFFB7185),
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                ),
                modifier = Modifier.testTag("slider_vocal_reduction")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Apply & Sing Along Button
            Button(
                onClick = {
                    localIsKaraokeActive = true
                    playbackManager?.setKaraokeMode(true, vocalReduction)
                    // If no song is playing or song is paused, start playback so user immediately hears karaoke
                    if (track != null) {
                        if (!isPlaying) {
                            playbackManager?.resume()
                        }
                    } else if (allTracks.isNotEmpty()) {
                        onPlayTrack?.invoke(allTracks.first())
                    }
                    Toast.makeText(context, "🎤 Karaoke Mode Active: Center vocals suppressed!", Toast.LENGTH_SHORT).show()
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB7185)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_apply_karaoke"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apply & Sing Along",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
