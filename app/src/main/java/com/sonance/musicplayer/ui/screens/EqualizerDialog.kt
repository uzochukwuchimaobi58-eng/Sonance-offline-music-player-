package com.sonance.musicplayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (currentSettings.enabled) "ON" else "OFF",
                        color = if (currentSettings.enabled) theme.accentColor else theme.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = currentSettings.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = currentSettings.copy(enabled = isChecked)
                            currentSettings = updated
                            onApplySettings(updated)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.accentColor,
                            checkedTrackColor = theme.accentColor.copy(alpha = 0.5f),
                            uncheckedThumbColor = Color(0xFF71717A),
                            uncheckedTrackColor = Color(0xFF27272A)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (currentSettings.enabled) 1f else 0.45f)
            ) {
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
                                    val updated = currentSettings.copy(enabled = true, preset = preset, bands = newBands)
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    freqLabels.forEach { (freq, label) ->
                        val gain = currentSettings.bands[freq] ?: 0

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(44.dp)
                        ) {
                            Text(
                                text = if (gain > 0) "+$gain" else "$gain",
                                color = if (gain != 0 && currentSettings.enabled) theme.accentColor else theme.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            VerticalEqFader(
                                gain = gain,
                                onGainChange = { newVal ->
                                    val updatedBands = currentSettings.bands.toMutableMap().apply {
                                        put(freq, newVal)
                                    }
                                    val updated = currentSettings.copy(
                                        enabled = true,
                                        preset = EqPreset.CUSTOM,
                                        bands = updatedBands
                                    )
                                    currentSettings = updated
                                    onApplySettings(updated)
                                },
                                enabled = currentSettings.enabled,
                                accentColor = theme.accentColor,
                                trackColor = theme.headerBorder,
                                modifier = Modifier.testTag("eq_fader_$freq")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

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
                        val updated = currentSettings.copy(enabled = true, bassBoost = it)
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
                        val updated = currentSettings.copy(enabled = true, spatialReverb = it)
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
                        val updated = currentSettings.copy(enabled = true, trebleBoost = it)
                        currentSettings = updated
                        onApplySettings(updated)
                    },
                    theme = theme
                )
            }

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

@Composable
private fun VerticalEqFader(
    gain: Int, // between -12 and +12
    onGainChange: (Int) -> Unit,
    enabled: Boolean,
    accentColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val faderHeight = 150.dp
    val faderWidth = 44.dp
    val thumbWidth = 32.dp
    val thumbHeight = 10.dp

    Box(
        modifier = modifier
            .width(faderWidth)
            .height(faderHeight)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hPx = size.height.toFloat()
                    val thumbHPx = thumbHeight.toPx()
                    val travel = (hPx - thumbHPx).coerceAtLeast(1f)

                    val update = { yPos: Float ->
                        val clampedY = (yPos - thumbHPx / 2f).coerceIn(0f, travel)
                        val fraction = 1f - (clampedY / travel) // 1.0f at top (+12), 0.0f at bottom (-12)
                        val newGain = ((fraction * 24f) - 12f).roundToInt().coerceIn(-12, 12)
                        onGainChange(newGain)
                    }

                    update(down.position.y)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        update(change.position.y)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerX = w / 2f
            val thumbHPx = thumbHeight.toPx()
            val thumbWPx = thumbWidth.toPx()
            val travel = (h - thumbHPx).coerceAtLeast(1f)

            // Calculate thumb center Y from gain
            // gain = +12 => fraction = 1f => thumbCenterY = thumbHPx / 2 (very top / upper)
            // gain = 0   => fraction = 0.5f => thumbCenterY = h / 2 (center)
            // gain = -12 => fraction = 0f => thumbCenterY = h - thumbHPx / 2 (very bottom / footer)
            val fraction = (gain + 12f) / 24f
            val thumbCenterY = thumbHPx / 2f + (1f - fraction) * travel
            val centerY = h / 2f

            // 1. Background vertical groove track
            val trackWidth = 4.dp.toPx()
            val trackCorner = 2.dp.toPx()
            drawRoundRect(
                color = trackColor.copy(alpha = 0.5f),
                topLeft = Offset(centerX - trackWidth / 2f, thumbHPx / 2f),
                size = Size(trackWidth, travel),
                cornerRadius = CornerRadius(trackCorner, trackCorner)
            )

            // 2. Active colored fill track from 0dB center to thumb
            if (gain != 0 && enabled) {
                val fillTop = minOf(centerY, thumbCenterY)
                val fillBottom = maxOf(centerY, thumbCenterY)
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(centerX - trackWidth / 2f, fillTop),
                    size = Size(trackWidth, (fillBottom - fillTop).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(trackCorner, trackCorner)
                )
            }

            // 3. Center 0dB horizontal tick mark
            val tickWidth = 16.dp.toPx()
            val tickHeight = 1.5.dp.toPx()
            drawLine(
                color = if (gain == 0 && enabled) accentColor else Color.White.copy(alpha = 0.25f),
                start = Offset(centerX - tickWidth / 2f, centerY),
                end = Offset(centerX + tickWidth / 2f, centerY),
                strokeWidth = tickHeight
            )

            // 4. Studio Fader Thumb Handle (the horizontal line)
            val thumbTop = thumbCenterY - thumbHPx / 2f
            val thumbLeft = centerX - thumbWPx / 2f
            val thumbRadius = 3.dp.toPx()

            // Subtle outer glow/halo when enabled
            if (enabled) {
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.35f),
                    topLeft = Offset(thumbLeft - 2.dp.toPx(), thumbTop - 1.dp.toPx()),
                    size = Size(thumbWPx + 4.dp.toPx(), thumbHPx + 2.dp.toPx()),
                    cornerRadius = CornerRadius(thumbRadius + 1.dp.toPx(), thumbRadius + 1.dp.toPx())
                )
            }

            // Fader Handle Body (horizontal bar)
            drawRoundRect(
                color = if (enabled) accentColor else Color(0xFF52525B),
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbWPx, thumbHPx),
                cornerRadius = CornerRadius(thumbRadius, thumbRadius)
            )

            // Inner horizontal groove line for studio hardware look
            val innerLineWidth = 18.dp.toPx()
            val innerLineHeight = 2.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.60f),
                topLeft = Offset(centerX - innerLineWidth / 2f, thumbCenterY - innerLineHeight / 2f),
                size = Size(innerLineWidth, innerLineHeight),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
    }
}

