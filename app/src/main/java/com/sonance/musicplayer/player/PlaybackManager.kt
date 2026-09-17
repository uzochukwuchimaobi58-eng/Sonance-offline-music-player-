package com.sonance.musicplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.sonance.musicplayer.model.EqualizerSettings
import com.sonance.musicplayer.model.PlayerSettings
import com.sonance.musicplayer.model.RepeatMode
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.model.TrendingAudioEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackManager(
    private val context: Context,
    private val onTrackCompletedCallback: ((Track) -> Unit)? = null
) {
    private val tag = "PlaybackManager"

    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var visualizer: Visualizer? = null

    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _activeEffect = MutableStateFlow(TrendingAudioEffect.OFF)
    val activeEffect: StateFlow<TrendingAudioEffect> = _activeEffect.asStateFlow()

    private val _isKaraokeMode = MutableStateFlow(false)
    val isKaraokeMode: StateFlow<Boolean> = _isKaraokeMode.asStateFlow()

    private val _sleepTimerRemainingSec = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSec: StateFlow<Int?> = _sleepTimerRemainingSec.asStateFlow()
    val sleepTimerSeconds: StateFlow<Int?> get() = sleepTimerRemainingSec
    val playQueue: StateFlow<List<Track>> get() = queue

    private val _visualizerFft = MutableStateFlow(ByteArray(64))
    val visualizerFft: StateFlow<ByteArray> = _visualizerFft.asStateFlow()

    private var currentEqSettings: EqualizerSettings = EqualizerSettings()
    private var playerSettings: PlayerSettings = PlayerSettings()

    private var sensorManager: SensorManager? = null
    private var shakeListener: SensorEventListener? = null
    private var lastShakeTime = 0L
    private var headsetReceiver: BroadcastReceiver? = null
    private val notificationManager = PlaybackNotificationManager(context)

    init {
        startProgressTracker()
        setupHeadsetReceiver()
        startNotificationTracker()
    }

    private fun startNotificationTracker() {
        scope.launch {
            kotlinx.coroutines.flow.combine(_currentTrack, _isPlaying) { track, isPlaying ->
                Pair(track, isPlaying)
            }.collect { (track, isPlaying) ->
                notificationManager.updateNotification(track, isPlaying)
            }
        }
    }

    fun updateSettings(settings: PlayerSettings) {
        playerSettings = settings
        updateShakeDetection(settings.shakeToPlayNext)
    }

    private fun setupHeadsetReceiver() {
        headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        if (playerSettings.headsetPauseWhenUnplugged) {
                            pause()
                        }
                    }
                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        if (state == 1 && playerSettings.headsetPlayWhenInserted) {
                            if (!_isPlaying.value && _currentTrack.value != null) {
                                resume()
                            }
                        } else if (state == 0 && playerSettings.headsetPauseWhenUnplugged) {
                            pause()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        try {
            context.registerReceiver(headsetReceiver, filter)
        } catch (e: Exception) {
            Log.e(tag, "Failed to register headset receiver", e)
        }
    }

    private fun updateShakeDetection(enabled: Boolean) {
        try {
            if (sensorManager == null) {
                sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            }
            val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return

            if (enabled && shakeListener == null) {
                shakeListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event == null) return
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        val gX = x / SensorManager.GRAVITY_EARTH
                        val gY = y / SensorManager.GRAVITY_EARTH
                        val gZ = z / SensorManager.GRAVITY_EARTH
                        val gForce = kotlin.math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble())
                        if (gForce > 2.4) {
                            val now = System.currentTimeMillis()
                            if (now - lastShakeTime > 1500) {
                                lastShakeTime = now
                                skipToNext()
                            }
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager?.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_UI)
            } else if (!enabled && shakeListener != null) {
                sensorManager?.unregisterListener(shakeListener)
                shakeListener = null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error configuring shake sensor", e)
        }
    }

    fun addToQueue(track: Track) {
        val current = _queue.value
        if (!current.any { it.id == track.id }) {
            _queue.value = current + track
        }
    }

    fun playNext(track: Track) {
        val current = _queue.value.toMutableList()
        val currentIndex = _queueIndex.value
        current.removeAll { it.id == track.id }
        val insertAt = (currentIndex + 1).coerceAtMost(current.size)
        current.add(insertAt, track)
        _queue.value = current
    }

    fun setQueue(newQueue: List<Track>, startIndex: Int = 0) {
        if (newQueue.isEmpty()) return
        _queue.value = newQueue
        val safeIndex = startIndex.coerceIn(0, newQueue.size - 1)
        _queueIndex.value = safeIndex
        playTrack(newQueue[safeIndex], newQueue)
    }

    fun skipToNext() = next()
    fun skipToPrevious() = previous()
    fun cycleRepeatMode() = toggleRepeat()
    fun toggleKaraokeMode() = toggleKaraoke()

    fun playTrack(track: Track, newQueue: List<Track>? = null) {
        if (newQueue != null && newQueue.isNotEmpty()) {
            _queue.value = newQueue
            val idx = newQueue.indexOfFirst { it.id == track.id }
            _queueIndex.value = if (idx >= 0) idx else 0
        } else if (!_queue.value.any { it.id == track.id }) {
            _queue.value = listOf(track) + _queue.value
            _queueIndex.value = 0
        } else {
            val idx = _queue.value.indexOfFirst { it.id == track.id }
            if (idx >= 0) _queueIndex.value = idx
        }

        _currentTrack.value = track
        loadAndPlay(track)
    }

    private fun loadAndPlay(track: Track) {
        releasePlayer()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            if (track.url.startsWith("http://") || track.url.startsWith("https://") || track.url.startsWith("content://")) {
                mp.setDataSource(context, Uri.parse(track.url))
            } else if (track.url.isNotEmpty()) {
                mp.setDataSource(track.url)
            } else {
                // Fallback default url if empty
                mp.setDataSource(context, Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"))
            }

            mp.setOnPreparedListener { player ->
                _durationMs.value = player.duration.toLong().coerceAtLeast(track.duration * 1000L)
                attachAudioEffects(player.audioSessionId)
                applySpeedInternal(player, _playbackSpeed.value)
                applyVolumeInternal(player, _volume.value)
                player.start()
                _isPlaying.value = true
                onTrackCompletedCallback?.invoke(track)
            }

            mp.setOnCompletionListener {
                handleTrackCompletion()
            }

            mp.setOnErrorListener { _, what, extra ->
                Log.e(tag, "MediaPlayer error: what=$what extra=$extra")
                _isPlaying.value = false
                false
            }

            mediaPlayer = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(tag, "Error loading track ${track.title}", e)
            _isPlaying.value = false
        }
    }

    private fun handleTrackCompletion() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentTrack.value?.let { loadAndPlay(it) }
            }
            RepeatMode.ALL -> {
                next()
            }
            RepeatMode.OFF -> {
                if (_queueIndex.value < _queue.value.size - 1) {
                    next()
                } else {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L
                }
            }
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp != null) {
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
            } else {
                mp.start()
                _isPlaying.value = true
            }
        } else {
            val track = _currentTrack.value ?: _queue.value.firstOrNull()
            if (track != null) {
                playTrack(track)
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                if (playerSettings.playPauseFade) {
                    fadeVolume(1f, 0f, 250L) {
                        mp.pause()
                        _isPlaying.value = false
                        mp.setVolume(1f, 1f)
                    }
                } else {
                    mp.pause()
                    _isPlaying.value = false
                }
            }
        }
    }

    fun resume() {
        mediaPlayer?.let { mp ->
            if (!mp.isPlaying) {
                if (playerSettings.playPauseFade) {
                    mp.setVolume(0f, 0f)
                    mp.start()
                    _isPlaying.value = true
                    fadeVolume(0f, 1f, 250L)
                } else {
                    mp.start()
                    _isPlaying.value = true
                }
            }
        }
    }

    private fun fadeVolume(from: Float, to: Float, durationMs: Long = 250L, onComplete: (() -> Unit)? = null) {
        scope.launch {
            val steps = 10
            val stepTime = durationMs / steps
            for (i in 0..steps) {
                val vol = from + (to - from) * (i.toFloat() / steps)
                try {
                    mediaPlayer?.setVolume(vol, vol)
                } catch (_: Throwable) {}
                delay(stepTime)
            }
            onComplete?.invoke()
        }
    }

    fun next() {
        val q = _queue.value
        if (q.isEmpty()) return

        val nextIndex = if (_isShuffle.value) {
            (0 until q.size).random()
        } else {
            (_queueIndex.value + 1) % q.size
        }
        _queueIndex.value = nextIndex
        val nextTrack = q[nextIndex]
        _currentTrack.value = nextTrack
        loadAndPlay(nextTrack)
    }

    fun previous() {
        val q = _queue.value
        if (q.isEmpty()) return

        // If played more than 3 seconds, restart current track
        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = if (_queueIndex.value > 0) _queueIndex.value - 1 else q.size - 1
        _queueIndex.value = prevIndex
        val prevTrack = q[prevIndex]
        _currentTrack.value = prevTrack
        loadAndPlay(prevTrack)
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let {
            val clamped = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1000L))
            it.seekTo(clamped.toInt())
            _currentPositionMs.value = clamped
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        mediaPlayer?.let { applyVolumeInternal(it, clamped) }
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        mediaPlayer?.let { applySpeedInternal(it, clamped) }
    }

    fun setAudioEffect(effect: TrendingAudioEffect) {
        _activeEffect.value = effect
        when (effect) {
            TrendingAudioEffect.OFF -> {
                setSpeed(1.0f)
                bassBoost?.setStrength(currentEqSettings.bassBoost.toShort())
                virtualizer?.setStrength(currentEqSettings.spatialReverb.toShort())
            }
            TrendingAudioEffect.BASS_BOOST -> {
                setSpeed(1.0f)
                bassBoost?.setStrength(1000.toShort())
            }
            TrendingAudioEffect.SLOWED_REVERB -> {
                setSpeed(0.85f)
                virtualizer?.setStrength(800.toShort())
            }
            TrendingAudioEffect.NIGHTCORE -> {
                setSpeed(1.25f)
                bassBoost?.setStrength(400.toShort())
            }
            TrendingAudioEffect.HIFI_STUDIO -> {
                setSpeed(1.0f)
                bassBoost?.setStrength(300.toShort())
                virtualizer?.setStrength(400.toShort())
            }
        }
    }

    fun toggleKaraoke() {
        _isKaraokeMode.value = !_isKaraokeMode.value
        // Attenuate vocals or apply voice filter via Equalizer center frequencies (1kHz - 3kHz)
        equalizer?.let { eq ->
            val numBands = eq.numberOfBands
            for (i in 0 until numBands) {
                val centerFreq = eq.getCenterFreq(i.toShort()) / 1000
                if (centerFreq in 800..3500) {
                    val bandLevel: Short = if (_isKaraokeMode.value) (-1200).toShort() else (0).toShort()
                    eq.setBandLevel(i.toShort(), bandLevel)
                }
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _sleepTimerRemainingSec.value = null
            return
        }

        var remainingSeconds = minutes * 60
        _sleepTimerRemainingSec.value = remainingSeconds

        sleepTimerJob = scope.launch {
            while (remainingSeconds > 0 && isActive) {
                delay(1000L)
                remainingSeconds -= 1
                _sleepTimerRemainingSec.value = remainingSeconds
            }
            if (remainingSeconds <= 0) {
                pause()
                _sleepTimerRemainingSec.value = null
            }
        }
    }

    fun applyEqualizerSettings(settings: EqualizerSettings) {
        currentEqSettings = settings
        equalizer?.let { eq ->
            eq.enabled = settings.enabled
            if (settings.enabled) {
                val numBands = eq.numberOfBands
                val levelRange = eq.bandLevelRange
                val minLevel = levelRange[0]
                val maxLevel = levelRange[1]

                for (i in 0 until numBands) {
                    val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000
                    // Find closest band in settings
                    val targetGain = settings.bands.minByOrNull { Math.abs(it.key - centerFreqHz) }?.value ?: 0
                    // Scale -12..12 dB to minLevel..maxLevel (usually -1500..1500 millibels)
                    val scaledLevel = ((targetGain / 12f) * maxLevel).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
                    eq.setBandLevel(i.toShort(), scaledLevel.toShort())
                }
            }
        }

        bassBoost?.let { bb ->
            bb.enabled = settings.bassBoost > 0
            val strength = ((settings.bassBoost / 100f) * 1000).toInt().coerceIn(0, 1000)
            bb.setStrength(strength.toShort())
        }

        virtualizer?.let { vz ->
            vz.enabled = settings.spatialReverb > 0
            val strength = ((settings.spatialReverb / 100f) * 1000).toInt().coerceIn(0, 1000)
            vz.setStrength(strength.toShort())
        }
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId == 0) return

        try {
            equalizer?.release()
        } catch (_: Throwable) {}
        equalizer = null

        try {
            bassBoost?.release()
        } catch (_: Throwable) {}
        bassBoost = null

        try {
            virtualizer?.release()
        } catch (_: Throwable) {}
        virtualizer = null

        try {
            visualizer?.release()
        } catch (_: Throwable) {}
        visualizer = null

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = currentEqSettings.enabled
            }
        } catch (e: Throwable) {
            Log.w(tag, "Equalizer effect not supported: ${e.message}")
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Throwable) {
            Log.w(tag, "BassBoost effect not supported: ${e.message}")
        }

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Throwable) {
            Log.w(tag, "Virtualizer effect not supported: ${e.message}")
        }

        applyEqualizerSettings(currentEqSettings)

        // Visualizer requires android.permission.RECORD_AUDIO.
        // Attempting to instantiate Visualizer without RECORD_AUDIO permission
        // causes AudioFlinger to fail with status -1 / initCheck -3 in Android's native layer.
        val hasRecordAudioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasRecordAudioPermission) {
            try {
                val capRate = Visualizer.getMaxCaptureRate()
                visualizer = Visualizer(audioSessionId).apply {
                    captureSize = 64
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                            waveform?.let { _visualizerFft.value = it }
                        }
                        override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                    }, capRate / 2, true, false)
                    enabled = true
                }
            } catch (e: Throwable) {
                Log.w(tag, "Visualizer initialization skipped: ${e.message}")
            }
        }
    }

    private fun applyVolumeInternal(player: MediaPlayer, vol: Float) {
        player.setVolume(vol, vol)
    }

    private fun applySpeedInternal(player: MediaPlayer, speed: Float) {
        try {
            val params = player.playbackParams
            params.speed = speed
            player.playbackParams = params
        } catch (e: Exception) {
            Log.w(tag, "Speed change not supported on this device/file", e)
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (_isPlaying.value && mp.isPlaying) {
                        _currentPositionMs.value = mp.currentPosition.toLong()
                    }
                }
                delay(200L)
            }
        }
    }

    private fun releasePlayer() {
        try {
            equalizer?.release()
            equalizer = null
            bassBoost?.release()
            bassBoost = null
            virtualizer?.release()
            virtualizer = null
            visualizer?.release()
            visualizer = null

            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(tag, "Error releasing player", e)
        }
    }

    fun release() {
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        notificationManager.cancelNotification()
        try {
            shakeListener?.let { sensorManager?.unregisterListener(it) }
            shakeListener = null
            headsetReceiver?.let { context.unregisterReceiver(it) }
            headsetReceiver = null
        } catch (_: Exception) {}
        releasePlayer()
    }

    companion object {
        @Volatile
        private var instance: PlaybackManager? = null

        fun getInstance(
            context: Context,
            repository: com.sonance.musicplayer.data.MusicRepository? = null
        ): PlaybackManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext) { completedTrack ->
                    repository?.incrementPlayCount(completedTrack.id)
                }.also { instance = it }
            }
        }
    }
}
