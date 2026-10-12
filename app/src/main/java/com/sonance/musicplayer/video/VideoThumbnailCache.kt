package com.sonance.musicplayer.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoThumbnailCache {
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxMemoryKb / 8 // Use 1/8th of available runtime memory

    private val memoryCache: LruCache<Long, Bitmap> = object : LruCache<Long, Bitmap>(cacheSizeKb.coerceAtLeast(1024)) {
        override fun sizeOf(key: Long, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun get(id: Long): Bitmap? = memoryCache.get(id)

    fun put(id: Long, bitmap: Bitmap) {
        memoryCache.put(id, bitmap)
    }

    suspend fun loadThumbnail(context: Context, video: VideoItem): Bitmap? = withContext(Dispatchers.IO) {
        get(video.id)?.let { return@withContext it }

        var bmp: Bitmap? = null

        // Strategy 1: Android Q+ ContentResolver loadThumbnail (uses Android OS pre-computed thumbnail cache)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                bmp = context.contentResolver.loadThumbnail(
                    video.uri,
                    Size(480, 320),
                    null
                )
            } catch (_: Throwable) {}
        }

        // Strategy 2: Pre-Q MediaStore query
        if (bmp == null) {
            try {
                bmp = MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    video.id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            } catch (_: Throwable) {}
        }

        // Strategy 3: MediaMetadataRetriever
        if (bmp == null) {
            try {
                val retriever = MediaMetadataRetriever()
                if (video.path.startsWith("/") || video.path.startsWith("file://")) {
                    val cleanPath = video.path.removePrefix("file://")
                    retriever.setDataSource(cleanPath)
                    bmp = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } else if (video.uri.scheme == "content") {
                    retriever.setDataSource(context, video.uri)
                    bmp = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                retriever.release()
            } catch (_: Throwable) {}
        }

        // Strategy 4: Legacy ThumbnailUtils
        if (bmp == null && video.path.isNotBlank() && video.path.startsWith("/")) {
            try {
                bmp = ThumbnailUtils.createVideoThumbnail(
                    video.path,
                    MediaStore.Images.Thumbnails.MINI_KIND
                )
            } catch (_: Throwable) {}
        }

        if (bmp != null) {
            put(video.id, bmp)
        }

        bmp
    }
}
