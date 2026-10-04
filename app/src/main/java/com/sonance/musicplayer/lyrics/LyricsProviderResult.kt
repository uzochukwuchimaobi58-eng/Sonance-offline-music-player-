package com.sonance.musicplayer.lyrics

import com.sonance.musicplayer.data.LyricLine

data class LyricsProviderResult(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val source: String
)
