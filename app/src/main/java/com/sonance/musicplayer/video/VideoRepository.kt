package com.sonance.musicplayer.video

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.media.MediaScannerConnection
import android.os.Environment
import com.sonance.musicplayer.R
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

class VideoRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sonance_video_prefs", Context.MODE_PRIVATE)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var videoContentObserver: ContentObserver? = null

    init {
        // Purge any residual demo videos
        purgeDemoVideos()
        // Load initially saved custom user videos
        loadCustomVideos()

        // Register ContentObserver to detect newly downloaded / recorded offline videos
        try {
            videoContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    coroutineScope.launch {
                        scanDeviceVideos()
                    }
                }
            }
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                videoContentObserver!!
            )
        } catch (e: Exception) {
            Log.w("VideoRepository", "Could not register video content observer", e)
        }

        // Trigger scan immediately on start
        coroutineScope.launch {
            scanDeviceVideos()
        }
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

    private fun isSystemOnlyVideo(path: String): Boolean {
        val p = path.lowercase()
        return p.startsWith("/system/") || p.startsWith("/vendor/") ||
            p.startsWith("/product/") || p.startsWith("/apex/") ||
            p.startsWith("/system_ext/")
    }

    private fun extractVideoMetadata(uri: Uri, customTitle: String = "", directFile: File? = null): VideoItem {
        val file = directFile ?: if (uri.scheme == "file") File(uri.path ?: "") else null
        var title = if (customTitle.isNotBlank()) customTitle else file?.nameWithoutExtension ?: "User Video"
        var duration = 0L
        var size = file?.length() ?: 0L
        var width = 1280
        var height = 720
        val dateAdded = if (file != null && file.lastModified() > 0) file.lastModified() / 1000 else System.currentTimeMillis() / 1000

        if (file == null || size <= 0L) {
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
                            val s = cursor.getLong(sizeCol)
                            if (s > 0) size = s
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("VideoRepository", "Could not query OpenableColumns", e)
            }
        }

        try {
            val retriever = MediaMetadataRetriever()
            if (file != null && file.exists()) {
                retriever.setDataSource(file.absolutePath)
            } else if (uri.scheme == "content") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(context, uri)
            }
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (d != null) duration = d.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            if (w != null) width = w.toIntOrNull() ?: 1280
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (h != null) height = h.toIntOrNull() ?: 720
            val t = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!t.isNullOrBlank() && customTitle.isBlank()) title = t
            retriever.release()
        } catch (e: Exception) {
            Log.w("VideoRepository", "Retriever metadata extraction info", e)
        }

        val stableId = if (file != null) {
            kotlin.math.abs(file.absolutePath.hashCode().toLong()) + 100_000L
        } else {
            kotlin.math.abs(uri.toString().hashCode().toLong()) + 200_000L
        }

        return VideoItem(
            id = stableId,
            title = title,
            path = file?.absolutePath ?: uri.toString(),
            uri = uri,
            durationMs = if (duration > 0) duration else 8000L,
            sizeBytes = if (size > 0) size else 206_000L,
            width = width,
            height = height,
            dateAdded = dateAdded
        )
    }

    suspend fun scanDeviceVideos() = withContext(Dispatchers.IO) {
        _isScanning.value = true
        val deletedIds = getDeletedVideoIds()
        val mediaStoreVideos = mutableListOf<VideoItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        fun queryVideoCollection(collectionUri: Uri) {
            try {
                context.contentResolver.query(
                    collectionUri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val dateAddedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                    val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                    while (cursor.moveToNext()) {
                        val id = if (idCol != -1) cursor.getLong(idCol) else continue
                        if (deletedIds.contains(id.toString())) continue

                        val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                        val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                        var title = if (titleCol != -1) cursor.getString(titleCol) ?: "" else ""

                        if (title.isBlank() && name.isNotBlank()) {
                            title = name.substringBeforeLast(".")
                        }
                        if (title.isBlank() && path.isNotBlank()) {
                            title = path.substringAfterLast("/").substringBeforeLast(".")
                        }
                        if (title.isBlank()) title = "Video_$id"

                        if (isSystemOnlyVideo(path)) continue

                        val durationMs = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                        val sizeBytes = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                        val dateAdded = if (dateAddedCol != -1) cursor.getLong(dateAddedCol) else System.currentTimeMillis() / 1000
                        val width = if (widthCol != -1) cursor.getInt(widthCol).coerceAtLeast(0) else 1920
                        val height = if (heightCol != -1) cursor.getInt(heightCol).coerceAtLeast(0) else 1080

                        val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                        mediaStoreVideos.add(
                            VideoItem(
                                id = id,
                                title = title,
                                path = path.ifBlank { contentUri.toString() },
                                uri = contentUri,
                                durationMs = durationMs,
                                sizeBytes = sizeBytes,
                                width = if (width > 0) width else 1920,
                                height = if (height > 0) height else 1080,
                                dateAdded = dateAdded
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.w("VideoRepository", "Permission not granted to query video collection $collectionUri", e)
            } catch (e: Exception) {
                Log.e("VideoRepository", "Failed querying MediaStore videos at $collectionUri", e)
            }
        }

        // Primary external video storage
        queryVideoCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

        // On Android Q+, also check any external SD cards or mounted volumes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val volumes = MediaStore.getExternalVolumeNames(context)
                for (vol in volumes) {
                    if (vol != MediaStore.VOLUME_EXTERNAL_PRIMARY) {
                        queryVideoCollection(MediaStore.Video.Media.getContentUri(vol))
                    }
                }
            } catch (e: Exception) {
                Log.w("VideoRepository", "Failed scanning external volumes for videos", e)
            }
        }

        // Combine with user custom/picked videos
        val customVideos = loadSavedCustomVideos().filter { !deletedIds.contains(it.id.toString()) }
        val allMap = linkedMapOf<String, VideoItem>()

        // Put custom videos first
        customVideos.forEach { allMap[it.uri.toString()] = it }
        // Then merge MediaStore videos
        mediaStoreVideos.forEach { allMap[it.uri.toString()] = it }

        // Direct storage folder scanning for newly downloaded / copied offline videos
        val directDirs = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            File(context.filesDir, "videos"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            File("/sdcard/Movies"),
            File("/sdcard/Download"),
            File("/sdcard/DCIM"),
            File("/storage/emulated/0/Movies"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/DCIM")
        )

        for (dir in directDirs) {
            try {
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles() ?: continue
                    for (file in files) {
                        if (file.isFile && isVideoExtension(file.extension)) {
                            val uri = Uri.fromFile(file)
                            if (!allMap.containsKey(uri.toString()) && !allMap.containsKey(file.absolutePath)) {
                                val item = extractVideoMetadata(uri, file.nameWithoutExtension, file)
                                allMap[uri.toString()] = item
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("VideoRepository", "Direct folder scan notice for $dir: ${e.message}")
            }
        }

        purgeDemoVideos()

        _videos.value = allMap.values
            .filter { !isDemoVideo(it) }
            .sortedByDescending { it.dateAdded }
        _isScanning.value = false
    }

    private fun isDemoVideo(item: VideoItem): Boolean {
        val titleLower = item.title.lowercase()
        val pathLower = item.path.lowercase()
        return titleLower.contains("sonance studio demo") ||
               titleLower.contains("synthwave neon soundstage") ||
               titleLower.contains("acoustic chill showcase") ||
               pathLower.contains("sonance_studio_demo") ||
               pathLower.contains("synthwave_neon_soundstage") ||
               pathLower.contains("acoustic_chill_showcase")
    }

    fun purgeDemoVideos() {
        try {
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            val demoFiles = listOf(
                File(targetDir, "Sonance_Studio_Demo.mp4"),
                File(targetDir, "Synthwave_Neon_Soundstage.mp4"),
                File(targetDir, "Acoustic_Chill_Showcase.mp4"),
                File(context.filesDir, "Sonance_Studio_Demo.mp4"),
                File(context.filesDir, "Synthwave_Neon_Soundstage.mp4"),
                File(context.filesDir, "Acoustic_Chill_Showcase.mp4"),
                File(context.filesDir, "videos/Sonance_Studio_Demo.mp4"),
                File(context.filesDir, "videos/Synthwave_Neon_Soundstage.mp4"),
                File(context.filesDir, "videos/Acoustic_Chill_Showcase.mp4")
            )
            demoFiles.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private fun isVideoExtension(ext: String): Boolean {
        val e = ext.lowercase()
        return e == "mp4" || e == "mkv" || e == "webm" || e == "3gp" ||
               e == "mov" || e == "m4v" || e == "ts" || e == "avi"
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
