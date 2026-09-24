package com.sonance.musicplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class VisualizerMode(val label: String) {
    SPECTRUM_BARS("D3 Spectrum Bars"),
    SPECTRAL_AREA("Recharts Area Curve"),
    NEON_PULSE("Neon Acoustic Pulse")
}

/**
 * High-performance, studio-grade visual equalizer component representing the audio frequency
 * spectrum (Sub-Bass to Brilliance) modeled after D3.js and Recharts frequency spectrum analyzers.
 */
@Composable
fun FrequencySpectrumVisualizer(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    initialMode: VisualizerMode = VisualizerMode.SPECTRUM_BARS
) {
    var mode by remember { mutableStateOf(initialMode) }

    val infiniteTransition = rememberInfiniteTransition(label = "spectrum_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val beatPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beat"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B0D13).copy(alpha = 0.85f))
            .clickable {
                mode = when (mode) {
                    VisualizerMode.SPECTRUM_BARS -> VisualizerMode.SPECTRAL_AREA
                    VisualizerMode.SPECTRAL_AREA -> VisualizerMode.NEON_PULSE
                    VisualizerMode.NEON_PULSE -> VisualizerMode.SPECTRUM_BARS
                }
            }
            .padding(10.dp)
            .testTag("frequency_spectrum_visualizer")
    ) {
        // Header: Frequency Spectrum Label & Mode Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isPlaying) Color(0xFF10B981) else Color(0xFF6B7280))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "EQUALIZER SPECTRUM",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "${mode.label} (Tap to switch)",
                color = accentColor.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Background Decibel Grid lines (-24dB, -12dB, -6dB, 0dB)
                val gridAlpha = 0.12f
                val gridColor = Color.White.copy(alpha = gridAlpha)
                drawLine(gridColor, Offset(0f, h * 0.2f), Offset(w, h * 0.2f), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, h * 0.8f), Offset(w, h * 0.8f), strokeWidth = 1f)

                when (mode) {
                    VisualizerMode.SPECTRUM_BARS -> {
                        // D3.js Multi-Bar Spectrum with Floating Peak Indicators
                        val barCount = 36
                        val totalGap = w * 0.25f
                        val barWidth = (w - totalGap) / barCount
                        val spacing = totalGap / (barCount - 1).coerceAtLeast(1)

                        for (i in 0 until barCount) {
                            val fraction = i.toFloat() / (barCount - 1).coerceAtLeast(1)

                            // Frequency curve distribution: Bass hits heavy on left, mids in center, brilliance on right
                            val bassWeight = (1.0f - fraction * 0.6f)
                            val wave1 = sin(fraction * 6.0 + phase)
                            val wave2 = cos(fraction * 11.0 - phase * 1.5)
                            val wave3 = sin(fraction * 18.0 + phase * 0.8)

                            val energy = if (isPlaying) {
                                (abs(wave1 * 0.5 + wave2 * 0.35 + wave3 * 0.15) * bassWeight * beatPulse).toFloat()
                                    .coerceIn(0.08f, 0.98f)
                            } else {
                                0.08f
                            }

                            val barHeight = h * energy
                            val x = i * (barWidth + spacing)
                            val y = h - barHeight

                            // Spectral Gradient Color (Violet -> Cyan -> Emerald -> Gold -> Rose)
                            val barBrush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFF43F5E), // Rose peak
                                    Color(0xFFFBBF24), // Gold upper
                                    accentColor,       // Theme accent
                                    Color(0xFF6366F1)  // Indigo base
                                ),
                                startY = y,
                                endY = h
                            )

                            // Draw rounded frequency bar
                            drawRoundRect(
                                brush = barBrush,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                            )

                            // Floating peak indicator dot (D3 style peak drop)
                            if (isPlaying && energy > 0.3f) {
                                val peakY = (y - 5f).coerceAtLeast(2f)
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.9f),
                                    radius = barWidth * 0.45f,
                                    center = Offset(x + barWidth / 2f, peakY)
                                )
                            }
                        }
                    }

                    VisualizerMode.SPECTRAL_AREA -> {
                        // Recharts Smooth Frequency Area Chart
                        val points = 32
                        val path = Path()
                        val fillPath = Path()
                        val stepX = w / (points - 1)

                        var startY = h * 0.85f
                        path.moveTo(0f, startY)
                        fillPath.moveTo(0f, h)
                        fillPath.lineTo(0f, startY)

                        for (i in 1 until points) {
                            val fraction = i.toFloat() / (points - 1)
                            val bassBoost = if (fraction < 0.3f) 1.25f else 0.85f
                            val wave = if (isPlaying) {
                                (sin(fraction * 8.0 + phase) * 0.45 + cos(fraction * 14.0 - phase) * 0.35 + 0.5) * bassBoost * beatPulse
                            } else {
                                0.15
                            }
                            val targetY = (h - (h * wave.toFloat() * 0.8f)).coerceIn(8f, h - 4f)
                            val currX = i * stepX

                            val prevX = (i - 1) * stepX
                            val midX = (prevX + currX) / 2f
                            path.quadraticTo(prevX, startY, midX, (startY + targetY) / 2f)
                            fillPath.quadraticTo(prevX, startY, midX, (startY + targetY) / 2f)
                            startY = targetY
                        }

                        path.lineTo(w, startY)
                        fillPath.lineTo(w, startY)
                        fillPath.lineTo(w, h)
                        fillPath.close()

                        // Translucent Area Fill
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(accentColor.copy(alpha = 0.45f), Color.Transparent),
                                startY = 0f,
                                endY = h
                            )
                        )

                        // Glowing Top Boundary Stroke
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF8B5CF6), accentColor, Color(0xFF06B6D4), Color(0xFFF43F5E))
                            ),
                            style = Stroke(width = 3f, cap = StrokeCap.Round)
                        )
                    }

                    VisualizerMode.NEON_PULSE -> {
                        // Neon Acoustic Waveform Pulse
                        val centerY = h / 2f
                        val waveCount = 28
                        val stepX = w / waveCount

                        for (i in 0 until waveCount) {
                            val fraction = i.toFloat() / waveCount
                            val amplitude: Float = if (isPlaying) {
                                val wave = (sin(fraction * 5.0 * Math.PI + phase) * 0.6 + cos(fraction * 8.0 * Math.PI - phase) * 0.4).toFloat()
                                wave * (h * 0.42f) * beatPulse
                            } else {
                                h * 0.08f
                            }
                            val x = i * stepX + stepX / 2f
                            val absAmp = kotlin.math.abs(amplitude)
                            drawLine(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF00F5D4), accentColor, Color(0xFFD946EF))
                                ),
                                start = Offset(x, centerY - absAmp),
                                end = Offset(x, centerY + absAmp),
                                strokeWidth = stepX * 0.6f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Frequency Axis Labels (60Hz, 150Hz, 400Hz, 1kHz, 2.5kHz, 6kHz, 16kHz)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("60Hz", "150Hz", "400Hz", "1kHz", "2.5kHz", "6kHz", "16kHz").forEach { label ->
                Text(
                    text = label,
                    color = Color(0xFF64748B),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
