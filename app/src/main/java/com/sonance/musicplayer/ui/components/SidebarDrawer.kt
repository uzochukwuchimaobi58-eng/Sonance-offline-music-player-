package com.sonance.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.Playlist
import com.sonance.musicplayer.model.RepeatMode
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.UserProfile

@Composable
fun SidebarDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    playlists: List<Playlist>,
    repeatMode: RepeatMode,
    sleepTimerSec: Int?,
    onSelectPlaylist: (String) -> Unit,
    onOpenCreatePlaylist: () -> Unit,
    onOpenScanModal: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onToggleRepeat: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onEnterDriveMode: () -> Unit,
    onEnterLyricsMode: () -> Unit,
    onOpenWebBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
    isPro: Boolean = false,
    onOpenPro: () -> Unit = {},
    userProfile: UserProfile = UserProfile(),
    onOpenAccount: () -> Unit = {},
    theme: ThemeConfig
) {
    if (!isOpen) return

    var isPlaylistsExpanded by remember { mutableStateOf(true) }

    ModalNavigationDrawer(
        drawerState = rememberDrawerState(initialValue = DrawerValue.Open, confirmStateChange = {
            if (it == DrawerValue.Closed) onClose()
            true
        }),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = theme.sidebarBg,
                drawerContentColor = theme.textPrimary,
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight()
                    .testTag("sidebar_drawer_content")
            ) {
                // Header brand
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF9BE39)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Logo",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Sonance Music",
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (isPro) "PRO VIP Active • Ad-Free" else "Lossless Audio Player",
                            color = if (isPro) Color(0xFFFFD700) else theme.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isPro) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                HorizontalDivider(color = theme.headerBorder)

                // Menu items
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    // User Account Profile Card in Sidebar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onClose()
                                onOpenAccount()
                            }
                            .border(
                                1.dp,
                                if (userProfile.isSignedIn) Color(0xFF4CAF50).copy(alpha = 0.5f) else theme.cardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("sidebar_account_card"),
                        color = if (userProfile.isSignedIn) Color(0xFF1E293B) else theme.bgCanvas
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (userProfile.isSignedIn) theme.accentColor else theme.cardBorder,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (userProfile.isSignedIn) {
                                        Text(
                                            text = userProfile.initials,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PersonOutline,
                                            contentDescription = null,
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (userProfile.isSignedIn) userProfile.displayName.ifBlank { userProfile.email } else "Sign In / Login",
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (userProfile.isSignedIn) Color(0xFF4CAF50) else Color(0xFFF58220))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (userProfile.isSignedIn) (if (userProfile.isGoogle) "Google • Firebase" else "Email • Firebase") else "Email or Google Account",
                                        color = if (userProfile.isSignedIn) Color(0xFF4CAF50) else theme.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // PRO Banner in Sidebar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onClose()
                                onOpenPro()
                            }
                            .border(
                                1.dp,
                                if (isPro) Color(0xFFFFD700).copy(alpha = 0.6f) else Color(0xFFFFD700).copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("sidebar_pro_card"),
                        color = if (isPro) Color(0xFFFFD700).copy(alpha = 0.15f) else Color(0xFF1E293B)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFD700).copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPro) "Sonance PRO Member" else "Upgrade to PRO",
                                    color = Color(0xFFFFD700),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (isPro) "Ad-free listening enabled" else "Remove all ads • From $1/yr",
                                    color = theme.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Account & Firebase Console
                    SidebarItem(
                        icon = Icons.Default.CloudSync,
                        title = "Account & Cloud Sync",
                        badge = if (userProfile.isSignedIn) "Synced" else "Sign In",
                        iconColor = Color(0xFFF58220),
                        onClick = { onClose(); onOpenAccount() },
                        tag = "menu_account_cloud",
                        theme = theme
                    )

                    // Web Browser
                    SidebarItem(
                        icon = Icons.Default.Language,
                        title = "Web Browser",
                        iconColor = Color(0xFF38BDF8),
                        onClick = { onClose(); onOpenWebBrowser() },
                        tag = "menu_web_browser",
                        theme = theme
                    )

                    // Playlists Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { isPlaylistsExpanded = !isPlaylistsExpanded }
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = theme.accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PLAYLISTS (${playlists.size})",
                                    color = theme.accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isPlaylistsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = theme.accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { onClose(); onOpenCreatePlaylist() },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("btn_sidebar_create_playlist")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create Playlist",
                                    tint = theme.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = isPlaylistsExpanded) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (playlists.isEmpty()) {
                                    Text(
                                        text = "+ Create your first playlist",
                                        color = theme.textSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .clickable { onClose(); onOpenCreatePlaylist() }
                                    )
                                } else {
                                    playlists.forEach { pl ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onClose()
                                                    onSelectPlaylist(pl.id)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                                .testTag("menu_playlist_${pl.id}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(theme.accentColor.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MusicNote,
                                                        contentDescription = null,
                                                        tint = theme.accentColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = pl.name,
                                                    color = theme.textPrimary,
                                                    fontSize = 13.sp,
                                                    maxLines = 1
                                                )
                                            }
                                            Text(
                                                text = "${pl.trackIds.size}",
                                                color = theme.textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = theme.headerBorder.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Scan Library
                    SidebarItem(
                        icon = Icons.Default.Refresh,
                        title = "Scan Library",
                        onClick = { onClose(); onOpenScanModal() },
                        tag = "menu_scan_library",
                        theme = theme
                    )

                    // Equalizer
                    SidebarItem(
                        icon = Icons.Default.Tune,
                        title = "Equalizer",
                        onClick = { onClose(); onOpenEqualizer() },
                        tag = "menu_equalizer",
                        theme = theme
                    )

                    // Repeat Current
                    SidebarItem(
                        icon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        title = "Repeat Current",
                        badge = if (repeatMode == RepeatMode.ONE) "ON" else null,
                        iconColor = if (repeatMode == RepeatMode.ONE) theme.accentColor else theme.textSecondary,
                        onClick = onToggleRepeat,
                        tag = "menu_repeat_current",
                        theme = theme
                    )

                    // Themes
                    SidebarItem(
                        icon = Icons.Default.Palette,
                        title = "Themes",
                        onClick = { onClose(); onOpenThemes() },
                        tag = "menu_themes",
                        theme = theme
                    )

                    // Sleep Timer
                    val timerLabel = if (sleepTimerSec != null && sleepTimerSec > 0) {
                        val m = sleepTimerSec / 60
                        val s = sleepTimerSec % 60
                        String.format("%d:%02d", m, s)
                    } else null

                    SidebarItem(
                        icon = Icons.Default.AccessTime,
                        title = "Sleep timer",
                        badge = timerLabel,
                        onClick = { onClose(); onOpenSleepTimer() },
                        tag = "menu_sleep_timer",
                        theme = theme
                    )

                    // Drive mode
                    SidebarItem(
                        icon = Icons.Default.DirectionsCar,
                        title = "Drive mode",
                        onClick = { onClose(); onEnterDriveMode() },
                        tag = "menu_drive_mode",
                        theme = theme
                    )

                    // Lyrics mode
                    SidebarItem(
                        icon = Icons.Default.Subtitles,
                        title = "Lyrics mode",
                        iconColor = theme.accentColor,
                        onClick = { onClose(); onEnterLyricsMode() },
                        tag = "menu_lyrics_mode",
                        theme = theme
                    )

                    // Settings
                    SidebarItem(
                        icon = Icons.Default.Settings,
                        title = "Settings",
                        onClick = { onClose(); onOpenSettings() },
                        tag = "menu_settings",
                        theme = theme
                    )
                }
            }
        },
        content = {}
    )
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    iconColor: Color? = null,
    onClick: () -> Unit,
    tag: String,
    theme: ThemeConfig
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor ?: theme.textSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = theme.textPrimary,
                fontSize = 14.sp
            )
        }
        if (badge != null) {
            Text(
                text = badge,
                color = theme.accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
