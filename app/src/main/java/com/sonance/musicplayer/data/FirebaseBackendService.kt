package com.sonance.musicplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.sonance.musicplayer.model.RemoteBackendSettings
import com.sonance.musicplayer.model.UserSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class FirebaseBackendService(private val context: Context) {

    companion object {
        const val PROJECT_ID = "studio-4528506733-318e2"
        const val DATABASE_ID = "ai-studio-musicplayer-5ed7924e-a272-4716-8b40-88ec4978f84c"
        const val API_KEY = "AIzaSyBhd5qy1deyOkuwG9yhocEFqujAHhLJeqE"
        const val COLLECTION_ID = "app_settings"
        const val DOCUMENT_ID = "global"
        const val SUBSCRIPTIONS_COLLECTION = "user_subscriptions"

        private const val BASE_REST_URL =
            "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/$DATABASE_ID/documents/$COLLECTION_ID/$DOCUMENT_ID"

        private const val BASE_SUBSCRIPTION_URL =
            "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/$DATABASE_ID/documents/$SUBSCRIPTIONS_COLLECTION"

        private const val PREFS_KEY_REMOTE = "cached_remote_backend_settings"
        private const val PREFS_KEY_SUBSCRIPTION = "cached_user_subscription"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sonance_firebase_prefs", Context.MODE_PRIVATE)

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _remoteSettings = MutableStateFlow(loadCachedSettings())
    val remoteSettings: StateFlow<RemoteBackendSettings> = _remoteSettings.asStateFlow()

    private val _userSubscription = MutableStateFlow(loadCachedSubscription())
    val userSubscription: StateFlow<UserSubscription> = _userSubscription.asStateFlow()

    private fun loadCachedSettings(): RemoteBackendSettings {
        val cached = prefs.getString(PREFS_KEY_REMOTE, null)
        if (!cached.isNullOrBlank()) {
            try {
                return jsonParser.decodeFromString<RemoteBackendSettings>(cached)
            } catch (e: Exception) {
                Log.e("FirebaseBackend", "Error parsing cached remote settings", e)
            }
        }
        return RemoteBackendSettings()
    }

    private fun loadCachedSubscription(): UserSubscription {
        val cached = prefs.getString(PREFS_KEY_SUBSCRIPTION, null)
        if (!cached.isNullOrBlank()) {
            try {
                return jsonParser.decodeFromString<UserSubscription>(cached)
            } catch (e: Exception) {
                Log.e("FirebaseBackend", "Error parsing cached user subscription", e)
            }
        }
        return UserSubscription()
    }

    private fun persistSubscription(subscription: UserSubscription) {
        _userSubscription.value = subscription
        try {
            val encoded = jsonParser.encodeToString(subscription)
            prefs.edit().putString(PREFS_KEY_SUBSCRIPTION, encoded).apply()
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error caching user subscription", e)
        }
    }

    fun updateSubscriptionLocal(subscription: UserSubscription) {
        persistSubscription(subscription)
    }

    suspend fun pushSubscriptionToCloud(subscription: UserSubscription): Result<UserSubscription> = withContext(Dispatchers.IO) {
        val docId = sanitizeDocId(if (subscription.userEmail.isNotBlank()) subscription.userEmail else "device_local_user")
        try {
            val urlString = "$BASE_SUBSCRIPTION_URL/$docId?key=$API_KEY"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")

            val payload = buildSubscriptionPayload(subscription)
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                conn.disconnect()
                val updated = subscription.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    syncStatus = "Saved to Cloud Firestore ($docId)"
                )
                persistSubscription(updated)
                Result.success(updated)
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                Log.w("FirebaseBackend", "Firestore subscription PATCH returned $responseCode: $errorText")
                val updated = subscription.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    syncStatus = "Saved locally (Cloud returned $responseCode)"
                )
                persistSubscription(updated)
                Result.success(updated)
            }
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error pushing subscription to Firestore", e)
            val updated = subscription.copy(
                lastSyncedAt = System.currentTimeMillis(),
                syncStatus = "Saved locally (Offline)"
            )
            persistSubscription(updated)
            Result.success(updated)
        }
    }

    suspend fun fetchSubscriptionFromCloud(email: String): Result<UserSubscription> = withContext(Dispatchers.IO) {
        if (email.isBlank()) {
            return@withContext Result.failure(Exception("No email provided"))
        }
        val docId = sanitizeDocId(email)
        try {
            val urlString = "$BASE_SUBSCRIPTION_URL/$docId?key=$API_KEY"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val responseText = reader.use { it.readText() }
                conn.disconnect()

                val parsed = parseSubscriptionDocument(responseText, email)
                persistSubscription(parsed)
                Result.success(parsed)
            } else {
                conn.disconnect()
                Result.failure(Exception("No cloud record found for $email (Code $responseCode)"))
            }
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Network error fetching subscription for $email", e)
            Result.failure(e)
        }
    }

    private fun sanitizeDocId(raw: String): String {
        return raw.trim().lowercase().replace("@", "_at_").replace(".", "_dot_").replace("/", "_")
    }

    private fun buildSubscriptionPayload(sub: UserSubscription): String {
        return """
        {
          "fields": {
            "user_email": { "stringValue": ${escapeJson(sub.userEmail)} },
            "user_name": { "stringValue": ${escapeJson(sub.userName)} },
            "is_pro": { "booleanValue": ${sub.isPro} },
            "plan": { "stringValue": ${escapeJson(sub.plan)} },
            "price": { "stringValue": ${escapeJson(sub.price)} },
            "auth_provider": { "stringValue": ${escapeJson(sub.authProvider)} },
            "purchase_timestamp": { "integerValue": "${sub.purchaseTimestamp}" },
            "expiry_timestamp": { "integerValue": "${sub.expiryTimestamp}" },
            "updatedAt": { "stringValue": "${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())}" }
          }
        }
        """.trimIndent()
    }

    private fun parseSubscriptionDocument(jsonStr: String, email: String): UserSubscription {
        try {
            val root = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val fields = root["fields"]?.jsonObject ?: return _userSubscription.value

            val isPro = fields["is_pro"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.booleanOrNull ?: false
            val plan = fields["plan"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: "free"
            val price = fields["price"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: ""
            val name = fields["user_name"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: ""
            val provider = fields["auth_provider"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: "google"
            val purchaseTs = fields["purchase_timestamp"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: System.currentTimeMillis()
            val expiryTs = fields["expiry_timestamp"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

            return UserSubscription(
                isPro = isPro,
                plan = plan,
                price = price,
                userEmail = email,
                userName = name,
                authProvider = provider,
                purchaseTimestamp = purchaseTs,
                expiryTimestamp = expiryTs,
                syncStatus = "Restored from Cloud Firestore",
                lastSyncedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error parsing subscription document", e)
            return _userSubscription.value
        }
    }

    private fun persistSettings(settings: RemoteBackendSettings) {
        _remoteSettings.value = settings
        try {
            val encoded = jsonParser.encodeToString(settings)
            prefs.edit().putString(PREFS_KEY_REMOTE, encoded).apply()
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error caching remote settings", e)
        }
    }

    suspend fun fetchSettingsFromCloud(): Result<RemoteBackendSettings> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$BASE_REST_URL?key=$API_KEY"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val responseText = reader.use { it.readText() }
                conn.disconnect()

                val parsed = parseFirestoreDocument(responseText)
                val updated = parsed.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    syncStatus = "Synced from Cloud Firestore"
                )
                persistSettings(updated)
                Result.success(updated)
            } else {
                val errorStream = conn.errorStream
                val errorText = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                Log.w("FirebaseBackend", "Firestore GET returned $responseCode: $errorText")

                val current = _remoteSettings.value.copy(
                    syncStatus = if (responseCode == 404) {
                        "Cloud doc not found (Ready to create in Console)"
                    } else {
                        "Cloud sync returned code $responseCode"
                    }
                )
                persistSettings(current)
                Result.failure(Exception("Cloud status $responseCode: $errorText"))
            }
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Network error fetching remote settings", e)
            val current = _remoteSettings.value.copy(
                syncStatus = "Offline / Connection error (${e.localizedMessage ?: "timeout"})"
            )
            persistSettings(current)
            Result.failure(e)
        }
    }

    suspend fun pushSettingsToCloud(settings: RemoteBackendSettings): Result<RemoteBackendSettings> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$BASE_REST_URL?key=$API_KEY"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")

            val payload = buildFirestorePayload(settings)
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                conn.disconnect()
                val updated = settings.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    syncStatus = "Successfully updated in Firebase Cloud"
                )
                persistSettings(updated)
                Result.success(updated)
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                Log.w("FirebaseBackend", "Firestore PATCH returned $responseCode: $errorText")
                // Even if remote push fails due to network or rules, keep local copy active
                val updated = settings.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    syncStatus = "Saved locally (Cloud returned code $responseCode)"
                )
                persistSettings(updated)
                Result.failure(Exception("Firestore PATCH failed with $responseCode: $errorText"))
            }
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error updating remote settings", e)
            val updated = settings.copy(
                lastSyncedAt = System.currentTimeMillis(),
                syncStatus = "Saved locally (Network: ${e.localizedMessage})"
            )
            persistSettings(updated)
            Result.failure(e)
        }
    }

    fun updateLocalCacheOnly(settings: RemoteBackendSettings) {
        persistSettings(settings.copy(syncStatus = "Local override active"))
    }

    private fun parseFirestoreDocument(jsonString: String): RemoteBackendSettings {
        try {
            val root = jsonParser.parseToJsonElement(jsonString).jsonObject
            val fields = root["fields"]?.jsonObject ?: return _remoteSettings.value

            fun getString(key: String, fallback: String): String {
                return fields[key]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: fallback
            }

            fun getBoolean(key: String, fallback: Boolean): Boolean {
                return fields[key]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.booleanOrNull ?: fallback
            }

            fun getInt(key: String, fallback: Int): Int {
                val intStr = fields[key]?.jsonObject?.get("integerValue")?.jsonPrimitive?.contentOrNull
                return intStr?.toIntOrNull() ?: fallback
            }

            return RemoteBackendSettings(
                announcementEnabled = getBoolean("announcement_enabled", false),
                announcementTitle = getString("announcement_title", "Sonance Studio Update"),
                announcementMessage = getString("announcement_message", "Welcome to Sonance Music Player!"),
                announcementType = getString("announcement_type", "info"),
                announcementActionUrl = getString("announcement_action_url", ""),
                latestVersion = getString("latest_version", "1.0.0"),
                minSupportedVersion = getInt("min_supported_version", 1),
                updateUrl = getString("update_url", "https://play.google.com/store/apps/details?id=com.sonance.musicplayer"),
                releaseNotes = getString("release_notes", "• Cloud settings sync\n• Performance optimizations"),
                forceUpdate = getBoolean("force_update", false),
                supportEmail = getString("support_email", "uzochukwuchimaobi58@gmail.com"),
                enableGearBillboard = getBoolean("enable_gear_billboard", true),
                defaultCrossfadeSeconds = getInt("default_crossfade_seconds", 0),
                showShuffleButtonDefault = getBoolean("show_shuffle_button_default", true),
                admobEnabled = getBoolean("admob_enabled", true),
                forceProOverride = getString("force_pro_override", "none"),
                proYearlyPrice = getString("pro_yearly_price", "$1.00"),
                proLifetimePrice = getString("pro_lifetime_price", "$2.00"),
                admobBannerUnitId = getString("admob_banner_unit_id", "ca-app-pub-3940256099942544/6300978111"),
                lastSyncedAt = System.currentTimeMillis(),
                syncStatus = "Active from Firebase"
            )
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error parsing fields from Firestore document", e)
            return _remoteSettings.value
        }
    }

    private fun buildFirestorePayload(settings: RemoteBackendSettings): String {
        return """
        {
          "fields": {
            "announcement_enabled": { "booleanValue": ${settings.announcementEnabled} },
            "announcement_title": { "stringValue": ${escapeJson(settings.announcementTitle)} },
            "announcement_message": { "stringValue": ${escapeJson(settings.announcementMessage)} },
            "announcement_type": { "stringValue": ${escapeJson(settings.announcementType)} },
            "announcement_action_url": { "stringValue": ${escapeJson(settings.announcementActionUrl)} },
            "latest_version": { "stringValue": ${escapeJson(settings.latestVersion)} },
            "min_supported_version": { "integerValue": "${settings.minSupportedVersion}" },
            "update_url": { "stringValue": ${escapeJson(settings.updateUrl)} },
            "release_notes": { "stringValue": ${escapeJson(settings.releaseNotes)} },
            "force_update": { "booleanValue": ${settings.forceUpdate} },
            "support_email": { "stringValue": ${escapeJson(settings.supportEmail)} },
            "enable_gear_billboard": { "booleanValue": ${settings.enableGearBillboard} },
            "default_crossfade_seconds": { "integerValue": "${settings.defaultCrossfadeSeconds}" },
            "show_shuffle_button_default": { "booleanValue": ${settings.showShuffleButtonDefault} },
            "admob_enabled": { "booleanValue": ${settings.admobEnabled} },
            "force_pro_override": { "stringValue": ${escapeJson(settings.forceProOverride)} },
            "pro_yearly_price": { "stringValue": ${escapeJson(settings.proYearlyPrice)} },
            "pro_lifetime_price": { "stringValue": ${escapeJson(settings.proLifetimePrice)} },
            "admob_banner_unit_id": { "stringValue": ${escapeJson(settings.admobBannerUnitId)} },
            "updatedAt": { "stringValue": "${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())}" }
          }
        }
        """.trimIndent()
    }

    private fun escapeJson(value: String): String {
        return "\"" + value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }
}
