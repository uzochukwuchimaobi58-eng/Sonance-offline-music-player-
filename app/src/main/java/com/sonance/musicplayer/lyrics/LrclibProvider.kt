package com.sonance.musicplayer.lyrics

import android.util.Log
import com.sonance.musicplayer.data.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

interface LrclibApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") duration: Long? = null
    ): String

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): String
}

class LrclibProvider {

    companion object {
        private const val TAG = "LrclibProvider"
        private const val BASE_URL = "https://lrclib.net/"
    }

    private val api: LrclibApi by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(LrclibApi::class.java)
    }

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationSec: Long? = null
    ): LyricsProviderResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanForSearch(title)
        val cleanArtist = cleanArtistForSearch(artist)

        // Tier 1: Exact lookup with track name and artist
        try {
            if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown artist", ignoreCase = true)) {
                val rawJson = api.getLyrics(
                    trackName = cleanTitle,
                    artistName = cleanArtist,
                    duration = if (durationSec != null && durationSec > 0) durationSec else null
                )
                val parsed = parseLyricsJson(rawJson)
                if (parsed != null) return@withContext parsed
            }
        } catch (e: Exception) {
            Log.d(TAG, "Tier 1 lookup failed: ${e.message}")
        }

        // Tier 2: Search with combined query "title artist"
        try {
            val query = if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown artist", ignoreCase = true)) {
                "$cleanTitle $cleanArtist"
            } else {
                cleanTitle
            }
            val searchJson = api.searchLyrics(query)
            val parsedSearch = parseSearchArray(searchJson)
            if (parsedSearch != null) return@withContext parsedSearch
        } catch (e: Exception) {
            Log.d(TAG, "Tier 2 search failed: ${e.message}")
        }

        // Tier 3: Search with clean title only
        try {
            if (cleanTitle.isNotBlank()) {
                val searchJson = api.searchLyrics(cleanTitle)
                val parsedSearch = parseSearchArray(searchJson)
                if (parsedSearch != null) return@withContext parsedSearch
            }
        } catch (e: Exception) {
            Log.d(TAG, "Tier 3 search failed: ${e.message}")
        }

        null
    }

    private fun parseLyricsJson(jsonStr: String): LyricsProviderResult? {
        return try {
            val obj = JSONObject(jsonStr)
            val synced = obj.optString("syncedLyrics").trim()
            if (synced.isNotBlank()) {
                val lines = parseLrc(synced)
                if (lines.isNotEmpty()) {
                    return LyricsProviderResult(lines = lines, isSynced = true, source = "LRCLIB")
                }
            }

            val plain = obj.optString("plainLyrics").trim()
            if (plain.isNotBlank()) {
                val lines = plain.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { LyricLine(-1L, it) }
                if (lines.isNotEmpty()) {
                    return LyricsProviderResult(lines = lines, isSynced = false, source = "LRCLIB")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing LRCLIB response", e)
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
                        return LyricsProviderResult(lines = lines, isSynced = true, source = "LRCLIB")
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
                            plainFallback = LyricsProviderResult(lines = lines, isSynced = false, source = "LRCLIB")
                        }
                    }
                }
            }
            plainFallback
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing LRCLIB search array", e)
            null
        }
    }

    private fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")

        for (rawLine in lrcContent.lines()) {
            val trimmed = rawLine.trim()
            val match = regex.find(trimmed)
            if (match != null) {
                val (minStr, secStr, fracStr, text) = match.destructured
                val min = minStr.toLongOrNull() ?: 0L
                val sec = secStr.toLongOrNull() ?: 0L
                val frac = if (fracStr.isNotEmpty()) {
                    val padded = fracStr.padEnd(3, '0').take(3)
                    padded.toLongOrNull() ?: 0L
                } else 0L

                val timeMs = min * 60_000L + sec * 1_000L + frac
                val cleanText = text.trim()
                if (cleanText.isNotBlank()) {
                    lines.add(LyricLine(timeMs, cleanText))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun cleanForSearch(raw: String): String {
        return raw.replace(Regex("""\.(mp3|m4a|flac|wav|aac|ogg|opus)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[.*?\]|\(.*?\)"""), "")
            .replace(Regex("""(official\s+video|official\s+audio|lyrics|lyric\s+video|remastered|remaster|hd|4k)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun cleanArtistForSearch(raw: String): String {
        return raw.replace(Regex("""(feat\.|ft\.|featuring).*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
