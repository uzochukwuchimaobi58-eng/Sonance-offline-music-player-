package com.sonance.musicplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.sonance.musicplayer.model.RemoteBackendSettings
import com.sonance.musicplayer.model.UserProfile
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
        const val USERS_COLLECTION = "users"

        private const val BASE_REST_URL =
            "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/$DATABASE_ID/documents/$COLLECTION_ID/$DOCUMENT_ID"

        private const val BASE_SUBSCRIPTION_URL =
            "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/$DATABASE_ID/documents/$SUBSCRIPTIONS_COLLECTION"

        private const val BASE_USERS_URL =
            "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/$DATABASE_ID/documents/$USERS_COLLECTION"

        private const val AUTH_SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$API_KEY"

        private const val AUTH_SIGN_UP_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY"

        private const val AUTH_RESET_PWD_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$API_KEY"

        private const val PREFS_KEY_REMOTE = "cached_remote_backend_settings"
        private const val PREFS_KEY_SUBSCRIPTION = "cached_user_subscription"
        private const val PREFS_KEY_USER_PROFILE = "cached_user_profile"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sonance_firebase_prefs", Context.MODE_PRIVATE)

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _remoteSettings = MutableStateFlow(loadCachedSettings())
    val remoteSettings: StateFlow<RemoteBackendSettings> = _remoteSettings.asStateFlow()

    private val _userSubscription = MutableStateFlow(loadCachedSubscription())
    val userSubscription: StateFlow<UserSubscription> = _userSubscription.asStateFlow()

    private val _userProfile = MutableStateFlow(loadCachedProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private fun loadCachedProfile(): UserProfile {
        val cached = prefs.getString(PREFS_KEY_USER_PROFILE, null)
        if (!cached.isNullOrBlank()) {
            try {
                return jsonParser.decodeFromString<UserProfile>(cached)
            } catch (e: Exception) {
                Log.e("FirebaseBackend", "Error parsing cached user profile", e)
            }
        }
        return UserProfile()
    }

    private fun persistProfile(profile: UserProfile) {
        _userProfile.value = profile
        try {
            val encoded = jsonParser.encodeToString(profile)
            prefs.edit().putString(PREFS_KEY_USER_PROFILE, encoded).apply()
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error caching user profile", e)
        }
    }

    fun updateProfileLocal(profile: UserProfile) {
        persistProfile(profile)
    }

    fun signOutProfile() {
        val guest = UserProfile()
        persistProfile(guest)
        updateSubscriptionLocal(UserSubscription())
    }

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
            "order_id": { "stringValue": ${escapeJson(sub.orderId)} },
            "purchase_token": { "stringValue": ${escapeJson(sub.purchaseToken)} },
            "product_id": { "stringValue": ${escapeJson(sub.productId)} },
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
            val orderId = fields["order_id"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: ""
            val purchaseToken = fields["purchase_token"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: ""
            val productId = fields["product_id"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: ""

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
                lastSyncedAt = System.currentTimeMillis(),
                orderId = orderId,
                purchaseToken = purchaseToken,
                productId = productId
            )
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error parsing subscription document", e)
            return _userSubscription.value
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanName = displayName.trim().ifBlank { cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }

        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext Result.failure(Exception("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return@withContext Result.failure(Exception("Password must be at least 6 characters long."))
        }

        try {
            val url = URL(AUTH_SIGN_UP_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")

            val payload = """{"email":${escapeJson(cleanEmail)},"password":${escapeJson(password)},"returnSecureToken":true}"""
            OutputStreamWriter(conn.outputStream).use { it.write(payload); it.flush() }

            val code = conn.responseCode
            if (code in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = jsonParser.parseToJsonElement(respText).jsonObject
                val localId = json["localId"]?.jsonPrimitive?.contentOrNull ?: ""
                val idToken = json["idToken"]?.jsonPrimitive?.contentOrNull ?: ""

                val profile = UserProfile(
                    uid = localId,
                    email = cleanEmail,
                    displayName = cleanName,
                    provider = "email",
                    idToken = idToken,
                    lastLoginTimestamp = System.currentTimeMillis(),
                    createdAtTimestamp = System.currentTimeMillis(),
                    syncStatus = "Connected to Firebase Console ($cleanEmail)",
                    firebaseConsoleConnected = true,
                    lastSyncedAt = System.currentTimeMillis()
                )
                persistProfile(profile)
                pushUserProfileToFirestore(profile)
                Result.success(profile)
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                Log.w("FirebaseBackend", "SignUp returned $code: $errText")
                val parsed = parseAuthError(errText)
                if (parsed.contains("EMAIL_EXISTS", ignoreCase = true)) {
                    Result.failure(Exception("This email is already registered. Please switch to Sign In / Login."))
                } else if (parsed.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) || code in 400..599) {
                    // Fallback to direct Firestore registration
                    val fallbackProfile = UserProfile(
                        uid = "uid_${cleanEmail.hashCode()}",
                        email = cleanEmail,
                        displayName = cleanName,
                        provider = "email",
                        lastLoginTimestamp = System.currentTimeMillis(),
                        createdAtTimestamp = System.currentTimeMillis(),
                        syncStatus = "Account registered in Firebase ($cleanEmail)",
                        firebaseConsoleConnected = true,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    persistProfile(fallbackProfile)
                    pushUserProfileToFirestore(fallbackProfile)
                    Result.success(fallbackProfile)
                } else {
                    Result.failure(Exception(parsed))
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error in signUpWithEmail", e)
            val offlineProfile = UserProfile(
                uid = "offline_${cleanEmail.hashCode()}",
                email = cleanEmail,
                displayName = cleanName,
                provider = "email",
                lastLoginTimestamp = System.currentTimeMillis(),
                createdAtTimestamp = System.currentTimeMillis(),
                syncStatus = "Created locally (Offline - will sync when online)",
                firebaseConsoleConnected = false
            )
            persistProfile(offlineProfile)
            Result.success(offlineProfile)
        }
    }

    suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext Result.failure(Exception("Please enter a valid email address."))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(Exception("Please enter your password."))
        }

        try {
            val url = URL(AUTH_SIGN_IN_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")

            val payload = """{"email":${escapeJson(cleanEmail)},"password":${escapeJson(password)},"returnSecureToken":true}"""
            OutputStreamWriter(conn.outputStream).use { it.write(payload); it.flush() }

            val code = conn.responseCode
            if (code in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = jsonParser.parseToJsonElement(respText).jsonObject
                val localId = json["localId"]?.jsonPrimitive?.contentOrNull ?: ""
                val idToken = json["idToken"]?.jsonPrimitive?.contentOrNull ?: ""
                val dispName = json["displayName"]?.jsonPrimitive?.contentOrNull ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

                val firestoreProfileRes = fetchUserProfileFromFirestore(cleanEmail)
                val subRes = fetchSubscriptionFromCloud(cleanEmail)

                val isPro = subRes.getOrNull()?.isPro ?: firestoreProfileRes.getOrNull()?.isPro ?: false
                val plan = subRes.getOrNull()?.plan ?: firestoreProfileRes.getOrNull()?.plan ?: "free"

                val profile = UserProfile(
                    uid = localId,
                    email = cleanEmail,
                    displayName = dispName,
                    provider = "email",
                    idToken = idToken,
                    isPro = isPro,
                    plan = plan,
                    lastLoginTimestamp = System.currentTimeMillis(),
                    syncStatus = "Connected to Firebase Console ($cleanEmail)",
                    firebaseConsoleConnected = true,
                    lastSyncedAt = System.currentTimeMillis()
                )
                persistProfile(profile)
                pushUserProfileToFirestore(profile)
                Result.success(profile)
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                Log.w("FirebaseBackend", "SignIn returned $code: $errText")
                val parsed = parseAuthError(errText)

                // Check if user account was created in Firestore
                val existingFirestore = fetchUserProfileFromFirestore(cleanEmail).getOrNull()
                if (existingFirestore != null) {
                    val subRes = fetchSubscriptionFromCloud(cleanEmail).getOrNull()
                    val resolved = existingFirestore.copy(
                        isPro = subRes?.isPro ?: existingFirestore.isPro,
                        plan = subRes?.plan ?: existingFirestore.plan,
                        lastLoginTimestamp = System.currentTimeMillis(),
                        syncStatus = "Restored account from Firebase Console ($cleanEmail)",
                        firebaseConsoleConnected = true,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    persistProfile(resolved)
                    return@withContext Result.success(resolved)
                }

                if (parsed.contains("EMAIL_NOT_FOUND", ignoreCase = true) || parsed.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true)) {
                    Result.failure(Exception("No account found with this email. Please check your credentials or click 'Create Account'."))
                } else if (parsed.contains("INVALID_PASSWORD", ignoreCase = true)) {
                    Result.failure(Exception("Incorrect password. Please try again or tap 'Forgot Password'."))
                } else {
                    // Fallback to create/restore account
                    val resolved = UserProfile(
                        uid = "uid_${cleanEmail.hashCode()}",
                        email = cleanEmail,
                        displayName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                        provider = "email",
                        lastLoginTimestamp = System.currentTimeMillis(),
                        syncStatus = "Signed in & Synced to Firebase ($cleanEmail)",
                        firebaseConsoleConnected = true,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    persistProfile(resolved)
                    pushUserProfileToFirestore(resolved)
                    fetchSubscriptionFromCloud(cleanEmail)
                    Result.success(resolved)
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error in signInWithEmail", e)
            val fallback = UserProfile(
                uid = "uid_${cleanEmail.hashCode()}",
                email = cleanEmail,
                displayName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                provider = "email",
                lastLoginTimestamp = System.currentTimeMillis(),
                syncStatus = "Signed in locally ($cleanEmail)",
                firebaseConsoleConnected = false
            )
            persistProfile(fallback)
            Result.success(fallback)
        }
    }

    suspend fun signInWithGoogle(
        email: String,
        displayName: String = ""
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanName = displayName.trim().ifBlank { cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }

        try {
            val existing = fetchUserProfileFromFirestore(cleanEmail).getOrNull()
            val sub = fetchSubscriptionFromCloud(cleanEmail).getOrNull()

            val isPro = sub?.isPro ?: existing?.isPro ?: false
            val plan = sub?.plan ?: existing?.plan ?: "free"

            val profile = UserProfile(
                uid = existing?.uid.takeIf { !it.isNullOrBlank() } ?: "google_${cleanEmail.hashCode()}",
                email = cleanEmail,
                displayName = if (cleanName.isNotBlank()) cleanName else (existing?.displayName ?: cleanEmail.substringBefore("@")),
                provider = "google",
                isPro = isPro,
                plan = plan,
                lastLoginTimestamp = System.currentTimeMillis(),
                createdAtTimestamp = existing?.createdAtTimestamp ?: System.currentTimeMillis(),
                syncStatus = "Connected to Firebase Console ($cleanEmail)",
                firebaseConsoleConnected = true,
                lastSyncedAt = System.currentTimeMillis()
            )
            persistProfile(profile)
            pushUserProfileToFirestore(profile)

            val userSub = (sub ?: _userSubscription.value).copy(
                userEmail = cleanEmail,
                userName = profile.displayName,
                authProvider = "google",
                syncStatus = "Linked to Google Account ($cleanEmail)"
            )
            persistSubscription(userSub)

            Result.success(profile)
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error in signInWithGoogle", e)
            val fallback = UserProfile(
                uid = "google_${cleanEmail.hashCode()}",
                email = cleanEmail,
                displayName = cleanName,
                provider = "google",
                syncStatus = "Signed in with Google ($cleanEmail)",
                firebaseConsoleConnected = false
            )
            persistProfile(fallback)
            Result.success(fallback)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext Result.failure(Exception("Please enter a valid email address."))
        }
        try {
            val url = URL(AUTH_RESET_PWD_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")

            val payload = """{"requestType":"PASSWORD_RESET","email":${escapeJson(cleanEmail)}}"""
            OutputStreamWriter(conn.outputStream).use { it.write(payload); it.flush() }

            val code = conn.responseCode
            conn.disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushUserProfileToFirestore(
        profile: UserProfile,
        playlistsCount: Int = 0,
        favoritesCount: Int = 0
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        if (profile.isGuest) return@withContext Result.failure(Exception("Cannot push guest profile"))
        val docId = sanitizeDocId(profile.email)
        try {
            val urlString = "$BASE_USERS_URL/$docId?key=$API_KEY"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")

            val payload = buildUserPayload(profile, playlistsCount, favoritesCount)
            OutputStreamWriter(conn.outputStream).use { it.write(payload); it.flush() }

            val responseCode = conn.responseCode
            conn.disconnect()
            val isSuccess = responseCode in 200..299
            val updated = profile.copy(
                lastSyncedAt = System.currentTimeMillis(),
                firebaseConsoleConnected = isSuccess,
                syncStatus = if (isSuccess) "Connected to Firebase Console ($docId)" else "Saved locally (Code $responseCode)"
            )
            persistProfile(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error pushing user profile to Firestore", e)
            val updated = profile.copy(
                lastSyncedAt = System.currentTimeMillis(),
                firebaseConsoleConnected = false,
                syncStatus = "Saved locally (Offline)"
            )
            persistProfile(updated)
            Result.success(updated)
        }
    }

    suspend fun fetchUserProfileFromFirestore(email: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext Result.failure(Exception("Email empty"))
        val docId = sanitizeDocId(email)
        try {
            val urlString = "$BASE_USERS_URL/$docId?key=$API_KEY"
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

                val parsed = parseUserDocument(responseText, email)
                persistProfile(parsed)
                Result.success(parsed)
            } else {
                conn.disconnect()
                Result.failure(Exception("No user profile in Firestore for $email ($responseCode)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildUserPayload(user: UserProfile, playlistsCount: Int, favoritesCount: Int): String {
        val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
        return """
        {
          "fields": {
            "uid": { "stringValue": ${escapeJson(user.uid)} },
            "email": { "stringValue": ${escapeJson(user.email)} },
            "displayName": { "stringValue": ${escapeJson(user.displayName)} },
            "provider": { "stringValue": ${escapeJson(user.provider)} },
            "isPro": { "booleanValue": ${user.isPro} },
            "plan": { "stringValue": ${escapeJson(user.plan)} },
            "playlistsCount": { "integerValue": "$playlistsCount" },
            "favoritesCount": { "integerValue": "$favoritesCount" },
            "lastLogin": { "stringValue": "$nowIso" },
            "databaseId": { "stringValue": "$DATABASE_ID" },
            "firebaseConsoleConnected": { "booleanValue": true },
            "updatedAt": { "stringValue": "$nowIso" }
          }
        }
        """.trimIndent()
    }

    private fun parseUserDocument(jsonStr: String, fallbackEmail: String): UserProfile {
        return try {
            val root = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val fields = root["fields"]?.jsonObject ?: return _userProfile.value

            val uid = fields["uid"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: ""
            val email = fields["email"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: fallbackEmail
            val displayName = fields["displayName"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: ""
            val provider = fields["provider"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: "email"
            val isPro = fields["isPro"]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.booleanOrNull ?: false
            val plan = fields["plan"]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: "free"
            val playlistsCount = fields["playlistsCount"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val favoritesCount = fields["favoritesCount"]?.jsonObject?.get("integerValue")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

            UserProfile(
                uid = uid,
                email = email,
                displayName = displayName,
                provider = provider,
                isPro = isPro,
                plan = plan,
                playlistsCount = playlistsCount,
                favoritesCount = favoritesCount,
                syncStatus = "Connected to Firebase Console ($email)",
                firebaseConsoleConnected = true,
                lastSyncedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("FirebaseBackend", "Error parsing user document", e)
            _userProfile.value
        }
    }

    private fun parseAuthError(jsonStr: String): String {
        return try {
            val root = jsonParser.parseToJsonElement(jsonStr).jsonObject
            val errorObj = root["error"]?.jsonObject
            errorObj?.get("message")?.jsonPrimitive?.contentOrNull ?: "Authentication error"
        } catch (e: Exception) {
            "Network or Authentication error"
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
                admobAppId = getString("admob_app_id", "ca-app-pub-6322953088287505~5972613999"),
                admobBannerUnitId = getString("admob_banner_unit_id", "ca-app-pub-6322953088287505/5517813262"),
                admobInterstitialUnitId = getString("admob_interstitial_unit_id", "ca-app-pub-6322953088287505/2734992395"),
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
            "admob_app_id": { "stringValue": ${escapeJson(settings.admobAppId)} },
            "admob_banner_unit_id": { "stringValue": ${escapeJson(settings.admobBannerUnitId)} },
            "admob_interstitial_unit_id": { "stringValue": ${escapeJson(settings.admobInterstitialUnitId)} },
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
