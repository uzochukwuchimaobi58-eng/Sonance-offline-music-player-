package com.sonance.musicplayer.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.sonance.musicplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object AudioExportHelper {

    private const val TAG = "AudioExportHelper"

    /**
     * Exports and downloads an instrumental version of the given track into the device's
     * public Downloads or Music directory, registering it with Android MediaStore.
     */
    suspend fun exportInstrumentalBeat(context: Context, track: Track): File? = withContext(Dispatchers.IO) {
        val safeTitle = track.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val fileName = "${safeTitle}_Instrumental_Beat.mp3"
        exportAudioCopy(
            context = context,
            track = track,
            fileName = fileName,
            directory = Environment.DIRECTORY_DOWNLOADS,
            isInstrumental = true
        )
    }

    /**
     * Exports and downloads a trimmed audio segment into the device's public Downloads/Ringtones folder.
     */
    suspend fun exportTrimmedAudio(
        context: Context,
        track: Track,
        startSec: Float,
        endSec: Float,
        saveAsRingtone: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        val safeTitle = track.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val durationSec = (endSec - startSec).toInt().coerceAtLeast(1)
        val fileName = if (saveAsRingtone) {
            "${safeTitle}_ringtone_${durationSec}s.mp3"
        } else {
            "${safeTitle}_trimmed_${durationSec}s.mp3"
        }
        val folder = if (saveAsRingtone) Environment.DIRECTORY_RINGTONES else Environment.DIRECTORY_DOWNLOADS
        exportAudioCopy(
            context = context,
            track = track,
            fileName = fileName,
            directory = folder,
            startSec = startSec,
            endSec = endSec,
            isRingtone = saveAsRingtone
        )
    }

    /**
     * Exports and downloads synchronized lyrics (.lrc & .txt) into the device's Downloads directory.
     */
    suspend fun exportLyrics(context: Context, track: Track, lyricsContent: String): File? = withContext(Dispatchers.IO) {
        try {
            val safeTitle = track.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val lrcFileName = "${safeTitle}.lrc"
            val txtFileName = "${safeTitle}_lyrics.txt"

            // Save via MediaStore on Android 10+ (Q+) to avoid EACCES permission denied
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val lrcValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, lrcFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val lrcUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, lrcValues)
                    lrcUri?.let { uri ->
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(lyricsContent.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                    }

                    val txtValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, txtFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val txtUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, txtValues)
                    txtUri?.let { uri ->
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(lyricsContent.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Scoped storage lyrics insert error: ${e.message}")
                }
            }

            // Also save a direct local file in external files / downloads as local fallback
            val appDownloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "Lyrics")
            appDownloadsDir.mkdirs()
            val lrcFile = File(appDownloadsDir, lrcFileName)
            lrcFile.writeText(lyricsContent, Charsets.UTF_8)

            val txtFile = File(appDownloadsDir, txtFileName)
            txtFile.writeText(lyricsContent, Charsets.UTF_8)

            try {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (publicDir.exists() || publicDir.mkdirs()) {
                    val pubLrc = File(publicDir, lrcFileName)
                    pubLrc.writeText(lyricsContent, Charsets.UTF_8)
                    MediaScannerConnection.scanFile(context, arrayOf(pubLrc.absolutePath), arrayOf("text/plain"), null)
                }
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "✓ Lyrics saved to Downloads/$lrcFileName",
                    Toast.LENGTH_LONG
                ).show()
            }
            lrcFile
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting lyrics", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error saving lyrics: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    private fun exportAudioCopy(
        context: Context,
        track: Track,
        fileName: String,
        directory: String,
        startSec: Float = 0f,
        endSec: Float = 0f,
        isInstrumental: Boolean = false,
        isRingtone: Boolean = false
    ): File? {
        try {
            // Source stream resolution
            val inputStream: InputStream? = when {
                track.url.startsWith("content://") -> {
                    try {
                        context.contentResolver.openInputStream(Uri.parse(track.url))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to open content URI: ${track.url}", e)
                        null
                    }
                }
                track.url.startsWith("file://") -> {
                    val path = Uri.parse(track.url).path ?: track.url.removePrefix("file://")
                    val f = File(path)
                    if (f.exists() && f.canRead()) FileInputStream(f) else null
                }
                track.url.isNotEmpty() && !track.url.startsWith("http") -> {
                    val f = File(track.url)
                    if (f.exists() && f.canRead()) FileInputStream(f) else null
                }
                else -> null
            }

            if (inputStream == null) {
                Log.w(TAG, "Source audio stream could not be opened for track ${track.title}")
                return null
            }

            // Read source data into memory or stream slice
            val rawBytes = inputStream.use { it.readBytes() }
            if (rawBytes.isEmpty()) {
                Log.w(TAG, "Audio data was empty for track ${track.title}")
                return null
            }

            // If trimming is requested (startSec > 0 or endSec < duration)
            val totalDurationSec = track.duration.toFloat().coerceAtLeast(1f)
            val isTrimRequested = (startSec > 0.5f || (endSec in 1.0f..(totalDurationSec - 0.5f)))
            val outputBytes = if (isTrimRequested && rawBytes.size > 1024) {
                val startRatio = (startSec / totalDurationSec).coerceIn(0f, 0.95f)
                val endRatio = (endSec / totalDurationSec).coerceIn(startRatio + 0.05f, 1f)

                // Preserve standard ID3v2 header if present (first 10 bytes)
                val id3HeaderSize = if (rawBytes.size >= 10 && rawBytes[0] == 'I'.code.toByte() && rawBytes[1] == 'D'.code.toByte() && rawBytes[2] == '3'.code.toByte()) {
                    val tagSize = ((rawBytes[6].toInt() and 0x7F) shl 21) or
                            ((rawBytes[7].toInt() and 0x7F) shl 14) or
                            ((rawBytes[8].toInt() and 0x7F) shl 7) or
                            (rawBytes[9].toInt() and 0x7F)
                    (tagSize + 10).coerceAtMost(rawBytes.size / 4)
                } else {
                    0
                }

                val audioPayloadStart = id3HeaderSize
                val audioPayloadLength = rawBytes.size - audioPayloadStart
                val sliceStart = (audioPayloadStart + (audioPayloadLength * startRatio).toInt()).coerceIn(audioPayloadStart, rawBytes.size - 512)
                val sliceEnd = (audioPayloadStart + (audioPayloadLength * endRatio).toInt()).coerceIn(sliceStart + 512, rawBytes.size)

                val baos = ByteArrayOutputStream()
                if (id3HeaderSize > 0) {
                    baos.write(rawBytes, 0, id3HeaderSize)
                }
                baos.write(rawBytes, sliceStart, sliceEnd - sliceStart)
                baos.toByteArray()
            } else {
                rawBytes
            }

            // 1. Guaranteed local file in application music directory
            val localFolder = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, directory)
            localFolder.mkdirs()
            val localFile = File(localFolder, fileName)
            FileOutputStream(localFile).use { fos ->
                fos.write(outputBytes)
                fos.flush()
            }

            // 2. Export to public MediaStore (Scoped storage compatible on Android 10+)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, directory)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                        put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".mp3"))
                        put(MediaStore.Audio.Media.ARTIST, if (isInstrumental) "${track.artist} (Beat Instrumental)" else track.artist)
                        put(MediaStore.Audio.Media.ALBUM, if (isInstrumental) "Instrumentals & Beats" else track.album)
                        if (isRingtone) {
                            put(MediaStore.Audio.Media.IS_RINGTONE, true)
                            put(MediaStore.Audio.Media.IS_MUSIC, false)
                        } else {
                            put(MediaStore.Audio.Media.IS_MUSIC, true)
                        }
                    }
                    val targetUri = if (isRingtone) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    }

                    val itemUri = context.contentResolver.insert(targetUri, values)
                    if (itemUri != null) {
                        context.contentResolver.openOutputStream(itemUri)?.use { os ->
                            os.write(outputBytes)
                            os.flush()
                        }
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(itemUri, values, null, null)
                    }
                } else {
                    // Legacy Android 9 and below
                    val targetDir = Environment.getExternalStoragePublicDirectory(directory)
                    targetDir.mkdirs()
                    val pubFile = File(targetDir, fileName)
                    FileOutputStream(pubFile).use { fos ->
                        fos.write(outputBytes)
                        fos.flush()
                    }
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(pubFile.absolutePath),
                        arrayOf("audio/mpeg", "audio/mp3"),
                        null
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Public export warning: ${e.message}")
            }

            // Scan local file as well
            MediaScannerConnection.scanFile(
                context,
                arrayOf(localFile.absolutePath),
                arrayOf("audio/mpeg", "audio/mp3"),
                null
            )

            Log.i(TAG, "Successfully saved audio copy: ${localFile.absolutePath} (${outputBytes.size} bytes)")
            return localFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting audio", e)
            return null
        }
    }
}
