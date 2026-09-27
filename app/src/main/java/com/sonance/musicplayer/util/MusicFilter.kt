package com.sonance.musicplayer.util

import com.sonance.musicplayer.model.Track
import java.io.File
import java.util.Locale

/**
 * Central audio and music track filtering utility.
 *
 * Enforces legitimate music discovery while filtering out:
 * - WhatsApp voice notes and voice recordings
 * - Device Voice Recorder recordings
 * - Sound Recorder recordings
 * - Phone Call recordings
 * - Non-music system sounds, ringtones, notifications, and app caches
 *
 * Allows legitimate music formats:
 * .mp3, .wav, .m4a, .aac, .flac, .ogg, .opus
 * Even when downloaded through Chrome without artist or album metadata.
 */
object MusicFilter {

    /**
     * Supported legitimate music extensions as specified:
     * .mp3, .wav, .m4a, .aac, .flac, .ogg, .opus
     */
    val SUPPORTED_EXTENSIONS = setOf(
        "mp3",
        "wav",
        "m4a",
        "aac",
        "flac",
        "ogg",
        "opus"
    )

    val SUPPORTED_MIME_TYPES = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/mp4",
        "audio/m4a",
        "audio/x-m4a",
        "audio/aac",
        "audio/x-aac",
        "audio/flac",
        "audio/x-flac",
        "audio/ogg",
        "audio/opus",
        "audio/x-ogg",
        "application/ogg",
        "application/x-ogg"
    )

    /**
     * Obvious non-music recording folders and keywords:
     * - WhatsApp Voice Notes
     * - WhatsApp voice recordings
     * - Voice Recorder recordings
     * - Sound Recorder recordings
     * - Call recordings
     * - Other non-music recording folders
     */
    private val EXCLUDED_FOLDER_KEYWORDS = listOf(
        "whatsapp voice notes",
        "whatsapp voice",
        "whatsapp audio",
        "whatsapp",
        "com.whatsapp",
        "voice recorder",
        "voicerecorder",
        "sound recorder",
        "sound_recorder",
        "soundrecorder",
        "call recording",
        "call_recording",
        "call recordings",
        "call_recordings",
        "callrecorder",
        "call record",
        "call_rec",
        "voice memos",
        "voicememo",
        "voicememos",
        "voice notes",
        "voicenote",
        "voicenotes",
        "audiorecorder",
        "audio recorder",
        "phone recorder",
        "smart recorder",
        "easy voice recorder",
        "recordings/call",
        "recordings/voice",
        "miui/sound_recorder"
    )

    /**
     * Non-music system sound folders (ringtones, notifications, alarms, internal app caches).
     */
    private val SYSTEM_SOUND_FOLDERS = listOf(
        "ringtones",
        "notifications",
        "alarms",
        ".cache",
        ".thumbnails",
        "/android/data",
        "/android/media/com.whatsapp",
        "/android/media/org.telegram.messenger",
        "/telegram/telegram audio"
    )

    /**
     * Core central filter method to determine whether an audio file is a legitimate music track.
     *
     * @param relativePath Relative storage path from MediaStore (e.g. "Download/", "Music/")
     * @param displayName Filename from MediaStore or File (e.g. "NewSong.mp3", "PTT-20231012-WA0001.opus")
     * @param mimeType Audio MIME type (e.g. "audio/mpeg")
     * @param isMusic Value of MediaStore.Audio.Media.IS_MUSIC (1 if flagged as music)
     * @param dataPath Absolute filesystem path (e.g. "/storage/emulated/0/Download/NewSong.mp3")
     * @param title Track title (optional)
     * @param artist Artist name (optional, may be null or unknown)
     * @param album Album name (optional, may be null or unknown)
     * @param durationMs Duration in milliseconds (optional, 0 if not yet extracted)
     * @param sizeBytes File size in bytes (optional)
     * @return true if the audio is a legitimate music track, false otherwise
     */
    fun isMusicTrack(
        relativePath: String? = null,
        displayName: String? = null,
        mimeType: String? = null,
        isMusic: Int? = null,
        dataPath: String? = null,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        durationMs: Long = 0L,
        sizeBytes: Long = 0L
    ): Boolean {
        val relPathNorm = (relativePath ?: "").lowercase(Locale.ROOT)
        val dataPathNorm = (dataPath ?: "").lowercase(Locale.ROOT)
        val nameNorm = (displayName ?: File(dataPath ?: "").name).lowercase(Locale.ROOT)
        val titleNorm = (title ?: "").lowercase(Locale.ROOT)

        // 1. Folder checks: Exclude WhatsApp, Voice Recorder, Call recordings, etc.
        for (keyword in EXCLUDED_FOLDER_KEYWORDS) {
            if (relPathNorm.contains(keyword) || dataPathNorm.contains(keyword)) {
                return false
            }
        }

        // Check if path is standalone system /Recordings/ directory
        if (relPathNorm.startsWith("recordings/") ||
            relPathNorm == "recordings" ||
            dataPathNorm.contains("/recordings/") ||
            dataPathNorm.endsWith("/recordings")
        ) {
            return false
        }

        // Check system sound folders (ringtones, notifications, alarms)
        for (sysFolder in SYSTEM_SOUND_FOLDERS) {
            if (relPathNorm.contains(sysFolder) || dataPathNorm.contains(sysFolder)) {
                return false
            }
        }

        // 2. Filename checks: Exclude WhatsApp voice note and call recording patterns
        // e.g. PTT-20231012-WA0001.opus, AUD-20231012-WA0001.m4a
        if (nameNorm.startsWith("ptt-") || titleNorm.startsWith("ptt-")) {
            return false
        }
        if ((nameNorm.startsWith("aud-") || titleNorm.startsWith("aud-")) &&
            (nameNorm.contains("-wa") || nameNorm.contains("wa0") || dataPathNorm.contains("whatsapp"))
        ) {
            return false
        }

        // Call recording file prefixes: Call@..., Call_..., call_recording_..., Call-1...
        if (nameNorm.startsWith("call@") ||
            nameNorm.startsWith("call_") ||
            nameNorm.startsWith("call-") ||
            nameNorm.startsWith("callrecording")
        ) {
            return false
        }

        // Voice recorder file prefixes if in a recording or generic folder:
        // Recording_..., Record_..., Voice_..., Sound_..., New Recording...
        if (nameNorm.startsWith("recording_") ||
            nameNorm.startsWith("record_") ||
            nameNorm.startsWith("voice_") ||
            nameNorm.startsWith("rec_") ||
            nameNorm.startsWith("new recording")
        ) {
            return false
        }

        // 3. Supported audio format check
        val extension = when {
            nameNorm.contains(".") -> nameNorm.substringAfterLast(".", "")
            dataPathNorm.contains(".") -> dataPathNorm.substringAfterLast(".", "")
            else -> ""
        }

        val hasValidExtension = extension in SUPPORTED_EXTENSIONS
        val mimeNorm = (mimeType ?: "").lowercase(Locale.ROOT)
        val hasValidMime = mimeNorm in SUPPORTED_MIME_TYPES || mimeNorm.startsWith("audio/")

        if (!hasValidExtension && !hasValidMime) {
            return false
        }

        // 4. Duration and Size checks
        // If duration is known and is under 3 seconds, it's typically a UI click sound / notification
        if (durationMs in 1..2999) {
            return false
        }

        // If file size is tiny (under 10KB), it's likely corrupt or an icon sound effect
        if (sizeBytes in 1..10240) {
            return false
        }

        // Legitimate music file (even without artist or album metadata) is approved
        return true
    }

    /**
     * Central isMusicTrack filter for Track model objects.
     * Used across Main Library, Recently Added, Search, Albums, and Artists.
     */
    fun isMusicTrack(track: Track): Boolean {
        // Built-in sample tracks are always valid music
        if (track.sourceType == "built-in") return true

        val folderNorm = track.folder.lowercase(Locale.ROOT)
        val titleNorm = track.title.lowercase(Locale.ROOT)
        val urlNorm = track.url.lowercase(Locale.ROOT)

        // Folder check
        for (keyword in EXCLUDED_FOLDER_KEYWORDS) {
            if (folderNorm.contains(keyword) || urlNorm.contains(keyword)) {
                return false
            }
        }
        if (folderNorm.startsWith("recordings/") ||
            folderNorm == "recordings" ||
            folderNorm.endsWith("/recordings") ||
            folderNorm.contains("/recordings/")
        ) {
            return false
        }
        for (sysFolder in SYSTEM_SOUND_FOLDERS) {
            if (folderNorm.contains(sysFolder) || urlNorm.contains(sysFolder)) {
                return false
            }
        }

        // Filename / title prefixes
        if (titleNorm.startsWith("ptt-") ||
            ((titleNorm.startsWith("aud-") || urlNorm.contains("/aud-")) && (titleNorm.contains("-wa") || urlNorm.contains("-wa"))) ||
            titleNorm.startsWith("call@") ||
            titleNorm.startsWith("call_") ||
            titleNorm.startsWith("call-") ||
            titleNorm.startsWith("callrecording") ||
            titleNorm.startsWith("recording_") ||
            titleNorm.startsWith("record_") ||
            titleNorm.startsWith("voice_") ||
            titleNorm.startsWith("rec_") ||
            titleNorm.startsWith("new recording")
        ) {
            return false
        }

        // If duration is known and under 3s
        if (track.duration in 1..2) {
            return false
        }

        return true
    }
}
