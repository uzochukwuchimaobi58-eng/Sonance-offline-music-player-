package com.sonance.musicplayer.model

import kotlinx.serialization.Serializable

@Serializable
data class RemoteBackendSettings(
    val announcementEnabled: Boolean = false,
    val announcementTitle: String = "Sonance Studio Update",
    val announcementMessage: String = "Welcome to Sonance Music Player! High-resolution audio engine and 10-band equalizer are active.",
    val announcementType: String = "info", // "info", "promo", "alert", "update"
    val announcementActionUrl: String = "",
    val latestVersion: String = "1.0.0",
    val minSupportedVersion: Int = 1,
    val updateUrl: String = "https://play.google.com/store/apps/details?id=com.sonance.musicplayer",
    val releaseNotes: String = "• Native 10-band equalizer presets\n• Gapless playback & customizable crossfade\n• Firebase cloud backend configuration support",
    val forceUpdate: Boolean = false,
    val supportEmail: String = "uzochukwuchimaobi58@gmail.com",
    val enableGearBillboard: Boolean = true,
    val defaultCrossfadeSeconds: Int = 0,
    val showShuffleButtonDefault: Boolean = true,
    val admobEnabled: Boolean = true,
    val forceProOverride: String = "none", // "none", "force_true", "force_false"
    val proYearlyPrice: String = "$1.00",
    val proLifetimePrice: String = "$2.00",
    val admobAppId: String = "ca-app-pub-6322953088287505~5972613999",
    val admobBannerUnitId: String = "ca-app-pub-6322953088287505/5517813262",
    val admobInterstitialUnitId: String = "ca-app-pub-6322953088287505/2734992395",
    val lastSyncedAt: Long = 0L,
    val syncStatus: String = "Ready"
)
