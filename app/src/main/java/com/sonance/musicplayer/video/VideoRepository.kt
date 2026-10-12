package com.sonance.musicplayer.video

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class VideoRepository(private val context: Context) {

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    suspend fun scanDeviceVideos() = withContext(Dispatchers.IO) {
        _isScanning.value = true
        val videoList = mutableListOf<VideoItem>()

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
                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: "" else ""
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
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
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error scanning device videos", e)
        }

        // If no offline videos exist on the physical device or emulator, provide offline sample showcase clips
        if (videoList.isEmpty()) {
            videoList.addAll(getSampleOfflineVideos())
        }

        _videos.value = videoList
        _isScanning.value = false
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
        _videos.value = listOf(newItem) + _videos.value.filter { it.uri != uri }
    }

    private fun getSampleOfflineVideos(): List<VideoItem> {
        return listOf(
            VideoItem(
                id = 1001L,
                title = "Lo-Fi Beats & City Neon Lights",
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
                durationMs = 15000L,
                sizeBytes = 15_400_000L,
                width = 1920,
                height = 1080,
                dateAdded = System.currentTimeMillis() / 1000
            ),
            VideoItem(
                id = 1002L,
                title = "Acoustic Nature Session - Sunset Vibes",
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                durationMs = 60000L,
                sizeBytes = 28_700_000L,
                width = 1920,
                height = 1080,
                dateAdded = (System.currentTimeMillis() - 86400000) / 1000
            ),
            VideoItem(
                id = 1003L,
                title = "Studio Bass & Subwoofer Frequency Test",
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
                durationMs = 45000L,
                sizeBytes = 22_100_000L,
                width = 1280,
                height = 720,
                dateAdded = (System.currentTimeMillis() - 172800000) / 1000
            )
        )
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
