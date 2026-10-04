package com.sonance.musicplayer.lyrics

import android.util.Log
import com.sonance.musicplayer.data.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface KaralyrApi {
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String
    ): String

    @GET("api/lyrics")
    suspend fun getLyrics(
        @Query("title") title: String,
        @Query("artist") artist: String
    ): String
}

class KaralyrProvider {

    companion object {
        private const val TAG = "KaralyrProvider"
        private const val BASE_URL = "https://karalyr.com/"
    }

    private val api: KaralyrApi by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(KaralyrApi::class.java)
    }

    suspend fun fetchLyrics(
        title: String,
        artist: String
    ): LyricsProviderResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTitle(title)
        val cleanArtist = cleanArtist(artist)

        if (cleanTitle.isBlank()) return@withContext null

        // Try direct lyrics endpoint
        try {
            val response = api.getLyrics(cleanTitle, cleanArtist)
            val parsed = parseKaralyrResponse(response)
            if (parsed != null) return@withContext parsed
        } catch (e: Exception) {
            Log.d(TAG, "Karalyr direct lookup failed: ${e.message}")
        }

        // Try search endpoint
        try {
            val query = if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown artist", ignoreCase = true)) {
                "$cleanTitle $cleanArtist"
            } else {
                cleanTitle
            }
            val searchResponse = api.search(query)
            val parsed = parseKaralyrSearchResponse(searchResponse)
            if (parsed != null) return@withContext parsed
        } catch (e: Exception) {
            Log.d(TAG, "Karalyr search failed: ${e.message}")
        }

        null
    }

    private fun parseKaralyrResponse(jsonStr: String): LyricsProviderResult? {
        return try {
            val obj = JSONObject(jsonStr)
            val timedLyrics = obj.optString("timedLyrics").ifBlank { obj.optString("lrc") }
            if (timedLyrics.isNotBlank()) {
                val lines = parseLrc(timedLyrics)
                if (lines.isNotEmpty()) {
                    return LyricsProviderResult(lines, isSynced = true, source = "Karalyr")
                }
            }

            val plainLyrics = obj.optString("lyrics").ifBlank { obj.optString("plainLyrics") }
            if (plainLyrics.isNotBlank()) {
                val lines = plainLyrics.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { LyricLine(-1L, it) }
                if (lines.isNotEmpty()) {
                    return LyricsProviderResult(lines, isSynced = false, source = "Karalyr")
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseKaralyrSearchResponse(jsonStr: String): LyricsProviderResult? {
        return try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val timed = item.optString("timedLyrics").ifBlank { item.optString("lrc") }
                if (timed.isNotBlank()) {
                    val lines = parseLrc(timed)
                    if (lines.isNotEmpty()) {
                        return LyricsProviderResult(lines, isSynced = true, source = "Karalyr")
                    }
                }
                val plain = item.optString("lyrics").ifBlank { item.optString("plainLyrics") }
                if (plain.isNotBlank()) {
                    val lines = plain.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { LyricLine(-1L, it) }
                    if (lines.isNotEmpty()) {
                        return LyricsProviderResult(lines, isSynced = false, source = "Karalyr")
                    }
                }
            }
            null
        } catch (e: Exception) {
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

    private fun cleanTitle(raw: String): String {
        return raw.replace(Regex("""\.(mp3|m4a|flac|wav|aac|ogg|opus)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[.*?\]|\(.*?\)"""), "")
            .replace(Regex("""(official\s+video|official\s+audio|lyrics|lyric\s+video|remastered|remaster|hd|4k)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun cleanArtist(raw: String): String {
        return raw.replace(Regex("""(feat\.|ft\.|featuring).*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
