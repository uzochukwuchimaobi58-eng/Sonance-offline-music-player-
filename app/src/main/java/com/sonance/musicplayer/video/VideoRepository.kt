package com.sonance.musicplayer.video

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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

    suspend fun scanDeviceVideos() = withContext(Dispatchers.IO) {
        _isScanning.value = true
        val videoList = mutableListOf<VideoItem>()
        val deletedIds = getDeletedVideoIds()

        // Always include user-saved custom imported videos first
        val customVideos = loadSavedCustomVideos()
        val customUris = customVideos.map { it.uri.toString() }.toSet()
        videoList.addAll(customVideos.filter { !deletedIds.contains(it.id.toString()) })

        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DATE_ADDED
            )

            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    if (deletedIds.contains(id.toString())) continue

                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: "" else ""
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""

                    // STRICT FILTER: Remove emulator sample / dummy / preloaded 5 videos
                    if (isSampleOrSystemVideo(title, name, path)) {
                        continue
                    }

                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 1920
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 1080
                    val dateAdded = if (dateCol != -1) cursor.getLong(dateCol) else 0L

                    val cleanTitle = when {
                        title.isNotBlank() -> title
                        name.isNotBlank() -> name.substringBeforeLast(".")
                        else -> "Video_${id}"
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    if (!customUris.contains(contentUri.toString())) {
                        videoList.add(
                            VideoItem(
                                id = id,
                                title = cleanTitle,
                                path = path,
                                uri = contentUri,
                                durationMs = duration,
                                sizeBytes = size,
                                width = width,
                                height = height,
                                dateAdded = dateAdded
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error scanning device videos", e)
        }

        _videos.value = videoList.distinctBy { it.uri.toString() }
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

    fun addCustomVideo(uri: Uri, title: String) {
        val newItem = VideoItem(
            id = System.currentTimeMillis(),
            title = title,
            path = uri.toString(),
            uri = uri,
            durationMs = 0L,
            sizeBytes = 0L,
            width = 1920,
            height = 1080,
            dateAdded = System.currentTimeMillis() / 1000
        )
        val updated = listOf(newItem) + loadSavedCustomVideos().filter { it.uri != uri }
        saveCustomVideos(updated)
        _videos.value = listOf(newItem) + _videos.value.filter { it.uri != uri }
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
