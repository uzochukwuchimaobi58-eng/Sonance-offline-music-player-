package com.sonance.musicplayer.player

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

class MusicPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                val notification = PlaybackNotificationManager.cachedNotification
                if (notification != null) {
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
        // User swiped away app from recents screen.
        // We explicitly keep this service running so music continues playing uninterrupted!
        Log.i(TAG, "App swiped away from recents. Keeping music playback active in background.")
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
