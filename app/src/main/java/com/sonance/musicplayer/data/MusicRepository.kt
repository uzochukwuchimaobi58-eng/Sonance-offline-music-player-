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
import com.sonance.musicplayer.util.MusicFilter
import java.io.File
import java.util.UUID

class MusicRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sonance_music_prefs", Context.MODE_PRIVATE)

    private val playCountPrefs = context.getSharedPreferences("sonance_play_counts", Context.MODE_PRIVATE)
    private val lastPlayedPrefs = context.getSharedPreferences("sonance_last_played", Context.MODE_PRIVATE)
    private val favoritesPrefs = context.getSharedPreferences("sonance_favorites", Context.MODE_PRIVATE)
    private val discoveryPrefs = context.getSharedPreferences("sonance_discovered", Context.MODE_PRIVATE)
    private val deletedPrefs = context.getSharedPreferences("sonance_deleted_tracks", Context.MODE_PRIVATE)

    fun getNormTrackKey(title: String, artist: String): String {
        val t = title.trim().lowercase()
        val a = artist.trim().lowercase()
        return if (t.isNotBlank()) "$t|$a" else ""
    }

    /**
     * Cleans up common prefixes/suffixes from titles (like "01 - ", ".mp3", "(Official Audio)")
     * to accurately detect duplicate songs across different scan sources.
     */
    fun cleanSongTitle(raw: String): String {
        var t = raw.trim()
        t = t.replace(Regex("\\.(mp3|m4a|flac|wav|aac|ogg|opus)$", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("^\\d{1,3}[\\s._-]+"), "")
        if (t.contains(" - ")) {
            val parts = t.split(" - ")
            if (parts.size == 2 && parts[1].isNotBlank()) {
                t = parts[1].trim()
            }
        }
        t = t.replace(Regex("\\[(Official.*|Audio|Video|Lyrics|HD|HQ)\\]", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\((Official.*|Audio|Video|Lyrics|HD|HQ|Remastered.*?)\\)", RegexOption.IGNORE_CASE), "")
        return t.replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    fun cleanArtist(raw: String): String {
        val a = raw.trim().lowercase()
        if (a == "<unknown>" || a == "unknown artist" || a == "unknown" || a == "download") return ""
        return a.replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun hasValidArtwork(track: Track): Boolean {
        val art = track.coverArt.trim()
        return art.isNotEmpty() && art != "null"
    }

    fun isTrackDeleted(track: Track): Boolean {
        val deletedSet = deletedPrefs.getStringSet("deleted_keys_set", emptySet()) ?: emptySet()
        if (deletedSet.isEmpty()) return false

        if (deletedSet.contains(track.id)) return true
        if (track.url.isNotBlank() && deletedSet.contains(track.url)) return true

        val filePath = if (track.url.startsWith("file://")) track.url.removePrefix("file://") else track.url
        if (filePath.isNotBlank() && deletedSet.contains(filePath)) return true

        val normKey = getNormTrackKey(track.title, track.artist)
        if (normKey.isNotBlank() && deletedSet.contains(normKey)) return true

        return false
    }

    fun markTrackAsDeleted(track: Track) {
        val currentSet = deletedPrefs.getStringSet("deleted_keys_set", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(track.id)
        if (track.url.isNotBlank()) currentSet.add(track.url)
        val filePath = if (track.url.startsWith("file://")) track.url.removePrefix("file://") else track.url
        if (filePath.isNotBlank()) currentSet.add(filePath)
        val normKey = getNormTrackKey(track.title, track.artist)
        if (normKey.isNotBlank()) currentSet.add(normKey)

        deletedPrefs.edit().putStringSet("deleted_keys_set", currentSet).apply()
    }

    fun areTracksDuplicate(t1: Track, t2: Track): Boolean {
        if (t1.id == t2.id) return true
        if (t1.url.isNotBlank() && t2.url.isNotBlank() && t1.url == t2.url) return true

        val p1 = if (t1.url.startsWith("file://")) t1.url.removePrefix("file://").lowercase() else t1.url.lowercase()
        val p2 = if (t2.url.startsWith("file://")) t2.url.removePrefix("file://").lowercase() else t2.url.lowercase()
        if (p1.isNotBlank() && p2.isNotBlank() && p1 == p2) return true

        val clean1 = cleanSongTitle(t1.title)
        val clean2 = cleanSongTitle(t2.title)

        if (clean1.isNotEmpty() && clean1 == clean2) {
            val a1 = cleanArtist(t1.artist)
            val a2 = cleanArtist(t2.artist)

            // If both have known artists
            if (a1.isNotEmpty() && a2.isNotEmpty()) {
                if (a1 == a2 || a1.contains(a2) || a2.contains(a1)) return true
            } else {
                // If one or both artists are unknown:
                if (t1.duration > 0L && t2.duration > 0L) {
                    if (Math.abs(t1.duration - t2.duration) <= 4L) return true
                } else {
                    // One has duration = 0 (Downloads or unindexed direct file)
                    if (clean1.length >= 3) return true
                }
            }
        }
        return false
    }

    /**
     * Resolves duplicates between tracks:
     * "resolve them to only with the one with artwork"
     * When two or more tracks are identified as the same song,
     * the one with artwork is strictly kept and the non-artwork duplicate is discarded.
     * All richer metadata (duration, real artist, lyrics, favorites, play count) is merged into the winner.
     */
    fun deduplicateTracks(rawTracks: List<Track>): List<Track> {
        if (rawTracks.isEmpty()) return emptyList()

        val result = mutableListOf<Track>()
        for (incoming in rawTracks) {
            if (isTrackDeleted(incoming)) continue

            val matchIndex = result.indexOfFirst { existing ->
                areTracksDuplicate(existing, incoming)
            }

            if (matchIndex < 0) {
                result.add(incoming)
            } else {
                val existing = result[matchIndex]
                val existingHasArt = hasValidArtwork(existing)
                val incomingHasArt = hasValidArtwork(incoming)

                val winner: Track
                val loser: Track

                if (incomingHasArt && !existingHasArt) {
                    // Incoming has artwork, existing does not -> Incoming WINS!
                    winner = incoming
                    loser = existing
                } else if (existingHasArt && !incomingHasArt) {
                    // Existing has artwork, incoming does not -> Existing WINS!
                    winner = existing
                    loser = incoming
                } else {
                    // Both have artwork or neither has artwork:
                    val existingIsMediaStore = existing.id.startsWith("local-") && !existing.id.startsWith("local-file-")
                    val incomingIsMediaStore = incoming.id.startsWith("local-") && !incoming.id.startsWith("local-file-")

                    if (incomingIsMediaStore && !existingIsMediaStore) {
                        winner = incoming
                        loser = existing
                    } else if (existingIsMediaStore && !incomingIsMediaStore) {
                        winner = existing
                        loser = incoming
                    } else if (incoming.duration > 0 && existing.duration <= 0) {
                        winner = incoming
                        loser = existing
                    } else {
                        winner = existing
                        loser = incoming
                    }
                }

                // Merge best metadata into winner from loser
                val mergedDuration = if (winner.duration > 0) winner.duration else loser.duration
                val mergedArtist = if (winner.artist.isNotBlank() && winner.artist != "Unknown Artist" && winner.artist != "<unknown>") {
                    winner.artist
                } else if (loser.artist.isNotBlank() && loser.artist != "Unknown Artist" && loser.artist != "<unknown>") {
                    loser.artist
                } else {
                    winner.artist
                }
                val mergedAlbum = if (winner.album.isNotBlank() && winner.album != "Music" && winner.album != "Download") {
                    winner.album
                } else if (loser.album.isNotBlank() && loser.album != "Music" && loser.album != "Download") {
                    loser.album
                } else {
                    winner.album
                }
                val mergedArt = if (hasValidArtwork(winner)) winner.coverArt else loser.coverArt
                val mergedLyrics = winner.lyrics.ifBlank { loser.lyrics }
                val mergedPlays = maxOf(winner.playCount, loser.playCount)
                val mergedFav = winner.isFavorite || loser.isFavorite
                val mergedLastPlayed = maxOf(winner.lastPlayed, loser.lastPlayed)

                val finalWinner = winner.copy(
                    duration = mergedDuration,
                    artist = mergedArtist,
                    album = mergedAlbum,
                    coverArt = mergedArt,
                    lyrics = mergedLyrics,
                    playCount = mergedPlays,
                    isFavorite = mergedFav,
                    lastPlayed = mergedLastPlayed
                )

                result[matchIndex] = finalWinner
            }
        }
        return result
    }

    fun extractEmbeddedArtwork(filePath: String, trackId: String): String {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val pic = retriever.embeddedPicture
            retriever.release()
            if (pic != null && pic.isNotEmpty()) {
                val cacheFolder = File(context.cacheDir, "albumart")
                if (!cacheFolder.exists()) cacheFolder.mkdirs()
                val artFile = File(cacheFolder, "art_${trackId.hashCode().toString().replace("-", "n")}.jpg")
                if (!artFile.exists() || artFile.length() == 0L) {
                    artFile.writeBytes(pic)
                }
                Uri.fromFile(artFile).toString()
            } else ""
        } catch (_: Exception) {
            ""
        }
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
        // Purge any unverified PRO state on app update/start
        val curSub = userSubscription.value
        if (curSub.isPro && curSub.purchaseToken.isBlank()) {
            firebaseService.updateSubscriptionLocal(com.sonance.musicplayer.model.UserSubscription())
        }

        billingManager.onPurchaseCompleted = { plan, price, orderId, purchaseToken ->
            val email = userProfile.value.email.ifBlank {
                userSubscription.value.userEmail.ifBlank { "subscriber@sonance.app" }
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

        billingManager.onNoPurchasesFound = {
            // No real active purchases in Google Play - reset to free tier
            val current = userSubscription.value
            if (current.isPro && current.purchaseToken.isBlank()) {
                val freeSub = com.sonance.musicplayer.model.UserSubscription(
                    isPro = false,
                    plan = "free",
                    syncStatus = "Free Tier"
                )
                firebaseService.updateSubscriptionLocal(freeSub)
            }
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
        return true // All themes and colors unlocked as requested
    }
    fun updateTrackDuration(trackId: String, durationSec: Long) {
        val current = _tracks.value
        val updated = current.map {
            if (it.id == trackId && (it.duration <= 0L || it.duration != durationSec)) {
                it.copy(duration = durationSec)
            } else it
        }
        _tracks.value = updated
    }
    fun saveEqualizer(eq: EqualizerSettings) = updateEqualizerSettings(eq)
    fun saveSettings(settings: PlayerSettings) = updateSettings(settings)

    fun syncFirebaseSettings() {
        coroutineScope.launch {
            firebaseService.fetchSettingsFromCloud()
            val email = userProfile.value.email
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
                    userEmail = if (current.userEmail.isBlank()) "subscriber@sonance.app" else current.userEmail,
                    syncStatus = "Sonance PRO (Active)"
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

            // Load Tracks or default - ensure only valid local user tracks are kept, deleted tracks are filtered, and duplicates are resolved to the one with artwork
            val tracksJson = prefs.getString("tracks", null)
            val savedTracks: List<Track>? = if (tracksJson != null) {
                try {
                    val parsed: List<Track> = json.decodeFromString(tracksJson)
                    val valid = parsed.filter { tr ->
                        tr.sourceType == "local" &&
                        !tr.url.startsWith("content://media/internal/") &&
                        !tr.url.contains("SoundHelix") &&
                        tr.url.isNotBlank() &&
                        MusicFilter.isMusicTrack(tr) &&
                        !isTrackDeleted(tr)
                    }
                    deduplicateTracks(valid)
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
        val existingUrls = mutableSetOf<String>()
        val existingFilePaths = mutableSetOf<String>()

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.MediaColumns.RELATIVE_PATH)
        }
        val projection = projectionList.toTypedArray()

        fun queryUri(contentUriBase: Uri, selection: String?) {
            var cursor: android.database.Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    contentUriBase,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )
            } catch (e: Exception) {
                Log.w("MusicRepository", "MediaStore query error on $contentUriBase: ${e.message}, attempting safe fallback")
                try {
                    val safeProjection = arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.MIME_TYPE,
                        MediaStore.Audio.Media.IS_MUSIC
                    )
                    cursor = context.contentResolver.query(
                        contentUriBase,
                        safeProjection,
                        selection,
                        null,
                        null
                    )
                } catch (e2: Exception) {
                    Log.e("MusicRepository", "Safe fallback query also failed on $contentUriBase: ${e2.message}")
                }
            }

            try {
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
                    val displayNameCol = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeTypeCol = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val isMusicCol = it.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)
                    val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else -1

                    while (it.moveToNext()) {
                        val mediaId = it.getLong(idCol)
                        if (existingMediaIds.contains(mediaId)) continue

                        val title = (if (titleCol >= 0) it.getString(titleCol) else null)?.takeIf { t -> t.isNotBlank() } ?: "Track $mediaId"
                        val artist = (if (artistCol >= 0) it.getString(artistCol) else null)?.takeIf { a -> a.isNotBlank() && a != "<unknown>" } ?: "Unknown Artist"
                        val album = (if (albumCol >= 0) it.getString(albumCol) else null)?.takeIf { al -> al.isNotBlank() } ?: "Music"
                        val durationMs = if (durationCol >= 0) it.getLong(durationCol) else 0L
                        val dataPath = if (dataCol >= 0) it.getString(dataCol) ?: "" else ""
                        val dateAddedSec = if (dateAddedCol >= 0) it.getLong(dateAddedCol) else 0L
                        val dateModifiedSec = if (dateModifiedCol >= 0) it.getLong(dateModifiedCol) else 0L
                        val sizeBytes = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                        val displayName = if (displayNameCol >= 0) it.getString(displayNameCol) else null
                        val mimeType = if (mimeTypeCol >= 0) it.getString(mimeTypeCol) else null
                        val isMusic = if (isMusicCol >= 0) it.getInt(isMusicCol) else null
                        val relativePath = if (relativePathCol >= 0) it.getString(relativePathCol) else null

                        // Central filter: Is this a legitimate music track?
                        // Excludes WhatsApp voice notes, Voice Recorder recordings, Sound Recorder, Call recordings, etc.
                        if (!MusicFilter.isMusicTrack(
                                relativePath = relativePath,
                                displayName = displayName,
                                mimeType = mimeType,
                                isMusic = isMusic,
                                dataPath = dataPath,
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = durationMs,
                                sizeBytes = sizeBytes
                            )
                        ) {
                            continue
                        }

                        existingMediaIds.add(mediaId)
                        if (dataPath.isNotEmpty()) existingFilePaths.add(dataPath.lowercase())

                        val contentUri = ContentUris.withAppendedId(contentUriBase, mediaId).toString()
                        existingUrls.add(contentUri)

                        val fileModified = if (dataPath.isNotEmpty()) {
                            try { File(dataPath).lastModified() } catch (_: Exception) { 0L }
                        } else 0L

                        // Use "DATE_ADDED" to sort Recently Added, newest first. Use "DATE_MODIFIED" only as a fallback.
                        val effectiveAdded = if (dateAddedSec > 0L) {
                            dateAddedSec * 1000L
                        } else if (dateModifiedSec > 0L) {
                            dateModifiedSec * 1000L
                        } else if (fileModified > 0L) {
                            fileModified
                        } else {
                            System.currentTimeMillis()
                        }

                        val folder = if (!relativePath.isNullOrBlank()) {
                            relativePath.trimEnd('/')
                        } else if (dataPath.isNotEmpty()) {
                            try { File(dataPath).parent ?: "Phone Storage" } catch (_: Exception) { "Phone Storage" }
                        } else "Phone Storage"

                        val sizeMb = String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
                        val albumIdCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                        val albumId = if (albumIdCol >= 0) it.getLong(albumIdCol) else -1L
                        val albumArtUri = if (albumId >= 0) {
                            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()
                        } else {
                            Uri.parse("content://media/external/audio/media/$mediaId/albumart").toString()
                        }

                        val track = Track(
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
                        if (!isTrackDeleted(track)) {
                            deviceTracks.add(track)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "MediaStore query error on $contentUriBase: ${e.message}")
            }
        }

        // Query external storage for audio
        queryUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null)

        // On Android 10+ (API 29+), check MediaStore.Downloads for newly downloaded Chrome songs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val downloadProjection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.MIME_TYPE,
                    MediaStore.Downloads.DATA,
                    MediaStore.Downloads.DATE_ADDED,
                    MediaStore.Downloads.DATE_MODIFIED,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.RELATIVE_PATH
                )
                val downloadCursor = context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    downloadProjection,
                    null,
                    null,
                    "${MediaStore.Downloads.DATE_ADDED} DESC"
                )
                downloadCursor?.use { dc ->
                    val dIdCol = dc.getColumnIndex(MediaStore.Downloads._ID)
                    val dNameCol = dc.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                    val dMimeCol = dc.getColumnIndex(MediaStore.Downloads.MIME_TYPE)
                    val dDataCol = dc.getColumnIndex(MediaStore.Downloads.DATA)
                    val dAddedCol = dc.getColumnIndex(MediaStore.Downloads.DATE_ADDED)
                    val dModCol = dc.getColumnIndex(MediaStore.Downloads.DATE_MODIFIED)
                    val dSizeCol = dc.getColumnIndex(MediaStore.Downloads.SIZE)
                    val dRelCol = dc.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)

                    while (dc.moveToNext()) {
                        val dId = if (dIdCol >= 0) dc.getLong(dIdCol) else continue
                        val dData = if (dDataCol >= 0) dc.getString(dDataCol) ?: "" else ""
                        val dName = if (dNameCol >= 0) dc.getString(dNameCol) ?: "" else ""
                        val dMime = if (dMimeCol >= 0) dc.getString(dMimeCol) else null
                        val dRel = if (dRelCol >= 0) dc.getString(dRelCol) else "Download/"
                        val dSize = if (dSizeCol >= 0) dc.getLong(dSizeCol) else 0L
                        val dAddedSec = if (dAddedCol >= 0) dc.getLong(dAddedCol) else 0L
                        val dModSec = if (dModCol >= 0) dc.getLong(dModCol) else 0L

                        if (dData.isNotEmpty() && existingFilePaths.contains(dData.lowercase())) continue

                        if (!MusicFilter.isMusicTrack(
                                relativePath = dRel,
                                displayName = dName,
                                mimeType = dMime,
                                dataPath = dData,
                                title = dName.substringBeforeLast("."),
                                sizeBytes = dSize
                            )
                        ) {
                            continue
                        }

                        if (dData.isNotEmpty()) existingFilePaths.add(dData.lowercase())
                        val downloadUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, dId).toString()
                        val fMod = if (dData.isNotEmpty()) try { File(dData).lastModified() } catch (_: Exception) { 0L } else 0L
                        val effAdded = if (dAddedSec > 0L) dAddedSec * 1000L else if (dModSec > 0L) dModSec * 1000L else if (fMod > 0L) fMod else System.currentTimeMillis()

                        val trackTitle = if (dName.contains(".")) dName.substringBeforeLast(".") else dName
                        val sizeMb = String.format("%.1f MB", dSize / (1024.0 * 1024.0))

                        val embeddedArt = if (dData.isNotEmpty()) extractEmbeddedArtwork(dData, "download-$dId") else ""
                        val dTrack = Track(
                            id = "download-$dId",
                            title = trackTitle,
                            artist = "Unknown Artist",
                            album = "Download",
                            duration = 0L,
                            url = downloadUri,
                            coverArt = embeddedArt,
                            folder = dRel.trimEnd('/'),
                            dateAdded = effAdded,
                            fileSize = sizeMb,
                            sourceType = "local"
                        )

                        if (!isTrackDeleted(dTrack)) {
                            deviceTracks.add(dTrack)
                        }

                        // Trigger media scanner so Android also indexes it into Audio.Media
                        if (dData.isNotEmpty()) {
                            try {
                                MediaScannerConnection.scanFile(context, arrayOf(dData), null, null)
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "MediaStore.Downloads query warning: ${e.message}")
            }
        }

        // Directly scan filesystem folders recursively to guarantee Chrome downloads and custom folders are detected
        scanDirectDownloadAndMusicFolders(deviceTracks, existingFilePaths)

        val existingTracks = _tracks.value
        val existingFavorites = existingTracks.filter { it.isFavorite }.map { it.id }.toSet()
        val existingLyrics = existingTracks.filter { it.lyrics.isNotBlank() }.associate { it.id to it.lyrics }

        val newTrackList = if (deviceTracks.isNotEmpty()) {
            val deduplicated = deduplicateTracks(deviceTracks.filter { MusicFilter.isMusicTrack(it) && !isTrackDeleted(it) })
            val updatedDeviceTracks = deduplicated.map { t ->
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

                t.copy(
                    isFavorite = isFav,
                    playCount = persistentPlayCount,
                    lastPlayed = persistentLastPlayed,
                    lyrics = existingLyrics[t.id] ?: t.lyrics,
                    dateAdded = t.dateAdded
                )
            }
            // Default order for library tracks is A to Z by title
            updatedDeviceTracks.sortedWith(com.sonance.musicplayer.util.TrackComparators.TitleComparator)
        } else {
            val validExisting = existingTracks.filter { it.sourceType == "local" && MusicFilter.isMusicTrack(it) && !it.url.startsWith("content://media/internal/") && !isTrackDeleted(it) }
            deduplicateTracks(validExisting)
        }

        _tracks.value = newTrackList
        persistTracks(newTrackList)
        newTrackList.size
    }

    /**
     * Direct recursive file system inspection of standard and device-specific audio directories.
     * Ensures music on all devices (including SD cards and subfolders) is found immediately
     * when downloaded through Chrome or saved locally.
     * Voice recording and WhatsApp folders are strictly excluded.
     */
    private fun scanDirectDownloadAndMusicFolders(
        deviceTracks: MutableList<Track>,
        existingFilePaths: MutableSet<String> = mutableSetOf()
    ) {
        try {
            val candidateDirs = mutableListOf<File>()

            // 1. Android public directories (Downloads, Music, Podcasts, Documents)
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
                candidateDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            } catch (_: Exception) {}

            // 2. Standard device storage paths (including Telegram, Snaptube, Vidmate, Xender, ShareIt, WhatsApp Audio)
            val commonPaths = listOf(
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/Music",
                "/storage/emulated/0/Audio",
                "/storage/emulated/0/bluetooth",
                "/storage/emulated/0/Telegram",
                "/storage/emulated/0/Telegram/Telegram Audio",
                "/storage/emulated/0/Snaptube",
                "/storage/emulated/0/Snaptube/download/Audio",
                "/storage/emulated/0/Vidmate",
                "/storage/emulated/0/Vidmate/download",
                "/storage/emulated/0/Audiomack",
                "/storage/emulated/0/Xender",
                "/storage/emulated/0/Xender/audio",
                "/storage/emulated/0/Shareit",
                "/storage/emulated/0/Shareit/audio",
                "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio",
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio",
                "/storage/emulated/0/Android/media/org.telegram.messenger/Telegram/Telegram Audio"
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
            val existingUrls = deviceTracks.map { it.url }.toMutableSet()
            val existingTitles = deviceTracks.map { it.title.trim().lowercase() }.toMutableSet()

            var retriever: MediaMetadataRetriever? = null

            for (rootDir in validDirs) {
                try {
                    rootDir.walkTopDown()
                        .maxDepth(5)
                        .filter { file ->
                            if (file.isDirectory) {
                                val name = file.name
                                !name.startsWith(".") &&
                                    name != "Android" &&
                                    name != "data" &&
                                    name != "cache" &&
                                    !name.equals("WhatsApp Voice Notes", ignoreCase = true) &&
                                    !name.equals("Voice Notes", ignoreCase = true) &&
                                    !name.equals("Voice Recorder", ignoreCase = true) &&
                                    !name.equals("Call Recordings", ignoreCase = true)
                            } else {
                                file.isFile && file.length() >= 10240L &&
                                    MusicFilter.isMusicTrack(
                                        displayName = file.name,
                                        dataPath = file.absolutePath,
                                        sizeBytes = file.length()
                                    )
                            }
                        }
                        .forEach { file ->
                            if (!file.isFile) return@forEach

                            val filePathLower = file.absolutePath.lowercase()
                            if (existingFilePaths.contains(filePathLower)) return@forEach

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

                            val trackId = "local-file-${file.absolutePath.hashCode()}"
                            val embeddedArt = extractEmbeddedArtwork(file.absolutePath, trackId)

                            val dirTrack = Track(
                                id = trackId,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = durationSec,
                                url = fileUri,
                                coverArt = embeddedArt,
                                folder = file.parent ?: "Download",
                                dateAdded = addedTime,
                                fileSize = sizeMb,
                                sourceType = "local"
                            )

                            if (!isTrackDeleted(dirTrack)) {
                                deviceTracks.add(dirTrack)
                            }
                            existingFilePaths.add(filePathLower)
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

    /**
     * Expose central isMusicTrack check for external consumers.
     */
    fun isMusicTrack(track: Track): Boolean = MusicFilter.isMusicTrack(track)

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
        if (!MusicFilter.isMusicTrack(track)) return
        val updated = listOf(track) + _tracks.value.filter { it.id != track.id }
        _tracks.value = updated
        persistTracks(updated)
    }

    fun deleteTrack(trackId: String) {
        deleteTracks(listOf(trackId))
    }

    fun deleteTracks(trackIds: Collection<String>) {
        if (trackIds.isEmpty()) return
        val idSet = trackIds.toSet()
        val targets = _tracks.value.filter { it.id in idSet }

        // Find all duplicate tracks in the library that match any of the targets
        val allDuplicatesToDelete = _tracks.value.filter { current ->
            current.id in idSet || targets.any { target -> areTracksDuplicate(current, target) }
        }

        val allIdsToDelete = (idSet + allDuplicatesToDelete.map { it.id }).toSet()

        // Mark all as deleted in persistent blacklist so scans never re-add them
        allDuplicatesToDelete.forEach { track ->
            markTrackAsDeleted(track)

            // Attempt physical file deletion across all supported URI and path formats
            try {
                if (track.url.startsWith("content://")) {
                    val uri = Uri.parse(track.url)
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (se: SecurityException) {
                        Log.w("MusicRepository", "Scoped storage delete restricted for ${track.url}: ${se.message}")
                    }

                    if (track.id.startsWith("local-")) {
                        val mid = track.id.removePrefix("local-").toLongOrNull()
                        if (mid != null) {
                            try {
                                context.contentResolver.delete(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    "${MediaStore.Audio.Media._ID} = ?",
                                    arrayOf(mid.toString())
                                )
                            } catch (_: Exception) {}
                        }
                    } else if (track.id.startsWith("download-")) {
                        val did = track.id.removePrefix("download-").toLongOrNull()
                        if (did != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                context.contentResolver.delete(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                    "${MediaStore.Downloads._ID} = ?",
                                    arrayOf(did.toString())
                                )
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    val path = if (track.url.startsWith("file://")) {
                        Uri.parse(track.url).path ?: track.url.removePrefix("file://")
                    } else {
                        track.url
                    }
                    if (path.isNotBlank()) {
                        val file = File(path)
                        if (file.exists()) {
                            file.delete()
                        }
                        try {
                            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Error attempting physical file deletion for '${track.title}': ${e.message}")
            }
        }

        val updated = _tracks.value.filter { it.id !in allIdsToDelete }
        _tracks.value = updated
        persistTracks(updated)

        // Remove from playlists
        val updatedPlaylists = _playlists.value.map { pl ->
            pl.copy(trackIds = pl.trackIds.filter { it !in allIdsToDelete })
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
