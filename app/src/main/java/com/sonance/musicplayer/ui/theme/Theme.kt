package com.sonance.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sonance.musicplayer.model.AppTheme
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.ThemeRepository

@Composable
fun SonanceTheme(
    appTheme: AppTheme = AppTheme.DARK_AMOLED,
    content: @Composable (ThemeConfig) -> Unit
) {
    val config = ThemeRepository.getTheme(appTheme)
    val colorScheme = if (config.isDark) {
        darkColorScheme(
            primary = config.accentColor,
            background = config.bgCanvas,
            surface = config.headerBg,
            onPrimary = Color.Black,
            onBackground = config.textPrimary,
            onSurface = config.textPrimary
        )
    } else {
        lightColorScheme(
            primary = config.accentColor,
            background = config.bgCanvas,
            surface = config.headerBg,
            onPrimary = Color.White,
            onBackground = config.textPrimary,
            onSurface = config.textPrimary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        content(config)
    }
}
