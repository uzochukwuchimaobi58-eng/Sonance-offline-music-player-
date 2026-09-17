package com.sonance.musicplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun VisualizerWaveform(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val barCount = 32
        val barWidth = size.width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val progress = (i.toFloat() / barCount)
            val waveHeightFactor = if (isPlaying) {
                val sinVal = Math.sin((progress * 4.0 * Math.PI + phase * 2.0 * Math.PI)).toFloat()
                val cosVal = Math.cos((progress * 2.0 * Math.PI - phase * 2.0 * Math.PI)).toFloat()
                (Math.abs(sinVal * 0.6f + cosVal * 0.4f) * 0.85f + 0.15f)
            } else {
                0.1f
            }

            val barHeight = (size.height * 0.85f * waveHeightFactor).coerceIn(4f, size.height)
            val x = i * (barWidth + spacing) + spacing / 2f
            val y = centerY - barHeight / 2f

            drawRoundRect(
                color = accentColor.copy(alpha = if (isPlaying) 0.85f else 0.35f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
