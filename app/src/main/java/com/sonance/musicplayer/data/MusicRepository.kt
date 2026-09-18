package com.sonance.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.sonance.musicplayer.model.AppTheme
import com.sonance.musicplayer.model.EqPreset
import com.sonance.musicplayer.model.EqualizerSettings
import com.sonance.musicplayer.model.PlayerSettings
import com.sonance.musicplayer.model.Playlist
import com.sonance.musicplayer.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class MusicRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sonance_music_prefs", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _settings = MutableStateFlow(PlayerSettings())
    val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    private val _currentTheme = MutableStateFlow(AppTheme.DARK_AMOLED)
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    private val _equalizerSettings = MutableStateFlow(EqualizerSettings())
    val equalizerSettings: StateFlow<EqualizerSettings> = _equalizerSettings.asStateFlow()

    val firebaseService = FirebaseBackendService(context)
    val remoteSettings: StateFlow<com.sonance.musicplayer.model.RemoteBackendSettings> =
        firebaseService.remoteSettings
    val userSubscription: StateFlow<com.sonance.musicplayer.model.UserSubscription> =
        firebaseService.userSubscription

    val tracksFlow: StateFlow<List<Track>> get() = tracks
    val playlistsFlow: StateFlow<List<Playlist>> get() = playlists
    val settingsFlow: StateFlow<PlayerSettings> get() = settings
    val themeFlow: StateFlow<AppTheme> get() = currentTheme
    val equalizerFlow: StateFlow<EqualizerSettings> get() = equalizerSettings
    val remoteSettingsFlow: StateFlow<com.sonance.musicplayer.model.RemoteBackendSettings> get() = remoteSettings
    val userSubscriptionFlow: StateFlow<com.sonance.musicplayer.model.UserSubscription> get() = userSubscription

    fun saveTheme(theme: AppTheme) = setTheme(theme)
    fun saveEqualizer(eq: EqualizerSettings) = updateEqualizerSettings(eq)
    fun saveSettings(settings: PlayerSettings) = updateSettings(settings)

    fun syncFirebaseSettings() {
        coroutineScope.launch {
            firebaseService.fetchSettingsFromCloud()
            val email = userSubscription.value.userEmail
            if (email.isNotBlank()) {
                firebaseService.fetchSubscriptionFromCloud(email)
            }
        }
    }

    suspend fun updateFirebaseSettings(remote: com.sonance.musicplayer.model.RemoteBackendSettings) =
        firebaseService.pushSettingsToCloud(remote)

    fun subscribePro(plan: String, price: String, email: String, provider: String = "google") {
        coroutineScope.launch {
            val now = System.currentTimeMillis()
            val expiry = if (plan.equals("yearly", ignoreCase = true)) {
                now + 365L * 24 * 60 * 60 * 1000L
            } else 0L // 0 means lifetime

            val sub = com.sonance.musicplayer.model.UserSubscription(
                isPro = true,
                plan = plan,
                price = price,
                userEmail = email,
                userName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                authProvider = provider,
                purchaseTimestamp = now,
                expiryTimestamp = expiry,
                syncStatus = "Active Pro ($plan)"
            )
            firebaseService.updateSubscriptionLocal(sub)
            firebaseService.pushSubscriptionToCloud(sub)
        }
    }

    fun setDevProState(isPro: Boolean) {
        coroutineScope.launch {
            val current = userSubscription.value
            val updated = if (isPro) {
                current.copy(
                    isPro = true,
                    plan = if (current.plan == "free") "yearly" else current.plan,
                    price = if (current.price.isBlank()) "$1.00/yr" else current.price,
                    userEmail = if (current.userEmail.isBlank()) "tester@sonance.pro" else current.userEmail,
                    syncStatus = "Dev Pro Mode (Active)"
                )
            } else {
                current.copy(
                    isPro = false,
                    plan = "free",
                    syncStatus = "Free Tier (Ads Active)"
                )
            }
            firebaseService.updateSubscriptionLocal(updated)
            firebaseService.pushSubscriptionToCloud(updated)
        }
    }

    fun signInUser(email: String, name: String, provider: String = "google") {
        coroutineScope.launch {
            val current = userSubscription.value
            val updated = current.copy(
                userEmail = email,
                userName = name.ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } },
                authProvider = provider,
                syncStatus = "Signed in as $email"
            )
            firebaseService.updateSubscriptionLocal(updated)
            firebaseService.fetchSubscriptionFromCloud(email)
        }
    }

    fun signOutUser() {
        coroutineScope.launch {
            val guest = com.sonance.musicplayer.model.UserSubscription(
                isPro = false,
                plan = "free",
                price = "",
                userEmail = "",
                userName = "",
                authProvider = "guest",
                syncStatus = "Guest (No sign-in required)"
            )
            firebaseService.updateSubscriptionLocal(guest)
        }
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var mediaObserver: ContentObserver? = null

    init {
        loadPersistedData()
        startMediaStoreObserver()
    }

    private fun startMediaStoreObserver() {
        if (mediaObserver != null) return
        val handler = Handler(Looper.getMainLooper())
        mediaObserver = object : ContentObserver(handler) {
            private var lastScanTime = 0L
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                val now = System.currentTimeMillis()
                if (now - lastScanTime > 1500L) {
                    lastScanTime = now
                    coroutineScope.launch {
                        scanMediaStore()
                    }
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver!!
            )
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to register MediaStore ContentObserver", e)
        }
    }

    private fun loadPersistedData() {
        coroutineScope.launch {
            // Load Settings
            val settingsJson = prefs.getString("player_settings", null)
            if (settingsJson != null) {
                try {
                    _settings.value = json.decodeFromString(settingsJson)
                } catch (e: Exception) {
                    Log.e("MusicRepository", "Failed to parse settings", e)
                }
            }

            // Load Theme
            val themeStr = prefs.getString("app_theme", AppTheme.DARK_AMOLED.idStr)
            val matchingTheme = AppTheme.entries.find { it.idStr == themeStr } ?: AppTheme.DARK_AMOLED
            _currentTheme.value = matchingTheme

            // Load Equalizer
            val eqJson = prefs.getString("equalizer_settings", null)
            if (eqJson != null) {
                try {
                    _equalizerSettings.value = json.decodeFromString(eqJson)
                } catch (e: Exception) {
                    Log.e("MusicRepository", "Failed to parse eq", e)
                }
            }

            // Load Playlists
            val playlistsJson = prefs.getString("playlists", null)
            if (playlistsJson != null) {
                try {
                    _playlists.value = json.decodeFromString(playlistsJson)
                } catch (e: Exception) {
                    Log.e("MusicRepository", "Failed to parse playlists", e)
                    _playlists.value = DefaultTracks.initialPlaylists
                }
            } else {
                _playlists.value = DefaultTracks.initialPlaylists
            }

            // Load Tracks or default
            val tracksJson = prefs.getString("tracks", null)
            val savedTracks: List<Track>? = if (tracksJson != null) {
                try {
                    json.decodeFromString(tracksJson)
                } catch (e: Exception) {
                    null
                }
            } else null

            val combined = if (savedTracks.isNullOrEmpty()) {
                DefaultTracks.initialTracks
            } else {
                savedTracks
            }
            _tracks.value = combined

            // Auto-scan local storage in background
            scanMediaStore()

            // Fetch latest backend settings from Firebase Console
            syncFirebaseSettings()
        }
    }

    suspend fun scanMediaStore(): Int = withContext(Dispatchers.IO) {
        val deviceTracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (it.moveToNext()) {
                    val mediaId = it.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    ).toString()
                    val title = it.getString(titleCol) ?: "Unknown Track"
                    val artist = it.getString(artistCol) ?: "<unknown>"
                    val album = it.getString(albumCol) ?: "Music"
                    val durationMs = it.getLong(durationCol)
                    val dataPath = it.getString(dataCol) ?: ""
                    val dateAdded = it.getLong(dateAddedCol) * 1000
                    val sizeBytes = it.getLong(sizeCol)
                    val folder = if (dataPath.isNotEmpty()) {
                        File(dataPath).parent ?: "Phone Storage"
                    } else "Phone Storage"

                    val sizeMb = String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))

                    // Artwork Uri
                    val albumArtUri = Uri.parse("content://media/external/audio/media/$mediaId/albumart").toString()

                    deviceTracks.add(
                        Track(
                            id = "local-$mediaId",
                            title = title,
                            artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                            album = album,
                            duration = durationMs / 1000L,
                            url = contentUri,
                            coverArt = albumArtUri,
                            folder = folder,
                            dateAdded = dateAdded,
                            fileSize = sizeMb,
                            sourceType = "local"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error querying MediaStore", e)
        }

        val existingTracks = _tracks.value
        val existingFavorites = existingTracks.filter { it.isFavorite }.map { it.id }.toSet()
        val playCounts = existingTracks.associate { it.id to it.playCount }
        val lastPlayedMap = existingTracks.associate { it.id to it.lastPlayed }
        val lyricsMap = existingTracks.filter { it.lyrics.isNotBlank() }.associate { it.id to it.lyrics }

        val newTrackList = if (deviceTracks.isNotEmpty()) {
            val updatedDeviceTracks = deviceTracks.map { t ->
                t.copy(
                    isFavorite = existingFavorites.contains(t.id),
                    playCount = playCounts[t.id] ?: 0,
                    lastPlayed = lastPlayedMap[t.id] ?: 0L,
                    lyrics = lyricsMap[t.id] ?: t.lyrics
                )
            }
            // Keep built-in tracks too if user has only few songs, or prepend local tracks
            updatedDeviceTracks + DefaultTracks.initialTracks.filter { def ->
                deviceTracks.none { it.title.equals(def.title, ignoreCase = true) }
            }.map { def ->
                def.copy(
                    isFavorite = existingFavorites.contains(def.id),
                    playCount = playCounts[def.id] ?: def.playCount,
                    lastPlayed = lastPlayedMap[def.id] ?: def.lastPlayed,
                    lyrics = lyricsMap[def.id] ?: def.lyrics
                )
            }
        } else {
            existingTracks.ifEmpty { DefaultTracks.initialTracks }
        }

        _tracks.value = newTrackList
        persistTracks(newTrackList)
        deviceTracks.size
    }

    fun toggleFavorite(trackId: String) {
        val updated = _tracks.value.map {
            if (it.id == trackId) it.copy(isFavorite = !it.isFavorite) else it
        }
        _tracks.value = updated
        persistTracks(updated)
    }

    fun recordTrackPlayed(trackId: String) {
        val now = System.currentTimeMillis()
        val updated = _tracks.value.map {
            if (it.id == trackId) it.copy(
                playCount = it.playCount + 1,
                lastPlayed = now
            ) else it
        }
        _tracks.value = updated
        persistTracks(updated)
    }

    fun incrementPlayCount(trackId: String) {
        recordTrackPlayed(trackId)
    }

    fun saveLastPlaybackState(trackId: String, positionMs: Long, queueIds: List<String>, queueIndex: Int) {
        try {
            prefs.edit()
                .putString("last_played_track_id", trackId)
                .putLong("last_played_position_ms", positionMs)
                .putString("last_queue_ids", queueIds.joinToString(","))
                .putInt("last_queue_index", queueIndex)
                .apply()
        } catch (_: Exception) {}
    }

    fun getLastPlaybackTrackId(): String? = prefs.getString("last_played_track_id", null)
    fun getLastPlaybackPositionMs(): Long = prefs.getLong("last_played_position_ms", 0L)
    fun getLastQueueIds(): List<String> {
        val str = prefs.getString("last_queue_ids", null) ?: return emptyList()
        return str.split(",").filter { it.isNotBlank() }
    }
    fun getLastQueueIndex(): Int = prefs.getInt("last_queue_index", 0)

    fun updateLyrics(trackId: String, lyrics: String) {
        val updated = _tracks.value.map {
            if (it.id == trackId) it.copy(lyrics = lyrics) else it
        }
        _tracks.value = updated
        persistTracks(updated)
    }

    fun addTrack(track: Track) {
        val updated = listOf(track) + _tracks.value.filter { it.id != track.id }
        _tracks.value = updated
        persistTracks(updated)
    }

    fun deleteTrack(trackId: String) {
        val track = _tracks.value.find { it.id == trackId }
        if (track != null) {
            try {
                if (track.contentUri.startsWith("content://")) {
                    val uri = Uri.parse(track.contentUri)
                    context.contentResolver.delete(uri, null, null)
                } else if (track.contentUri.isNotBlank()) {
                    val file = File(track.contentUri)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val updated = _tracks.value.filter { it.id != trackId }
        _tracks.value = updated
        persistTracks(updated)

        // Remove from playlists
        val updatedPlaylists = _playlists.value.map { pl ->
            pl.copy(trackIds = pl.trackIds.filter { it != trackId })
        }
        _playlists.value = updatedPlaylists
        persistPlaylists(updatedPlaylists)
    }

    fun createPlaylist(name: String, color: String = "#38bdf8") {
        val newPl = Playlist(
            id = "pl-${UUID.randomUUID()}",
            name = name.trim().ifEmpty { "New Playlist" },
            color = color,
            trackIds = emptyList(),
            createdAt = System.currentTimeMillis()
        )
        val updated = _playlists.value + newPl
        _playlists.value = updated
        persistPlaylists(updated)
    }

    fun addTrackToPlaylist(trackId: String, playlistId: String) {
        val insertTop = _settings.value.addMusicToPlaylistPosition == "Top"
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                if (!pl.trackIds.contains(trackId)) {
                    val newTracks = if (insertTop) listOf(trackId) + pl.trackIds else pl.trackIds + trackId
                    pl.copy(trackIds = newTracks)
                } else pl
            } else pl
        }
        _playlists.value = updated
        persistPlaylists(updated)
    }

    fun removeTrackFromPlaylist(trackId: String, playlistId: String) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(trackIds = pl.trackIds.filter { it != trackId })
            } else pl
        }
        _playlists.value = updated
        persistPlaylists(updated)
    }

    fun deletePlaylist(playlistId: String) {
        val updated = _playlists.value.filter { it.id != playlistId }
        _playlists.value = updated
        persistPlaylists(updated)
    }

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
        prefs.edit().putString("app_theme", theme.idStr).apply()
    }

    fun updateEqualizerSettings(eq: EqualizerSettings) {
        _equalizerSettings.value = eq
        coroutineScope.launch {
            try {
                prefs.edit().putString("equalizer_settings", json.encodeToString(eq)).apply()
            } catch (e: Exception) {
                Log.e("MusicRepository", "Failed saving eq settings", e)
            }
        }
    }

    fun updateSettings(settings: PlayerSettings) {
        _settings.value = settings
        coroutineScope.launch {
            try {
                prefs.edit().putString("player_settings", json.encodeToString(settings)).apply()
            } catch (e: Exception) {
                Log.e("MusicRepository", "Failed saving player settings", e)
            }
        }
    }

    private fun persistTracks(list: List<Track>) {
        coroutineScope.launch {
            try {
                prefs.edit().putString("tracks", json.encodeToString(list)).apply()
            } catch (e: Exception) {
                Log.e("MusicRepository", "Failed to persist tracks", e)
            }
        }
    }

    private fun persistPlaylists(list: List<Playlist>) {
        coroutineScope.launch {
            try {
                prefs.edit().putString("playlists", json.encodeToString(list)).apply()
            } catch (e: Exception) {
                Log.e("MusicRepository", "Failed to persist playlists", e)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return instance ?: synchronized(this) {
                instance ?: MusicRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
