package com.sonance.musicplayer.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global Video Manager handling video playback across the entire app,
 * including persistent floating mini-player across all screens and
 * seamless Picture-in-Picture (PiP) on app exit until the user presses 'X'.
 */
class GlobalVideoManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    private var exoPlayer: ExoPlayer? = null

    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _isFloatingVisible = MutableStateFlow(false)
    val isFloatingVisible: StateFlow<Boolean> = _isFloatingVisible.asStateFlow()

    init {
        initPlayer()
    }

    private fun initPlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(appContext).build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            if (_isLooping.value) {
                                seekTo(0)
                                play()
                            }
                        }
                    }
                })
            }
        }
    }

    fun getPlayer(): ExoPlayer {
        initPlayer()
        return exoPlayer!!
    }

    /**
     * Start playing video, optionally with continuous loop enabled across the app.
     */
    fun playVideo(video: VideoItem, loop: Boolean = false, showFloating: Boolean = false) {
        val player = getPlayer()
        _currentVideo.value = video
        _isLooping.value = loop
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

        val mediaItem = MediaItem.fromUri(video.uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        _isPlaying.value = true

        if (showFloating || loop) {
            _isFloatingVisible.value = true
        }
    }

    /**
     * Set looping on a video - displays across the app and on exit until user presses X.
     */
    fun startLoopingVideo(video: VideoItem) {
        playVideo(video, loop = true, showFloating = true)
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                player.play()
                _isPlaying.value = true
            }
        }
    }

    fun toggleLoop() {
        val newLoop = !_isLooping.value
        _isLooping.value = newLoop
        exoPlayer?.repeatMode = if (newLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (newLoop) {
            _isFloatingVisible.value = true
        }
    }

    fun showFloating() {
        if (_currentVideo.value != null) {
            _isFloatingVisible.value = true
        }
    }

    /**
     * User presses X: close floating video, stop playback, and reset states.
     */
    fun dismissFloating() {
        _isFloatingVisible.value = false
        _isLooping.value = false
        exoPlayer?.pause()
        _isPlaying.value = false
        _currentVideo.value = null
    }

    /**
     * Enter native Android Picture-in-Picture mode when user leaves the app.
     */
    fun enterPictureInPictureIfActive(activity: Activity): Boolean {
        if (_currentVideo.value != null && (_isLooping.value || _isFloatingVisible.value || _isPlaying.value)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val video = _currentVideo.value
                val aspectRatio = if (video != null && video.width > 0 && video.height > 0) {
                    val w = video.width.coerceIn(1, 239)
                    val h = video.height.coerceIn(1, 100)
                    try {
                        Rational(w, h)
                    } catch (_: Exception) {
                        Rational(16, 9)
                    }
                } else {
                    Rational(16, 9)
                }

                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()
                try {
                    return activity.enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    android.util.Log.e("GlobalVideoManager", "Error entering PiP", e)
                }
            }
        }
        return false
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    companion object {
        @Volatile
        private var instance: GlobalVideoManager? = null

        fun getInstance(context: Context): GlobalVideoManager {
            return instance ?: synchronized(this) {
                instance ?: GlobalVideoManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
