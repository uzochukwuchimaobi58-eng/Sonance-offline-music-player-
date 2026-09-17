package com.sonance.musicplayer.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sonance.musicplayer.MainActivity
import com.sonance.musicplayer.model.Track

class PlaybackNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "sonance_music_playback"
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sonance Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active music playback controls"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(track: Track?, isPlaying: Boolean) {
        if (track == null) {
            cancelNotification()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previous / Backward Action
        val prevIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_PREV
        }
        val prevPending = PendingIntent.getBroadcast(
            context,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play / Pause Action
        val playPauseIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_TOGGLE_PLAY
        }
        val playPausePending = PendingIntent.getBroadcast(
            context,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Next Action
        val nextIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_NEXT
        }
        val nextPending = PendingIntent.getBroadcast(
            context,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val subtext = if (track.album.isNotBlank() && track.album != "<unknown>") {
            "${track.artist} • ${track.album}"
        } else {
            track.artist
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(track.title)
            .setContentText(subtext)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, playPauseTitle, playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }

    fun cancelNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
