package com.sonance.musicplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Wallpaper component with deep dark background and glowing golden sinusoidal
 * wave ribbons that sweep across the interface, matching the visual design.
 */
@Composable
fun GoldenWavyWallpaper(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Deep rich black canvas
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A0A0D),
                    Color(0xFF070709),
                    Color(0xFF000000)
                )
            )
        )

        val goldBrush1 = Brush.linearGradient(
            colors = listOf(
                Color(0x00D97706),
                Color(0x55F5B83D),
                Color(0x99FBBF24),
                Color(0x44D97706),
                Color(0x00F5B83D)
            )
        )

        val goldBrush2 = Brush.linearGradient(
            colors = listOf(
                Color(0x00F5B83D),
                Color(0x44FBBF24),
                Color(0x77EAB308),
                Color(0x33CA8A04),
                Color(0x00CA8A04)
            )
        )

        val goldBrush3 = Brush.linearGradient(
            colors = listOf(
                Color(0x00B45309),
                Color(0x55F59E0B),
                Color(0x88FCD34D),
                Color(0x33B45309),
                Color(0x00B45309)
            )
        )

        // Multiple flowing wave paths forming a resonant frequency ribbon
        val waveConfigs = listOf(
            Triple(0.36f, 0.46f, 0.38f),
            Triple(0.38f, 0.49f, 0.41f),
            Triple(0.40f, 0.52f, 0.44f),
            Triple(0.42f, 0.55f, 0.47f),
            Triple(0.45f, 0.58f, 0.51f),
            Triple(0.48f, 0.62f, 0.55f),
            Triple(0.51f, 0.66f, 0.60f),
            Triple(0.54f, 0.70f, 0.65f),
            Triple(0.58f, 0.75f, 0.70f),
            Triple(0.62f, 0.80f, 0.76f),
            Triple(0.66f, 0.85f, 0.82f)
        )

        for ((idx, cfg) in waveConfigs.withIndex()) {
            val (cp1Y, cp2Y, endY) = cfg
            val startY = h * (0.42f + idx * 0.018f)

            val path = Path().apply {
                moveTo(-w * 0.1f, startY)
                cubicTo(
                    w * 0.30f, h * cp1Y,
                    w * 0.65f, h * cp2Y,
                    w * 1.15f, h * endY
                )
            }

            val brush = when (idx % 3) {
                0 -> goldBrush1
                1 -> goldBrush2
                else -> goldBrush3
            }

            drawPath(
                path = path,
                brush = brush,
                style = Stroke(width = if (idx % 4 == 0) 2.2f else 1.2f)
            )
        }
    }
}
