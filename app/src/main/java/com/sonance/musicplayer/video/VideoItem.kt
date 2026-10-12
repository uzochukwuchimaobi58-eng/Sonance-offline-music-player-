package com.sonance.musicplayer.video

import android.net.Uri

data class VideoItem(
    val id: Long,
    val title: String,
    val path: String,
    val uri: Uri,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val resolution: String = when {
        width >= 3840 || height >= 2160 -> "4K UHD"
        width >= 1920 || height >= 1080 -> "1080p FHD"
        width >= 1280 || height >= 720 -> "720p HD"
        width > 0 && height > 0 -> "${width}x${height}"
        else -> "HD"
    },
    val formattedDuration: String = formatDuration(durationMs),
    val formattedSize: String = formatSize(sizeBytes)
)

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val hours = min / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, min % 60, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format("%.1f GB", mb / 1024)
    } else {
        String.format("%.1f MB", mb)
    }
}
