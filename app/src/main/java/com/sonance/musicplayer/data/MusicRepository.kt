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

    private val playCountPrefs = context.getSharedPreferences("sonance_play_counts", Context.MODE_PRIVATE)
    private val lastPlayedPrefs = context.getSharedPreferences("sonance_last_played", Context.MODE_PRIVATE)
    private val favoritesPrefs = context.getSharedPreferences("sonance_favorites", Context.MODE_PRIVATE)
    private val discoveryPrefs = context.getSharedPreferences("sonance_discovered", Context.MODE_PRIVATE)

    fun getNormTrackKey(title: String, artist: String): String {
        val t = title.trim().lowercase()
        val a = artist.trim().lowercase()
        return if (t.isNotBlank()) "$t|$a" else ""
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _settings = MutableStateFlow(PlayerSettings())
    val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    private val _currentTheme = MutableStateFlow(AppTheme.DARK_AMOLED)
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    private val _unlockedThemeIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedThemeIds: StateFlow<Set<String>> = _unlockedThemeIds.asStateFlow()

    private val _customWallpaperUri = MutableStateFlow<String?>(null)
    val customWallpaperUri: StateFlow<String?> = _customWallpaperUri.asStateFlow()

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
    val unlockedThemeIdsFlow: StateFlow<Set<String>> get() = unlockedThemeIds
    val customWallpaperUriFlow: StateFlow<String?> get() = customWallpaperUri
    val equalizerFlow: StateFlow<EqualizerSettings> get() = equalizerSettings
    val remoteSettingsFlow: StateFlow<com.sonance.musicplayer.model.RemoteBackendSettings> get() = remoteSettings
    val userSubscriptionFlow: StateFlow<com.sonance.musicplayer.model.UserSubscription> get() = userSubscription
    val userProfileFlow: StateFlow<UserProfile> get() = userProfile

    fun saveTheme(theme: AppTheme) = setTheme(theme)
    fun setCustomWallpaperUri(uri: String?) {
        _customWallpaperUri.value = uri
        prefs.edit().putString("custom_wallpaper_uri", uri).apply()
    }
    fun unlockThemeByAd(theme: AppTheme) {
        val updated = _unlockedThemeIds.value + theme.idStr
        _unlockedThemeIds.value = updated
        prefs.edit().putStringSet("unlocked_themes", updated).apply()
    }
    fun isThemeUnlocked(theme: AppTheme, isPro: Boolean): Boolean {
        if (!theme.isProOnly) return true
        if (isPro) return true
        return _unlockedThemeIds.value.contains(theme.idStr)
    }
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

            // Load Theme & Unlocked Themes
            val themeStr = prefs.getString("app_theme", AppTheme.DARK_AMOLED.idStr)
            val matchingTheme = AppTheme.entries.find { it.idStr == themeStr } ?: AppTheme.DARK_AMOLED
            _currentTheme.value = matchingTheme
            val savedUnlocked = prefs.getStringSet("unlocked_themes", emptySet()) ?: emptySet()
            _unlockedThemeIds.value = savedUnlocked
            _customWallpaperUri.value = prefs.getString("custom_wallpaper_uri", null)

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

            // Load Tracks or default - ensure only valid local user tracks are kept
            val tracksJson = prefs.getString("tracks", null)
            val savedTracks: List<Track>? = if (tracksJson != null) {
                try {
                    val parsed: List<Track> = json.decodeFromString(tracksJson)
                    parsed.filter { tr ->
                        tr.sourceType == "local" &&
                        !tr.url.startsWith("content://media/internal/") &&
                        !tr.url.contains("SoundHelix") &&
                        tr.url.isNotBlank()
                    }
                } catch (e: Exception) {
                    null
                }
            } else null

            _tracks.value = savedTracks ?: emptyList()

            // Auto-scan local storage in background
            scanMediaStore()

            // Fetch latest backend settings from Firebase Console
            syncFirebaseSettings()
        }
    }

    suspend fun scanMediaStore(): Int = withContext(Dispatchers.IO) {
        val deviceTracks = mutableListOf<Track>()
        val existingMediaIds = mutableSetOf<Long>()

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

        fun queryUri(contentUriBase: Uri, selection: String?) {
            try {
                val cursor = context.contentResolver.query(
                    contentUriBase,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
                )

                cursor?.use {
                    val idCol = it.getColumnIndex(MediaStore.Audio.Media._ID)
                    if (idCol < 0) return@use
                    val titleCol = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistCol = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albumCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val durationCol = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val dateAddedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                    val dateModifiedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                    val sizeCol = it.getColumnIndex(MediaStore.Audio.Media.SIZE)

                    while (it.moveToNext()) {
                        val mediaId = it.getLong(idCol)
                        if (existingMediaIds.contains(mediaId)) continue
                        existingMediaIds.add(mediaId)

                        val contentUri = ContentUris.withAppendedId(contentUriBase, mediaId).toString()
                        val title = (if (titleCol >= 0) it.getString(titleCol) else null)?.takeIf { t -> t.isNotBlank() } ?: "Track $mediaId"
                        val artist = (if (artistCol >= 0) it.getString(artistCol) else null)?.takeIf { a -> a.isNotBlank() && a != "<unknown>" } ?: "Unknown Artist"
                        val album = (if (albumCol >= 0) it.getString(albumCol) else null)?.takeIf { al -> al.isNotBlank() } ?: "Music"
                        val durationMs = if (durationCol >= 0) it.getLong(durationCol) else 0L
                        val dataPath = if (dataCol >= 0) it.getString(dataCol) ?: "" else ""
                        val dateAddedSec = if (dateAddedCol >= 0) it.getLong(dateAddedCol) else 0L
                        val dateModifiedSec = if (dateModifiedCol >= 0) it.getLong(dateModifiedCol) else 0L
                        val fileModified = if (dataPath.isNotEmpty()) {
                            try { File(dataPath).lastModified() } catch (_: Exception) { 0L }
                        } else 0L

                        val effectiveAdded = maxOf(dateAddedSec * 1000L, dateModifiedSec * 1000L, fileModified).takeIf { t -> t > 0L } ?: System.currentTimeMillis()
                        val sizeBytes = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                        val folder = if (dataPath.isNotEmpty()) {
                            try { File(dataPath).parent ?: "Phone Storage" } catch (_: Exception) { "Phone Storage" }
                        } else "Phone Storage"

                        val sizeMb = String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
                        val albumArtUri = Uri.parse("content://media/external/audio/media/$mediaId/albumart").toString()

                        deviceTracks.add(
                            Track(
                                id = "local-$mediaId",
                                title = title,
                                artist = artist,
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
                Log.w("MusicRepository", "MediaStore query error on $contentUriBase: ${e.message}")
            }
        }

        // Query external storage for real user music
        val broadSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 0"
        queryUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, broadSelection)

        // If no tracks found, query external without any filter (essential for Redmi / HyperOS devices)
        if (deviceTracks.isEmpty()) {
            queryUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null)
        }

        // Directly scan filesystem folders recursively to guarantee detection on all devices, SD cards, and subfolders
        scanDirectDownloadAndMusicFolders(deviceTracks)

        val existingTracks = _tracks.value
        val existingFavorites = existingTracks.filter { it.isFavorite }.map { it.id }.toSet()
        val existingLyrics = existingTracks.filter { it.lyrics.isNotBlank() }.associate { it.id to it.lyrics }

        val newTrackList = if (deviceTracks.isNotEmpty()) {
            val updatedDeviceTracks = deviceTracks.map { t ->
                val normKey = getNormTrackKey(t.title, t.artist)
                val persistentPlayCount = maxOf(
                    playCountPrefs.getInt(t.id, 0),
                    if (normKey.isNotBlank()) playCountPrefs.getInt(normKey, 0) else 0,
                    t.playCount
                )
                val persistentLastPlayed = maxOf(
                    lastPlayedPrefs.getLong(t.id, 0L),
                    if (normKey.isNotBlank()) lastPlayedPrefs.getLong(normKey, 0L) else 0L,
                    t.lastPlayed
                )
                val isFav = existingFavorites.contains(t.id) ||
                        favoritesPrefs.getBoolean(t.id, false) ||
                        (normKey.isNotBlank() && favoritesPrefs.getBoolean(normKey, false)) ||
                        t.isFavorite

                // Track discovery timestamp so newly added songs immediately show at the top of Recently Added
                val savedDiscovered = discoveryPrefs.getLong(t.id, 0L)
                val effectiveDiscovered = if (savedDiscovered > 0L) {
                    savedDiscovered
                } else {
                    val now = System.currentTimeMillis()
                    discoveryPrefs.edit().putLong(t.id, now).apply()
                    now
                }
                val effectiveAdded = maxOf(t.dateAdded, effectiveDiscovered)

                t.copy(
                    isFavorite = isFav,
                    playCount = persistentPlayCount,
                    lastPlayed = persistentLastPlayed,
                    lyrics = existingLyrics[t.id] ?: t.lyrics,
                    dateAdded = effectiveAdded
                )
            }
            // Default order for library tracks is A to Z by title
            updatedDeviceTracks.sortedWith(com.sonance.musicplayer.util.TrackComparators.TitleComparator)
        } else {
            existingTracks.filter { it.sourceType == "local" && !it.url.startsWith("content://media/internal/") }
        }

        _tracks.value = newTrackList
        persistTracks(newTrackList)
        deviceTracks.size
    }

    /**
     * Direct recursive file system inspection of standard and device-specific audio directories.
     * Ensures music on all Redmi, Tecno, Samsung, and other devices (including SD cards and subfolders)
     * is found immediately even before system MediaStore runs its periodic indexing cycle.
     */
    private fun scanDirectDownloadAndMusicFolders(deviceTracks: MutableList<Track>) {
        try {
            val candidateDirs = mutableListOf<File>()

            // 1. Android public directories
            try {
                candidateDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            } catch (_: Exception) {}
            try {
                candidateDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
            } catch (_: Exception) {}
            try {
                candidateDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS))
            } catch (_: Exception) {}
            try {
                candidateDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS))
            } catch (_: Exception) {}
            try {
                candidateDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            } catch (_: Exception) {}

            // 2. Standard device storage paths (including Redmi / Xiaomi MIUI custom paths)
            val commonPaths = listOf(
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/Music",
                "/storage/emulated/0/Audio",
                "/storage/emulated/0/Recordings",
                "/storage/emulated/0/MIUI/sound_recorder",
                "/storage/emulated/0/bluetooth",
                "/storage/emulated/0/Telegram"
            )
            for (p in commonPaths) {
                candidateDirs.add(File(p))
            }

            // 3. Removable SD Cards (e.g. /storage/ABCD-1234/)
            try {
                val externalFilesDirs = context.getExternalFilesDirs(null)
                for (f in externalFilesDirs) {
                    if (f != null) {
                        val path = f.absolutePath
                        // Extract root mount point before /Android/data/...
                        val rootIdx = path.indexOf("/Android/data/")
                        if (rootIdx > 0) {
                            val sdCardRoot = File(path.substring(0, rootIdx))
                            candidateDirs.add(sdCardRoot)
                            candidateDirs.add(File(sdCardRoot, "Music"))
                            candidateDirs.add(File(sdCardRoot, "Download"))
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                val storageDir = File("/storage")
                if (storageDir.exists() && storageDir.isDirectory) {
                    storageDir.listFiles()?.forEach { sub ->
                        if (sub.isDirectory && sub.name != "emulated" && sub.name != "self") {
                            candidateDirs.add(sub)
                            candidateDirs.add(File(sub, "Music"))
                            candidateDirs.add(File(sub, "Download"))
                        }
                    }
                }
            } catch (_: Exception) {}

            val validDirs = candidateDirs.filter { it.exists() && it.isDirectory }.distinctBy { it.absolutePath }
            val audioExts = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma")
            val existingUrls = deviceTracks.map { it.url }.toMutableSet()
            val existingTitles = deviceTracks.map { it.title.trim().lowercase() }.toMutableSet()

            var retriever: MediaMetadataRetriever? = null

            for (rootDir in validDirs) {
                try {
                    rootDir.walkTopDown()
                        .maxDepth(5)
                        .filter { file ->
                            if (file.isDirectory) {
                                // Skip hidden folders and Android private data caches
                                val name = file.name
                                !name.startsWith(".") && name != "Android" && name != "data" && name != "cache"
                            } else {
                                file.isFile && file.length() >= 10240L && file.extension.lowercase() in audioExts
                            }
                        }
                        .forEach { file ->
                            if (!file.isFile) return@forEach

                            val fileUri = Uri.fromFile(file).toString()
                            val fileNameClean = file.nameWithoutExtension.trim().lowercase()
                            if (existingUrls.contains(fileUri) || existingTitles.contains(fileNameClean)) {
                                return@forEach
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
                                retriever?.setDataSource(file.absolutePath)
                                val metaTitle = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                val metaArtist = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                val metaAlbum = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                val metaDur = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

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
                } catch (e: Exception) {
                    Log.w("MusicRepository", "Folder crawl warning on ${rootDir.absolutePath}: ${e.message}")
                }
            }
            try { retriever?.release() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w("MusicRepository", "Direct folder scan warning: ${e.message}")
        }
    }

    fun toggleFavorite(trackId: String) {
        val target = _tracks.value.find { it.id == trackId }
        val newFav = !(target?.isFavorite ?: false)
        favoritesPrefs.edit().putBoolean(trackId, newFav).apply()
        if (target != null) {
            val normKey = getNormTrackKey(target.title, target.artist)
            if (normKey.isNotBlank()) {
                favoritesPrefs.edit().putBoolean(normKey, newFav).apply()
            }
        }
        val updated = _tracks.value.map {
            if (it.id == trackId) it.copy(isFavorite = newFav) else it
        }
        _tracks.value = updated
        persistTracks(updated)
    }

    fun recordTrackPlayed(trackId: String, title: String = "", artist: String = "") {
        val now = System.currentTimeMillis()
        val targetTrack = _tracks.value.find { it.id == trackId }
        val effectiveTitle = title.ifBlank { targetTrack?.title ?: "" }
        val effectiveArtist = artist.ifBlank { targetTrack?.artist ?: "" }
        val normKey = getNormTrackKey(effectiveTitle, effectiveArtist)

        val currentCount = maxOf(
            playCountPrefs.getInt(trackId, 0),
            if (normKey.isNotBlank()) playCountPrefs.getInt(normKey, 0) else 0,
            targetTrack?.playCount ?: 0
        )
        val newCount = currentCount + 1

        playCountPrefs.edit().putInt(trackId, newCount).apply()
        if (normKey.isNotBlank()) {
            playCountPrefs.edit().putInt(normKey, newCount).apply()
        }

        lastPlayedPrefs.edit().putLong(trackId, now).apply()
        if (normKey.isNotBlank()) {
            lastPlayedPrefs.edit().putLong(normKey, now).apply()
        }

        val updated = _tracks.value.map {
            val matches = it.id == trackId || (normKey.isNotBlank() && getNormTrackKey(it.title, it.artist) == normKey)
            if (matches) {
                it.copy(
                    playCount = newCount,
                    lastPlayed = now
                )
            } else it
        }
        _tracks.value = updated
        persistTracks(updated)
        Log.i("MusicRepository", "Play recorded for '$effectiveTitle' (id=$trackId), new count=$newCount")
    }

    fun incrementPlayCount(trackId: String, title: String = "", artist: String = "") {
        recordTrackPlayed(trackId, title, artist)
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
        addTracksToPlaylist(listOf(trackId), playlistId)
    }

    fun addTracksToPlaylist(trackIds: Collection<String>, playlistId: String) {
        if (trackIds.isEmpty()) return
        val insertTop = _settings.value.addMusicToPlaylistPosition == "Top"
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                val existing = pl.trackIds.toSet()
                val toAdd = trackIds.filterNot { existing.contains(it) }
                val newTracks = if (insertTop) toAdd + pl.trackIds else pl.trackIds + toAdd
                pl.copy(trackIds = newTracks)
            } else pl
        }
        _playlists.value = updated
        persistPlaylists(updated)
    }

    fun addTracksToFavorites(trackIds: Collection<String>) {
        if (trackIds.isEmpty()) return
        val idSet = trackIds.toSet()
        val updated = _tracks.value.map {
            if (it.id in idSet) it.copy(isFavorite = true) else it
        }
        _tracks.value = updated
        persistTracks(updated)
    }

    fun deleteTracks(trackIds: Collection<String>) {
        if (trackIds.isEmpty()) return
        val idSet = trackIds.toSet()
        val toDelete = _tracks.value.filter { it.id in idSet }
        toDelete.forEach { track ->
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
        val updated = _tracks.value.filter { it.id !in idSet }
        _tracks.value = updated
        persistTracks(updated)

        // Remove from playlists
        val updatedPlaylists = _playlists.value.map { pl ->
            pl.copy(trackIds = pl.trackIds.filter { it !in idSet })
        }
        _playlists.value = updatedPlaylists
        persistPlaylists(updatedPlaylists)
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
