package com.sonance.musicplayer.model

import androidx.compose.ui.graphics.Color
import com.sonance.musicplayer.R

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
    val shuffleFabBg: Color,
    val coverDrawableRes: Int? = null,
    val previewTag: String = "TEMPLATE",
    val styleDescription: String = ""
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
        shuffleFabBg = Color(0xFFF9BE39),
        coverDrawableRes = R.drawable.img_theme_dark_amoled,
        previewTag = "AMOLED DARK",
        styleDescription = "Deep obsidian black with ambient glowing amber acoustics"
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

    private val royalAmethyst = ThemeConfig(
        theme = AppTheme.ROYAL_AMETHYST,
        isDark = true,
        accentColor = Color(0xFFD946EF),
        bgCanvas = Color(0xFF140722),
        headerBg = Color(0xFF1B0B2E),
        headerBorder = Color(0xFF38125E),
        miniPlayerBg = Color(0xFF120520),
        miniPlayerBorder = Color(0xFF38125E),
        sidebarBg = Color(0xFF1B0B2E),
        textPrimary = Color(0xFFFAF5FF),
        textSecondary = Color(0xFFD8B4FE),
        cardBorder = Color(0x33D946EF),
        libraryCard = CardTheme(Color(0xFF9333EA), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFC026D3), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFE879F9), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFFA855F7), Color.White, Color(0xFFA855F7)),
        recentAddCard = CardTheme(Color(0xFF7E22CE), Color.White, Color(0xFF7E22CE)),
        mostPlayCard = CardTheme(Color(0xFFD946EF), Color.White, Color.White),
        shuffleFabBg = Color(0xFFD946EF)
    )

    private val auroraBorealis = ThemeConfig(
        theme = AppTheme.AURORA_BOREALIS,
        isDark = true,
        accentColor = Color(0xFF00F5D4),
        bgCanvas = Color(0xFF061521),
        headerBg = Color(0xFF0A2234),
        headerBorder = Color(0xFF13415C),
        miniPlayerBg = Color(0xFF05111B),
        miniPlayerBorder = Color(0xFF13415C),
        sidebarBg = Color(0xFF0A2234),
        textPrimary = Color(0xFFF0FDFA),
        textSecondary = Color(0xFF99F6E4),
        cardBorder = Color(0x3300F5D4),
        libraryCard = CardTheme(Color(0xFF0D9488), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF0284C7), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFF14B8A6), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF06B6D4), Color.White, Color(0xFF06B6D4)),
        recentAddCard = CardTheme(Color(0xFF10B981), Color.White, Color(0xFF10B981)),
        mostPlayCard = CardTheme(Color(0xFF2DD4BF), Color.White, Color.White),
        shuffleFabBg = Color(0xFF00F5D4)
    )

    private val carbonTitanium = ThemeConfig(
        theme = AppTheme.CARBON_TITANIUM,
        isDark = true,
        accentColor = Color(0xFFF59E0B),
        bgCanvas = Color(0xFF111215),
        headerBg = Color(0xFF181A1F),
        headerBorder = Color(0xFF2C313A),
        miniPlayerBg = Color(0xFF0F1013),
        miniPlayerBorder = Color(0xFF2C313A),
        sidebarBg = Color(0xFF181A1F),
        textPrimary = Color(0xFFF1F5F9),
        textSecondary = Color(0xFF94A3B8),
        cardBorder = Color(0x33F59E0B),
        libraryCard = CardTheme(Color(0xFF475569), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF334155), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFD97706), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF64748B), Color.White, Color(0xFF64748B)),
        recentAddCard = CardTheme(Color(0xFFF59E0B), Color.White, Color(0xFFF59E0B)),
        mostPlayCard = CardTheme(Color(0xFFB45309), Color.White, Color.White),
        shuffleFabBg = Color(0xFFF59E0B)
    )

    private val roseGoldLuxe = ThemeConfig(
        theme = AppTheme.ROSE_GOLD_LUXE,
        isDark = true,
        accentColor = Color(0xFFFB7185),
        bgCanvas = Color(0xFF1C0E14),
        headerBg = Color(0xFF28141D),
        headerBorder = Color(0xFF4C2033),
        miniPlayerBg = Color(0xFF170B10),
        miniPlayerBorder = Color(0xFF4C2033),
        sidebarBg = Color(0xFF28141D),
        textPrimary = Color(0xFFFFF1F2),
        textSecondary = Color(0xFFFECDD3),
        cardBorder = Color(0x33FB7185),
        libraryCard = CardTheme(Color(0xFFE11D48), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFBE123C), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFFB7185), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFFF43F5E), Color.White, Color(0xFFF43F5E)),
        recentAddCard = CardTheme(Color(0xFFFDA4AF), Color.Black, Color(0xFFFDA4AF)),
        mostPlayCard = CardTheme(Color(0xFF9F1239), Color.White, Color.White),
        shuffleFabBg = Color(0xFFFB7185)
    )

    private val neonMatrix = ThemeConfig(
        theme = AppTheme.NEON_MATRIX,
        isDark = true,
        accentColor = Color(0xFF22C55E),
        bgCanvas = Color(0xFF07110A),
        headerBg = Color(0xFF0D1C12),
        headerBorder = Color(0xFF163E24),
        miniPlayerBg = Color(0xFF050E07),
        miniPlayerBorder = Color(0xFF163E24),
        sidebarBg = Color(0xFF0D1C12),
        textPrimary = Color(0xFFDCFCE7),
        textSecondary = Color(0xFF86EFAC),
        cardBorder = Color(0x3322C55E),
        libraryCard = CardTheme(Color(0xFF15803D), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF166534), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFF22C55E), Color.Black, Color.Black),
        recentPlayCard = CardTheme(Color(0xFF4ADE80), Color.Black, Color(0xFF4ADE80)),
        recentAddCard = CardTheme(Color(0xFF10B981), Color.White, Color(0xFF10B981)),
        mostPlayCard = CardTheme(Color(0xFF059669), Color.White, Color.White),
        shuffleFabBg = Color(0xFF22C55E)
    )

    private val frostedGlass = ThemeConfig(
        theme = AppTheme.FROSTED_GLASS,
        isDark = true,
        accentColor = Color(0xFFE2E8F0),
        bgCanvas = Color(0xFF1E2124),
        headerBg = Color(0xFF181A1D),
        headerBorder = Color(0x66FFFFFF),
        miniPlayerBg = Color(0xFF1A1C1E),
        miniPlayerBorder = Color(0x44FFFFFF),
        sidebarBg = Color(0xFF181A1D),
        textPrimary = Color.White,
        textSecondary = Color(0xFFA0AEC0),
        cardBorder = Color(0x44FFFFFF),
        libraryCard = CardTheme(Color(0xFF4A5568), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF718096), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFE2E8F0), Color.Black, Color.Black),
        recentPlayCard = CardTheme(Color(0xFF4A5568), Color.White, Color(0xFFE2E8F0)),
        recentAddCard = CardTheme(Color(0xFF2D3748), Color.White, Color(0xFFE2E8F0)),
        mostPlayCard = CardTheme(Color(0xFF4A5568), Color.White, Color.White),
        shuffleFabBg = Color(0xFFE2E8F0)
    )

    private val crimsonCountdown = ThemeConfig(
        theme = AppTheme.CRIMSON_COUNTDOWN,
        isDark = true,
        accentColor = Color(0xFFEF4444),
        bgCanvas = Color(0xFF1A0507),
        headerBg = Color(0xFF2B090E),
        headerBorder = Color(0xFF5B101D),
        miniPlayerBg = Color(0xFF180507),
        miniPlayerBorder = Color(0xFF5B101D),
        sidebarBg = Color(0xFF2B090E),
        textPrimary = Color(0xFFFFF1F2),
        textSecondary = Color(0xFFFECDD3),
        cardBorder = Color(0x33EF4444),
        libraryCard = CardTheme(Color(0xFFDC2626), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFB91C1C), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFEF4444), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF991B1B), Color.White, Color(0xFFEF4444)),
        recentAddCard = CardTheme(Color(0xFF7F1D1D), Color.White, Color(0xFFEF4444)),
        mostPlayCard = CardTheme(Color(0xFFDC2626), Color.White, Color.White),
        shuffleFabBg = Color(0xFFEF4444),
        coverDrawableRes = R.drawable.img_template_vinyl_retro,
        previewTag = "RED GRADIENT",
        styleDescription = "Deep crimson red gradient with analog vinyl grooves"
    )

    private val monochromeSpoke = ThemeConfig(
        theme = AppTheme.MONOCHROME_SPOKE,
        isDark = true,
        accentColor = Color(0xFF38BDF8),
        bgCanvas = Color(0xFF121316),
        headerBg = Color(0xFF181A1F),
        headerBorder = Color(0xFF2C3038),
        miniPlayerBg = Color(0xFF101114),
        miniPlayerBorder = Color(0xFF2C3038),
        sidebarBg = Color(0xFF181A1F),
        textPrimary = Color.White,
        textSecondary = Color(0xFF94A3B8),
        cardBorder = Color(0x3338BDF8),
        libraryCard = CardTheme(Color(0xFF0284C7), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF334155), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFEF4444), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF0369A1), Color.White, Color(0xFF38BDF8)),
        recentAddCard = CardTheme(Color(0xFF075985), Color.White, Color(0xFF38BDF8)),
        mostPlayCard = CardTheme(Color(0xFF0284C7), Color.White, Color.White),
        shuffleFabBg = Color(0xFF38BDF8)
    )

    private val emeraldArc = ThemeConfig(
        theme = AppTheme.EMERALD_ARC,
        isDark = true,
        accentColor = Color(0xFF10B981),
        bgCanvas = Color(0xFF06140E),
        headerBg = Color(0xFF0A2319),
        headerBorder = Color(0xFF134E36),
        miniPlayerBg = Color(0xFF040E0A),
        miniPlayerBorder = Color(0xFF134E36),
        sidebarBg = Color(0xFF0A2319),
        textPrimary = Color(0xFFECFDF5),
        textSecondary = Color(0xFF6EE7B7),
        cardBorder = Color(0x3310B981),
        libraryCard = CardTheme(Color(0xFF059669), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF047857), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFF10B981), Color.Black, Color.Black),
        recentPlayCard = CardTheme(Color(0xFF065F46), Color.White, Color(0xFF10B981)),
        recentAddCard = CardTheme(Color(0xFF064E3B), Color.White, Color(0xFF10B981)),
        mostPlayCard = CardTheme(Color(0xFF059669), Color.White, Color.White),
        shuffleFabBg = Color(0xFF10B981)
    )

    private val studioPiano = ThemeConfig(
        theme = AppTheme.STUDIO_PIANO,
        isDark = true,
        accentColor = Color(0xFFF43F5E),
        bgCanvas = Color(0xFF18181B),
        headerBg = Color(0xFF27272A),
        headerBorder = Color(0xFF3F3F46),
        miniPlayerBg = Color(0xFF121214),
        miniPlayerBorder = Color(0xFF3F3F46),
        sidebarBg = Color(0xFF27272A),
        textPrimary = Color.White,
        textSecondary = Color(0xFFA1A1AA),
        cardBorder = Color(0x33F43F5E),
        libraryCard = CardTheme(Color(0xFFE11D48), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF52525B), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFF43F5E), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFFBE123C), Color.White, Color(0xFFF43F5E)),
        recentAddCard = CardTheme(Color(0xFF9F1239), Color.White, Color(0xFFF43F5E)),
        mostPlayCard = CardTheme(Color(0xFFE11D48), Color.White, Color.White),
        shuffleFabBg = Color(0xFFF43F5E),
        coverDrawableRes = R.drawable.img_theme_studio_piano,
        previewTag = "ACOUSTIC PIANO",
        styleDescription = "Dancing Girl studio acoustic piano keys"
    )

    private val autumnBokeh = ThemeConfig(
        theme = AppTheme.AUTUMN_BOKEH,
        isDark = true,
        accentColor = Color(0xFFF59E0B),
        bgCanvas = Color(0xFF1C1308),
        headerBg = Color(0xFF2E1F0D),
        headerBorder = Color(0xFF573B18),
        miniPlayerBg = Color(0xFF160F06),
        miniPlayerBorder = Color(0xFF573B18),
        sidebarBg = Color(0xFF2E1F0D),
        textPrimary = Color(0xFFFEF3C7),
        textSecondary = Color(0xFFFDE68A),
        cardBorder = Color(0x33F59E0B),
        libraryCard = CardTheme(Color(0xFFD97706), Color.Black, Color.Black),
        folderCard = CardTheme(Color(0xFFB45309), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFF59E0B), Color.Black, Color.Black),
        recentPlayCard = CardTheme(Color(0xFF92400E), Color.White, Color(0xFFF59E0B)),
        recentAddCard = CardTheme(Color(0xFF78350F), Color.White, Color(0xFFF59E0B)),
        mostPlayCard = CardTheme(Color(0xFFD97706), Color.Black, Color.Black),
        shuffleFabBg = Color(0xFFF59E0B),
        coverDrawableRes = R.drawable.img_theme_autumn_leaves,
        previewTag = "AUTUMN LEAVES",
        styleDescription = "Golden autumn leaves and warm amber bokeh"
    )

    private val rockPlaylist = ThemeConfig(
        theme = AppTheme.ROCK_PLAYLIST,
        isDark = true,
        accentColor = Color(0xFFF8FAFC),
        bgCanvas = Color(0xFF0F0F12),
        headerBg = Color(0xFF18181D),
        headerBorder = Color(0xFF33333D),
        miniPlayerBg = Color(0xFF0A0A0D),
        miniPlayerBorder = Color(0xFF33333D),
        sidebarBg = Color(0xFF18181D),
        textPrimary = Color.White,
        textSecondary = Color(0xFF94A3B8),
        cardBorder = Color(0x44FFFFFF),
        libraryCard = CardTheme(Color(0xFF334155), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF1E293B), Color.White, Color.White),
        favoriteCard = CardTheme(Color.White, Color.Black, Color.Black),
        recentPlayCard = CardTheme(Color(0xFF475569), Color.White, Color.White),
        recentAddCard = CardTheme(Color(0xFF0F172A), Color.White, Color.White),
        mostPlayCard = CardTheme(Color(0xFF334155), Color.White, Color.White),
        shuffleFabBg = Color.White,
        coverDrawableRes = R.drawable.img_theme_rock_violin,
        previewTag = "ROCK VIOLIN",
        styleDescription = "Dark rock music stage with violin aesthetics"
    )

    private val mountainBlur = ThemeConfig(
        theme = AppTheme.MOUNTAIN_BLUR,
        isDark = true,
        accentColor = Color(0xFF94A3B8),
        bgCanvas = Color(0xFF1E2024),
        headerBg = Color(0xFF272A30),
        headerBorder = Color(0xFF3C414A),
        miniPlayerBg = Color(0xFF17191C),
        miniPlayerBorder = Color(0xFF3C414A),
        sidebarBg = Color(0xFF272A30),
        textPrimary = Color.White,
        textSecondary = Color(0xFF94A3B8),
        cardBorder = Color(0x3394A3B8),
        libraryCard = CardTheme(Color(0xFF475569), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF334155), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFCBD5E1), Color.Black, Color.Black),
        recentPlayCard = CardTheme(Color(0xFF64748B), Color.White, Color(0xFF94A3B8)),
        recentAddCard = CardTheme(Color(0xFF1E293B), Color.White, Color(0xFF94A3B8)),
        mostPlayCard = CardTheme(Color(0xFF475569), Color.White, Color.White),
        shuffleFabBg = Color(0xFF94A3B8),
        coverDrawableRes = R.drawable.img_template_lofi_mountain,
        previewTag = "LO-FI MIST",
        styleDescription = "Starry night alpine mountains with serene moonlit tones"
    )

    private val weeklyBlue = ThemeConfig(
        theme = AppTheme.WEEKLY_BLUE,
        isDark = true,
        accentColor = Color(0xFF3B82F6),
        bgCanvas = Color(0xFF0B132B),
        headerBg = Color(0xFF1C2541),
        headerBorder = Color(0xFF2D3C66),
        miniPlayerBg = Color(0xFF080E20),
        miniPlayerBorder = Color(0xFF2D3C66),
        sidebarBg = Color(0xFF1C2541),
        textPrimary = Color.White,
        textSecondary = Color(0xFF93C5FD),
        cardBorder = Color(0x333B82F6),
        libraryCard = CardTheme(Color(0xFF2563EB), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF1D4ED8), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFF3B82F6), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF1E40AF), Color.White, Color(0xFF3B82F6)),
        recentAddCard = CardTheme(Color(0xFF172554), Color.White, Color(0xFF3B82F6)),
        mostPlayCard = CardTheme(Color(0xFF2563EB), Color.White, Color.White),
        shuffleFabBg = Color(0xFF3B82F6)
    )

    private val tropicalDusk = ThemeConfig(
        theme = AppTheme.TROPICAL_DUSK,
        isDark = true,
        accentColor = Color(0xFF2DD4BF),
        bgCanvas = Color(0xFF131D20),
        headerBg = Color(0xFF1C2C30),
        headerBorder = Color(0xFF2C444B),
        miniPlayerBg = Color(0xFF0E1618),
        miniPlayerBorder = Color(0xFF2C444B),
        sidebarBg = Color(0xFF1C2C30),
        textPrimary = Color(0xFFF0FDFA),
        textSecondary = Color(0xFF99F6E4),
        cardBorder = Color(0x332DD4BF),
        libraryCard = CardTheme(Color(0xFF0D9488), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF0F766E), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFF2DD4BF), Color.Black, Color.Black),
        recentPlayCard = CardTheme(Color(0xFF115E59), Color.White, Color(0xFF2DD4BF)),
        recentAddCard = CardTheme(Color(0xFF134E4A), Color.White, Color(0xFF2DD4BF)),
        mostPlayCard = CardTheme(Color(0xFF0D9488), Color.White, Color.White),
        shuffleFabBg = Color(0xFF2DD4BF)
    )

    private val campusSunshine = ThemeConfig(
        theme = AppTheme.CAMPUS_SUNSHINE,
        isDark = true,
        accentColor = Color(0xFFF59E0B),
        bgCanvas = Color(0xFF181B18),
        headerBg = Color(0xFF222922),
        headerBorder = Color(0xFF354235),
        miniPlayerBg = Color(0xFF121412),
        miniPlayerBorder = Color(0xFF354235),
        sidebarBg = Color(0xFF222922),
        textPrimary = Color.White,
        textSecondary = Color(0xFFA7F3D0),
        cardBorder = Color(0x33F59E0B),
        libraryCard = CardTheme(Color(0xFF15803D), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFB45309), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFEF4444), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFFD97706), Color.Black, Color(0xFFF59E0B)),
        recentAddCard = CardTheme(Color(0xFF166534), Color.White, Color(0xFFF59E0B)),
        mostPlayCard = CardTheme(Color(0xFF15803D), Color.White, Color.White),
        shuffleFabBg = Color(0xFFF59E0B)
    )

    private val skyBlossom = ThemeConfig(
        theme = AppTheme.SKY_BLOSSOM,
        isDark = true,
        accentColor = Color(0xFFF472B6),
        bgCanvas = Color(0xFF1F1218),
        headerBg = Color(0xFF331926),
        headerBorder = Color(0xFF5B2240),
        miniPlayerBg = Color(0xFF190C12),
        miniPlayerBorder = Color(0xFF5B2240),
        sidebarBg = Color(0xFF331926),
        textPrimary = Color(0xFFFDF2F8),
        textSecondary = Color(0xFFFBCFE8),
        cardBorder = Color(0x33F472B6),
        libraryCard = CardTheme(Color(0xFFEC4899), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFDB2777), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFF472B6), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFFBE185D), Color.White, Color(0xFFF472B6)),
        recentAddCard = CardTheme(Color(0xFF9D174D), Color.White, Color(0xFFF472B6)),
        mostPlayCard = CardTheme(Color(0xFFEC4899), Color.White, Color.White),
        shuffleFabBg = Color(0xFFF472B6),
        coverDrawableRes = R.drawable.img_theme_pink_pastel,
        previewTag = "PINK MINIMALIST",
        styleDescription = "Soft pastel pink minimalist aesthetic and clean typographic cards"
    )

    private val bohoPampas = ThemeConfig(
        theme = AppTheme.BOHO_PAMPAS,
        isDark = true,
        accentColor = Color(0xFFDB7093),
        bgCanvas = Color(0xFF20161B),
        headerBg = Color(0xFF332029),
        headerBorder = Color(0xFF543444),
        miniPlayerBg = Color(0xFF181014),
        miniPlayerBorder = Color(0xFF543444),
        sidebarBg = Color(0xFF332029),
        textPrimary = Color(0xFFFCE7F3),
        textSecondary = Color(0xFFFBCFE8),
        cardBorder = Color(0x33DB7093),
        libraryCard = CardTheme(Color(0xFFBE185D), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF9D174D), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFDB7093), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF831843), Color.White, Color(0xFFDB7093)),
        recentAddCard = CardTheme(Color(0xFF500724), Color.White, Color(0xFFDB7093)),
        mostPlayCard = CardTheme(Color(0xFFBE185D), Color.White, Color.White),
        shuffleFabBg = Color(0xFFDB7093)
    )

    private val alpineLake = ThemeConfig(
        theme = AppTheme.ALPINE_LAKE,
        isDark = true,
        accentColor = Color(0xFF38BDF8),
        bgCanvas = Color(0xFF0F1E28),
        headerBg = Color(0xFF162D3D),
        headerBorder = Color(0xFF234760),
        miniPlayerBg = Color(0xFF0B171F),
        miniPlayerBorder = Color(0xFF234760),
        sidebarBg = Color(0xFF162D3D),
        textPrimary = Color.White,
        textSecondary = Color(0xFFBAE6FD),
        cardBorder = Color(0x3338BDF8),
        libraryCard = CardTheme(Color(0xFF0284C7), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF0369A1), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFF38BDF8), Color.Black, Color.Black),
        recentPlayCard = CardTheme(Color(0xFF075985), Color.White, Color(0xFF38BDF8)),
        recentAddCard = CardTheme(Color(0xFF0C4A6E), Color.White, Color(0xFF38BDF8)),
        mostPlayCard = CardTheme(Color(0xFF0284C7), Color.White, Color.White),
        shuffleFabBg = Color(0xFF38BDF8)
    )

    private val synthwaveNeon = ThemeConfig(
        theme = AppTheme.SYNTHWAVE_NEON,
        isDark = true,
        accentColor = Color(0xFFEC4899),
        bgCanvas = Color(0xFF180A22),
        headerBg = Color(0xFF2A103D),
        headerBorder = Color(0xFF4C1D6F),
        miniPlayerBg = Color(0xFF12071A),
        miniPlayerBorder = Color(0xFF4C1D6F),
        sidebarBg = Color(0xFF2A103D),
        textPrimary = Color(0xFFFDF2F8),
        textSecondary = Color(0xFFF472B6),
        cardBorder = Color(0x33EC4899),
        libraryCard = CardTheme(Color(0xFFC026D3), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF9333EA), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFEC4899), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFFA21CAF), Color.White, Color(0xFFEC4899)),
        recentAddCard = CardTheme(Color(0xFF701A75), Color.White, Color(0xFFEC4899)),
        mostPlayCard = CardTheme(Color(0xFFC026D3), Color.White, Color.White),
        shuffleFabBg = Color(0xFFEC4899),
        coverDrawableRes = R.drawable.img_template_synthwave,
        previewTag = "NEON GRID",
        styleDescription = "Retro 80s neon grid with laser audio waveforms"
    )

    private val sunsetOcean = ThemeConfig(
        theme = AppTheme.SUNSET_OCEAN,
        isDark = true,
        accentColor = Color(0xFFF97316),
        bgCanvas = Color(0xFF1E1015),
        headerBg = Color(0xFF311520),
        headerBorder = Color(0xFF562337),
        miniPlayerBg = Color(0xFF170C10),
        miniPlayerBorder = Color(0xFF562337),
        sidebarBg = Color(0xFF311520),
        textPrimary = Color(0xFFFFF7ED),
        textSecondary = Color(0xFFFED7AA),
        cardBorder = Color(0x33F97316),
        libraryCard = CardTheme(Color(0xFFEA580C), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFC2410C), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFF97316), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF9A3412), Color.White, Color(0xFFF97316)),
        recentAddCard = CardTheme(Color(0xFF7C2D12), Color.White, Color(0xFFF97316)),
        mostPlayCard = CardTheme(Color(0xFFEA580C), Color.White, Color.White),
        shuffleFabBg = Color(0xFFF97316),
        coverDrawableRes = R.drawable.img_template_sunset_chill,
        previewTag = "SUNSET CHILL",
        styleDescription = "Warm twilight gradient with coastal lo-fi ambiance"
    )

    private val liquidChrome = ThemeConfig(
        theme = AppTheme.LIQUID_CHROME,
        isDark = true,
        accentColor = Color(0xFFD946EF),
        bgCanvas = Color(0xFF140D24),
        headerBg = Color(0xFF22153D),
        headerBorder = Color(0xFF3F276D),
        miniPlayerBg = Color(0xFF0F091B),
        miniPlayerBorder = Color(0xFF3F276D),
        sidebarBg = Color(0xFF22153D),
        textPrimary = Color.White,
        textSecondary = Color(0xFFE9D5FF),
        cardBorder = Color(0x33D946EF),
        libraryCard = CardTheme(Color(0xFFC026D3), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFF7C3AED), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFD946EF), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFFA855F7), Color.White, Color(0xFFD946EF)),
        recentAddCard = CardTheme(Color(0xFF6B21A8), Color.White, Color(0xFFD946EF)),
        mostPlayCard = CardTheme(Color(0xFFC026D3), Color.White, Color.White),
        shuffleFabBg = Color(0xFFD946EF)
    )

    private val romanticDreams = ThemeConfig(
        theme = AppTheme.ROMANTIC_DREAMS,
        isDark = true,
        accentColor = Color(0xFFD4A373),
        bgCanvas = Color(0xFF1F1B18),
        headerBg = Color(0xFF2D2722),
        headerBorder = Color(0xFF473E36),
        miniPlayerBg = Color(0xFF181512),
        miniPlayerBorder = Color(0xFF473E36),
        sidebarBg = Color(0xFF2D2722),
        textPrimary = Color(0xFFFAEDCD),
        textSecondary = Color(0xFFD4A373),
        cardBorder = Color(0x33D4A373),
        libraryCard = CardTheme(Color(0xFF3F80C6), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFD18752), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFC6727C), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF5995B8), Color.White, Color.White),
        recentAddCard = CardTheme(Color(0xFF18AD75), Color.White, Color.White),
        mostPlayCard = CardTheme(Color(0xFF986EBD), Color.White, Color.White),
        shuffleFabBg = Color(0xFFD4A373),
        coverDrawableRes = R.drawable.img_theme_beige_boho,
        previewTag = "BEIGE MINIMALIST",
        styleDescription = "Warm oat, beige & amber acoustics with cozy earthy vibe"
    )

    private val natureFlower = ThemeConfig(
        theme = AppTheme.NATURE_FLOWER,
        isDark = true,
        accentColor = Color(0xFF4ADE80),
        bgCanvas = Color(0xFF0D1F12),
        headerBg = Color(0xFF142E1C),
        headerBorder = Color(0xFF1E462B),
        miniPlayerBg = Color(0xFF0F2416),
        miniPlayerBorder = Color(0xFF1E462B),
        sidebarBg = Color(0xFF142E1C),
        textPrimary = Color(0xFFF0FDF4),
        textSecondary = Color(0xFF86EFAC),
        cardBorder = Color(0x334ADE80),
        libraryCard = CardTheme(Color(0xFF2563EB), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFD97706), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFE11D48), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF0284C7), Color.White, Color.White),
        recentAddCard = CardTheme(Color(0xFF16A34A), Color.White, Color.White),
        mostPlayCard = CardTheme(Color(0xFF9333EA), Color.White, Color.White),
        shuffleFabBg = Color(0xFF4ADE80),
        coverDrawableRes = R.drawable.img_theme_nature_flower,
        previewTag = "NATURE",
        styleDescription = "Purple bellflowers in fresh dew morning meadow"
    )

    private val animalPets = ThemeConfig(
        theme = AppTheme.ANIMAL_PETS,
        isDark = true,
        accentColor = Color(0xFFFBBF24),
        bgCanvas = Color(0xFF1C1917),
        headerBg = Color(0xFF292524),
        headerBorder = Color(0xFF44403C),
        miniPlayerBg = Color(0xFF1C1917),
        miniPlayerBorder = Color(0xFF44403C),
        sidebarBg = Color(0xFF292524),
        textPrimary = Color(0xFFFEF3C7),
        textSecondary = Color(0xFFFDE68A),
        cardBorder = Color(0x33FBBF24),
        libraryCard = CardTheme(Color(0xFF2563EB), Color.White, Color.White),
        folderCard = CardTheme(Color(0xFFD97706), Color.White, Color.White),
        favoriteCard = CardTheme(Color(0xFFE11D48), Color.White, Color.White),
        recentPlayCard = CardTheme(Color(0xFF0284C7), Color.White, Color.White),
        recentAddCard = CardTheme(Color(0xFF16A34A), Color.White, Color.White),
        mostPlayCard = CardTheme(Color(0xFF9333EA), Color.White, Color.White),
        shuffleFabBg = Color(0xFFFBBF24),
        coverDrawableRes = R.drawable.img_theme_animal_pets,
        previewTag = "ANIMALS",
        styleDescription = "Playful golden puppy and kitten in sunny grass field"
    )

    fun getTheme(theme: AppTheme): ThemeConfig {
        return when (theme) {
            AppTheme.NATURE_FLOWER -> natureFlower
            AppTheme.ANIMAL_PETS -> animalPets
            AppTheme.DARK_AMOLED -> darkAmoled
            AppTheme.DARK_SLATE -> darkSlate
            AppTheme.FROSTED_GLASS -> frostedGlass
            AppTheme.CRIMSON_COUNTDOWN -> crimsonCountdown
            AppTheme.MONOCHROME_SPOKE -> monochromeSpoke
            AppTheme.EMERALD_ARC -> emeraldArc
            AppTheme.STUDIO_PIANO -> studioPiano
            AppTheme.AUTUMN_BOKEH -> autumnBokeh
            AppTheme.ROCK_PLAYLIST -> rockPlaylist
            AppTheme.MOUNTAIN_BLUR -> mountainBlur
            AppTheme.WEEKLY_BLUE -> weeklyBlue
            AppTheme.TROPICAL_DUSK -> tropicalDusk
            AppTheme.CAMPUS_SUNSHINE -> campusSunshine
            AppTheme.SKY_BLOSSOM -> skyBlossom
            AppTheme.BOHO_PAMPAS -> bohoPampas
            AppTheme.ALPINE_LAKE -> alpineLake
            AppTheme.SYNTHWAVE_NEON -> synthwaveNeon
            AppTheme.SUNSET_OCEAN -> sunsetOcean
            AppTheme.LIQUID_CHROME -> liquidChrome
            AppTheme.ROMANTIC_DREAMS -> romanticDreams
            AppTheme.CYBERPUNK -> cyberpunk
            AppTheme.MIDNIGHT_BLUE -> midnightBlue
            AppTheme.SUNSET_WARM -> sunsetWarm
            AppTheme.EMERALD_FOREST -> emeraldForest
            AppTheme.CRIMSON_RUBY -> crimsonRuby
            AppTheme.GOLDEN_LUXURY -> goldenLuxury
            AppTheme.LIGHT_MINIMAL -> lightMinimal
            AppTheme.ROYAL_AMETHYST -> royalAmethyst
            AppTheme.AURORA_BOREALIS -> auroraBorealis
            AppTheme.CARBON_TITANIUM -> carbonTitanium
            AppTheme.ROSE_GOLD_LUXE -> roseGoldLuxe
            AppTheme.NEON_MATRIX -> neonMatrix
        }
    }
}
