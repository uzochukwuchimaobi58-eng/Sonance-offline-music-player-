package com.sonance.musicplayer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sonance.musicplayer.model.AppTheme
import com.sonance.musicplayer.model.PlayerSettings
import com.sonance.musicplayer.model.RemoteBackendSettings
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.model.UserProfile
import com.sonance.musicplayer.ui.components.FirebaseConsoleGuideDialog
import com.sonance.musicplayer.ui.components.FirebaseLiveBackendEditorDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    settings: PlayerSettings,
    onUpdateSettings: (PlayerSettings) -> Unit,
    tracks: List<Track> = emptyList(),
    onSelectTheme: (AppTheme) -> Unit = {},
    onDeleteDuplicateTrack: (String) -> Unit = {},
    remoteSettings: RemoteBackendSettings = RemoteBackendSettings(),
    onSyncFirebase: () -> Unit = {},
    onUpdateFirebaseSettings: (RemoteBackendSettings) -> Unit = {},
    userSubscription: com.sonance.musicplayer.model.UserSubscription = com.sonance.musicplayer.model.UserSubscription(),
    userProfile: UserProfile = UserProfile(),
    onOpenAccount: () -> Unit = {},
    onOpenProUpgrade: () -> Unit = {},
    onSetDevProState: (Boolean) -> Unit = {},
    theme: ThemeConfig
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Dialog State Trackers
    var showBatteryDialog by remember { mutableStateOf(false) }
    var showQueueAfterSearchDialog by remember { mutableStateOf(false) }
    var showAccentColorDialog by remember { mutableStateOf(false) }
    var showLibraryTabOrderDialog by remember { mutableStateOf(false) }
    var showDuplicateFinderDialog by remember { mutableStateOf(false) }
    var showStatusBarLyricsDialog by remember { mutableStateOf(false) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }
    var showReplayGainModeDialog by remember { mutableStateOf(false) }
    var showReplayGainPreampDialog by remember { mutableStateOf(false) }
    var showAddPlaylistPosDialog by remember { mutableStateOf(false) }
    var showSmartPlaylistLimitDialog by remember { mutableStateOf(false) }
    var showLockScreenTimeDialog by remember { mutableStateOf(false) }
    var showLockScreenBgDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showFaqDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    // Battery optimization status check
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isBatteryIgnored by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }

    val headerGold = Color(0xFFE5B83B)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.bgCanvas)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("settings_screen")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar: Back arrow and Settings title (Clean, zero ads, no hot app)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(theme.headerBg)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("btn_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Settings",
                        color = theme.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Settings List
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // --- User Account & Firebase Console Section ---
                    SettingsSectionHeader(title = "Account & Cloud Sync", color = Color(0xFFF58220))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (userProfile.isSignedIn) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color(0xFFF58220).copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onOpenAccount() }
                            .testTag("settings_account_card"),
                        color = theme.sidebarBg
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (userProfile.isSignedIn) theme.accentColor else Color(0xFFF58220).copy(alpha = 0.2f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (userProfile.isSignedIn) {
                                        Text(
                                            text = userProfile.initials,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CloudSync,
                                            contentDescription = null,
                                            tint = Color(0xFFF58220),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (userProfile.isSignedIn) userProfile.displayName.ifBlank { userProfile.email } else "Sign In / Register",
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (userProfile.isSignedIn) "Connected to Firebase Console • ${if (userProfile.isGoogle) "Google Account" else "Email Account"}" else "Link email or Google account to sync data",
                                    color = if (userProfile.isSignedIn) Color(0xFF4CAF50) else theme.textSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // --- General Section ---
                    SettingSwitchItem(
                        title = "Forward and backward",
                        subtitle = "Show forward and backward buttons on nowplaying page",
                        checked = settings.forwardAndBackward,
                        onCheckedChange = { onUpdateSettings(settings.copy(forwardAndBackward = it)) },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Music stops playing?",
                        subtitle = "Grant permission to avoid abnormal music stops.",
                        hasBadge = !isBatteryIgnored,
                        badgeColor = Color(0xFFEF4444),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                                isBatteryIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                            }
                            showBatteryDialog = true
                        },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Queue after searching",
                        subtitle = settings.queueAfterSearching,
                        onClick = { showQueueAfterSearchDialog = true },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Show shuffle button",
                        subtitle = "Custom shuffle button show on list pages or not",
                        checked = settings.showShuffleButton,
                        onCheckedChange = { onUpdateSettings(settings.copy(showShuffleButton = it)) },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Accent color",
                        subtitle = "The accent theme color",
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(theme.accentColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            )
                        },
                        onClick = { showAccentColorDialog = true },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Library tab order",
                        subtitle = "Customize order for Tracks, Artists, Albums, Genres",
                        onClick = { showLibraryTabOrderDialog = true },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Find Duplicate",
                        subtitle = "Scan library for duplicate songs",
                        onClick = { showDuplicateFinderDialog = true },
                        theme = theme
                    )

                    // --- Sonance PRO & AdMob Section ---
                    SettingsSectionHeader(title = "Sonance PRO & AdMob Ads", color = Color(0xFFFFD700))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        color = theme.sidebarBg
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (userSubscription.isPro) "PRO VIP Active" else "Free Plan (AdMob Active)",
                                        color = if (userSubscription.isPro) Color(0xFFFFD700) else theme.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (userSubscription.isPro) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (userSubscription.isPro) "ZERO ADS" else "$1/yr or $2",
                                        color = if (userSubscription.isPro) Color(0xFF10B981) else Color(0xFFFFB300),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (userSubscription.isPro)
                                    "Account: ${userSubscription.userEmail.ifBlank { "Tester" }} • Plan: ${userSubscription.plan.uppercase()} (${userSubscription.price})"
                                else
                                    "Guest users enjoy free music with AdMob ads. Upgrade to remove all ads and unlock Pro presets.",
                                color = theme.textSecondary,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenProUpgrade,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = if (userSubscription.isPro) "Manage Plan" else "Go PRO ($1 / $2)",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        onSetDevProState(!userSubscription.isPro)
                                        val target = !userSubscription.isPro
                                        Toast.makeText(context, if (target) "Dev Pro set to TRUE" else "Dev Pro set to FALSE", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = if (userSubscription.isPro) "Test False (Ads)" else "Test True (Pro)",
                                        color = theme.textPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // --- Lyrics Section ---
                    SettingsSectionHeader(title = "Lyrics", color = headerGold)

                    SettingSwitchItem(
                        title = "Desktop lyrics",
                        subtitle = "Show floating lyrics over other apps",
                        checked = settings.desktopLyrics,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                Toast.makeText(context, "Grant 'Display over other apps' to show desktop lyrics", Toast.LENGTH_LONG).show()
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            onUpdateSettings(settings.copy(desktopLyrics = enable))
                        },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Car bluetooth lyrics",
                        subtitle = "Send track lyrics and title to car bluetooth display",
                        checked = settings.carBluetoothLyrics,
                        onCheckedChange = { onUpdateSettings(settings.copy(carBluetoothLyrics = it)) },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Status bar lyrics",
                        subtitle = settings.statusBarLyrics,
                        onClick = { showStatusBarLyricsDialog = true },
                        theme = theme
                    )

                    // --- Audio Section ---
                    SettingsSectionHeader(title = "Audio", color = headerGold)

                    SettingSwitchItem(
                        title = "Shake to play next song",
                        subtitle = "Shake your device to skip to the next track",
                        checked = settings.shakeToPlayNext,
                        onCheckedChange = {
                            onUpdateSettings(settings.copy(shakeToPlayNext = it))
                            if (it) Toast.makeText(context, "Shake detection enabled", Toast.LENGTH_SHORT).show()
                        },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Swipe to change songs",
                        subtitle = "Swipe on player artwork to change tracks",
                        checked = settings.swipeToChangeSongs,
                        onCheckedChange = { onUpdateSettings(settings.copy(swipeToChangeSongs = it)) },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Allow others playing music while Music Player playing",
                        subtitle = "Mix audio or ignore audio focus losses",
                        checked = settings.allowOthersPlaying,
                        onCheckedChange = { onUpdateSettings(settings.copy(allowOthersPlaying = it)) },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Play/pause fade",
                        subtitle = "Fade during play/pause",
                        checked = settings.playPauseFade,
                        onCheckedChange = { onUpdateSettings(settings.copy(playPauseFade = it)) },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Gapless Playback",
                        subtitle = "Seamless uninterrupted music",
                        checked = settings.gaplessPlayback,
                        onCheckedChange = { onUpdateSettings(settings.copy(gaplessPlayback = it)) },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Crossfade",
                        subtitle = if (settings.crossfadeSeconds == 0) "Off" else "${settings.crossfadeSeconds} seconds",
                        onClick = { showCrossfadeDialog = true },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Open nowplaying on play",
                        subtitle = "Click tracks to open nowplaying page",
                        checked = settings.openNowPlayingOnPlay,
                        onCheckedChange = { onUpdateSettings(settings.copy(openNowPlayingOnPlay = it)) },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Replay the song",
                        subtitle = "Replay when click the current playing song",
                        checked = settings.replayTheSong,
                        onCheckedChange = { onUpdateSettings(settings.copy(replayTheSong = it)) },
                        theme = theme
                    )

                    // --- ReplayGain Section ---
                    SettingsSectionHeader(title = "ReplayGain", color = headerGold)

                    SettingActionItem(
                        title = "ReplayGain source mode",
                        subtitle = settings.replayGainMode,
                        onClick = { showReplayGainModeDialog = true },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "ReplayGain preamp",
                        subtitle = settings.replayGainPreamp,
                        onClick = { showReplayGainPreampDialog = true },
                        theme = theme
                    )

                    // --- Playlist Section ---
                    SettingsSectionHeader(title = "Playlist", color = headerGold)

                    SettingSwitchItem(
                        title = "Click tracks add to current queue",
                        subtitle = "Only add the song into current queue, not add/change other songs",
                        checked = settings.clickTracksAddToCurrentQueue,
                        onCheckedChange = { onUpdateSettings(settings.copy(clickTracksAddToCurrentQueue = it)) },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Add music to playlist",
                        subtitle = "Choose add music to end of playlist or top of playlist",
                        trailingLabel = settings.addMusicToPlaylistPosition,
                        onClick = { showAddPlaylistPosDialog = true },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Smart playlist track limit",
                        subtitle = "The limit of Recent play, Recent add & Most play",
                        trailingLabel = settings.smartPlaylistTrackLimit,
                        onClick = { showSmartPlaylistLimitDialog = true },
                        theme = theme
                    )

                    // --- Notification Section ---
                    SettingsSectionHeader(title = "Notification", color = headerGold)

                    SettingSwitchItem(
                        title = "Use notification bar to play music",
                        subtitle = "Show media controls in notification panel",
                        checked = settings.useNotificationBar,
                        onCheckedChange = { onUpdateSettings(settings.copy(useNotificationBar = it)) },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Classic notification design",
                        subtitle = "Use compact classic notification style",
                        checked = settings.classicNotificationDesign,
                        onCheckedChange = { onUpdateSettings(settings.copy(classicNotificationDesign = it)) },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Color notification",
                        subtitle = "Tint notification with album art colors",
                        checked = settings.colorNotification,
                        onCheckedChange = { onUpdateSettings(settings.copy(colorNotification = it)) },
                        theme = theme
                    )

                    // --- Lockscreen Section ---
                    SettingsSectionHeader(title = "Lockscreen", color = headerGold)

                    SettingSwitchItem(
                        title = "Lock screen playing",
                        subtitle = "Show nowplaying when lock screen",
                        checked = settings.lockScreenPlaying,
                        onCheckedChange = { onUpdateSettings(settings.copy(lockScreenPlaying = it)) },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Lock screen time format",
                        subtitle = settings.lockScreenTimeFormat,
                        onClick = { showLockScreenTimeDialog = true },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Lock screen background",
                        subtitle = settings.lockScreenBackground,
                        onClick = { showLockScreenBgDialog = true },
                        theme = theme
                    )

                    // --- Headset Section ---
                    SettingsSectionHeader(title = "Headset", color = headerGold)

                    SettingSwitchItem(
                        title = "Play when inserted",
                        subtitle = "Auto start playing when a wired headset is inserted",
                        checked = settings.headsetPlayWhenInserted,
                        onCheckedChange = { onUpdateSettings(settings.copy(headsetPlayWhenInserted = it)) },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Paused when unplugged",
                        subtitle = "Auto stop playing when a wired headset is unplugged",
                        checked = settings.headsetPauseWhenUnplugged,
                        onCheckedChange = { onUpdateSettings(settings.copy(headsetPauseWhenUnplugged = it)) },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Bluetooth autostart",
                        subtitle = "Auto start playing when a Bluetooth headset/A2DP device is connected",
                        checked = settings.bluetoothAutostart,
                        onCheckedChange = { onUpdateSettings(settings.copy(bluetoothAutostart = it)) },
                        theme = theme
                    )

                    // --- Others Section ---
                    SettingsSectionHeader(title = "Others", color = headerGold)

                    SettingSwitchItem(
                        title = "Use English language",
                        subtitle = "Default application language",
                        checked = settings.useEnglishLanguage,
                        onCheckedChange = { onUpdateSettings(settings.copy(useEnglishLanguage = it)) },
                        theme = theme
                    )

                    SettingSwitchItem(
                        title = "Hide update reminder",
                        subtitle = "Do not show prompt when new version is available",
                        checked = settings.hideUpdateReminder,
                        onCheckedChange = { onUpdateSettings(settings.copy(hideUpdateReminder = it)) },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "FAQ",
                        subtitle = "Frequently asked questions & help",
                        onClick = { showFaqDialog = true },
                        theme = theme
                    )

                    SettingActionItem(
                        title = "Feedback",
                        subtitle = "Send suggestions or report bugs",
                        onClick = { showFeedbackDialog = true },
                        theme = theme
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // --- Interactive Popups & Dialogs ---

    // 1. Battery Optimization Dialog
    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            containerColor = theme.sidebarBg,
            icon = {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = theme.accentColor,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Background Playback Optimization",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Android battery management frequently restricts or terminates background audio services when the screen is turned off or in battery saver mode.",
                        color = theme.textSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "To guarantee uninterrupted, seamless music playback, set Sonance to 'Unrestricted' battery usage.",
                        color = theme.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatteryDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                ) {
                    Text("Open Battery Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text("Close", color = theme.textSecondary)
                }
            }
        )
    }

    // 2. Queue After Searching Dialog
    if (showQueueAfterSearchDialog) {
        SingleChoiceListDialog(
            title = "Queue after searching",
            options = listOf("Search results", "Play immediately & replace queue", "Add to current queue", "Play next"),
            selectedOption = settings.queueAfterSearching,
            onSelect = {
                onUpdateSettings(settings.copy(queueAfterSearching = it))
                showQueueAfterSearchDialog = false
            },
            onDismiss = { showQueueAfterSearchDialog = false },
            theme = theme
        )
    }

    // 3. Accent Color Picker Dialog
    if (showAccentColorDialog) {
        val accentOptions = listOf(
            Triple("Emerald Forest", Color(0xFF18AD75), AppTheme.EMERALD_FOREST),
            Triple("Golden Luxury", Color(0xFFF9BE39), AppTheme.GOLDEN_LUXURY),
            Triple("Dark Slate", Color(0xFF06B6D4), AppTheme.DARK_SLATE),
            Triple("Cyberpunk", Color(0xFFA855F7), AppTheme.CYBERPUNK),
            Triple("Sunset Warm", Color(0xFFF97316), AppTheme.SUNSET_WARM),
            Triple("Crimson Ruby", Color(0xFFEF4444), AppTheme.CRIMSON_RUBY),
            Triple("Dark AMOLED", Color(0xFFF9BE39), AppTheme.DARK_AMOLED),
            Triple("Light Minimal", Color(0xFF2563EB), AppTheme.LIGHT_MINIMAL)
        )

        AlertDialog(
            onDismissRequest = { showAccentColorDialog = false },
            containerColor = theme.sidebarBg,
            title = {
                Text("Select Accent Color", color = theme.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    accentOptions.forEach { (name, color, appTheme) ->
                        val isSelected = theme.theme == appTheme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) theme.accentColor.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    onSelectTheme(appTheme)
                                    onUpdateSettings(settings.copy(accentColor = name.lowercase()))
                                    showAccentColorDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = name,
                                color = if (isSelected) theme.accentColor else theme.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = theme.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccentColorDialog = false }) {
                    Text("Done", color = theme.accentColor)
                }
            }
        )
    }

    // 4. Library Tab Order Dialog
    if (showLibraryTabOrderDialog) {
        var currentTabs by remember { mutableStateOf(settings.libraryTabOrder) }
        AlertDialog(
            onDismissRequest = { showLibraryTabOrderDialog = false },
            containerColor = theme.sidebarBg,
            title = { Text("Library Tab Order", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Arrange tab display order:", color = theme.textSecondary, fontSize = 13.sp)
                    currentTabs.forEachIndexed { index, tab ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "${index + 1}. $tab", color = theme.textPrimary, fontWeight = FontWeight.Medium)
                            Row {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val mutable = currentTabs.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index - 1, item)
                                            currentTabs = mutable
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = theme.textPrimary)
                                    }
                                }
                                if (index < currentTabs.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val mutable = currentTabs.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index + 1, item)
                                            currentTabs = mutable
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = theme.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSettings(settings.copy(libraryTabOrder = currentTabs))
                        showLibraryTabOrderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLibraryTabOrderDialog = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }

    // 5. Find Duplicate Songs Dialog
    if (showDuplicateFinderDialog) {
        val duplicates = remember(tracks) {
            tracks.groupBy { it.title.trim().lowercase() }
                .filter { it.value.size > 1 }
                .values.flatten()
        }

        AlertDialog(
            onDismissRequest = { showDuplicateFinderDialog = false },
            containerColor = theme.sidebarBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = theme.accentColor)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Find Duplicate Songs", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                if (duplicates.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No duplicate tracks found!",
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Your music library is clean and organized.",
                            color = theme.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(duplicates) { tr ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr.title,
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${tr.artist} • ${tr.folder}",
                                        color = theme.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onDeleteDuplicateTrack(tr.id)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete duplicate",
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDuplicateFinderDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                ) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 6. Status Bar Lyrics Dialog
    if (showStatusBarLyricsDialog) {
        SingleChoiceListDialog(
            title = "Status bar lyrics",
            options = listOf("Off", "On"),
            selectedOption = settings.statusBarLyrics,
            onSelect = {
                onUpdateSettings(settings.copy(statusBarLyrics = it))
                showStatusBarLyricsDialog = false
            },
            onDismiss = { showStatusBarLyricsDialog = false },
            theme = theme
        )
    }

    // 7. Crossfade Dialog
    if (showCrossfadeDialog) {
        val options = listOf("Off" to 0, "2 seconds" to 2, "4 seconds" to 4, "6 seconds" to 6, "8 seconds" to 8, "10 seconds" to 10)
        val currentLabel = if (settings.crossfadeSeconds == 0) "Off" else "${settings.crossfadeSeconds} seconds"

        SingleChoiceListDialog(
            title = "Crossfade duration",
            options = options.map { it.first },
            selectedOption = currentLabel,
            onSelect = { label ->
                val seconds = options.find { it.first == label }?.second ?: 0
                onUpdateSettings(settings.copy(crossfadeSeconds = seconds))
                showCrossfadeDialog = false
            },
            onDismiss = { showCrossfadeDialog = false },
            theme = theme
        )
    }

    // 8. ReplayGain Mode Dialog
    if (showReplayGainModeDialog) {
        SingleChoiceListDialog(
            title = "ReplayGain source mode",
            options = listOf("None", "Track", "Album"),
            selectedOption = settings.replayGainMode,
            onSelect = {
                onUpdateSettings(settings.copy(replayGainMode = it))
                showReplayGainModeDialog = false
            },
            onDismiss = { showReplayGainModeDialog = false },
            theme = theme
        )
    }

    // 9. ReplayGain Preamp Dialog
    if (showReplayGainPreampDialog) {
        SingleChoiceListDialog(
            title = "ReplayGain preamp",
            options = listOf("-6 dB", "-3 dB", "0 dB", "+3 dB", "+6 dB"),
            selectedOption = settings.replayGainPreamp,
            onSelect = {
                onUpdateSettings(settings.copy(replayGainPreamp = it))
                showReplayGainPreampDialog = false
            },
            onDismiss = { showReplayGainPreampDialog = false },
            theme = theme
        )
    }

    // 10. Add Music to Playlist Position Dialog
    if (showAddPlaylistPosDialog) {
        SingleChoiceListDialog(
            title = "Add music to playlist",
            options = listOf("Top", "End"),
            selectedOption = settings.addMusicToPlaylistPosition,
            onSelect = {
                onUpdateSettings(settings.copy(addMusicToPlaylistPosition = it))
                showAddPlaylistPosDialog = false
            },
            onDismiss = { showAddPlaylistPosDialog = false },
            theme = theme
        )
    }

    // 11. Smart Playlist Limit Dialog
    if (showSmartPlaylistLimitDialog) {
        SingleChoiceListDialog(
            title = "Smart playlist track limit",
            options = listOf("Past month", "Past 3 months", "Past 6 months", "Past year", "All time"),
            selectedOption = settings.smartPlaylistTrackLimit,
            onSelect = {
                onUpdateSettings(settings.copy(smartPlaylistTrackLimit = it))
                showSmartPlaylistLimitDialog = false
            },
            onDismiss = { showSmartPlaylistLimitDialog = false },
            theme = theme
        )
    }

    // 12. Lock Screen Time Format Dialog
    if (showLockScreenTimeDialog) {
        SingleChoiceListDialog(
            title = "Lock screen time format",
            options = listOf("Auto", "12-hour", "24-hour"),
            selectedOption = settings.lockScreenTimeFormat,
            onSelect = {
                onUpdateSettings(settings.copy(lockScreenTimeFormat = it))
                showLockScreenTimeDialog = false
            },
            onDismiss = { showLockScreenTimeDialog = false },
            theme = theme
        )
    }

    // 13. Lock Screen Background Dialog
    if (showLockScreenBgDialog) {
        SingleChoiceListDialog(
            title = "Lock screen background",
            options = listOf("Theme", "Album Art", "Blurred Art", "Default"),
            selectedOption = settings.lockScreenBackground,
            onSelect = {
                onUpdateSettings(settings.copy(lockScreenBackground = it))
                showLockScreenBgDialog = false
            },
            onDismiss = { showLockScreenBgDialog = false },
            theme = theme
        )
    }

    // 14. Check For Update Dialog
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = theme.sidebarBg,
            title = {
                Text(
                    text = if (isCheckingUpdate) "Checking for updates..." else "Version Status",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (isCheckingUpdate) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = theme.accentColor)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Verifying latest build from Firebase...", color = theme.textSecondary)
                    }
                } else {
                    val currentVersion = com.sonance.musicplayer.BuildConfig.VERSION_NAME
                    val hasNewer = remoteSettings.latestVersion.isNotBlank() && remoteSettings.latestVersion != currentVersion
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasNewer) Icons.Default.SystemUpdate else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (hasNewer) theme.accentColor else Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasNewer) "Update Available: v${remoteSettings.latestVersion}" else "Sonance Music Player v$currentVersion",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (hasNewer) {
                            Text(
                                text = "Installed version: v$currentVersion",
                                color = theme.textSecondary,
                                fontSize = 12.sp
                            )
                            if (remoteSettings.releaseNotes.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.05f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Release Notes:", color = theme.accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(remoteSettings.releaseNotes, color = theme.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            Text("You are using the latest version of Sonance Music Player.", color = theme.textSecondary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                val currentVersion = com.sonance.musicplayer.BuildConfig.VERSION_NAME
                val hasNewer = remoteSettings.latestVersion.isNotBlank() && remoteSettings.latestVersion != currentVersion
                if (!isCheckingUpdate && hasNewer && remoteSettings.updateUrl.isNotBlank()) {
                    Button(
                        onClick = {
                            showUpdateDialog = false
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(remoteSettings.updateUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                    ) {
                        Text("Update Now", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("OK", color = theme.accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                val hasNewer = remoteSettings.latestVersion.isNotBlank() && remoteSettings.latestVersion != "1.0.0"
                if (!isCheckingUpdate && hasNewer) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("Later", color = theme.textSecondary)
                    }
                }
            }
        )
    }

    // 15. FAQ Dialog
    if (showFaqDialog) {
        val faqs = listOf(
            "Why does playback stop when the screen turns off?" to "Enable 'Unrestricted' battery access in system settings to prevent Android power saving from killing background playback.",
            "What audio formats are supported?" to "Sonance supports MP3, FLAC, AAC, WAV, OGG, M4A, OPUS, and standard high-resolution Android audio formats.",
            "How does the 10-Band Equalizer work?" to "The high-precision 10-Band EQ allows adjusting frequencies from 31Hz up to 16kHz with customizable presets like Bass Boost and Virtualizer.",
            "How can I add songs to playlists?" to "Tap the three-dots menu on any track, album, or folder and choose 'Add to Playlist'. You can configure whether tracks are added to the top or end of playlists.",
            "How do I scan newly added songs?" to "Open the navigation drawer and tap 'Scan Library' to auto-detect new songs from your device storage."
        )

        AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            containerColor = theme.sidebarBg,
            title = {
                Text("Frequently Asked Questions", color = theme.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(faqs) { (q, a) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(12.dp)
                        ) {
                            Text(text = q, color = theme.accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = a, color = theme.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFaqDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                ) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 16. Feedback Dialog
    if (showFeedbackDialog) {
        var feedbackText by remember { mutableStateOf("") }
        var selectedRating by remember { mutableIntStateOf(5) }

        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            containerColor = theme.sidebarBg,
            title = {
                Text("Send Feedback", color = theme.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("We'd love to hear how we can improve Sonance:", color = theme.textSecondary, fontSize = 14.sp)
                    
                    // Star Rating
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { selectedRating = star }) {
                                Icon(
                                    imageVector = if (star <= selectedRating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "$star stars",
                                    tint = if (star <= selectedRating) Color(0xFFF9BE39) else theme.textSecondary
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("Tell us your thoughts, bug reports, or feature requests...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.accentColor,
                            unfocusedBorderColor = theme.cardBorder,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFeedbackDialog = false
                        Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                ) {
                    Text("Submit", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
}

// --- Reusable Component Helpers ---

@Composable
private fun SettingsSectionHeader(title: String, color: Color) {
    Text(
        text = title,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    theme: ThemeConfig
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = theme.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = theme.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF18AD75),
                checkedTrackColor = Color(0xFF18AD75).copy(alpha = 0.4f),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
private fun SettingActionItem(
    title: String,
    subtitle: String,
    hasBadge: Boolean = false,
    badgeColor: Color = Color(0xFFEF4444),
    trailingLabel: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    theme: ThemeConfig
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = theme.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = theme.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasBadge) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (trailingLabel != null) {
                Text(
                    text = trailingLabel,
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            trailingContent?.invoke()

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = theme.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SingleChoiceListDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    theme: ThemeConfig
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.sidebarBg,
        title = {
            Text(text = title, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) theme.accentColor.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onSelect(option) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = option,
                            color = if (isSelected) theme.accentColor else theme.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = theme.accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}
