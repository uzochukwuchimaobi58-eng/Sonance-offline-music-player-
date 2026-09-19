package com.sonance.musicplayer.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val provider: String = "guest", // "guest", "google", "email"
    val isPro: Boolean = false,
    val plan: String = "free",
    val idToken: String = "",
    val lastLoginTimestamp: Long = 0L,
    val createdAtTimestamp: Long = 0L,
    val playlistsCount: Int = 0,
    val favoritesCount: Int = 0,
    val syncStatus: String = "Not signed in (Guest)",
    val firebaseConsoleConnected: Boolean = false,
    val lastSyncedAt: Long = 0L
) {
    val isSignedIn: Boolean get() = email.isNotBlank()
    val isGuest: Boolean get() = email.isBlank()
    val isGoogle: Boolean get() = provider.equals("google", ignoreCase = true)
    val isEmail: Boolean get() = provider.equals("email", ignoreCase = true)
    val initials: String
        get() {
            if (displayName.isNotBlank()) {
                val parts = displayName.trim().split(" ")
                return if (parts.size >= 2) {
                    "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
                } else {
                    parts[0].take(2).uppercase()
                }
            }
            if (email.isNotBlank()) {
                return email.take(2).uppercase()
            }
            return "G"
        }
}
