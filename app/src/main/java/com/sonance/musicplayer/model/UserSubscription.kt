package com.sonance.musicplayer.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSubscription(
    val isPro: Boolean = false,
    val plan: String = "free", // "free", "yearly", "lifetime"
    val price: String = "",    // "$1.00/yr" or "$2.00"
    val userEmail: String = "",
    val userName: String = "",
    val authProvider: String = "guest", // "guest", "google", "email"
    val purchaseTimestamp: Long = 0L,
    val expiryTimestamp: Long = 0L,     // 0 for lifetime or free, unix timestamp for yearly
    val syncStatus: String = "Guest (No sign-in required)",
    val lastSyncedAt: Long = 0L,
    val orderId: String = "",
    val purchaseToken: String = "",
    val productId: String = ""
) {
    val isGuest: Boolean get() = userEmail.isBlank()
    val isYearly: Boolean get() = plan.equals("yearly", ignoreCase = true)
    val isLifetime: Boolean get() = plan.equals("lifetime", ignoreCase = true)
}
