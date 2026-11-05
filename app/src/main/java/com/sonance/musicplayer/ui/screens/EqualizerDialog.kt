package com.sonance.musicplayer.ui.screens

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sonance.musicplayer.model.EqPreset
import com.sonance.musicplayer.model.EqualizerSettings
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    settings: EqualizerSettings,
    onApplySettings: (EqualizerSettings) -> Unit,
    theme: ThemeConfig,
    track: Track? = null
) {
    if (!isOpen) return

    val freqBands = remember {
        listOf(
            Triple(60, "32", false),      // Cyan
            Triple(170, "64", false),     // Cyan
            Triple(310, "125", false),    // Cyan
            Triple(600, "250", true),     // Warm Amber/Orange
            Triple(1000, "500", true),    // Warm Amber/Orange
            Triple(3000, "1k", true),     // Warm Amber/Orange
            Triple(6000, "2k", true),     // Warm Amber/Orange
            Triple(12000, "4k", false),   // Cyan
            Triple(14000, "8k", false),   // Cyan
            Triple(16000, "16k", false)   // Cyan
        )
    }

    var currentSettings by remember(settings) { mutableStateOf(settings) }
    val bandGains = remember {
        mutableStateMapOf<Int, Int>().apply {
            freqBands.forEach { (freq, _, _) ->
                put(freq, settings.bands[freq] ?: 0)
            }
        }
    }

    val presetGains = remember {
        mapOf(
            EqPreset.FLAT to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            EqPreset.BASS_BOOST to listOf(8, 7, 5, 3, 1, 0, 0, 0, 0, 0),
            EqPreset.VOCAL_BOOSTER to listOf(-2, -1, 1, 4, 6, 6, 4, 2, 0, -1),
            EqPreset.ROCK to listOf(6, 4, 2, 0, -1, 1, 3, 5, 6, 7),
            EqPreset.ELECTRONIC to listOf(7, 6, 3, 0, -2, 2, 4, 5, 7, 8),
            EqPreset.JAZZ to listOf(3, 2, 1, 2, -1, -1, 0, 2, 4, 5),
            EqPreset.ACOUSTIC to listOf(4, 3, 2, 1, 2, 3, 4, 4, 3, 2),
            EqPreset.HIFI_MASTER to listOf(5, 4, 2, 1, 1, 2, 3, 5, 6, 7)
        )
    }

    // Only update bandGains when preset explicitly changes, never clear all bands while dragging
    LaunchedEffect(settings.preset) {
        val gains = presetGains[settings.preset]
        if (gains != null) {
            freqBands.forEachIndexed { idx, (freq, _, _) ->
                bandGains[freq] = gains.getOrElse(idx) { 0 }
            }
        } else {
            settings.bands.forEach { (f, g) -> bandGains[f] = g }
        }
    }

    val neonCyan = Color(0xFF00E5FF)
    val neonOrange = Color(0xFFFF9100)
    val surfaceDark = Color(0xFF0B0F17)
    val cardDark = Color(0xFF111827)

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = surfaceDark,
        dragHandle = null,
        modifier = Modifier.testTag("equalizer_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Drag handle pill
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // TOP LOSSLESS HEADER (Exact Image 1 Design)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF131D2D).copy(alpha = 0.85f),
                                Color(0xFF0A0F19).copy(alpha = 0.95f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art Card
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!track?.coverArt.isNullOrBlank()) {
                        AsyncImage(
                            model = Uri.parse(track?.coverArt),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = neonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    // Glass reflection overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Artist, Track and LOSSLESS Badge
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ARTIST: ${(track?.artist ?: "ARCTIC VOIDS").uppercase()} | TRACK: ${(track?.title ?: "ECLIPSE (FLAC)").uppercase()}",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "LOSSLESS",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Green Pill Badge: FLAC | 24-bit/96kHz
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = "FLAC | 24-bit/96kHz",
                            color = Color(0xFF34D399),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                        )
                    }
                }

                // Power Toggle Button
                IconButton(
                    onClick = {
                        val updated = currentSettings.copy(enabled = !currentSettings.enabled)
                        currentSettings = updated
                        onApplySettings(updated)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (currentSettings.enabled) neonCyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            if (currentSettings.enabled) neonCyan else Color.White.copy(alpha = 0.15f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Toggle Equalizer",
                        tint = if (currentSettings.enabled) neonCyan else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // PRESETS ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EqPreset.entries.forEach { preset ->
                    val isSelected = currentSettings.preset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) neonCyan.copy(alpha = 0.20f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) neonCyan else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                val gains = presetGains[preset]
                                val newBands = if (gains != null) {
                                    freqBands.mapIndexed { idx, (freq, _, _) -> freq to gains[idx] }.toMap()
                                } else {
                                    currentSettings.bands
                                }
                                bandGains.clear()
                                bandGains.putAll(newBands)
                                val updated = currentSettings.copy(enabled = true, preset = preset, bands = newBands)
                                currentSettings = updated
                                onApplySettings(updated)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = preset.displayName,
                            color = if (isSelected) neonCyan else Color(0xFFCBD5E1),
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 10-BAND GRAPHIC EQUALIZER FADERS SECTION (Image 1 Style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FREQUENCY RESPONSE (-12dB to +12dB)",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "RESET FLAT",
                    color = neonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            val flatBands = freqBands.associate { it.first to 0 }
                            bandGains.clear()
                            bandGains.putAll(flatBands)
                            val updated = currentSettings.copy(
                                enabled = true,
                                preset = EqPreset.FLAT,
                                bands = flatBands
                            )
                            currentSettings = updated
                            onApplySettings(updated)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Faders Card Container with Glowing Rails
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = cardDark.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    freqBands.forEachIndexed { idx, (freq, label, isAmber) ->
                        key(freq) {
                            val gain = bandGains[freq] ?: 0
                            val bandColor = if (isAmber) neonOrange else neonCyan

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 1.5.dp)
                            ) {
                                // Top dB text (Click toggles only this single band)
                                Text(
                                    text = if (gain > 0) "+$gain" else "$gain",
                                    color = if (gain != 0 && currentSettings.enabled) bandColor else Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    modifier = Modifier.clickable {
                                        val targetGain = if (gain != 0) 0 else 12
                                        bandGains[freq] = targetGain

                                        val updated = currentSettings.copy(
                                            enabled = true,
                                            preset = EqPreset.CUSTOM,
                                            bands = bandGains.toMap()
                                        )
                                        currentSettings = updated
                                        onApplySettings(updated)
                                    }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Neon Fader - Strictly moves only this individual line/band
                                NeonGlowingEqFader(
                                    freq = freq,
                                    gain = gain,
                                    onGainChange = { newVal ->
                                        bandGains[freq] = newVal

                                        val updated = currentSettings.copy(
                                            enabled = true,
                                            preset = EqPreset.CUSTOM,
                                            bands = bandGains.toMap()
                                        )
                                        currentSettings = updated
                                        onApplySettings(updated)
                                    },
                                    onTurnOn = {
                                        if (!currentSettings.enabled) {
                                            val updated = currentSettings.copy(enabled = true)
                                            currentSettings = updated
                                            onApplySettings(updated)
                                        }
                                    },
                                    enabled = currentSettings.enabled,
                                    neonColor = bandColor,
                                    modifier = Modifier.testTag("eq_fader_$freq")
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Bottom Frequency Label (32, 64, 125, etc.)
                                Text(
                                    text = label,
                                    color = if (gain != 0 && currentSettings.enabled) Color.White else Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 2 BIG ROUND WORKING ROTARY KNOBS (Image 1 Style)
            // "make it to display 2 big button and should working"
            Text(
                text = "STUDIO ROTARY ENHANCERS",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Knob 1: BASS BOOST
                MetallicStudioKnob(
                    title = "BASS BOOST",
                    value = currentSettings.bassBoost,
                    onValueChange = { newVal ->
                        val updated = currentSettings.copy(enabled = true, bassBoost = newVal)
                        currentSettings = updated
                        onApplySettings(updated)
                    },
                    onTurnOn = {
                        if (!currentSettings.enabled) {
                            val updated = currentSettings.copy(enabled = true)
                            currentSettings = updated
                            onApplySettings(updated)
                        }
                    },
                    accentColor = neonCyan,
                    enabled = currentSettings.enabled,
                    modifier = Modifier.testTag("knob_bass_boost")
                )

                // Knob 2: 3D SURROUND / VIRTUALIZER
                MetallicStudioKnob(
                    title = "3D SURROUND",
                    value = currentSettings.spatialReverb,
                    onValueChange = { newVal ->
                        val updated = currentSettings.copy(enabled = true, spatialReverb = newVal)
                        currentSettings = updated
                        onApplySettings(updated)
                    },
                    onTurnOn = {
                        if (!currentSettings.enabled) {
                            val updated = currentSettings.copy(enabled = true)
                            currentSettings = updated
                            onApplySettings(updated)
                        }
                    },
                    accentColor = neonCyan,
                    enabled = currentSettings.enabled,
                    modifier = Modifier.testTag("knob_3d_surround")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Done Button
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = neonCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Done",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/**
 * High-fidelity Neon Glowing Graphic Equalizer Fader matching Image 1:
 * - Glowing Ice Cyan or Warm Sunset Amber neon track
 * - Metallic capsule fader thumb with glowing center slit
 * - Fully unconstrained dragging from +12dB to -12dB
 */
@Composable
private fun NeonGlowingEqFader(
    freq: Int,
    gain: Int, // between -12 and +12
    onGainChange: (Int) -> Unit,
    onTurnOn: (() -> Unit)? = null,
    enabled: Boolean,
    neonColor: Color,
    modifier: Modifier = Modifier
) {
    val faderHeight = 155.dp
    val thumbWidth = 24.dp
    val thumbHeight = 14.dp

    var isDraggingThis by remember { mutableStateOf(false) }
    val currentOnGainChange by rememberUpdatedState(onGainChange)
    val currentOnTurnOn by rememberUpdatedState(onTurnOn)

    val animatedGain by animateFloatAsState(
        targetValue = gain.toFloat(),
        animationSpec = if (isDraggingThis) snap() else tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "neon_fader_anim"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(faderHeight)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Main, requireUnconsumed = true)
                    val activePointerId = down.id
                    isDraggingThis = true
                    down.consume()
                    currentOnTurnOn?.invoke()

                    val hPx = size.height.toFloat()
                    val thumbHPx = thumbHeight.toPx()
                    val travel = (hPx - thumbHPx).coerceAtLeast(1f)

                    val update = { yPos: Float ->
                        val clampedY = (yPos - thumbHPx / 2f).coerceIn(0f, travel)
                        val fraction = 1f - (clampedY / travel) // 1.0f at top (+12), 0.0f at bottom (-12)
                        val newGain = ((fraction * 24f) - 12f).roundToInt().coerceIn(-12, 12)
                        currentOnGainChange(newGain)
                    }

                    update(down.position.y)

                    try {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == activePointerId } ?: break
                            if (!change.pressed) break
                            change.consume()
                            update(change.position.y)
                        }
                    } finally {
                        isDraggingThis = false
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

            // Current thumb position
            val fraction = ((animatedGain + 12f) / 24f).coerceIn(0f, 1f)
            val thumbCenterY = thumbHPx / 2f + (1f - fraction) * travel
            val centerY = h / 2f

            // 1. Dark Recessed Slot Channel Track
            val trackWidth = 5.dp.toPx()
            val trackCorner = 2.5.dp.toPx()
            drawRoundRect(
                color = Color(0xFF030712),
                topLeft = Offset(centerX - trackWidth / 2f, thumbHPx / 2f),
                size = Size(trackWidth, travel),
                cornerRadius = CornerRadius(trackCorner, trackCorner)
            )

            // 2. Glowing Neon Rail (from thumb down to bottom / 0dB)
            if (enabled) {
                // Wide ambient neon blur aura
                val glowTop = thumbCenterY
                val glowBottom = h - thumbHPx / 2f
                if (glowBottom > glowTop) {
                    drawRoundRect(
                        color = neonColor.copy(alpha = 0.28f),
                        topLeft = Offset(centerX - 5.dp.toPx(), glowTop),
                        size = Size(10.dp.toPx(), (glowBottom - glowTop).coerceAtLeast(1f)),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                    // Sharp bright neon core line
                    drawRoundRect(
                        color = neonColor,
                        topLeft = Offset(centerX - 1.5.dp.toPx(), glowTop),
                        size = Size(3.dp.toPx(), (glowBottom - glowTop).coerceAtLeast(1f)),
                        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                    )
                }
            }

            // 3. 0dB Center Reference Line
            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = Offset(centerX - 12.dp.toPx(), centerY),
                end = Offset(centerX + 12.dp.toPx(), centerY),
                strokeWidth = 1.dp.toPx()
            )

            // 4. Metallic Pill Fader Cap
            val thumbTop = thumbCenterY - thumbHPx / 2f
            val thumbLeft = centerX - thumbWPx / 2f
            val thumbRadius = 6.dp.toPx()

            // Outer capsule glow when enabled
            if (enabled) {
                drawRoundRect(
                    color = neonColor.copy(alpha = 0.35f),
                    topLeft = Offset(thumbLeft - 2.dp.toPx(), thumbTop - 2.dp.toPx()),
                    size = Size(thumbWPx + 4.dp.toPx(), thumbHPx + 4.dp.toPx()),
                    cornerRadius = CornerRadius(thumbRadius + 2.dp.toPx(), thumbRadius + 2.dp.toPx())
                )
            }

            // Metallic body with gradient (brushed chrome / dark titanium)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF64748B),
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    ),
                    startY = thumbTop,
                    endY = thumbTop + thumbHPx
                ),
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbWPx, thumbHPx),
                cornerRadius = CornerRadius(thumbRadius, thumbRadius)
            )

            // Subtle metallic chamfer border
            drawRoundRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbWPx, thumbHPx),
                cornerRadius = CornerRadius(thumbRadius, thumbRadius),
                style = Stroke(width = 1.dp.toPx())
            )

            // Center Glowing Neon Indicator Slit
            val slitWidth = 16.dp.toPx()
            val slitHeight = 3.dp.toPx()
            drawRoundRect(
                color = if (enabled) neonColor else Color(0xFF94A3B8),
                topLeft = Offset(centerX - slitWidth / 2f, thumbCenterY - slitHeight / 2f),
                size = Size(slitWidth, slitHeight),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
    }
}

/**
 * Large Brushed Aluminum Metallic Rotary Knob matching Image 1:
 * - Concentric brushed metal radial gradient dial
 * - Surrounding illuminated neon arc gauge
 * - Smooth vertical dragging and 360-degree circular turning from 0% to 100%
 */
@Composable
private fun MetallicStudioKnob(
    title: String,
    value: Int, // 0..100
    onValueChange: (Int) -> Unit,
    accentColor: Color,
    enabled: Boolean,
    onTurnOn: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val knobSize = 118.dp
    var isDragging by remember { mutableStateOf(false) }

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnTurnOn by rememberUpdatedState(onTurnOn)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(knobSize)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Main, requireUnconsumed = true)
                        val activePointerId = down.id
                        isDragging = true
                        down.consume()
                        currentOnTurnOn?.invoke()

                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val knobRadiusPx = (size.width / 2f - 4.dp.toPx()) * 0.72f
                        var lastPos = down.position
                        var accum = currentValue.toFloat()

                        try {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == activePointerId } ?: break
                                if (!change.pressed) break
                                change.consume()

                                val currPos = change.position
                                val dy = currPos.y - lastPos.y
                                val dx = currPos.x - lastPos.x

                                val rx = currPos.x - centerX
                                val ry = currPos.y - centerY
                                val dist = kotlin.math.hypot(rx, ry)

                                // 1. Clockwise tangential circular component
                                val tangentialDelta = if (dist > 6f) {
                                    (dx * (-ry / dist) + dy * (rx / dist))
                                } else 0f
                                val circularDelta = (tangentialDelta / (2 * Math.PI.toFloat() * dist.coerceAtLeast(knobRadiusPx * 0.5f))) * (360f / 270f) * 100f * 1.5f

                                // 2. Vertical scroll component (up = increase, down = decrease)
                                val verticalScrollDelta = (-dy / (size.height * 1.4f)) * 100f

                                val delta = if (kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.25f && kotlin.math.abs(dy) >= 1.5f) {
                                    verticalScrollDelta
                                } else if (kotlin.math.abs(circularDelta) >= 0.15f) {
                                    circularDelta
                                } else {
                                    verticalScrollDelta
                                }

                                accum = (accum + delta).coerceIn(0f, 100f)
                                currentOnValueChange(accum.roundToInt())

                                lastPos = currPos
                            }
                        } finally {
                            isDragging = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.width / 2f - 4.dp.toPx()
                val knobRadius = outerRadius * 0.72f

                // Outer Scale Arc Range: 135 deg to 405 deg (270 deg sweep)
                val startAngle = 135f
                val sweepAngle = 270f
                val currentFraction = (currentValue / 100f).coerceIn(0f, 1f)

                // 1. Background Inactive Arc Track
                drawArc(
                    color = Color.White.copy(alpha = 0.10f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // 2. Active Glowing Neon Arc
                if (currentFraction > 0.005f && enabled) {
                    // Outer soft glow
                    drawArc(
                        color = accentColor.copy(alpha = 0.35f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * currentFraction,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = Size(outerRadius * 2, outerRadius * 2),
                        style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Sharp neon line
                    drawArc(
                        color = accentColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * currentFraction,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = Size(outerRadius * 2, outerRadius * 2),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 3. Tick Marks Around Perimeter
                val tickCount = 18
                for (i in 0..tickCount) {
                    val tAngle = startAngle + (i.toFloat() / tickCount) * sweepAngle
                    val tRad = Math.toRadians(tAngle.toDouble())
                    val r1 = outerRadius + 1.dp.toPx()
                    val r2 = outerRadius + 4.dp.toPx()
                    val p1 = Offset((center.x + r1 * cos(tRad)).toFloat(), (center.y + r1 * sin(tRad)).toFloat())
                    val p2 = Offset((center.x + r2 * cos(tRad)).toFloat(), (center.y + r2 * sin(tRad)).toFloat())
                    val isPastActive = (i.toFloat() / tickCount) <= currentFraction && enabled
                    drawLine(
                        color = if (isPastActive) accentColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
                        start = p1,
                        end = p2,
                        strokeWidth = 1.2.dp.toPx()
                    )
                }

                // 4. Large Brushed Concentric Metallic Dial Body
                // Deep drop shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.65f),
                    radius = knobRadius + 3.dp.toPx(),
                    center = center
                )

                // Concentric metallic gradients
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF94A3B8),
                            Color(0xFF334155),
                            Color(0xFF0F172A)
                        ),
                        center = center,
                        radius = knobRadius
                    ),
                    radius = knobRadius,
                    center = center
                )

                // Metallic Beveled Rim
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFCBD5E1),
                            Color(0xFF475569),
                            Color(0xFFE2E8F0),
                            Color(0xFF334155),
                            Color(0xFFCBD5E1)
                        ),
                        center = center
                    ),
                    radius = knobRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // 5. Radial Pointer Notch Needle on Dial Face (Direct trigonometric calculation)
                val pointerAngleDeg = startAngle + sweepAngle * currentFraction
                val pointerRad = Math.toRadians(pointerAngleDeg.toDouble())
                val n1 = knobRadius * 0.32f
                val n2 = knobRadius * 0.88f
                val notchStart = Offset((center.x + n1 * cos(pointerRad)).toFloat(), (center.y + n1 * sin(pointerRad)).toFloat())
                val notchEnd = Offset((center.x + n2 * cos(pointerRad)).toFloat(), (center.y + n2 * sin(pointerRad)).toFloat())

                // Glow behind notch
                if (enabled) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.45f),
                        start = notchStart,
                        end = notchEnd,
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    color = if (enabled) accentColor else Color.White,
                    start = notchStart,
                    end = notchEnd,
                    strokeWidth = 2.8.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center Cap
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = knobRadius * 0.22f,
                    center = center
                )
                drawCircle(
                    color = if (enabled) accentColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f),
                    radius = knobRadius * 0.22f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title and Value Label
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        Text(
            text = if (value == 0) "OFF" else if (value >= 100) "MAX" else "$value%",
            color = if (value > 0 && enabled) accentColor else Color(0xFF94A3B8),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
