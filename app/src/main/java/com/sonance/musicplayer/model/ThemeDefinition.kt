package com.sonance.musicplayer.model

import androidx.compose.ui.graphics.Color

data class CardTheme(
    val bg: Color,
    val text: Color,
    val iconColor: Color
)

data class ThemeConfig(
    val theme: AppTheme,
    val isDark: Boolean,
    val accentColor: Color,
    val bgCanvas: Color,
    val headerBg: Color,
    val headerBorder: Color,
    val miniPlayerBg: Color,
    val miniPlayerBorder: Color,
    val sidebarBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val cardBorder: Color,
    val libraryCard: CardTheme,
    val folderCard: CardTheme,
    val favoriteCard: CardTheme,
    val recentPlayCard: CardTheme,
    val recentAddCard: CardTheme,
    val mostPlayCard: CardTheme,
    val shuffleFabBg: Color
)

object ThemeRepository {
    private val darkAmoled = ThemeConfig(
        theme = AppTheme.DARK_AMOLED,
        isDark = true,
        accentColor = Color(0xFFF9BE39),
        bgCanvas = Color(0xFF19191B),
        headerBg = Color(0xFF171719),
        headerBorder = Color(0xFF27272A),
        miniPlayerBg = Color(0xFF161618),
        miniPlayerBorder = Color(0xFF27272A),
        sidebarBg = Color(0xFF18181A),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA1A1AA),
        cardBorder = Color.Transparent,
        libraryCard = CardTheme(Color(0xFF3F80C6), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFD18752), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFC6727C), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF5995B8), Color.White, Color(0xFF5995B8)),
        recentAddCard = CardTheme(Color(0xFF18AD75), Color.White, Color(0xFF18AD75)),
        mostPlayCard = CardTheme(Color(0xFF986EBD), Color.White, Color.White),
        shuffleFabBg = Color(0xFFF9BE39)
    )

    private val darkSlate = ThemeConfig(
        theme = AppTheme.DARK_SLATE,
        isDark = true,
        accentColor = Color(0xFF06B6D4),
        bgCanvas = Color(0xFF0F172A),
        headerBg = Color(0xFF0F172A),
        headerBorder = Color(0xFF1E293B),
        miniPlayerBg = Color(0xFF0B1120),
        miniPlayerBorder = Color(0xFF1E293B),
        sidebarBg = Color(0xFF0F172A),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        cardBorder = Color(0x2638BDF8),
        libraryCard = CardTheme(Color(0xFF0284C7), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF0F766E), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFF0369A1), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF0891B2), Color.White, Color(0xFF0891B2)),
        recentAddCard = CardTheme(Color(0xFF059669), Color.White, Color(0xFF059669)),
        mostPlayCard = CardTheme(Color(0xFF4F46E5), Color.White, Color.White),
        shuffleFabBg = Color(0xFF06B6D4)
    )

    private val cyberpunk = ThemeConfig(
        theme = AppTheme.CYBERPUNK,
        isDark = true,
        accentColor = Color(0xFFF43F5E),
        bgCanvas = Color(0xFF1A0826),
        headerBg = Color(0xFF1F092E),
        headerBorder = Color(0xFF3B0764),
        miniPlayerBg = Color(0xFF160522),
        miniPlayerBorder = Color(0xFF3B0764),
        sidebarBg = Color(0xFF1F092E),
        textPrimary = Color.White,
        textSecondary = Color(0xFFD8B4FE),
        cardBorder = Color(0x33F43F5E),
        libraryCard = CardTheme(Color(0xFFEC4899), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFD946EF), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFF43F5E), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF06B6D4), Color.White, Color(0xFF06B6D4)),
        recentAddCard = CardTheme(Color(0xFF10B981), Color.White, Color(0xFF10B981)),
        mostPlayCard = CardTheme(Color(0xFF8B5CF6), Color.White, Color.White),
        shuffleFabBg = Color(0xFFF43F5E)
    )

    private val midnightBlue = ThemeConfig(
        theme = AppTheme.MIDNIGHT_BLUE,
        isDark = true,
        accentColor = Color(0xFF38BDF8),
        bgCanvas = Color(0xFF071529),
        headerBg = Color(0xFF0A1D38),
        headerBorder = Color(0xFF1E3A8A),
        miniPlayerBg = Color(0xFF061324),
        miniPlayerBorder = Color(0xFF1E3A8A),
        sidebarBg = Color(0xFF0A1D38),
        textPrimary = Color.White,
        textSecondary = Color(0xFFBAE6FD),
        cardBorder = Color(0x3338BDF8),
        libraryCard = CardTheme(Color(0xFF2563EB), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF0284C7), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFF3B82F6), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF0EA5E9), Color.White, Color(0xFF0EA5E9)),
        recentAddCard = CardTheme(Color(0xFF06B6D4), Color.White, Color(0xFF06B6D4)),
        mostPlayCard = CardTheme(Color(0xFF6366F1), Color.White, Color.White),
        shuffleFabBg = Color(0xFF38BDF8)
    )

    private val sunsetWarm = ThemeConfig(
        theme = AppTheme.SUNSET_WARM,
        isDark = true,
        accentColor = Color(0xFFF97316),
        bgCanvas = Color(0xFF24140E),
        headerBg = Color(0xFF2C1810),
        headerBorder = Color(0xFF431407),
        miniPlayerBg = Color(0xFF1C0E08),
        miniPlayerBorder = Color(0xFF431407),
        sidebarBg = Color(0xFF2C1810),
        textPrimary = Color.White,
        textSecondary = Color(0xFFFED7AA),
        cardBorder = Color(0x33F97316),
        libraryCard = CardTheme(Color(0xFFEA580C), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFD97706), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFE11D48), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFFC2410C), Color.White, Color(0xFFC2410C)),
        recentAddCard = CardTheme(Color(0xFFCA8A04), Color.White, Color(0xFFCA8A04)),
        mostPlayCard = CardTheme(Color(0xFFB45309), Color.White, Color.White),
        shuffleFabBg = Color(0xFFF97316)
    )

    private val emeraldForest = ThemeConfig(
        theme = AppTheme.EMERALD_FOREST,
        isDark = true,
        accentColor = Color(0xFF10B981),
        bgCanvas = Color(0xFF062217),
        headerBg = Color(0xFF092D1F),
        headerBorder = Color(0xFF064E3B),
        miniPlayerBg = Color(0xFF051F15),
        miniPlayerBorder = Color(0xFF064E3B),
        sidebarBg = Color(0xFF092D1F),
        textPrimary = Color.White,
        textSecondary = Color(0xFFA7F3D0),
        cardBorder = Color(0x3310B981),
        libraryCard = CardTheme(Color(0xFF059669), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF15803D), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFF047857), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF0D9488), Color.White, Color(0xFF0D9488)),
        recentAddCard = CardTheme(Color(0xFF16A34A), Color.White, Color(0xFF16A34A)),
        mostPlayCard = CardTheme(Color(0xFF0F766E), Color.White, Color.White),
        shuffleFabBg = Color(0xFF10B981)
    )

    private val crimsonRuby = ThemeConfig(
        theme = AppTheme.CRIMSON_RUBY,
        isDark = true,
        accentColor = Color(0xFFF43F5E),
        bgCanvas = Color(0xFF240914),
        headerBg = Color(0xFF2D0C19),
        headerBorder = Color(0xFF4C0519),
        miniPlayerBg = Color(0xFF1F0611),
        miniPlayerBorder = Color(0xFF4C0519),
        sidebarBg = Color(0xFF2D0C19),
        textPrimary = Color.White,
        textSecondary = Color(0xFFFECDD3),
        cardBorder = Color(0x33F43F5E),
        libraryCard = CardTheme(Color(0xFFBE123C), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF9F1239), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFE11D48), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF881337), Color.White, Color(0xFF881337)),
        recentAddCard = CardTheme(Color(0xFFDB2777), Color.White, Color(0xFFDB2777)),
        mostPlayCard = CardTheme(Color(0xFF9D174D), Color.White, Color.White),
        shuffleFabBg = Color(0xFFF43F5E)
    )

    private val goldenLuxury = ThemeConfig(
        theme = AppTheme.GOLDEN_LUXURY,
        isDark = true,
        accentColor = Color(0xFFFBBF24),
        bgCanvas = Color(0xFF1C180E),
        headerBg = Color(0xFF252012),
        headerBorder = Color(0xFF451A03),
        miniPlayerBg = Color(0xFF18150A),
        miniPlayerBorder = Color(0xFF451A03),
        sidebarBg = Color(0xFF252012),
        textPrimary = Color.White,
        textSecondary = Color(0xFFFEF08A),
        cardBorder = Color(0x33FBBF24),
        libraryCard = CardTheme(Color(0xFFCA8A04), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFB45309), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFD97706), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFFA16207), Color.White, Color(0xFFA16207)),
        recentAddCard = CardTheme(Color(0xFF854D0E), Color.White, Color(0xFF854D0E)),
        mostPlayCard = CardTheme(Color(0xFF713F12), Color.White, Color.White),
        shuffleFabBg = Color(0xFFFBBF24)
    )

    private val lightMinimal = ThemeConfig(
        theme = AppTheme.LIGHT_MINIMAL,
        isDark = false,
        accentColor = Color(0xFF2563EB),
        bgCanvas = Color(0xFFF4F5F8),
        headerBg = Color.White,
        headerBorder = Color(0xFFE2E8F0),
        miniPlayerBg = Color.White,
        miniPlayerBorder = Color(0xFFE2E8F0),
        sidebarBg = Color.White,
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF64748B),
        cardBorder = Color(0x0F000000),
        libraryCard = CardTheme(Color(0xFF3B82F6), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFF97316), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFF43F5E), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF06B6D4), Color.White, Color(0xFF06B6D4)),
        recentAddCard = CardTheme(Color(0xFF10B981), Color.White, Color(0xFF10B981)),
        mostPlayCard = CardTheme(Color(0xFF8B5CF6), Color.White, Color.White),
        shuffleFabBg = Color(0xFF2563EB)
    )

    fun getTheme(theme: AppTheme): ThemeConfig {
        return when (theme) {
            AppTheme.DARK_AMOLED -> darkAmoled
            AppTheme.DARK_SLATE -> darkSlate
            AppTheme.CYBERPUNK -> cyberpunk
            AppTheme.MIDNIGHT_BLUE -> midnightBlue
            AppTheme.SUNSET_WARM -> sunsetWarm
            AppTheme.EMERALD_FOREST -> emeraldForest
            AppTheme.CRIMSON_RUBY -> crimsonRuby
            AppTheme.GOLDEN_LUXURY -> goldenLuxury
            AppTheme.LIGHT_MINIMAL -> lightMinimal
        }
    }
}
