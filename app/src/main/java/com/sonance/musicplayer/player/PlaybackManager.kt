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
import com.sonance.musicplayer.model.MusicBassSettings
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

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
    private var karaokeVocalReduction: Float = 85f

    private val _musicBassSettings = MutableStateFlow(MusicBassSettings())
    val musicBassSettings: StateFlow<MusicBassSettings> = _musicBassSettings.asStateFlow()

    private val _isBeatInstrumentalMode = MutableStateFlow(false)
    val isBeatInstrumentalMode: StateFlow<Boolean> = _isBeatInstrumentalMode.asStateFlow()

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
    var repository: com.sonance.musicplayer.data.MusicRepository? = null
    val store = com.sonance.musicplayer.data.MusicStore(context.applicationContext)
    val statsTracker = com.sonance.musicplayer.data.PlayStatsTracker(store)
    private var currentStatsTrackId: String? = null
    private val audioPrefs = context.getSharedPreferences("sonance_audio_effects", Context.MODE_PRIVATE)

    init {
        val savedBassEnabled = audioPrefs.getBoolean("bass_enabled", false)
        val savedBassBoost = audioPrefs.getInt("bass_boost", 85)
        val savedSubRumble = audioPrefs.getInt("bass_sub_rumble", 80)
        val savedPunchKick = audioPrefs.getInt("bass_punch_kick", 75)
        val savedClarity = audioPrefs.getInt("bass_clarity", 50)
        val savedPreset = audioPrefs.getString("bass_preset", "Deep Bass") ?: "Deep Bass"
        _musicBassSettings.value = MusicBassSettings(
            enabled = savedBassEnabled,
            bassBoost = savedBassBoost,
            subBassRumble = savedSubRumble,
            punchKick = savedPunchKick,
            clarityHighs = savedClarity,
            presetName = savedPreset
        )

        val savedKaraokeEnabled = audioPrefs.getBoolean("karaoke_enabled", false)
        val savedVocalReduction = audioPrefs.getFloat("karaoke_reduction", 85f)
        _isKaraokeMode.value = savedKaraokeEnabled
        karaokeVocalReduction = savedVocalReduction

        startProgressTracker()
        setupHeadsetReceiver()
        startNotificationTracker()
    }

    private fun startNotificationTracker() {
        scope.launch {
            kotlinx.coroutines.flow.combine(_currentTrack, _isPlaying) { track, isPlaying ->
                Pair(track, isPlaying)
            }.collect { (track, isPlaying) ->
                notificationManager.updateNotification(track, isPlaying, _currentPositionMs.value, _durationMs.value)
            }
        }
    }

    fun updateNotificationImmediate() {
        val track = _currentTrack.value ?: return
        notificationManager.updateNotification(track, _isPlaying.value, _currentPositionMs.value, _durationMs.value)
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
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                headsetReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (t: Throwable) {
            Log.e(tag, "Failed to register headset receiver", t)
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

    private var pendingPlayNextTrack: Track? = null

    fun addToQueue(track: Track) {
        val current = _queue.value
        if (!current.any { it.id == track.id }) {
            _queue.value = current + track
        }
    }

    fun addTracksToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val current = _queue.value.toMutableList()
        val existingIds = current.map { it.id }.toSet()
        val newTracks = tracks.filterNot { existingIds.contains(it.id) }
        if (newTracks.isNotEmpty()) {
            _queue.value = current + newTracks
        }
        if (_currentTrack.value == null && _queue.value.isNotEmpty()) {
            playTrack(_queue.value.first())
        }
    }

    fun playNext(track: Track) {
        pendingPlayNextTrack = track
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

    fun removeTracksFromQueue(trackIds: Set<String>) {
        if (trackIds.isEmpty()) return
        val current = _queue.value
        val filtered = current.filter { it.id !in trackIds }
        _queue.value = filtered

        val currentId = _currentTrack.value?.id
        if (currentId != null && currentId in trackIds) {
            if (filtered.isNotEmpty()) {
                val nextIdx = _queueIndex.value.coerceIn(0, filtered.size - 1)
                _queueIndex.value = nextIdx
                loadAndPlay(filtered[nextIdx])
            } else {
                stop()
            }
        } else {
            val newIdx = filtered.indexOfFirst { it.id == currentId }
            if (newIdx >= 0) {
                _queueIndex.value = newIdx
            }
        }
    }

    fun skipToNext() = next()
    fun skipToPrevious() = previous()
    fun cycleRepeatMode() = toggleRepeat()
    fun toggleKaraokeMode() = toggleKaraoke()

    fun playTrack(track: Track, newQueue: List<Track>? = null, startPositionMs: Long = 0L) {
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
        statsTracker.onTrackStarted(track.id)
        repository?.recordRecentPlay(track.id, track.title, track.artist)
        loadAndPlay(track, startPositionMs)
    }

    private var playJob: Job? = null

    private fun loadAndPlay(track: Track, startPositionMs: Long = 0L) {
        playJob?.cancel()
        releasePlayer()

        statsTracker.onTrackStarted(track.id)
        repository?.recordRecentPlay(track.id, track.title, track.artist)

        val initialDuration = if (track.duration > 0L) track.duration * 1000L else 0L
        _durationMs.value = initialDuration
        _currentPositionMs.value = startPositionMs

        if (initialDuration <= 0L && track.url.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    if (track.url.startsWith("content://")) {
                        retriever.setDataSource(context, Uri.parse(track.url))
                    } else if (track.url.startsWith("/") || track.url.startsWith("file://")) {
                        val path = if (track.url.startsWith("file://")) Uri.parse(track.url).path ?: track.url else track.url
                        retriever.setDataSource(path)
                    }
                    val dStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val pDur = dStr?.toLongOrNull() ?: 0L
                    retriever.release()
                    if (pDur > 0L) {
                        withContext(Dispatchers.Main) {
                            if (_durationMs.value <= 0L) {
                                _durationMs.value = pDur
                                notificationManager.updateNotification(track, _isPlaying.value, _currentPositionMs.value, pDur)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        playJob = scope.launch(Dispatchers.IO) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                // High compatibility data source resolution for all devices (Redmi, Tecno, Samsung, etc.)
                when {
                    track.url.startsWith("content://") -> {
                        val uri = Uri.parse(track.url)
                        var sourceSet = false
                        try {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                mp.setDataSource(pfd.fileDescriptor)
                                sourceSet = true
                            }
                        } catch (e: Throwable) {
                            Log.w(tag, "openFileDescriptor fallback: ${e.message}")
                        }
                        if (!sourceSet) {
                            mp.setDataSource(context, uri)
                        }
                    }
                    track.url.startsWith("file://") || track.url.startsWith("/") -> {
                        val filePath = if (track.url.startsWith("file://")) {
                            Uri.parse(track.url).path ?: track.url.removePrefix("file://")
                        } else {
                            track.url
                        }
                        val file = File(filePath)
                        if (file.exists() && file.canRead()) {
                            FileInputStream(file).use { fis ->
                                mp.setDataSource(fis.fd)
                            }
                        } else {
                            mp.setDataSource(context, Uri.parse(track.url))
                        }
                    }
                    track.url.startsWith("http://") || track.url.startsWith("https://") -> {
                        mp.setDataSource(context, Uri.parse(track.url))
                    }
                    track.url.isNotEmpty() -> {
                        mp.setDataSource(track.url)
                    }
                    else -> {
                        mp.setDataSource(context, Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"))
                    }
                }

                withContext(Dispatchers.Main) {
                    mp.setOnPreparedListener { player ->
                        val pDur = try { player.duration.toLong() } catch (_: Exception) { 0L }
                        val safeDuration = when {
                            pDur > 0L -> pDur
                            track.duration > 0L -> track.duration * 1000L
                            _durationMs.value > 0L -> _durationMs.value
                            else -> 0L
                        }
                        _durationMs.value = safeDuration
                        if (pDur > 0L && track.duration <= 0L) {
                            val durSec = pDur / 1000L
                            repository?.updateTrackDuration(track.id, durSec)
                        }
                        applySpeedInternal(player, _playbackSpeed.value)
                        applyVolumeInternal(player, _volume.value)
                        if (startPositionMs > 0L) {
                            player.seekTo(startPositionMs.toInt())
                            _currentPositionMs.value = startPositionMs
                        }
                        player.start()
                        _isPlaying.value = true
                        currentStatsTrackId = track.id
                        statsTracker.onTrackStarted(track.id)
                        repository?.recordRecentPlay(track.id, track.title, track.artist)
                        val updatedTrack = track.copy(
                            duration = if (safeDuration > 0L) safeDuration / 1000L else track.duration,
                            lastPlayed = System.currentTimeMillis()
                        )
                        _currentTrack.value = updatedTrack
                        notificationManager.updateNotification(updatedTrack, true, if (startPositionMs > 0L) startPositionMs else 0L, safeDuration)

                        // Attach audio effects asynchronously so audio HAL never freezes the UI thread
                        scope.launch(Dispatchers.IO) {
                            attachAudioEffects(player.audioSessionId)
                        }
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
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading track ${track.title}", e)
                withContext(Dispatchers.Main) {
                    _isPlaying.value = false
                }
            }
        }
    }

    private fun handleTrackCompletion() {
        val completedTrack = _currentTrack.value
        val trackId = completedTrack?.id ?: currentStatsTrackId
        if (trackId != null) {
            // Count exactly +1 when song plays to the end.
            // Duplicate events within 5s are ignored inside MusicStore.
            statsTracker.onTrackFinished(trackId)
            repository?.syncTrackPlayCount(trackId)
            if (completedTrack != null) {
                onTrackCompletedCallback?.invoke(completedTrack)
            }
        }

        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                statsTracker.resetCountedForRepeat()
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
                _currentTrack.value?.let { track ->
                    repository?.saveLastPlaybackState(
                        track.id,
                        _currentPositionMs.value,
                        _queue.value.map { it.id },
                        _queueIndex.value
                    )
                }
            } else {
                mp.start()
                _isPlaying.value = true
            }
        } else {
            val track = _currentTrack.value ?: _queue.value.firstOrNull()
            if (track != null) {
                loadAndPlay(track, _currentPositionMs.value)
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

    fun stop() {
        releasePlayer()
        _isPlaying.value = false
        _currentTrack.value = null
        _durationMs.value = 0L
        _currentPositionMs.value = 0L
        progressJob?.cancel()
        notificationManager.cancelNotification()
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

        val playNextTarget = pendingPlayNextTrack
        if (playNextTarget != null) {
            pendingPlayNextTrack = null
            val idx = q.indexOfFirst { it.id == playNextTarget.id }
            if (idx >= 0) {
                _queueIndex.value = idx
                _currentTrack.value = playNextTarget
                loadAndPlay(playNextTarget)
                return
            }
        }

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
        val clamped = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1000L))
        mediaPlayer?.let {
            it.seekTo(clamped.toInt())
        }
        _currentPositionMs.value = clamped
        notificationManager.updateNotification(_currentTrack.value, _isPlaying.value, clamped, _durationMs.value)
        _currentTrack.value?.let { track ->
            repository?.saveLastPlaybackState(
                track.id,
                clamped,
                _queue.value.map { it.id },
                _queueIndex.value
            )
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

    fun setKaraokeMode(enabled: Boolean, vocalReductionPercent: Float = 85f) {
        _isKaraokeMode.value = enabled
        karaokeVocalReduction = vocalReductionPercent
        audioPrefs.edit()
            .putBoolean("karaoke_enabled", enabled)
            .putFloat("karaoke_reduction", vocalReductionPercent)
            .apply()
        applyEffectiveAudioEffects()
    }

    fun toggleKaraoke() {
        val newState = !_isKaraokeMode.value
        setKaraokeMode(newState, karaokeVocalReduction)
    }

    fun applyKaraokeProcessing(enabled: Boolean, vocalReductionPercent: Float = 85f) {
        setKaraokeMode(enabled, vocalReductionPercent)
    }

    fun applyMusicBass(settings: MusicBassSettings) {
        _musicBassSettings.value = settings
        audioPrefs.edit()
            .putBoolean("bass_enabled", settings.enabled)
            .putInt("bass_boost", settings.bassBoost)
            .putInt("bass_sub_rumble", settings.subBassRumble)
            .putInt("bass_punch_kick", settings.punchKick)
            .putInt("bass_clarity", settings.clarityHighs)
            .putString("bass_preset", settings.presetName)
            .apply()
        applyEffectiveAudioEffects()
    }

    fun applyEffectiveAudioEffects() {
        val bassSettings = _musicBassSettings.value
        val karaokeActive = _isKaraokeMode.value

        // 1. Hardware BassBoost Engine: Active when Music Bass is enabled or Equalizer Bass Boost is > 0
        bassBoost?.let { bb ->
            try {
                if (bassSettings.enabled) {
                    bb.enabled = true
                    val strength = ((bassSettings.bassBoost / 100f) * 1000).toInt().coerceIn(200, 1000).toShort()
                    bb.setStrength(strength)
                } else if (currentEqSettings.bassBoost > 0) {
                    bb.enabled = true
                    val str = ((currentEqSettings.bassBoost / 100f) * 1000).toInt().coerceIn(0, 1000).toShort()
                    bb.setStrength(str)
                } else {
                    bb.enabled = false
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to apply bass boost: ${e.message}")
            }
        }

        // 2. Hardware 3D Virtualizer: In Karaoke mode, expand stereo field to suppress center-panned vocals
        virtualizer?.let { vz ->
            try {
                if (karaokeActive) {
                    vz.enabled = true
                    vz.setStrength(850.toShort())
                } else if (currentEqSettings.spatialReverb > 0) {
                    vz.enabled = true
                    val strength = ((currentEqSettings.spatialReverb / 100f) * 1000).toInt().coerceIn(0, 1000)
                    vz.setStrength(strength.toShort())
                } else {
                    vz.enabled = false
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to apply virtualizer: ${e.message}")
            }
        }

        // 3. Hardware Equalizer bands
        equalizer?.let { eq ->
            try {
                eq.enabled = true
                val numBands = eq.numberOfBands
                val levelRange = eq.bandLevelRange
                val minLevel = levelRange[0] // e.g. -1500 millibels (-15 dB)
                val maxLevel = levelRange[1] // e.g. +1500 millibels (+15 dB)

                if (karaokeActive) {
                    // Deep vocal cut: center frequencies of human vocal cords (200Hz - 4500Hz)
                    val cutRatio = (karaokeVocalReduction / 100f).coerceIn(0.6f, 1.0f)
                    val vocalCutLevel = (minLevel * cutRatio).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()

                    // If bass is also enabled, use bassBoost/subBass settings for low frequencies
                    val subBassBoost = if (bassSettings.enabled) {
                        val subBassRatio = (bassSettings.subBassRumble / 100f).coerceIn(0f, 1f)
                        ((subBassRatio * 0.75f + 0.25f) * maxLevel).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                    } else {
                        (maxLevel * 0.35f).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                    }

                    for (i in 0 until numBands) {
                        val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000
                        val bandLevel: Short = when {
                            // Low end (rhythm / kick / bass): keep strong or boosted
                            centerFreqHz < 200 -> subBassBoost
                            // Human vocal range: severely attenuate center vocals
                            centerFreqHz in 200..4500 -> vocalCutLevel
                            // High frequencies (air, shimmer): keep clear so instrumentals sound crisp
                            else -> (maxLevel * 0.20f).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                        }
                        eq.setBandLevel(i.toShort(), bandLevel)
                    }
                } else if (bassSettings.enabled) {
                    // Music Bass DSP profile
                    val subBassRatio = (bassSettings.subBassRumble / 100f).coerceIn(0f, 1f)
                    val punchRatio = (bassSettings.punchKick / 100f).coerceIn(0f, 1f)
                    val clarityRatio = (bassSettings.clarityHighs / 100f).coerceIn(0f, 1f)

                    for (i in 0 until numBands) {
                        val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000
                        val targetLevel: Short = when {
                            centerFreqHz < 100 -> {
                                val boost = ((subBassRatio * 0.75f + 0.25f) * maxLevel).toInt()
                                boost.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                            }
                            centerFreqHz in 100..350 -> {
                                val boost = ((punchRatio * 0.65f + 0.15f) * maxLevel).toInt()
                                boost.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                            }
                            centerFreqHz in 351..4000 -> 0.toShort()
                            else -> {
                                val treble = (((clarityRatio - 0.5f) * 0.5f) * maxLevel).toInt()
                                treble.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                            }
                        }
                        eq.setBandLevel(i.toShort(), targetLevel)
                    }
                } else if (currentEqSettings.enabled) {
                    for (i in 0 until numBands) {
                        val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000
                        val targetGain = currentEqSettings.bands.minByOrNull { Math.abs(it.key - centerFreqHz) }?.value ?: 0
                        val scaledLevel = ((targetGain / 12f) * maxLevel).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
                        eq.setBandLevel(i.toShort(), scaledLevel.toShort())
                    }
                } else {
                    for (i in 0 until numBands) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to apply combined audio effects: ${e.message}")
            }
        }
    }

    fun setBeatInstrumentalProcessing(
        enabled: Boolean,
        vocalLevel: Float = 0f,
        drumsLevel: Float = 120f,
        bassLevel: Float = 130f,
        melodyLevel: Float = 100f
    ) {
        _isBeatInstrumentalMode.value = enabled
        if (enabled) {
            applyMusicBass(
                MusicBassSettings(
                    enabled = true,
                    bassBoost = (bassLevel / 2f).toInt().coerceIn(0, 100),
                    subBassRumble = (bassLevel / 2f).toInt().coerceIn(0, 100),
                    punchKick = (drumsLevel / 2f).toInt().coerceIn(0, 100),
                    clarityHighs = (melodyLevel / 2f).toInt().coerceIn(0, 100),
                    presetName = "Music Bass"
                )
            )
        } else {
            applyMusicBass(_musicBassSettings.value.copy(enabled = false))
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
        try {
            if (equalizer == null) {
                val sid = mediaPlayer?.audioSessionId ?: 0
                if (sid != 0) {
                    attachAudioEffects(sid)
                }
            }
            equalizer?.let { eq ->
                eq.enabled = settings.enabled
                if (settings.enabled) {
                    val numBands = eq.numberOfBands
                    val levelRange = eq.bandLevelRange
                    val minLevel = levelRange[0]
                    val maxLevel = levelRange[1]

                    for (i in 0 until numBands) {
                        val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000
                        val targetGain = settings.bands.minByOrNull { Math.abs(it.key - centerFreqHz) }?.value ?: 0
                        val scaledLevel = ((targetGain / 12f) * maxLevel).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
                        eq.setBandLevel(i.toShort(), scaledLevel.toShort())
                    }
                }
            }

            bassBoost?.let { bb ->
                bb.enabled = settings.enabled && settings.bassBoost > 0
                val strength = ((settings.bassBoost / 100f) * 1000).toInt().coerceIn(0, 1000)
                bb.setStrength(strength.toShort())
            }

            virtualizer?.let { vz ->
                vz.enabled = settings.enabled && settings.spatialReverb > 0
                val strength = ((settings.spatialReverb / 100f) * 1000).toInt().coerceIn(0, 1000)
                vz.setStrength(strength.toShort())
            }
        } catch (e: Throwable) {
            Log.w(tag, "applyEqualizerSettings error: ${e.message}")
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

        applyEffectiveAudioEffects()

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

    private var lastSaveTime = 0L

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (_isPlaying.value && mp.isPlaying) {
                        val pos = mp.currentPosition.toLong()
                        _currentPositionMs.value = pos
                        if ((_durationMs.value <= 0L || _durationMs.value < pos) && mp.duration > 0) {
                            _durationMs.value = mp.duration.toLong()
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastSaveTime > 2000L) {
                            lastSaveTime = now
                            _currentTrack.value?.let { track ->
                                repository?.saveLastPlaybackState(
                                    track.id,
                                    pos,
                                    _queue.value.map { it.id },
                                    _queueIndex.value
                                )
                            }
                        }
                    }
                }
                delay(200L)
            }
        }
    }

    fun restoreLastPlaybackState(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (_currentTrack.value != null) return

        val repo = repository ?: return
        val lastTrackId = repo.getLastPlaybackTrackId() ?: return
        val track = tracks.find { it.id == lastTrackId } ?: return
        val lastPos = repo.getLastPlaybackPositionMs()
        val queueIds = repo.getLastQueueIds()
        val queueIdx = repo.getLastQueueIndex()

        val restoredQueue = if (queueIds.isNotEmpty()) {
            val q = queueIds.mapNotNull { qId -> tracks.find { it.id == qId } }
            if (q.isNotEmpty()) q else listOf(track)
        } else listOf(track)

        _queue.value = restoredQueue
        _queueIndex.value = queueIdx.coerceIn(0, restoredQueue.size - 1)
        _currentTrack.value = track
        _currentPositionMs.value = lastPos
        _durationMs.value = track.duration * 1000L
        notificationManager.updateNotification(track, false, lastPos, _durationMs.value)
    }

    fun toggleFavoriteCurrent() {
        val track = _currentTrack.value ?: return
        val newFav = !track.isFavorite
        repository?.toggleFavorite(track.id)
        val updatedTrack = track.copy(isFavorite = newFav)
        _currentTrack.value = updatedTrack
        _queue.value = _queue.value.map { if (it.id == track.id) updatedTrack else it }
        notificationManager.updateNotification(updatedTrack, _isPlaying.value, _currentPositionMs.value, _durationMs.value)
    }

    fun stopPlaybackAndDismiss() {
        try {
            mediaPlayer?.pause()
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        _isPlaying.value = false
        _currentTrack.value?.let { track ->
            repository?.saveLastPlaybackState(
                track.id,
                _currentPositionMs.value,
                _queue.value.map { it.id },
                _queueIndex.value
            )
        }
        notificationManager.cancelNotification()
    }

    private fun releasePlayer() {
        val oldPlayer = mediaPlayer
        mediaPlayer = null
        val oldEq = equalizer
        equalizer = null
        val oldBb = bassBoost
        bassBoost = null
        val oldVz = virtualizer
        virtualizer = null
        val oldVis = visualizer
        visualizer = null

        scope.launch(Dispatchers.IO) {
            try {
                oldVis?.release()
            } catch (_: Throwable) {}
            try {
                oldEq?.release()
            } catch (_: Throwable) {}
            try {
                oldBb?.release()
            } catch (_: Throwable) {}
            try {
                oldVz?.release()
            } catch (_: Throwable) {}
            try {
                oldPlayer?.stop()
                oldPlayer?.release()
            } catch (_: Throwable) {}
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
            val inst = instance ?: synchronized(this) {
                instance ?: PlaybackManager(context.applicationContext) { _ ->
                    // Completion handling is managed strictly once by statsTracker inside handleTrackCompletion()
                }.also { instance = it }
            }
            if (repository != null) {
                inst.repository = repository
            }
            return inst
        }
    }
}
