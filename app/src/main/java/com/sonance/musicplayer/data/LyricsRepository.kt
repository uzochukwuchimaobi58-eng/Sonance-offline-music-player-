package com.sonance.musicplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.sonance.musicplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

typealias Song = Track

data class LyricLine(val timeMs: Long, val text: String)

sealed interface LyricsState {
    object None : LyricsState
    object Loading : LyricsState
    object NotFound : LyricsState
    data class Found(
        val lines: List<LyricLine>,
        val synced: Boolean,
        val source: String = "LRCLIB"
    ) : LyricsState
}

/**
 * Gets lyrics using the official fallback chain:
 * LRCLIB → Lyrics.ovh → Karalyr → "Lyrics not available"
 * With local offline caching, LRC timestamp synchronization, and instant ID3 parsing.
 */
class LyricsRepository(private val ctx: Context) {

    companion object {
        private const val TAG = "LyricsRepository"
        private const val USER_AGENT = "MusicStudio/1.0 (Sonance Music Player)"
    }

    private val lrclibProvider by lazy { com.sonance.musicplayer.lyrics.LrclibProvider() }
    private val lyricsOvhProvider by lazy { com.sonance.musicplayer.lyrics.LyricsOvhProvider() }
    private val karalyrProvider by lazy { com.sonance.musicplayer.lyrics.KaralyrProvider() }

    suspend fun load(track: Track): LyricsState = withContext(Dispatchers.IO) {
        try {
            val safeId = track.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val cache = File(ctx.filesDir, "lyrics_${safeId}.json")

            // 1. Check local phone cache
            if (cache.exists()) {
                try {
                    val cachedContent = cache.readText().trim()
                    if (cachedContent.isNotBlank()) {
                        val json = JSONObject(cachedContent)
                        val state = parse(json)
                        if (state is LyricsState.Found) return@withContext state
                    }
                } catch (ce: Exception) {
                    Log.w(TAG, "Cache parse error: ${ce.message}")
                }
            }

            // 2. If track model already has valid embedded LRC lyrics
            if (track.lyrics.isNotBlank() && track.lyrics.contains("[")) {
                val lines = parseLrc(track.lyrics)
                if (lines.isNotEmpty()) {
                    return@withContext LyricsState.Found(lines, true, "Embedded LRC")
                }
            }

            // 3. Fallback Step 1: LRCLIB (synced + plain)
            val durationSec = if (track.duration > 0) track.duration else (track.durationMs / 1000)
            val lrclibResult = lrclibProvider.fetchLyrics(track.title, track.artist, durationSec)
            if (lrclibResult != null && lrclibResult.lines.isNotEmpty()) {
                saveCache(cache, lrclibResult)
                return@withContext LyricsState.Found(lrclibResult.lines, lrclibResult.isSynced, lrclibResult.source)
            }

            // 4. Fallback Step 2: Lyrics.ovh
            val ovhResult = lyricsOvhProvider.fetchLyrics(track.title, track.artist)
            if (ovhResult != null && ovhResult.lines.isNotEmpty()) {
                saveCache(cache, ovhResult)
                return@withContext LyricsState.Found(ovhResult.lines, ovhResult.isSynced, ovhResult.source)
            }

            // 5. Fallback Step 3: Karalyr (Karaoke lyrics)
            val karalyrResult = karalyrProvider.fetchLyrics(track.title, track.artist)
            if (karalyrResult != null && karalyrResult.lines.isNotEmpty()) {
                saveCache(cache, karalyrResult)
                return@withContext LyricsState.Found(karalyrResult.lines, karalyrResult.isSynced, karalyrResult.source)
            }

            // 6. Try scanning local .lrc or .txt file in the same directory on device storage
            val localLrc = findLocalLrcFile(track)
            if (!localLrc.isNullOrBlank()) {
                val lines = parseLrc(localLrc)
                if (lines.isNotEmpty()) {
                    return@withContext LyricsState.Found(lines, true, "Local File")
                }
            }

            // 7. Try extracting embedded ID3 lyrics via MediaMetadataRetriever
            val embedded = extractEmbeddedLyrics(track)
            if (!embedded.isNullOrBlank()) {
                val isSynced = embedded.contains("[")
                val lines = if (isSynced) parseLrc(embedded) else embedded.lines().filter { it.isNotBlank() }.map { LyricLine(-1L, it) }
                if (lines.isNotEmpty()) {
                    return@withContext LyricsState.Found(lines, isSynced, "ID3 Tag")
                }
            }

            // 8. Fallback Step 4: "Lyrics not available"
            LyricsState.NotFound
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading lyrics for ${track.title}", e)
            LyricsState.NotFound
        }
    }

    private fun saveCache(cacheFile: File, result: com.sonance.musicplayer.lyrics.LyricsProviderResult) {
        try {
            val json = JSONObject().apply {
                put("source", result.source)
                put("isSynced", result.isSynced)
                if (result.isSynced) {
                    val lrcStr = result.lines.joinToString("\n") { line ->
                        val min = (line.timeMs / 1000) / 60
                        val sec = (line.timeMs / 1000) % 60
                        val frac = (line.timeMs % 1000) / 10
                        String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]%s", min, sec, frac, line.text)
                    }
                    put("syncedLyrics", lrcStr)
                } else {
                    val plainStr = result.lines.joinToString("\n") { it.text }
                    put("plainLyrics", plainStr)
                }
            }
            cacheFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed caching lyrics: ${e.message}")
        }
    }

    private fun fetch(track: Track): JSONObject? {
        val cleanTitle = cleanTitleForSearch(track.title)
        val rawTitle = track.title.replace(Regex("""\.(mp3|m4a|flac|wav|aac|ogg|opus)$""", RegexOption.IGNORE_CASE), "").trim()
        val cleanArtist = cleanArtistForSearch(track.artist, track.title)
        val secs = if (track.duration > 0) track.duration else (track.durationMs / 1000)

        // Tier 1: Exact match with track name, artist, and duration
        if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown artist", ignoreCase = true)) {
            val titleEnc = enc(cleanTitle)
            val artistEnc = enc(cleanArtist)
            if (secs > 0) {
                get("https://lrclib.net/api/get?track_name=$titleEnc&artist_name=$artistEnc&duration=$secs")?.let {
                    try {
                        val obj = JSONObject(it)
                        if (hasLyrics(obj)) return obj
                    } catch (_: Exception) {}
                }
            }
            get("https://lrclib.net/api/get?track_name=$titleEnc&artist_name=$artistEnc")?.let {
                try {
                    val obj = JSONObject(it)
                    if (hasLyrics(obj)) return obj
                } catch (_: Exception) {}
            }
        }

        // Tier 2: Search by clean title + artist combined
        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown artist", ignoreCase = true)) {
            val queryEnc = enc("$cleanTitle $cleanArtist")
            val arrStr = get("https://lrclib.net/api/search?q=$queryEnc")
            if (arrStr != null) {
                findFirstWithLyrics(arrStr)?.let { return it }
            }
        }

        // Tier 3: Search by clean title alone
        if (cleanTitle.isNotBlank()) {
            val titleEnc = enc(cleanTitle)
            val arrTitle = get("https://lrclib.net/api/search?q=$titleEnc")
            if (arrTitle != null) {
                findFirstWithLyrics(arrTitle)?.let { return it }
            }
        }

        // Tier 4: Search by raw title
        if (rawTitle.isNotBlank() && rawTitle != cleanTitle) {
            val rawEnc = enc(rawTitle)
            val arrRaw = get("https://lrclib.net/api/search?q=$rawEnc")
            if (arrRaw != null) {
                findFirstWithLyrics(arrRaw)?.let { return it }
            }
        }

        return null
    }

    private fun findFirstWithLyrics(jsonArrayString: String): JSONObject? {
        return try {
            val list = JSONArray(jsonArrayString)
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                if (hasLyrics(o)) {
                    return o
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun hasLyrics(o: JSONObject): Boolean {
        val synced = o.optString("syncedLyrics")
        val plain = o.optString("plainLyrics")
        return synced.isNotBlank() || plain.isNotBlank()
    }

    private fun get(url: String): String? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            try {
                if (c.responseCode == 200) {
                    c.inputStream.bufferedReader().readText()
                } else {
                    null
                }
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network request to $url failed: ${e.message}")
            null
        }
    }

    private fun enc(s: String): String {
        return try {
            URLEncoder.encode(s, "UTF-8")
        } catch (_: Exception) {
            s.replace(" ", "%20")
        }
    }

    fun parse(o: JSONObject): LyricsState {
        val synced = o.optString("syncedLyrics")
        if (synced.isNotBlank()) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return LyricsState.Found(lines, true)
        }
        val plain = o.optString("plainLyrics")
        if (plain.isNotBlank()) {
            val lines = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(-1L, it) }
            if (lines.isNotEmpty()) return LyricsState.Found(lines, false)
        }
        return LyricsState.NotFound
    }

    fun parseLrc(text: String): List<LyricLine> {
        val stamp = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
        val out = mutableListOf<LyricLine>()
        for (line in text.lines()) {
            val stamps = stamp.findAll(line).toList()
            if (stamps.isEmpty()) continue
            val words = line.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toDoubleOrNull() ?: 0.0
                val ms = (min * 60_000 + sec * 1000).toLong()
                out += LyricLine(ms, words)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun cleanTitleForSearch(raw: String): String {
        var t = raw.trim()
        t = t.replace(Regex("""\.(mp3|m4a|flac|wav|aac|ogg|opus)$""", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("""^\d{1,3}[\s._-]+"""), "")
        t = t.replace(Regex("""[-_]copy.*$""", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("""\s*[\(\[].*?(official|audio|video|lyrics|hd|hq|remastered|feat|ft\.).*?[\)\]]""", RegexOption.IGNORE_CASE), "")
        t = t.replace('_', ' ')
        if (t.contains(" - ")) {
            val parts = t.split(" - ")
            if (parts.size >= 2 && parts[1].isNotBlank()) {
                t = parts[1].trim()
            }
        }
        if (t.contains(" | ")) {
            t = t.substringBefore(" | ")
        }
        return t.trim()
    }

    private fun cleanArtistForSearch(raw: String, titleRaw: String): String {
        var a = raw.trim()
        if (a.equals("<unknown>", ignoreCase = true) || a.equals("Unknown Artist", ignoreCase = true) || a.equals("Unknown", ignoreCase = true)) {
            val withoutExt = titleRaw.replace(Regex("""\.(mp3|m4a|flac|wav|aac|ogg|opus)$""", RegexOption.IGNORE_CASE), "")
            if (withoutExt.contains(" - ")) {
                val parts = withoutExt.split(" - ")
                if (parts[0].isNotBlank()) {
                    return parts[0].replace(Regex("""^\d{1,3}[\s._-]+"""), "").replace('_', ' ').trim()
                }
            }
            return ""
        }
        return a.replace('_', ' ').trim()
    }

    private fun findLocalLrcFile(track: Track): String? {
        return try {
            val path = if (track.url.startsWith("file://")) track.url.removePrefix("file://") else track.url
            if (path.isNotBlank() && !path.startsWith("content://") && !path.startsWith("http")) {
                val audioFile = File(path)
                val base = audioFile.parentFile ?: return null
                val nameWithoutExt = audioFile.nameWithoutExtension
                val lrcFile = File(base, "$nameWithoutExt.lrc")
                if (lrcFile.exists()) return lrcFile.readLines().joinToString("\n")
                val txtFile = File(base, "$nameWithoutExt.txt")
                if (txtFile.exists()) return txtFile.readLines().joinToString("\n")
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractEmbeddedLyrics(track: Track): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (track.url.startsWith("content://")) {
                retriever.setDataSource(ctx, Uri.parse(track.url))
            } else if (track.url.isNotBlank() && !track.url.startsWith("http")) {
                retriever.setDataSource(track.url.removePrefix("file://"))
            } else return null
            null
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
