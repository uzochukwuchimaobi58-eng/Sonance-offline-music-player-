package com.sonance.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.util.Log
import com.sonance.musicplayer.model.AppTheme
import com.sonance.musicplayer.model.EqPreset
import com.sonance.musicplayer.model.EqualizerSettings
import com.sonance.musicplayer.model.PlayerSettings
import com.sonance.musicplayer.model.Playlist
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.model.UserProfile
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val billingManager = com.sonance.musicplayer.billing.GooglePlayBillingManager.getInstance(context)

    val remoteSettings: StateFlow<com.sonance.musicplayer.model.RemoteBackendSettings> =
        firebaseService.remoteSettings
    val userSubscription: StateFlow<com.sonance.musicplayer.model.UserSubscription> =
        firebaseService.userSubscription
    val userProfile: StateFlow<UserProfile> = firebaseService.userProfile

    init {
        billingManager.onPurchaseCompleted = { plan, price, orderId, purchaseToken ->
            val email = userProfile.value.email.ifBlank {
                userSubscription.value.userEmail.ifBlank { "uzochukwuchimaobi58@gmail.com" }
            }
            subscribePro(
                plan = plan,
                price = price,
                email = email,
                provider = "google_play",
                orderId = orderId,
                purchaseToken = purchaseToken
            )
        }
    }

    val tracksFlow: StateFlow<List<Track>> get() = tracks
    val playlistsFlow: StateFlow<List<Playlist>> get() = playlists
    val settingsFlow: StateFlow<PlayerSettings> get() = settings
    val themeFlow: StateFlow<AppTheme> get() = currentTheme
    val equalizerFlow: StateFlow<EqualizerSettings> get() = equalizerSettings
    val remoteSettingsFlow: StateFlow<com.sonance.musicplayer.model.RemoteBackendSettings> get() = remoteSettings
    val userSubscriptionFlow: StateFlow<com.sonance.musicplayer.model.UserSubscription> get() = userSubscription
    val userProfileFlow: StateFlow<UserProfile> get() = userProfile

    fun saveTheme(theme: AppTheme) = setTheme(theme)
    fun saveEqualizer(eq: EqualizerSettings) = updateEqualizerSettings(eq)
    fun saveSettings(settings: PlayerSettings) = updateSettings(settings)

    fun syncFirebaseSettings() {
        coroutineScope.launch {
            firebaseService.fetchSettingsFromCloud()
            val email = userProfile.value.email.ifBlank { userSubscription.value.userEmail }
            if (email.isNotBlank()) {
                firebaseService.fetchSubscriptionFromCloud(email)
                firebaseService.fetchUserProfileFromFirestore(email)
            }
        }
    }

    suspend fun updateFirebaseSettings(remote: com.sonance.musicplayer.model.RemoteBackendSettings) =
        firebaseService.pushSettingsToCloud(remote)

    fun signInWithEmail(email: String, password: String, onResult: (Result<UserProfile>) -> Unit) {
        coroutineScope.launch {
            val res = firebaseService.signInWithEmail(email, password)
            if (res.isSuccess) {
                val profile = res.getOrNull()!!
                if (profile.isPro) {
                    firebaseService.updateSubscriptionLocal(
                        userSubscription.value.copy(
                            isPro = true,
                            plan = profile.plan,
                            userEmail = profile.email,
                            userName = profile.displayName,
                            authProvider = "email"
                        )
                    )
                }
            }
            withContext(Dispatchers.Main) {
                onResult(res)
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, displayName: String, onResult: (Result<UserProfile>) -> Unit) {
        coroutineScope.launch {
            val res = firebaseService.signUpWithEmail(email, password, displayName)
            withContext(Dispatchers.Main) {
                onResult(res)
            }
        }
    }

    fun signInWithGoogle(email: String, displayName: String = "", onResult: (Result<UserProfile>) -> Unit) {
        coroutineScope.launch {
            val res = firebaseService.signInWithGoogle(email, displayName)
            if (res.isSuccess) {
                val profile = res.getOrNull()!!
                if (profile.isPro) {
                    firebaseService.updateSubscriptionLocal(
                        userSubscription.value.copy(
                            isPro = true,
                            plan = profile.plan,
                            userEmail = profile.email,
                            userName = profile.displayName,
                            authProvider = "google"
                        )
                    )
                }
            }
            withContext(Dispatchers.Main) {
                onResult(res)
            }
        }
    }

    fun sendPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        coroutineScope.launch {
            val res = firebaseService.sendPasswordReset(email)
            withContext(Dispatchers.Main) {
                onResult(res)
            }
        }
    }

    fun syncUserProfileToFirebaseConsole(onResult: ((Result<UserProfile>) -> Unit)? = null) {
        coroutineScope.launch {
            val current = userProfile.value
            val playlistCount = _playlists.value.size
            val favoriteCount = _tracks.value.count { it.isFavorite }
            val res = firebaseService.pushUserProfileToFirestore(current, playlistCount, favoriteCount)
            withContext(Dispatchers.Main) {
                onResult?.invoke(res)
            }
        }
    }

    fun subscribePro(
        plan: String,
        price: String,
        email: String,
        provider: String = "google_play",
        orderId: String = "",
        purchaseToken: String = ""
    ) {
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
                syncStatus = "Active Pro ($plan)",
                orderId = orderId,
                purchaseToken = purchaseToken,
                productId = if (plan == "yearly") com.sonance.musicplayer.billing.GooglePlayBillingManager.PRODUCT_YEARLY else com.sonance.musicplayer.billing.GooglePlayBillingManager.PRODUCT_LIFETIME
            )
            firebaseService.updateSubscriptionLocal(sub)
            firebaseService.pushSubscriptionToCloud(sub)

            // Also update user profile
            val currentProfile = userProfile.value
            if (currentProfile.isSignedIn) {
                val updatedProfile = currentProfile.copy(isPro = true, plan = plan)
                firebaseService.updateProfileLocal(updatedProfile)
                firebaseService.pushUserProfileToFirestore(updatedProfile, _playlists.value.size, _tracks.value.count { it.isFavorite })
            }
        }
    }

    fun restorePurchases(onResult: (Boolean, String) -> Unit) {
        billingManager.queryExistingPurchases { success, message ->
            onResult(success, message)
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

            val profile = userProfile.value
            if (profile.isSignedIn) {
                val updatedProf = profile.copy(isPro = isPro, plan = if (isPro) "yearly" else "free")
                firebaseService.updateProfileLocal(updatedProf)
                firebaseService.pushUserProfileToFirestore(updatedProf)
            }
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
            firebaseService.signInWithGoogle(email, name)
        }
    }

    fun signOutUser() {
        coroutineScope.launch {
            firebaseService.signOutProfile()
        }
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MusicRepository", "Background coroutine error: ${throwable.message}", throwable)
    }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
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
                if (now - lastScanTime > 300L) {
                    lastScanTime = now
                    coroutineScope.launch {
                        scanMediaStore()
                    }
                }
            }
        }
        try {
            // Register on MediaStore Audio
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver!!
            )
            // Register on Files (catches newly created downloads before media tagging)
            context.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                true,
                mediaObserver!!
            )
            // Register on Downloads for Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.registerContentObserver(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    true,
                    mediaObserver!!
                )
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to register ContentObserver", e)
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
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE
        )
        // Broad selection ensuring browser downloads and newly added songs are captured immediately
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 0 OR ${MediaStore.Audio.Media.DATA} LIKE '%.mp3' OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.aac' OR ${MediaStore.Audio.Media.DATA} LIKE '%.wav' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac' OR ${MediaStore.Audio.Media.DATA} LIKE '%.ogg' OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
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
                    val dateAddedSec = it.getLong(dateAddedCol)
                    val dateModifiedSec = if (dateModifiedCol >= 0) it.getLong(dateModifiedCol) else 0L
                    val fileModified = if (dataPath.isNotEmpty()) {
                        try { File(dataPath).lastModified() } catch (_: Exception) { 0L }
                    } else 0L

                    // Effective timestamp: latest of dateAdded, dateModified, or file last modified
                    val effectiveAdded = maxOf(dateAddedSec * 1000L, dateModifiedSec * 1000L, fileModified).takeIf { t -> t > 0L } ?: System.currentTimeMillis()

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
                            dateAdded = effectiveAdded,
                            fileSize = sizeMb,
                            sourceType = "local"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error querying MediaStore", e)
        }

        // Also directly scan Download and Music folders so newly downloaded files from Chrome appear immediately
        scanDirectDownloadAndMusicFolders(deviceTracks)

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
            // Sort device tracks by dateAdded descending so recently added/downloaded files appear first
            val sortedDevice = updatedDeviceTracks.sortedByDescending { it.dateAdded }

            // Keep built-in tracks too if user has only few songs, placed after local device tracks
            sortedDevice + DefaultTracks.initialTracks.filter { def ->
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

    /**
     * Direct file system inspection of standard Download and Music directories.
     * Ensures music freshly downloaded from Chrome is detected and added immediately
     * even before system MediaStore runs its periodic indexing cycle.
     */
    private fun scanDirectDownloadAndMusicFolders(deviceTracks: MutableList<Track>) {
        try {
            val candidateDirs = mutableListOf<File>()
            try {
                candidateDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            } catch (_: Exception) {}
            try {
                candidateDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
            } catch (_: Exception) {}
            candidateDirs.add(File("/storage/emulated/0/Download"))
            candidateDirs.add(File("/storage/emulated/0/Music"))

            val validDirs = candidateDirs.filter { it.exists() && it.isDirectory }.distinctBy { it.absolutePath }
            val audioExts = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
            val existingUrls = deviceTracks.map { it.url }.toMutableSet()
            val existingTitles = deviceTracks.map { it.title.trim().lowercase() }.toMutableSet()

            var retriever: MediaMetadataRetriever? = null

            for (dir in validDirs) {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (!file.isFile || file.length() < 10240) continue // Skip non-files or <10KB
                    val ext = file.extension.lowercase()
                    if (ext in audioExts) {
                        val fileUri = Uri.fromFile(file).toString()
                        val fileNameClean = file.nameWithoutExtension.trim().lowercase()
                        if (existingUrls.contains(fileUri) || existingTitles.contains(fileNameClean)) {
                            continue
                        }

                        // Trigger MediaScannerConnection so Android indexes it
                        try {
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(file.absolutePath),
                                null,
                                null
                            )
                        } catch (_: Exception) {}

                        if (retriever == null) {
                            retriever = MediaMetadataRetriever()
                        }

                        var title = file.nameWithoutExtension
                        var artist = "Unknown Artist"
                        var album = file.parentFile?.name ?: "Download"
                        var durationSec = 0L

                        try {
                            retriever.setDataSource(file.absolutePath)
                            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                            val metaDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                            if (!metaTitle.isNullOrBlank()) title = metaTitle.trim()
                            if (!metaArtist.isNullOrBlank() && metaArtist != "<unknown>") artist = metaArtist.trim()
                            if (!metaAlbum.isNullOrBlank()) album = metaAlbum.trim()
                            if (!metaDur.isNullOrBlank()) durationSec = (metaDur.toLongOrNull() ?: 0L) / 1000L
                        } catch (_: Exception) {}

                        val sizeMb = String.format("%.1f MB", file.length() / (1024.0 * 1024.0))
                        val addedTime = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()

                        deviceTracks.add(
                            Track(
                                id = "local-file-${file.absolutePath.hashCode()}",
                                title = title,
                                artist = artist,
                                album = album,
                                duration = durationSec,
                                url = fileUri,
                                coverArt = "",
                                folder = file.parent ?: "Download",
                                dateAdded = addedTime,
                                fileSize = sizeMb,
                                sourceType = "local"
                            )
                        )
                        existingUrls.add(fileUri)
                        existingTitles.add(title.trim().lowercase())
                    }
                }
            }
            try { retriever?.release() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w("MusicRepository", "Direct folder scan warning: ${e.message}")
        }
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
