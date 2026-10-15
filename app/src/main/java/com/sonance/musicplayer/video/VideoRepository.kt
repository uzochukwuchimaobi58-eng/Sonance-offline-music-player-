package com.sonance.musicplayer.video

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VideoRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sonance_video_prefs", Context.MODE_PRIVATE)

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        // Load initially saved custom user videos
        loadCustomVideos()
    }

    private fun getDeletedVideoIds(): Set<String> {
        return prefs.getStringSet("deleted_video_ids", emptySet()) ?: emptySet()
    }

    fun deleteVideo(id: Long) {
        val currentDeleted = getDeletedVideoIds().toMutableSet()
        currentDeleted.add(id.toString())
        prefs.edit().putStringSet("deleted_video_ids", currentDeleted).apply()

        // Also remove from custom videos if present
        removeCustomVideo(id)

        _videos.value = _videos.value.filter { it.id != id }
    }

    private fun isSampleOrSystemVideo(title: String, name: String, path: String): Boolean {
        val t = title.lowercase()
        val n = name.lowercase()
        val p = path.lowercase()

        // Filter out system / emulator preloaded sample media
        val sampleKeywords = listOf(
            "sample", "test", "demo", "bunny", "big_buck", "elephants_dream",
            "sintel", "tearsofsteel", "placeholder", "trailer", "default_video"
        )
        if (sampleKeywords.any { t.contains(it) || n.contains(it) || p.contains(it) }) {
            return true
        }

        if (p.startsWith("/system/") || p.startsWith("/vendor/") ||
            p.startsWith("/product/") || p.startsWith("/apex/") ||
            p.startsWith("/system_ext/")
        ) {
            return true
        }

        return false
    }

    private fun extractVideoMetadata(uri: Uri, customTitle: String = ""): VideoItem {
        var title = if (customTitle.isNotBlank()) customTitle else "User Video"
        var duration = 0L
        var size = 0L
        var width = 1920
        var height = 1080
        val dateAdded = System.currentTimeMillis() / 1000

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameCol != -1) {
                        val n = cursor.getString(nameCol)
                        if (!n.isNullOrBlank() && customTitle.isBlank()) {
                            title = n.substringBeforeLast(".")
                        }
                    }
                    if (sizeCol != -1) {
                        size = cursor.getLong(sizeCol)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("VideoRepository", "Could not query OpenableColumns", e)
        }

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (d != null) duration = d.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            if (w != null) width = w.toIntOrNull() ?: 1920
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (h != null) height = h.toIntOrNull() ?: 1080
            val t = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!t.isNullOrBlank() && customTitle.isBlank()) title = t
            retriever.release()
        } catch (e: Exception) {
            Log.w("VideoRepository", "Retriever metadata extraction info", e)
        }

        return VideoItem(
            id = System.currentTimeMillis() + (0..999).random(),
            title = title,
            path = uri.toString(),
            uri = uri,
            durationMs = duration,
            sizeBytes = size,
            width = width,
            height = height,
            dateAdded = dateAdded
        )
    }

    suspend fun scanDeviceVideos() = withContext(Dispatchers.IO) {
        _isScanning.value = true
        val deletedIds = getDeletedVideoIds()
        val customVideos = loadSavedCustomVideos()
        // Only load the user's authentic chosen videos - no emulator / system / sample videos
        _videos.value = customVideos.filter { !deletedIds.contains(it.id.toString()) }
        _isScanning.value = false
    }

    private fun loadCustomVideos() {
        val list = loadSavedCustomVideos()
        val deletedIds = getDeletedVideoIds()
        _videos.value = list.filter { !deletedIds.contains(it.id.toString()) }
    }

    private fun loadSavedCustomVideos(): List<VideoItem> {
        val raw = prefs.getString("custom_user_videos", null) ?: return emptyList()
        val list = mutableListOf<VideoItem>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    VideoItem(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        title = obj.optString("title", "User Video"),
                        path = obj.optString("path", ""),
                        uri = Uri.parse(obj.optString("uri")),
                        durationMs = obj.optLong("durationMs", 0L),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        width = obj.optInt("width", 1920),
                        height = obj.optInt("height", 1080),
                        dateAdded = obj.optLong("dateAdded", System.currentTimeMillis() / 1000)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Failed to parse saved custom videos", e)
        }
        return list
    }

    private fun saveCustomVideos(items: List<VideoItem>) {
        try {
            val array = JSONArray()
            items.forEach { v ->
                val obj = JSONObject().apply {
                    put("id", v.id)
                    put("title", v.title)
                    put("path", v.path)
                    put("uri", v.uri.toString())
                    put("durationMs", v.durationMs)
                    put("sizeBytes", v.sizeBytes)
                    put("width", v.width)
                    put("height", v.height)
                    put("dateAdded", v.dateAdded)
                }
                array.put(obj)
            }
            prefs.edit().putString("custom_user_videos", array.toString()).apply()
        } catch (e: Exception) {
            Log.e("VideoRepository", "Failed to save custom videos", e)
        }
    }

    private fun removeCustomVideo(id: Long) {
        val list = loadSavedCustomVideos().filter { it.id != id }
        saveCustomVideos(list)
    }

    fun addCustomVideo(uri: Uri, title: String = "") {
        val newItem = extractVideoMetadata(uri, title)
        val updated = listOf(newItem) + loadSavedCustomVideos().filter { it.uri != uri }
        saveCustomVideos(updated)
        _videos.value = updated
    }

    fun addCustomVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val newItems = uris.map { extractVideoMetadata(it) }
        val existing = loadSavedCustomVideos().filter { existing -> uris.none { it == existing.uri } }
        val updated = newItems + existing
        saveCustomVideos(updated)
        _videos.value = updated
    }

    companion object {
        @Volatile
        private var instance: VideoRepository? = null

        fun getInstance(context: Context): VideoRepository {
            return instance ?: synchronized(this) {
                instance ?: VideoRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
