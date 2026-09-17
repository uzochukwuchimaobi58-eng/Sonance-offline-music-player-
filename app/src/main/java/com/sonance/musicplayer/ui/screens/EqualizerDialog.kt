package com.sonance.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.EqPreset
import com.sonance.musicplayer.model.EqualizerSettings
import com.sonance.musicplayer.model.ThemeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    settings: EqualizerSettings,
    onApplySettings: (EqualizerSettings) -> Unit,
    theme: ThemeConfig
) {
    if (!isOpen) return

    var currentSettings by remember(settings) { mutableStateOf(settings) }

    val presetGains = mapOf(
        EqPreset.FLAT to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        EqPreset.BASS_BOOST to listOf(8, 7, 5, 3, 1, 0, 0, 0, 0, 0),
        EqPreset.VOCAL_BOOSTER to listOf(-2, -1, 1, 4, 6, 6, 4, 2, 0, -1),
        EqPreset.ROCK to listOf(6, 4, 2, 0, -1, 1, 3, 5, 6, 7),
        EqPreset.ELECTRONIC to listOf(7, 6, 3, 0, -2, 2, 4, 5, 7, 8),
        EqPreset.JAZZ to listOf(3, 2, 1, 2, -1, -1, 0, 2, 4, 5),
        EqPreset.ACOUSTIC to listOf(4, 3, 2, 1, 2, 3, 4, 4, 3, 2),
        EqPreset.HIFI_MASTER to listOf(5, 4, 2, 1, 1, 2, 3, 5, 6, 7)
    )

    val freqLabels = listOf(
        60 to "60Hz",
        170 to "170Hz",
        310 to "310Hz",
        600 to "600Hz",
        1000 to "1kHz",
        3000 to "3kHz",
        6000 to "6kHz",
        12000 to "12kHz",
        14000 to "14kHz",
        16000 to "16kHz"
    )

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = theme.bgCanvas,
        modifier = Modifier.testTag("equalizer_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = theme.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "10-Band Equalizer & FX",
                        color = theme.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = currentSettings.enabled,
                    onCheckedChange = {
                        val updated = currentSettings.copy(enabled = it)
                        currentSettings = updated
                        onApplySettings(updated)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.accentColor,
                        checkedTrackColor = theme.accentColor.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets Horizontal Scroller
            Text(
                text = "PRESETS",
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
                EqPreset.entries.forEach { preset ->
                    val isSelected = currentSettings.preset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) theme.accentColor else theme.headerBg)
                            .border(
                                1.dp,
                                if (isSelected) theme.accentColor else theme.headerBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                val gains = presetGains[preset]
                                val newBands = if (gains != null) {
                                    freqLabels.mapIndexed { idx, (freq, _) -> freq to gains[idx] }.toMap()
                                } else {
                                    currentSettings.bands
                                }
                                val updated = currentSettings.copy(preset = preset, bands = newBands)
                                currentSettings = updated
                                onApplySettings(updated)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = preset.displayName,
                            color = if (isSelected) Color.Black else theme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 10 EQ Frequency Bands Sliders
            Text(
                text = "FREQUENCY RESPONSE (-12dB to +12dB)",
                color = theme.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                freqLabels.forEach { (freq, label) ->
                    val gain = currentSettings.bands[freq] ?: 0

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp)
                    ) {
                        Text(
                            text = if (gain > 0) "+$gain" else "$gain",
                            color = if (gain != 0) theme.accentColor else theme.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Custom vertical slider represented via custom bar or Slider
                        Slider(
                            value = gain.toFloat(),
                            onValueChange = { newVal ->
                                val intVal = newVal.toInt()
                                val updatedBands = currentSettings.bands.toMutableMap().apply {
                                    put(freq, intVal)
                                }
                                val updated = currentSettings.copy(
                                    preset = EqPreset.CUSTOM,
                                    bands = updatedBands
                                )
                                currentSettings = updated
                                onApplySettings(updated)
                            },
                            valueRange = -12f..12f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = theme.accentColor,
                                activeTrackColor = theme.accentColor,
                                inactiveTrackColor = theme.headerBorder
                            ),
                            modifier = Modifier
                                .height(130.dp)
                                .width(130.dp)
                                .offset(y = 48.dp)
                                .rotate(-90f)
                        )

                        Spacer(modifier = Modifier.height(68.dp))

                        Text(
                            text = label,
                            color = theme.textSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sound FX Knobs / Sliders: Bass Boost, 3D Reverb, Treble
            Text(
                text = "AUDIO ENHANCEMENTS",
                color = theme.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bass Boost Slider
            AudioFxSlider(
                title = "Bass Boost",
                value = currentSettings.bassBoost,
                onValueChange = {
                    val updated = currentSettings.copy(bassBoost = it)
                    currentSettings = updated
                    onApplySettings(updated)
                },
                theme = theme
            )

            // 3D Spatial Reverb Slider
            AudioFxSlider(
                title = "3D Spatial Reverb",
                value = currentSettings.spatialReverb,
                onValueChange = {
                    val updated = currentSettings.copy(spatialReverb = it)
                    currentSettings = updated
                    onApplySettings(updated)
                },
                theme = theme
            )

            // Treble Boost Slider
            AudioFxSlider(
                title = "Treble Clarity",
                value = currentSettings.trebleBoost,
                onValueChange = {
                    val updated = currentSettings.copy(trebleBoost = it)
                    currentSettings = updated
                    onApplySettings(updated)
                },
                theme = theme
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun AudioFxSlider(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    theme: ThemeConfig
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = theme.textPrimary, fontSize = 13.sp)
            Text(text = "$value%", color = theme.accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = theme.accentColor,
                activeTrackColor = theme.accentColor,
                inactiveTrackColor = theme.headerBorder
            )
        )
    }
}

