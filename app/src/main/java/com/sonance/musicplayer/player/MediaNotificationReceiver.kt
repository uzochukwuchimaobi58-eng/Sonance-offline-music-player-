package com.sonance.musicplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MediaNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PREV = "com.sonance.musicplayer.ACTION_PREV"
        const val ACTION_TOGGLE_PLAY = "com.sonance.musicplayer.ACTION_TOGGLE_PLAY"
        const val ACTION_NEXT = "com.sonance.musicplayer.ACTION_NEXT"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val playbackManager = PlaybackManager.getInstance(context)
        when (intent.action) {
            ACTION_PREV -> playbackManager.skipToPrevious()
            ACTION_TOGGLE_PLAY -> playbackManager.togglePlayPause()
            ACTION_NEXT -> playbackManager.skipToNext()
        }
    }
}
