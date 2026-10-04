package com.sonance.musicplayer.lyrics

import android.util.Log
import com.sonance.musicplayer.data.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

interface LyricsOvhApi {
    @GET("v1/{artist}/{title}")
    suspend fun getLyrics(
        @Path("artist", encoded = true) artist: String,
        @Path("title", encoded = true) title: String
    ): String
}

class LyricsOvhProvider {

    companion object {
        private const val TAG = "LyricsOvhProvider"
        private const val BASE_URL = "https://api.lyrics.ovh/"
    }

    private val api: LyricsOvhApi by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(LyricsOvhApi::class.java)
    }

    suspend fun fetchLyrics(
        title: String,
        artist: String
    ): LyricsProviderResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTitle(title)
        val cleanArtist = cleanArtist(artist)

        if (cleanTitle.isBlank() || cleanArtist.isBlank() || cleanArtist.equals("Unknown artist", ignoreCase = true)) {
            return@withContext null
        }

        try {
            val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")

            val response = api.getLyrics(encodedArtist, encodedTitle)
            val json = JSONObject(response)
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
                        source = "Lyrics.ovh"
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Lyrics.ovh request failed for '$cleanTitle' by '$cleanArtist': ${e.message}")
        }
        null
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
