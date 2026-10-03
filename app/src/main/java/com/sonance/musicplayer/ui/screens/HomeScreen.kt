package com.sonance.musicplayer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sonance.musicplayer.model.ActiveView
import com.sonance.musicplayer.model.Playlist
import com.sonance.musicplayer.model.RemoteBackendSettings
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track

@Composable
fun HomeScreen(
    tracks: List<Track>,
    playlists: List<Playlist>,
    theme: ThemeConfig,
    showShuffleButton: Boolean = true,
    remoteSettings: RemoteBackendSettings? = null,
    currentTrack: Track? = null,
    isPlaying: Boolean = false,
    isScanning: Boolean = false,
    onSelectView: (ActiveView) -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onOpenCreatePlaylist: () -> Unit,
    onShuffleAll: () -> Unit,
    onOpenMusicTrim: () -> Unit,
    onOpenKaraoke: () -> Unit,
    onOpenMusicBass: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onPlayTrack: ((Track, List<Track>) -> Unit)? = null,
    onToggleFavorite: ((String) -> Unit)? = null,
    recentPlayCount: Int? = null,
    mostPlayCount: Int? = null
) {
    val context = LocalContext.current
    var isAnnouncementDismissed by remember { mutableStateOf(false) }

    val libraryCount = tracks.size
    val validFolders = tracks.map { it.folder }.filter { it.isNotBlank() && it != "<unknown>" }.distinct()
    val folderCount = if (validFolders.isNotEmpty()) validFolders.size else if (tracks.isNotEmpty()) 1 else 0
    val favoriteCount = tracks.count { it.isFavorite }
    val computedRecentCount = tracks.count { it.playCount > 0 || it.lastPlayed > 0 }
    val recentAddCount = tracks.size
    val effectiveMostPlayCount = mostPlayCount ?: tracks.count { it.playCount > 0 }
    val effectiveRecentPlayCount = recentPlayCount ?: computedRecentCount

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Cloud Broadcast Announcement from Firebase Backend
            if (remoteSettings != null && remoteSettings.announcementEnabled && !isAnnouncementDismissed) {
                val bannerIcon = when (remoteSettings.announcementType.lowercase()) {
                    "promo" -> Icons.Default.LocalOffer
                    "alert", "warning" -> Icons.Default.Warning
                    "update" -> Icons.Default.SystemUpdate
                    else -> Icons.Default.Campaign
                }
                val bannerColor = when (remoteSettings.announcementType.lowercase()) {
                    "promo" -> Color(0xFFFF9800)
                    "alert", "warning" -> Color(0xFFEF5350)
                    "update" -> Color(0xFF4CAF50)
                    else -> theme.accentColor
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(1.dp, bannerColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .testTag("backend_announcement_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = theme.sidebarBg
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(bannerColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = bannerIcon,
                                    contentDescription = "Announcement",
                                    tint = bannerColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "CLOUD BROADCAST",
                                        color = bannerColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(bannerColor)
                                    )
                                }
                                Text(
                                    text = remoteSettings.announcementTitle,
                                    color = theme.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { isAnnouncementDismissed = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = theme.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (remoteSettings.announcementMessage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = remoteSettings.announcementMessage,
                                color = theme.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        if (remoteSettings.announcementActionUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(remoteSettings.announcementActionUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = bannerColor),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Open Link", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 6 Category Tiles Grid (3 columns, 2 rows)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CategoryCard(
                    title = "LIBRARY",
                    icon = Icons.Default.MusicNote,
                    bg = theme.libraryCard.bg,
                    modifier = Modifier.weight(1f),
                    tag = "card_library",
                    onClick = { onSelectView(ActiveView.LIBRARY) }
                )
                CategoryCard(
                    title = "FOLDER",
                    icon = Icons.Default.Folder,
                    bg = theme.folderCard.bg,
                    modifier = Modifier.weight(1f),
                    tag = "card_folder",
                    onClick = { onSelectView(ActiveView.FOLDER) }
                )
                CategoryCard(
                    title = "FAVORITE",
                    icon = Icons.Default.Favorite,
                    bg = theme.favoriteCard.bg,
                    modifier = Modifier.weight(1f),
                    tag = "card_favorite",
                    onClick = { onSelectView(ActiveView.FAVORITE) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CategoryCard(
                    title = "RECENT PLAY",
                    icon = Icons.Default.History,
                    bg = theme.recentPlayCard.bg,
                    modifier = Modifier.weight(1f),
                    tag = "card_recent_play",
                    onClick = { onSelectView(ActiveView.RECENT_PLAY) }
                )
                CategoryCard(
                    title = "RECENT ADD",
                    icon = Icons.Default.PlaylistAddCheck,
                    bg = theme.recentAddCard.bg,
                    modifier = Modifier.weight(1f),
                    tag = "card_recent_add",
                    onClick = { onSelectView(ActiveView.RECENT_ADD) }
                )
                CategoryCard(
                    title = "MOST PLAY",
                    icon = Icons.Default.Equalizer,
                    bg = theme.mostPlayCard.bg,
                    modifier = Modifier.weight(1f),
                    tag = "card_most_play",
                    onClick = { onSelectView(ActiveView.MOST_PLAY) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // STUDIO TOOLS SECTION (Matching Image 1)
            Row(
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(theme.accentColor)
                )
                Text(
                    text = "STUDIO TOOLS",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioToolCard(
                    title = "Music Trim",
                    subtitle = "Ringtone, Alarm & Cutter",
                    badge = "TRIM",
                    icon = Icons.Default.ContentCut,
                    iconTint = theme.accentColor,
                    badgeBg = theme.accentColor.copy(alpha = 0.20f),
                    badgeText = theme.accentColor,
                    cardBg = theme.sidebarBg.copy(alpha = 0.65f),
                    cardBorder = Color.White.copy(alpha = 0.12f),
                    textColor = Color.White,
                    subtextColor = Color(0xFF86EFAC).copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                    tag = "card_music_trim",
                    onClick = onOpenMusicTrim
                )

                StudioToolCard(
                    title = "Karaoke Mode",
                    subtitle = "Vocal Remover & Synced Lyrics",
                    badge = "AI VOCAL",
                    icon = Icons.Default.Mic,
                    iconTint = Color(0xFFFB7185),
                    badgeBg = Color(0xFFF43F5E).copy(alpha = 0.20f),
                    badgeText = Color(0xFFFDA4AF),
                    cardBg = theme.sidebarBg.copy(alpha = 0.65f),
                    cardBorder = Color.White.copy(alpha = 0.12f),
                    textColor = Color.White,
                    subtextColor = Color(0xFF86EFAC).copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                    tag = "card_karaoke_studio",
                    onClick = onOpenKaraoke
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioToolCard(
                    title = "Beat Instrumental",
                    subtitle = "Convert & Isolate AI Stems",
                    badge = "AI STEMS",
                    icon = Icons.Default.AutoFixHigh,
                    iconTint = Color(0xFFFBBF24),
                    badgeBg = Color(0xFFF59E0B).copy(alpha = 0.20f),
                    badgeText = Color(0xFFFDE68A),
                    cardBg = theme.sidebarBg.copy(alpha = 0.65f),
                    cardBorder = Color.White.copy(alpha = 0.12f),
                    textColor = Color.White,
                    subtextColor = Color(0xFF86EFAC).copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                    tag = "card_beat_instrumental",
                    onClick = onOpenMusicBass
                )

                StudioToolCard(
                    title = "Sound Equalizer",
                    subtitle = "Bass Boost, 3D Sound & Presets",
                    badge = "10-BAND EQ",
                    icon = Icons.Default.Tune,
                    iconTint = Color(0xFFC084FC),
                    badgeBg = Color(0xFFA855F7).copy(alpha = 0.20f),
                    badgeText = Color(0xFFE9D5FF),
                    cardBg = theme.sidebarBg.copy(alpha = 0.65f),
                    cardBorder = Color.White.copy(alpha = 0.12f),
                    textColor = Color.White,
                    subtextColor = Color(0xFF86EFAC).copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                    tag = "card_sound_equalizer",
                    onClick = onOpenEqualizer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // PLAYLISTS SECTION (Matching Image 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp, 16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(theme.accentColor)
                    )
                    Text(
                        text = "PLAYLISTS",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenCreatePlaylist() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Playlist",
                        tint = theme.accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "NEW PLAYLIST",
                        color = theme.accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val homePlaylists = playlists.take(2)
            if (homePlaylists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    homePlaylists.forEach { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.sidebarBg.copy(alpha = 0.65f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .clickable { onSelectPlaylist(pl.id) }
                                .padding(horizontal = 12.dp)
                                .testTag("home_playlist_${pl.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.accentColor.copy(alpha = 0.20f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = theme.accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = pl.name,
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${pl.trackIds.size} songs",
                                        color = Color(0xFF86EFAC).copy(alpha = 0.85f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF86EFAC).copy(alpha = 0.70f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button (Shuffle All)
        if (showShuffleButton) {
            FloatingActionButton(
                onClick = onShuffleAll,
                containerColor = theme.shuffleFabBg,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp)
                    .size(54.dp)
                    .testTag("btn_fab_shuffle_all")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle playback",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    title: String,
    icon: ImageVector,
    bg: Color,
    modifier: Modifier = Modifier,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .aspectRatio(1.05f)
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = bg,
        shadowElevation = 3.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StudioToolCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    iconTint: Color,
    badgeBg: Color,
    badgeText: Color,
    cardBg: Color,
    cardBorder: Color,
    textColor: Color,
    subtextColor: Color,
    modifier: Modifier = Modifier,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(102.dp)
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        shadowElevation = 2.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badge,
                        color = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = subtextColor,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
