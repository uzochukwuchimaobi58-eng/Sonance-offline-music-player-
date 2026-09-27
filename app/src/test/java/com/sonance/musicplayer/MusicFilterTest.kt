package com.sonance.musicplayer

import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.util.MusicFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFilterTest {

    @Test
    fun testChromeDownloadMp3_isShown() {
        val isMusic = MusicFilter.isMusicTrack(
            relativePath = "Download/",
            displayName = "NewSong.mp3",
            mimeType = "audio/mpeg",
            dataPath = "/storage/emulated/0/Download/NewSong.mp3",
            title = "NewSong",
            artist = null,
            album = null,
            durationMs = 210000L,
            sizeBytes = 8000000L
        )
        assertTrue("Download/NewSong.mp3 must be shown as legitimate music", isMusic)
    }

    @Test
    fun testChromeDownloadWav_isShown() {
        val isMusic = MusicFilter.isMusicTrack(
            relativePath = "Download/",
            displayName = "NewSong.wav",
            mimeType = "audio/wav",
            dataPath = "/storage/emulated/0/Download/NewSong.wav",
            title = "NewSong",
            artist = null,
            album = null,
            durationMs = 180000L,
            sizeBytes = 25000000L
        )
        assertTrue("Download/NewSong.wav must be shown as legitimate music", isMusic)
    }

    @Test
    fun testWhatsAppVoiceNotes_areExcluded() {
        val isMusic1 = MusicFilter.isMusicTrack(
            relativePath = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202340/",
            displayName = "PTT-20231012-WA0001.opus",
            mimeType = "audio/ogg",
            dataPath = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202340/PTT-20231012-WA0001.opus",
            durationMs = 15000L,
            sizeBytes = 25000L
        )
        assertFalse("WhatsApp Voice Notes must NOT be shown", isMusic1)

        val isMusic2 = MusicFilter.isMusicTrack(
            relativePath = "WhatsApp/Media/WhatsApp Voice Notes/202312/",
            displayName = "AUD-20231012-WA0002.m4a",
            dataPath = "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes/202312/AUD-20231012-WA0002.m4a"
        )
        assertFalse("WhatsApp Voice Notes must NOT be shown", isMusic2)
    }

    @Test
    fun testVoiceRecorder_isExcluded() {
        val isMusic = MusicFilter.isMusicTrack(
            relativePath = "Recordings/Voice Recorder/",
            displayName = "Recording_001.m4a",
            mimeType = "audio/mp4",
            dataPath = "/storage/emulated/0/Recordings/Voice Recorder/Recording_001.m4a",
            durationMs = 30000L
        )
        assertFalse("Voice Recorder recordings must NOT be shown", isMusic)
    }

    @Test
    fun testSoundRecorder_isExcluded() {
        val isMusic = MusicFilter.isMusicTrack(
            relativePath = "MIUI/sound_recorder/",
            displayName = "Record_002.mp3",
            dataPath = "/storage/emulated/0/MIUI/sound_recorder/Record_002.mp3"
        )
        assertFalse("Sound Recorder recordings must NOT be shown", isMusic)
    }

    @Test
    fun testCallRecordings_areExcluded() {
        val isMusic = MusicFilter.isMusicTrack(
            relativePath = "Call Recordings/",
            displayName = "Call_08012345678_20231010.m4a",
            dataPath = "/storage/emulated/0/Call Recordings/Call_08012345678_20231010.m4a"
        )
        assertFalse("Call recordings must NOT be shown", isMusic)
    }

    @Test
    fun testChromeDownloadWithNoMetadata_isShown() {
        val track = Track(
            id = "test-123",
            title = "Unknown Track 1",
            artist = "Unknown Artist",
            album = "Music",
            duration = 195L,
            url = "content://media/external/audio/media/999",
            folder = "/storage/emulated/0/Download",
            dateAdded = System.currentTimeMillis(),
            sourceType = "local"
        )
        assertTrue("Legitimate music downloaded through Chrome without metadata must be detected", MusicFilter.isMusicTrack(track))
    }

    @Test
    fun testSupportedMusicExtensions() {
        val exts = listOf("mp3", "wav", "m4a", "aac", "flac", "ogg", "opus")
        for (ext in exts) {
            val isMusic = MusicFilter.isMusicTrack(
                displayName = "Track.$ext",
                dataPath = "/storage/emulated/0/Music/Track.$ext",
                durationMs = 120000L,
                sizeBytes = 5000000L
            )
            assertTrue("Format .$ext must be supported", isMusic)
        }
    }
}
