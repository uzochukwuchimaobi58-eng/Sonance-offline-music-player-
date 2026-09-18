package com.sonance.musicplayer.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import com.sonance.musicplayer.model.Track
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object RingtoneHelper {

    fun setAsRingtoneImmediately(context: Context, track: Track): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(context)) {
                Toast.makeText(
                    context,
                    "Please allow 'Modify system settings' so Sonance can set your ringtone",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return false
            }
        }

        try {
            var ringtoneUri: Uri? = null

            // 1. If already a MediaStore Content URI
            if (track.contentUri.startsWith("content://")) {
                val parsedUri = Uri.parse(track.contentUri)
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.IS_RINGTONE, true)
                    }
                    context.contentResolver.update(parsedUri, values, null, null)
                } catch (_: Exception) {}
                ringtoneUri = parsedUri
            }

            // 2. If it's a direct file path
            if (ringtoneUri == null && track.contentUri.isNotBlank() && !track.contentUri.startsWith("http")) {
                val file = File(track.contentUri)
                if (file.exists()) {
                    ringtoneUri = copyToRingtoneCollection(context, file, track.title)
                }
            }

            // 3. Fallback: create ringtone entry from existing URI stream
            if (ringtoneUri == null) {
                ringtoneUri = Uri.parse(track.contentUri)
            }

            // Set actual system default ringtone
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                ringtoneUri
            )

            Toast.makeText(
                context,
                "✓ '${track.title}' is now set as your phone ringtone!",
                Toast.LENGTH_LONG
            ).show()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Failed to set ringtone: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
    }

    private fun copyToRingtoneCollection(context: Context, sourceFile: File, title: String): Uri? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")}.mp3")
                put(MediaStore.MediaColumns.TITLE, title)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp3")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
                put(MediaStore.Audio.Media.IS_ALARM, false)
                put(MediaStore.Audio.Media.IS_MUSIC, false)
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, values) ?: return null

            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }

            itemUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
