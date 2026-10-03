package com.sonance.musicplayer.player

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

class MusicPlaybackService : Service() {

    private lateinit var store: com.sonance.musicplayer.data.MusicStore
    private lateinit var playbackManager: PlaybackManager

    override fun onCreate() {
        super.onCreate()
        store = com.sonance.musicplayer.data.MusicStore(applicationContext)
        playbackManager = PlaybackManager.getInstance(applicationContext)
        Log.i(TAG, "MusicPlaybackService created with MusicStore and PlaybackManager")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                val notification = PlaybackNotificationManager.cachedNotification ?: run {
                    androidx.core.app.NotificationCompat.Builder(this, PlaybackNotificationManager.CHANNEL_ID)
                        .setSmallIcon(com.sonance.musicplayer.R.drawable.ic_notification_play)
                        .setContentTitle("Playing Music")
                        .setContentText("Sonance Music Player")
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .build()
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            PlaybackNotificationManager.NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(PlaybackNotificationManager.NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting foreground service", e)
                }
            }
            ACTION_STOP_SERVICE -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping foreground service", e)
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Swiping the app from recents must NOT stop music if currently playing.
        // It keeps playing in the background and tracking completed plays.
        if (::playbackManager.isInitialized && !playbackManager.isPlaying.value) {
            stopSelf()
        } else {
            Log.i(TAG, "App swiped away from recents. Keeping music playback and play counting active in background.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MusicPlaybackService destroyed")
    }

    companion object {
        private const val TAG = "MusicPlaybackService"
        const val ACTION_START_SERVICE = "com.sonance.musicplayer.ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.sonance.musicplayer.ACTION_STOP_SERVICE"
    }
}
