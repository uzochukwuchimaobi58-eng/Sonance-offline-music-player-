package com.sonance.musicplayer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeUp
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
import com.sonance.musicplayer.model.MusicBassSettings
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.player.PlaybackManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicBassDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    track: Track?,
    theme: ThemeConfig,
    playbackManager: PlaybackManager? = null,
    repository: MusicRepository? = null,
    onConversionFinished: () -> Unit = {}
) {
    if (!isOpen) return

    val context = LocalContext.current
    val currentBassSettings by playbackManager?.musicBassSettings?.collectAsState()
        ?: remember { mutableStateOf(MusicBassSettings()) }

    var isBassEnabled by remember(currentBassSettings.enabled) { mutableStateOf(currentBassSettings.enabled) }
    var bassBoost by remember(currentBassSettings.bassBoost) { mutableFloatStateOf(currentBassSettings.bassBoost.toFloat()) }
    var subBassRumble by remember(currentBassSettings.subBassRumble) { mutableFloatStateOf(currentBassSettings.subBassRumble.toFloat()) }
    var punchKick by remember(currentBassSettings.punchKick) { mutableFloatStateOf(currentBassSettings.punchKick.toFloat()) }
    var clarityHighs by remember(currentBassSettings.clarityHighs) { mutableFloatStateOf(currentBassSettings.clarityHighs.toFloat()) }
    var selectedPreset by remember(currentBassSettings.presetName) { mutableStateOf(currentBassSettings.presetName) }

    // Real-time audio preview while user adjusts sliders inside the dialog
    LaunchedEffect(isBassEnabled, bassBoost, subBassRumble, punchKick, clarityHighs, selectedPreset) {
        if (isBassEnabled) {
            playbackManager?.applyMusicBass(
                MusicBassSettings(
                    enabled = true,
                    bassBoost = bassBoost.toInt(),
                    subBassRumble = subBassRumble.toInt(),
                    punchKick = punchKick.toInt(),
                    clarityHighs = clarityHighs.toInt(),
                    presetName = selectedPreset
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = theme.sidebarBg,
        modifier = Modifier.testTag("music_bass_dialog")
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFBBF24).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Music Bass",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Music Bass",
                            color = theme.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Deep Bass & Subwoofer Punch",
                            color = theme.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Toggle switch: Turns bass ON/OFF for all playing music
                Switch(
                    checked = isBassEnabled,
                    onCheckedChange = { checked ->
                        isBassEnabled = checked
                        val newSettings = MusicBassSettings(
                            enabled = checked,
                            bassBoost = bassBoost.toInt(),
                            subBassRumble = subBassRumble.toInt(),
                            punchKick = punchKick.toInt(),
                            clarityHighs = clarityHighs.toInt(),
                            presetName = selectedPreset
                        )
                        playbackManager?.applyMusicBass(newSettings)
                        Toast.makeText(
                            context,
                            if (checked) "Music Bass ON for all music" else "Music Bass turned OFF",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFBBF24),
                        checkedTrackColor = Color(0xFFFBBF24).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("switch_music_bass")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status message
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = if (isBassEnabled) Color(0xFF10B981).copy(alpha = 0.14f) else theme.headerBg.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isBassEnabled) Color(0xFF10B981).copy(alpha = 0.4f) else theme.headerBorder.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isBassEnabled) Icons.Default.Check else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = if (isBassEnabled) Color(0xFF10B981) else theme.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBassEnabled) {
                            "✓ Music Bass Active across all songs until turned off"
                        } else {
                            "Toggle switch ON or tap Apply to activate for all songs"
                        },
                        color = if (isBassEnabled) Color(0xFF10B981) else theme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Presets row
            Text(
                text = "SOUND PROFILES",
                color = theme.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    "Deep 808" to listOf(90f, 95f, 75f, 50f),
                    "Car Subwoofer" to listOf(100f, 100f, 70f, 40f),
                    "Club Punch" to listOf(85f, 70f, 95f, 60f),
                    "Warm Bass" to listOf(70f, 65f, 60f, 55f),
                    "Extreme Bass" to listOf(100f, 100f, 100f, 50f)
                )

                presets.forEach { (name, vals) ->
                    val isSelected = selectedPreset == name
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFFFBBF24).copy(alpha = 0.22f) else theme.headerBg.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFFFBBF24) else theme.headerBorder.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.clickable {
                            selectedPreset = name
                            bassBoost = vals[0]
                            subBassRumble = vals[1]
                            punchKick = vals[2]
                            clarityHighs = vals[3]
                            if (!isBassEnabled) isBassEnabled = true
                        }
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) Color(0xFFFBBF24) else theme.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sliders for how the music sounds
            BassControlSlider(
                title = "Deep Bass Boost",
                subtitle = "Hardware BassBoost Engine Intensity",
                value = bassBoost,
                onValueChange = {
                    bassBoost = it
                    selectedPreset = "Custom"
                },
                accentColor = Color(0xFFFBBF24),
                theme = theme
            )

            Spacer(modifier = Modifier.height(14.dp))

            BassControlSlider(
                title = "Sub-Bass 808 Rumble",
                subtitle = "Low-end frequencies (20Hz - 80Hz)",
                value = subBassRumble,
                onValueChange = {
                    subBassRumble = it
                    selectedPreset = "Custom"
                },
                accentColor = Color(0xFF38BDF8),
                theme = theme
            )

            Spacer(modifier = Modifier.height(14.dp))

            BassControlSlider(
                title = "Kick & Punch Energy",
                subtitle = "Mid-bass punch impact (80Hz - 250Hz)",
                value = punchKick,
                onValueChange = {
                    punchKick = it
                    selectedPreset = "Custom"
                },
                accentColor = Color(0xFFF43F5E),
                theme = theme
            )

            Spacer(modifier = Modifier.height(14.dp))

            BassControlSlider(
                title = "Clarity & Highs Balance",
                subtitle = "Upper air & clean frequency separation",
                value = clarityHighs,
                onValueChange = {
                    clarityHighs = it
                    selectedPreset = "Custom"
                },
                accentColor = Color(0xFF10B981),
                theme = theme
            )

            Spacer(modifier = Modifier.height(24.dp))

            // APPLY BUTTON: Applies bass settings to all music playback permanently until turned off
            Button(
                onClick = {
                    val finalSettings = MusicBassSettings(
                        enabled = true,
                        bassBoost = bassBoost.toInt(),
                        subBassRumble = subBassRumble.toInt(),
                        punchKick = punchKick.toInt(),
                        clarityHighs = clarityHighs.toInt(),
                        presetName = selectedPreset
                    )
                    isBassEnabled = true
                    playbackManager?.applyMusicBass(finalSettings)
                    Toast.makeText(context, "Music Bass applied to all music playback!", Toast.LENGTH_SHORT).show()
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_apply_music_bass"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF1E1A0E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apply Bass to All Music",
                        color = Color(0xFF1E1A0E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Backward compatibility alias so callers can reference BeatInstrumentalDialog
 */
@Composable
fun BeatInstrumentalDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    track: Track?,
    theme: ThemeConfig,
    playbackManager: PlaybackManager? = null,
    repository: MusicRepository? = null,
    onConversionFinished: () -> Unit = {}
) {
    MusicBassDialog(
        isOpen = isOpen,
        onClose = onClose,
        track = track,
        theme = theme,
        playbackManager = playbackManager,
        repository = repository,
        onConversionFinished = onConversionFinished
    )
}

@Composable
private fun BassControlSlider(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    theme: ThemeConfig
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = theme.textSecondary,
                    fontSize = 11.sp
                )
            }
            Text(
                text = "${value.toInt()}%",
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}
