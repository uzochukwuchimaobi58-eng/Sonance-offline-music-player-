package com.sonance.musicplayer.model

import kotlinx.serialization.Serializable

@Serializable
enum class RepeatMode {
    OFF, ALL, ONE
}

@Serializable
enum class EqPreset(val displayName: String) {
    FLAT("Flat"),
    BASS_BOOST("Bass Boost"),
    VOCAL_BOOSTER("Vocal Booster"),
    ROCK("Rock"),
    ELECTRONIC("Electronic"),
    JAZZ("Jazz"),
    ACOUSTIC("Acoustic"),
    HIFI_MASTER("Hi-Fi Master"),
    CUSTOM("Custom")
}

@Serializable
enum class AppTheme(val idStr: String, val displayName: String, val isProOnly: Boolean = false) {
    // 3 Free Templates & Wallpapers
    NATURE_FLOWER("nature-flower", "Nature Bellflower Meadow", false),
    ANIMAL_PETS("animal-pets", "Meadow Puppy & Kitten", false),
    DARK_AMOLED("dark-amoled", "Classic Player (AMOLED)", false),

    // Template Themes (Locked for Pro or Watch Ad)
    DARK_SLATE("dark-slate", "Modern Slate & Cyan", true),
    FROSTED_GLASS("frosted-glass", "Frosted Glass Studio", true),
    CRIMSON_COUNTDOWN("crimson-countdown", "Red Gradient & Vinyl Retro", true),
    MONOCHROME_SPOKE("monochrome-spoke", "Monochrome Spoke Turntable", true),
    EMERALD_ARC("emerald-arc", "Emerald Arc Hi-Fi", true),
    STUDIO_PIANO("studio-piano", "Studio Piano Acoustic", true),
    AUTUMN_BOKEH("autumn-bokeh", "Golden Autumn Vibes", true),
    ROCK_PLAYLIST("rock-playlist", "Rock Festival Stage", true),
    MOUNTAIN_BLUR("mountain-blur", "Mountain Serenity Lo-Fi", true),
    WEEKLY_BLUE("weekly-blue", "Weekly Blue Top Hits", true),
    TROPICAL_DUSK("tropical-dusk", "Tropical Sunset Chill", true),
    CAMPUS_SUNSHINE("campus-sunshine", "Sunshine Pop Beats", true),
    SKY_BLOSSOM("sky-blossom", "Pink Minimalist Pastel", true),
    BOHO_PAMPAS("boho-pampas", "Boho Acoustic Warmth", true),
    ALPINE_LAKE("alpine-lake", "Alpine Vista Soundscape", true),
    SYNTHWAVE_NEON("synthwave-neon", "Synthwave Neon Visualizer", true),
    SUNSET_OCEAN("sunset-ocean", "Sunset Ocean Synth & Chill", true),
    LIQUID_CHROME("liquid-chrome", "Holographic Liquid Chrome", true),
    ROMANTIC_DREAMS("romantic-dreams", "Beige Minimalist & Warm Boho", true),

    // Classic & VIP Themes
    CYBERPUNK("cyberpunk", "Cyberpunk Neon & Violet", true),
    MIDNIGHT_BLUE("midnight-blue", "Midnight Sapphire Ocean", true),
    SUNSET_WARM("sunset-warm", "Warm Sunset & Amber", true),
    EMERALD_FOREST("emerald-forest", "Deep Emerald Forest", true),
    CRIMSON_RUBY("crimson-ruby", "Velvet Crimson Ruby", true),
    GOLDEN_LUXURY("golden-luxury", "Golden Royalty & Onyx", true),
    LIGHT_MINIMAL("light-minimal", "Clean Studio Minimal", true),
    ROYAL_AMETHYST("royal-amethyst", "VIP Royal Velvet Amethyst", true),
    AURORA_BOREALIS("aurora-borealis", "VIP Nordic Aurora Glow", true),
    CARBON_TITANIUM("carbon-titanium", "VIP Stealth Carbon Titanium", true),
    ROSE_GOLD_LUXE("rose-gold-luxe", "VIP Rose Gold Champagne", true),
    NEON_MATRIX("neon-matrix", "VIP Neon Green Matrix", true)
}

@Serializable
enum class ActiveView {
    HOME,
    LIBRARY,
    FOLDER,
    FAVORITE,
    RECENT_PLAY,
    RECENT_ADD,
    MOST_PLAY,
    PLAYLIST_DETAIL,
    DRIVE_MODE,
    LYRICS_MODE
}

@Serializable
enum class TrendingAudioEffect(val label: String) {
    OFF("Normal"),
    BASS_BOOST("Super Bass"),
    SLOWED_REVERB("Slowed & Reverb"),
    NIGHTCORE("Nightcore (+Speed)"),
    HIFI_STUDIO("Lossless Studio")
}

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Music",
    val duration: Long = 0L, // in seconds
    val url: String = "",
    val coverArt: String = "",
    val folder: String = "Phone Storage",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val dateAdded: Long = 1672531200000L, // Jan 1, 2023 - built-in tracks default to past timestamp
    val lastPlayed: Long = 0L,
    val isOffline: Boolean = true,
    val lyrics: String = "",
    val bitrate: String = "320 kbps",
    val sampleRate: String = "44.1 kHz",
    val fileSize: String = "8.0 MB",
    val sourceType: String = "built-in"
) {
    val addedDate: Long get() = dateAdded
    val contentUri: String get() = url
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val color: String = "#38bdf8",
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false
)

@Serializable
data class EqualizerSettings(
    val enabled: Boolean = true,
    val preset: EqPreset = EqPreset.FLAT,
    val bands: Map<Int, Int> = mapOf(
        60 to 0,
        170 to 0,
        310 to 0,
        600 to 0,
        1000 to 0,
        3000 to 0,
        6000 to 0,
        12000 to 0,
        14000 to 0,
        16000 to 0
    ),
    val bassBoost: Int = 0, // 0..100
    val spatialReverb: Int = 0, // 0..100
    val trebleBoost: Int = 0 // 0..100
)

@Serializable
data class PlayerSettings(
    val use10BandsEqualizer: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val showDirectories: Boolean = true,
    val keepScreenOn: Boolean = false,
    val forwardAndBackward: Boolean = true,
    val gaplessPlayback: Boolean = true,
    val accentColor: String = "emerald",

    // General
    val queueAfterSearching: String = "Search results",
    val showShuffleButton: Boolean = true,
    val libraryTabOrder: List<String> = listOf("Tracks", "Artists", "Albums", "Genres"),

    // Lyrics
    val desktopLyrics: Boolean = false,
    val carBluetoothLyrics: Boolean = true,
    val statusBarLyrics: String = "Off",

    // Audio
    val shakeToPlayNext: Boolean = false,
    val swipeToChangeSongs: Boolean = true,
    val allowOthersPlaying: Boolean = false,
    val playPauseFade: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val openNowPlayingOnPlay: Boolean = false,
    val replayTheSong: Boolean = false,

    // ReplayGain
    val replayGainMode: String = "None",
    val replayGainPreamp: String = "0 dB",

    // Playlist
    val clickTracksAddToCurrentQueue: Boolean = false,
    val addMusicToPlaylistPosition: String = "Top",
    val smartPlaylistTrackLimit: String = "Past 6 months",

    // Notification
    val useNotificationBar: Boolean = true,
    val classicNotificationDesign: Boolean = false,
    val colorNotification: Boolean = false,

    // Lockscreen
    val lockScreenPlaying: Boolean = false,
    val lockScreenTimeFormat: String = "Auto",
    val lockScreenBackground: String = "Theme",

    // Headset
    val headsetPlayWhenInserted: Boolean = false,
    val headsetPauseWhenUnplugged: Boolean = true,
    val bluetoothAutostart: Boolean = false,

    // Others
    val useEnglishLanguage: Boolean = true,
    val hideUpdateReminder: Boolean = false
)
