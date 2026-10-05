package com.sonance.musicplayer.lyrics

import android.util.Log
import com.sonance.musicplayer.data.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsOvhProvider {

    companion object {
        private const val TAG = "LyricsOvhProvider"
        private const val BASE_URL = "https://api.lyrics.ovh/v1/"
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
        artist: String
    ): LyricsProviderResult? = withContext(Dispatchers.IO) {
        val (cleanTitle, cleanArtist) = parseArtistAndTitle(title, artist)

        if (cleanTitle.isBlank() || cleanArtist.isBlank() || cleanArtist.equals("Unknown artist", ignoreCase = true)) {
            return@withContext null
        }

        try {
            val url = "${BASE_URL}${encPath(cleanArtist)}/${encPath(cleanTitle)}"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null

                val json = JSONObject(body)
                val lyricsText = json.optString("lyrics").trim()

                if (lyricsText.isNotBlank()) {
                    val lines = lyricsText.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { LyricLine(-1L, it) }

                    if (lines.isNotEmpty()) {
                        return@withContext LyricsProviderResult(
                            lines = lines,
                            isSynced = false,
                            source = "Online"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Lyrics.ovh request failed for '$cleanTitle' by '$cleanArtist': ${e.message}")
        }
        null
    }

    private fun parseArtistAndTitle(rawTitle: String, rawArtist: String): Pair<String, String> {
        var cleanArtist = cleanArtist(rawArtist)
        var cleanTitle = cleanTitle(rawTitle)

        if ((cleanArtist.isBlank() || cleanArtist.equals("Unknown artist", ignoreCase = true) || cleanArtist.equals("<unknown>", ignoreCase = true)) && cleanTitle.contains(" - ")) {
            val parts = cleanTitle.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                cleanArtist = cleanArtist(parts[0])
                cleanTitle = cleanTitle(parts[1])
            }
        }
        return Pair(cleanTitle, cleanArtist)
    }

    private fun cleanTitle(raw: String): String {
        return raw.replace(Regex("""\.(mp3|m4a|flac|wav|aac|ogg|opus)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^\d{1,3}[\s._-]+"""), "")
            .replace(Regex("""\[.*?\]|\(.*?\)"""), "")
            .replace(Regex("""(official\s+video|official\s+audio|lyrics|lyric\s+video|remastered|remaster|hd|4k)""", RegexOption.IGNORE_CASE), "")
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun cleanArtist(raw: String): String {
        return raw.replace(Regex("""(feat\.|ft\.|featuring).*""", RegexOption.IGNORE_CASE), "")
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun encPath(s: String): String {
        return try {
            URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            s.replace(" ", "%20").replace("/", "%2F")
        }
    }
}
