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

    val initialTracks = listOf(
        Track(
            id = "track-achikoro",
            title = "Achikoro | mp3dynasty.com",
            artist = "Zoro Ft Phyno | mp3dynasty.com",
            album = "Afrobeats & Highlife",
            duration = 218L,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            coverArt = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=80",
            folder = "/storage/emulated/0/Download",
            bitrate = "320 kbps (Hi-Fi)",
            sampleRate = "44.1 kHz",
            fileSize = "6.8 MB",
            sourceType = "built-in"
        ),
        Track(
            id = "track-beautiful-onyinye",
            title = "Beautiful Onyinye (Remix) || TrendyBeatz.com",
            artist = "P Sqaure (Ft. Rick Ross)",
            album = "The Invasion",
            duration = 245L,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            coverArt = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&auto=format&fit=crop&q=80",
            folder = "/storage/emulated/0/Download",
            bitrate = "320 kbps",
            sampleRate = "44.1 kHz",
            fileSize = "7.4 MB",
            sourceType = "built-in"
        ),
        Track(
            id = "track-cultural-praise",
            title = "Cultural Praise ft. Okwesili Ezeukwu",
            artist = "Kcee",
            album = "Cultural Praise EP",
            duration = 312L,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            coverArt = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&auto=format&fit=crop&q=80",
            folder = "/storage/emulated/0/Music",
            bitrate = "Lossless FLAC",
            sampleRate = "48.0 kHz",
            fileSize = "11.2 MB",
            sourceType = "built-in"
        ),
        Track(
            id = "track-enemy-solo",
            title = "Enemy Solo",
            artist = "Awilo Longomba | PraiseZion.com",
            album = "Makossa Hits",
            duration = 230L,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            coverArt = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&auto=format&fit=crop&q=80",
            folder = "/storage/emulated/0/Download",
            bitrate = "320 kbps",
            sampleRate = "44.1 kHz",
            fileSize = "6.9 MB",
            sourceType = "built-in"
        ),
        Track(
            id = "track-fvck-you",
            title = "Fvck You via www.abegmusic.com",
            artist = "Kizz Daniel | www.abegmusic.com",
            album = "Singles Collection",
            duration = 195L,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            coverArt = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&auto=format&fit=crop&q=80",
            folder = "/storage/emulated/0/Music",
            bitrate = "320 kbps",
            sampleRate = "44.1 kHz",
            fileSize = "5.8 MB",
            sourceType = "built-in"
        ),
        Track(
            id = "phone-track-1",
            title = "TION SICKNESS (Remix)",
            artist = "ODUMODUBLVCK ft. Fireboy",
            album = "EZIOKWU (Uncut)",
            duration = 372L,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            coverArt = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&auto=format&fit=crop&q=80",
            folder = "/storage/emulated/0/Music",
            bitrate = "320 kbps (Hi-Fi)",
            sampleRate = "48.0 kHz",
            fileSize = "8.9 MB",
            sourceType = "built-in",
            lyrics = """[00:10.00] Yeah, we pulling up on high frequency
[00:18.00] Bass vibrating through the whole city
[00:25.00] No distraction when the beat hits right
[00:32.00] We taking over through the neon night
[00:40.00] Eziokwu, tell 'em nothing but the truth
[00:52.00] From the studio straight down to the booth
[01:05.00] Heavy drum kicks, feel the sub-bass roll
[01:20.00] Music that takes full control of your soul
[01:40.00] Keep the rhythm running offline or live
[02:00.00] High fidelity vibes, feel the drive"""
        ),
        Track(
            id = "phone-track-2",
            title = "Midnight City Lights",
            artist = "Aetheria Wave",
            album = "Synthetic Dreams",
            duration = 425L,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            coverArt = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&auto=format&fit=crop&q=80",
            folder = "/storage/emulated/0/Download",
            bitrate = "Lossless FLAC 24-bit",
            sampleRate = "96.0 kHz",
            fileSize = "10.2 MB",
            sourceType = "built-in",
            lyrics = """[00:08.00] Neon reflections on wet pavement
[00:22.00] Echoes of analog synthesizers in the dark
[00:36.00] Running down the endless boulevard
[00:50.00] Glowing circuits pulse within the heart
[01:15.00] Electric highway taking us away
[01:35.00] Chasing the horizon till the break of day"""
        ),
        Track(
            id = "phone-track-3",
            title = "Warm Velvet Chill",
            artist = "Komorebi Sound",
            album = "Coffee & Rain",
            duration = 344L,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            coverArt = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&auto=format&fit=crop&q=80",
            folder = "/storage/emulated/0/Music",
            bitrate = "320 kbps",
            sampleRate = "44.1 kHz",
            fileSize = "8.3 MB",
            sourceType = "built-in",
            lyrics = """[00:15.00] Raindrops tapping gently against the glass
[00:30.00] Warm cup in hand watching shadows pass
[00:55.00] Lo-fi beats winding down the slow afternoon
[01:20.00] Calming melodies beneath a cloudy moon"""
        ),
        Track(
            id = "phone-track-4",
            title = "Ethereal Horizons (Piano & Cello)",
            artist = "Julian Vance & Orchestra",
            album = "Acoustic Sanctuary",
            duration = 302L,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            coverArt = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500&auto=format&fit=crop&q=80",
            folder = "/storage/emulated/0/Podcasts",
            bitrate = "Lossless FLAC",
            sampleRate = "96.0 kHz",
            fileSize = "7.3 MB",
            sourceType = "built-in",
            lyrics = """[00:20.00] Instrumental piece with deep emotional resonance
[01:00.00] Strings swell into grand harmonies
[01:45.00] Solo piano keys resonating peacefully
[02:10.00] Gentle cello fading into silence"""
        ),
        Track(
            id = "phone-track-5",
            title = "Cyber Bass Ignition",
            artist = "Sub Zero X",
            album = "Overdrive Protocol",
            duration = 353L,
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            coverArt = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=80",
            folder = "/storage/emulated/0/Download",
            bitrate = "320 kbps (Hi-Fi)",
            sampleRate = "48.0 kHz",
            fileSize = "8.5 MB",
            sourceType = "built-in",
            lyrics = """[00:12.00] Drop the frequency to 30 Hertz
[00:28.00] Feel the subwoofers engage
[00:45.00] Maximum power output initiated
[01:10.00] Pure electronic energy in sync"""
        )
    )
}
