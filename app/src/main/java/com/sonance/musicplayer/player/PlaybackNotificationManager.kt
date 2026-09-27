package com.sonance.musicplayer.player

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sonance.musicplayer.MainActivity
import com.sonance.musicplayer.R
import com.sonance.musicplayer.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "sonance_music_playback"
        const val NOTIFICATION_ID = 1001
        @Volatile
        var cachedNotification: Notification? = null
            private set
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val scope = CoroutineScope(Dispatchers.IO)
    private var mediaSession: MediaSessionCompat? = null

    init {
        createChannel()
        initMediaSession()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sonance Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active music playback controls and media notification"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initMediaSession() {
        try {
            mediaSession = MediaSessionCompat(context, "SonanceMediaSession").apply {
                isActive = true
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        PlaybackManager.getInstance(context).resume()
                    }

                    override fun onPause() {
                        PlaybackManager.getInstance(context).pause()
                    }

                    override fun onSkipToNext() {
                        PlaybackManager.getInstance(context).skipToNext()
                    }

                    override fun onSkipToPrevious() {
                        PlaybackManager.getInstance(context).skipToPrevious()
                    }

                    override fun onSeekTo(pos: Long) {
                        PlaybackManager.getInstance(context).seekTo(pos)
                    }

                    override fun onStop() {
                        PlaybackManager.getInstance(context).stopPlaybackAndDismiss()
                    }

                    override fun onCustomAction(action: String?, extras: Bundle?) {
                        if (action == MediaNotificationReceiver.ACTION_FAVORITE) {
                            PlaybackManager.getInstance(context).toggleFavoriteCurrent()
                        }
                    }
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaybackNotification", "Error initializing MediaSession", e)
        }
    }

    fun updateNotification(
        track: Track?,
        isPlaying: Boolean,
        currentPosMs: Long = 0L,
        durationMs: Long = 0L
    ) {
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

        scope.launch {
            val artBitmap = loadAlbumArtBitmap(track)

            withContext(Dispatchers.Main) {
                try {
                    buildAndPostNotification(track, isPlaying, currentPosMs, durationMs, artBitmap)
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackNotification", "Error posting notification", e)
                }
            }
        }
    }

    private fun buildAndPostNotification(
        track: Track,
        isPlaying: Boolean,
        currentPosMs: Long,
        durationMs: Long,
        artBitmap: Bitmap?
    ) {
        val session = mediaSession ?: return

        val safeDuration = when {
            durationMs > 0L -> durationMs
            track.duration > 0L -> track.duration * 1000L
            else -> 0L
        }
        val safePos = if (safeDuration > 0L) currentPosMs.coerceIn(0L, safeDuration) else currentPosMs.coerceAtLeast(0L)

        // 1. Update MediaSession Metadata
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, if (track.artist == "<unknown>") "Unknown Artist" else track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
        if (safeDuration > 0L) {
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, safeDuration)
        }

        if (artBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artBitmap)
        }
        session.setMetadata(metadataBuilder.build())

        // 2. Update PlaybackStateCompat with actions including SEEK_TO and STOP
        val stateActions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val effectiveSpeed = if (isPlaying) 1.0f else 0f
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(stateActions)
            .setState(state, safePos, effectiveSpeed, android.os.SystemClock.elapsedRealtime())

        // Add custom action for favorite
        val favIconRes = if (track.isFavorite) R.drawable.ic_notification_heart else R.drawable.ic_notification_heart_outline
        val customFavAction = PlaybackStateCompat.CustomAction.Builder(
            MediaNotificationReceiver.ACTION_FAVORITE,
            "Favorite",
            favIconRes
        ).build()
        stateBuilder.addCustomAction(customFavAction)

        session.setPlaybackState(stateBuilder.build())

        // 3. Pending Intents
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val favoriteIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_FAVORITE
        }
        val favoritePending = PendingIntent.getBroadcast(
            context,
            10,
            favoriteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_PREV
        }
        val prevPending = PendingIntent.getBroadcast(
            context,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_TOGGLE_PLAY
        }
        val playPausePending = PendingIntent.getBroadcast(
            context,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_NEXT
        }
        val nextPending = PendingIntent.getBroadcast(
            context,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = MediaNotificationReceiver.ACTION_CLOSE
        }
        val closePending = PendingIntent.getBroadcast(
            context,
            4,
            closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. MediaStyle layout
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(1, 2, 3) // Previous, Play/Pause, Next in compact view
            .setShowCancelButton(true)
            .setCancelButtonIntent(closePending)

        val artistText = if (track.artist.isBlank() || track.artist == "<unknown>") "Unknown Artist" else track.artist
        val playPauseIcon = if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setStyle(mediaStyle)
            .setSmallIcon(R.drawable.ic_notification_play)
            .setContentTitle(track.title)
            .setContentText(artistText)
            .setSubText(track.album.ifBlank { "Sonance" })
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(closePending)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (artBitmap != null) {
                    setLargeIcon(artBitmap)
                }
            }
            // Action 0: Favorite (Heart)
            .addAction(favIconRes, "Favorite", favoritePending)
            // Action 1: Previous
            .addAction(R.drawable.ic_notification_prev, "Previous", prevPending)
            // Action 2: Play/Pause
            .addAction(playPauseIcon, playPauseTitle, playPausePending)
            // Action 3: Next
            .addAction(R.drawable.ic_notification_next, "Next", nextPending)
            // Action 4: Close (X)
            .addAction(R.drawable.ic_notification_close, "Close", closePending)
            .build()

        cachedNotification = notification

        if (isPlaying) {
            try {
                val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_START_SERVICE
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                android.util.Log.w("PlaybackNotification", "Could not start foreground service", e)
            }
        }

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun loadAlbumArtBitmap(track: Track): Bitmap? {
        return try {
            // 1. Try content uri from MediaStore
            if (track.url.startsWith("content://")) {
                val uri = Uri.parse(track.url)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        return context.contentResolver.loadThumbnail(uri, Size(300, 300), null)
                    } catch (_: Exception) {}
                }
            }

            // 2. Try album art URI
            if (track.coverArt.startsWith("content://") || track.coverArt.startsWith("file://")) {
                try {
                    val stream = context.contentResolver.openInputStream(Uri.parse(track.coverArt))
                    if (stream != null) {
                        val bmp = BitmapFactory.decodeStream(stream)
                        stream.close()
                        if (bmp != null) return bmp
                    }
                } catch (_: Exception) {}
            }

            // 3. Fallback: Generate a clean stylish music disc icon bitmap
            generatePlaceholderArt(track.title)
        } catch (_: Exception) {
            null
        }
    }

    private fun generatePlaceholderArt(title: String): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient or deep solid disc background
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#26262b")
        }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 32f, 32f, paint)

        // Inner circle
        paint.color = android.graphics.Color.parseColor("#38bdf8")
        canvas.drawCircle(size / 2f, size / 2f, 70f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 56f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val initial = title.firstOrNull()?.uppercase() ?: "♪"
        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2)
        canvas.drawText(initial, size / 2f, yPos, paint)

        return bitmap
    }

    fun cancelNotification() {
        cachedNotification = null
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}

        try {
            val stopIntent = Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_STOP_SERVICE
            }
            context.startService(stopIntent)
        } catch (_: Exception) {}
    }

    fun release() {
        cancelNotification()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (_: Exception) {}
    }
}
