package com.sonance.musicplayer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.data.MusicRepository
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.player.PlaybackManager
import com.sonance.musicplayer.util.AudioExportHelper
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneTrimmerDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    track: Track?,
    theme: ThemeConfig,
    playbackManager: PlaybackManager? = null,
    repository: MusicRepository? = null
) {
    if (!isOpen || track == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var startTimeSec by remember { mutableFloatStateOf(0f) }
    var endTimeSec by remember(track) { mutableFloatStateOf(track.duration.toFloat().coerceAtMost(30f)) }
    var isPreviewing by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = theme.sidebarBg,
        modifier = Modifier.testTag("ringtone_trimmer_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = null,
                    tint = theme.accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Music Trim & Ringtone",
                    color = theme.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = track.title,
                color = theme.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = track.artist,
                color = theme.textSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Start position slider
            Text(
                text = "Start Time: ${String.format("%d:%02d", startTimeSec.toInt() / 60, startTimeSec.toInt() % 60)}",
                color = theme.textPrimary,
                fontSize = 13.sp
            )
            Slider(
                value = startTimeSec,
                onValueChange = {
                    startTimeSec = it.coerceAtMost(endTimeSec - 5f)
                },
                valueRange = 0f..track.duration.toFloat().coerceAtLeast(10f),
                colors = SliderDefaults.colors(
                    thumbColor = theme.accentColor,
                    activeTrackColor = theme.accentColor
                )
            )

            // End position slider
            Text(
                text = "End Time: ${String.format("%d:%02d", endTimeSec.toInt() / 60, endTimeSec.toInt() % 60)} (Duration: ${(endTimeSec - startTimeSec).toInt()}s)",
                color = theme.textPrimary,
                fontSize = 13.sp
            )
            Slider(
                value = endTimeSec,
                onValueChange = {
                    endTimeSec = it.coerceAtLeast(startTimeSec + 5f)
                },
                valueRange = 0f..track.duration.toFloat().coerceAtLeast(10f),
                colors = SliderDefaults.colors(
                    thumbColor = theme.accentColor,
                    activeTrackColor = theme.accentColor
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (isPreviewing) {
                            playbackManager?.pause()
                            isPreviewing = false
                        } else {
                            playbackManager?.playTrack(track, startPositionMs = (startTimeSec * 1000L).toLong())
                            isPreviewing = true
                            Toast.makeText(context, "Playing preview from ${startTimeSec.toInt()}s", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPreviewing) "Stop" else "Preview", color = theme.textPrimary)
                }

                var isSettingRingtone by remember { mutableStateOf(false) }

                Button(
                    onClick = {
                        if (isSettingRingtone) return@Button
                        isSettingRingtone = true
                        coroutineScope.launch {
                            val durationSec = (endTimeSec - startTimeSec).toInt().coerceAtLeast(1)
                            val exported = AudioExportHelper.exportTrimmedAudio(
                                context = context,
                                track = track,
                                startSec = startTimeSec,
                                endSec = endTimeSec,
                                saveAsRingtone = true
                            )
                            isSettingRingtone = false
                            if (exported != null) {
                                val success = com.sonance.musicplayer.util.RingtoneHelper.setFileAsRingtone(
                                    context = context,
                                    audioFile = exported,
                                    title = "${track.title} (Trimmed ${durationSec}s)"
                                )
                                if (success) {
                                    onClose()
                                }
                            } else {
                                val success = com.sonance.musicplayer.util.RingtoneHelper.setAsRingtoneImmediately(context, track)
                                if (success) onClose()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor),
                    modifier = Modifier.weight(1f),
                    enabled = !isSettingRingtone
                ) {
                    Text(
                        if (isSettingRingtone) "Setting..." else "Set Ringtone",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Download Trimmed Audio Button
            Button(
                onClick = {
                    if (isDownloading) return@Button
                    isDownloading = true
                    coroutineScope.launch {
                        val exported = AudioExportHelper.exportTrimmedAudio(
                            context = context,
                            track = track,
                            startSec = startTimeSec,
                            endSec = endTimeSec
                        )
                        isDownloading = false
                        if (exported != null) {
                            val durationSec = (endTimeSec - startTimeSec).toInt().coerceAtLeast(1)
                            val trimmedTrack = track.copy(
                                id = "trim-${UUID.randomUUID()}",
                                title = "${track.title} (Trimmed ${durationSec}s)",
                                duration = durationSec.toLong(),
                                url = android.net.Uri.fromFile(exported).toString(),
                                folder = exported.parent ?: "Download",
                                dateAdded = System.currentTimeMillis()
                            )
                            repository?.addTrack(trimmedTrack)
                            Toast.makeText(
                                context,
                                "✓ Saved to Downloads/${exported.name} & added to Music!",
                                Toast.LENGTH_LONG
                            ).show()
                            onClose()
                        } else {
                            Toast.makeText(context, "Could not export trimmed audio", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_download_trimmed_audio")
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDownloading) "Downloading..." else "Download Trimmed Audio File",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
