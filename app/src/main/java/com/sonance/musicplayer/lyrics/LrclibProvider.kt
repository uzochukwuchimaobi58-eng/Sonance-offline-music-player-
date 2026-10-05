package com.sonance.musicplayer.lyrics

import android.util.Log
import com.sonance.musicplayer.data.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LrclibProvider {

    companion object {
        private const val TAG = "LrclibProvider"
        private const val BASE_URL = "https://lrclib.net/"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "SonanceMusicPlayer/2.1 (Android; Background Lyrics Fetcher)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationSec: Long? = null
    ): LyricsProviderResult? = withContext(Dispatchers.IO) {
        val (cleanTitle, cleanArtist) = parseArtistAndTitle(title, artist)
        val rawTitle = cleanForSearch(title)

        // Tier 1: Exact lookup without duration (much more lenient and reliable)
        if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown artist", ignoreCase = true)) {
            val urlNoDur = "${BASE_URL}api/get?track_name=${enc(cleanTitle)}&artist_name=${enc(cleanArtist)}"
            fetchAndParseJson(urlNoDur)?.let { return@withContext it }

            // Tier 1b: Exact lookup with duration if duration was available
            if (durationSec != null && durationSec > 0) {
                val urlWithDur = "${BASE_URL}api/get?track_name=${enc(cleanTitle)}&artist_name=${enc(cleanArtist)}&duration=$durationSec"
                fetchAndParseJson(urlWithDur)?.let { return@withContext it }
            }
        }

        // Tier 2: Search with combined query "Artist Title" or "Title Artist"
        if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown artist", ignoreCase = true)) {
            val query = "$cleanArtist $cleanTitle"
            val searchUrl = "${BASE_URL}api/search?q=${enc(query)}"
            fetchAndParseSearch(searchUrl)?.let { return@withContext it }
        }

        // Tier 3: Search with clean title only
        if (cleanTitle.isNotBlank()) {
            val searchUrl = "${BASE_URL}api/search?q=${enc(cleanTitle)}"
            fetchAndParseSearch(searchUrl)?.let { return@withContext it }
        }

        // Tier 4: Search with raw title without extension
        if (rawTitle.isNotBlank() && !rawTitle.equals(cleanTitle, ignoreCase = true)) {
            val searchUrl = "${BASE_URL}api/search?q=${enc(rawTitle)}"
            fetchAndParseSearch(searchUrl)?.let { return@withContext it }
        }

        null
    }

    private fun fetchAndParseJson(url: String): LyricsProviderResult? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseLyricsJson(body)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Request to $url failed: ${e.message}")
            null
        }
    }

    private fun fetchAndParseSearch(url: String): LyricsProviderResult? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseSearchArray(body)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Search request to $url failed: ${e.message}")
            null
        }
    }

    private fun parseLyricsJson(jsonStr: String): LyricsProviderResult? {
        return try {
            val obj = JSONObject(jsonStr)
            val synced = obj.optString("syncedLyrics").trim()
            if (synced.isNotBlank()) {
                val lines = parseLrc(synced)
                if (lines.isNotEmpty()) {
                    return LyricsProviderResult(lines = lines, isSynced = true, source = "Online")
                }
            }

            val plain = obj.optString("plainLyrics").trim()
            if (plain.isNotBlank()) {
                val lines = plain.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { LyricLine(-1L, it) }
                if (lines.isNotEmpty()) {
                    return LyricsProviderResult(lines = lines, isSynced = false, source = "Online")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing lyrics JSON", e)
            null
        }
    }

    private fun parseSearchArray(jsonStr: String): LyricsProviderResult? {
        return try {
            val arr = JSONArray(jsonStr)
            var plainFallback: LyricsProviderResult? = null

            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val synced = item.optString("syncedLyrics").trim()
                if (synced.isNotBlank()) {
                    val lines = parseLrc(synced)
                    if (lines.isNotEmpty()) {
                        return LyricsProviderResult(lines = lines, isSynced = true, source = "Online")
                    }
                }

                if (plainFallback == null) {
                    val plain = item.optString("plainLyrics").trim()
                    if (plain.isNotBlank()) {
                        val lines = plain.lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { LyricLine(-1L, it) }
                        if (lines.isNotEmpty()) {
                            plainFallback = LyricsProviderResult(lines = lines, isSynced = false, source = "Online")
                        }
                    }
                }
            }
            plainFallback
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search array", e)
            null
        }
    }

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val stampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        for (rawLine in lrcContent.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue
            val matches = stampRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            val text = trimmed.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val (minStr, secStr, fracStr) = m.destructured
                val min = minStr.toLongOrNull() ?: 0L
                val sec = secStr.toLongOrNull() ?: 0L
                val frac = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                    3 -> fracStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timeMs = min * 60_000L + sec * 1_000L + frac
                lines.add(LyricLine(timeMs, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun parseArtistAndTitle(rawTitle: String, rawArtist: String): Pair<String, String> {
        var cleanArtist = cleanArtistForSearch(rawArtist)
        var cleanTitle = cleanForSearch(rawTitle)

        if ((cleanArtist.isBlank() || cleanArtist.equals("Unknown artist", ignoreCase = true) || cleanArtist.equals("<unknown>", ignoreCase = true)) && cleanTitle.contains(" - ")) {
            val parts = cleanTitle.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                cleanArtist = cleanArtistForSearch(parts[0])
                cleanTitle = cleanForSearch(parts[1])
            }
        }
        return Pair(cleanTitle, cleanArtist)
    }

    private fun cleanForSearch(raw: String): String {
        return raw.replace(Regex("""\.(mp3|m4a|flac|wav|aac|ogg|opus)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^\d{1,3}[\s._-]+"""), "") // strip track numbers "01. "
            .replace(Regex("""\[.*?\]|\(.*?\)"""), "")
            .replace(Regex("""(official\s+video|official\s+audio|lyrics|lyric\s+video|remastered|remaster|hd|4k)""", RegexOption.IGNORE_CASE), "")
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun cleanArtistForSearch(raw: String): String {
        return raw.replace(Regex("""(feat\.|ft\.|featuring).*""", RegexOption.IGNORE_CASE), "")
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun enc(s: String): String {
        return try {
            URLEncoder.encode(s, "UTF-8")
        } catch (_: Exception) {
            s.replace(" ", "%20")
        }
    }
}
