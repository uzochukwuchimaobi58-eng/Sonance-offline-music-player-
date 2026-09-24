package com.sonance.musicplayer.data

import com.sonance.musicplayer.model.Playlist
import com.sonance.musicplayer.model.Track

object DefaultTracks {
    val initialPlaylists = listOf(
        Playlist(
            id = "playlist-default",
            name = "Default list",
            color = "#38bdf8",
            trackIds = emptyList(),
            createdAt = System.currentTimeMillis(),
            isDefault = true
        ),
        Playlist(
            id = "playlist-workout",
            name = "Gym & High Energy",
            color = "#f97316",
            trackIds = emptyList(),
            createdAt = System.currentTimeMillis()
        ),
        Playlist(
            id = "playlist-chill",
            name = "Deep Focus & Study",
            color = "#10b981",
            trackIds = emptyList(),
            createdAt = System.currentTimeMillis()
        )
    )

    // No dummy/sample tracks - app discovers and shows 100% real user music from device storage
    val initialTracks = emptyList<Track>()
}
