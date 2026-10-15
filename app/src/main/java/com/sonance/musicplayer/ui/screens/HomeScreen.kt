package com.sonance.musicplayer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.ActiveView
import com.sonance.musicplayer.model.Playlist
import com.sonance.musicplayer.model.RemoteBackendSettings
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import kotlin.math.cos
import kotlin.math.sin

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
    mostPlayCount: Int? = null,
    recentTracks: List<Track> = emptyList(),
    videosCount: Int = 0,
    onTogglePlay: (() -> Unit)? = null,
    onNextTrack: (() -> Unit)? = null,
    onOpenFullPlayer: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isAnnouncementDismissed by remember { mutableStateOf(false) }

    val musicCount = tracks.size
    val videoCount = videosCount
    val favoriteCount = remember(tracks) { tracks.count { it.isFavorite } }
    val folderCount = remember(tracks) {
        tracks.mapNotNull { if (it.folder.isNotBlank()) it.folder else null }.distinct().size.coerceAtLeast(if (tracks.isNotEmpty()) 1 else 0)
    }
    val recentAddCount = remember(tracks) {
        val count = tracks.count { it.dateAdded > 0 }
        if (count > 0) count.coerceAtMost(tracks.size) else tracks.size.coerceAtMost(30)
    }
    val effectiveMostPlayCount = mostPlayCount ?: remember(tracks) { tracks.count { it.playCount > 0 } }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Cloud Broadcast Announcement (if active)
            if (remoteSettings != null && remoteSettings.announcementEnabled && !isAnnouncementDismissed) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .testTag("backend_announcement_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF14161C)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = "Announcement",
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SONANCE BROADCAST",
                                    color = Color(0xFF22C55E),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = remoteSettings.announcementTitle,
                                    color = Color.White,
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
                                    tint = Color(0xFF8E8E93),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 6 FACETED GEM BUTTONS WITH COUNT BADGES
            // ROW 1: [LIBRARY] [VIDEO PLAYER] [FAVORITE]
            // ROW 2: [FOLDER] [RECENT ADD] [MOST PLAY]
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. LIBRARY (Royal Sapphire Gem)
                BeveledGemCard(
                    title = "LIBRARY",
                    count = musicCount,
                    icon = Icons.Default.MusicNote,
                    primaryColor = Color(0xFF2555B4),
                    highlightColor = Color(0xFF4C82F7),
                    deepColor = Color(0xFF153375),
                    modifier = Modifier.weight(1f),
                    tag = "card_library",
                    onClick = { onSelectView(ActiveView.LIBRARY) }
                )

                // 2. VIDEO (Terracotta / Warm Amber Gem)
                BeveledGemCard(
                    title = "VIDEO",
                    count = videoCount,
                    icon = Icons.Default.VideoLibrary,
                    primaryColor = Color(0xFFBF5B3A),
                    highlightColor = Color(0xFFDE7C5B),
                    deepColor = Color(0xFF8C381E),
                    modifier = Modifier.weight(1f),
                    tag = "card_video_player",
                    onClick = { onSelectView(ActiveView.VIDEOS) }
                )

                // 3. FAVORITE (Rose Ruby Gem)
                BeveledGemCard(
                    title = "FAVORITE",
                    count = favoriteCount,
                    icon = Icons.Default.Favorite,
                    primaryColor = Color(0xFFBE2A56),
                    highlightColor = Color(0xFFE84D7B),
                    deepColor = Color(0xFF831838),
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
                // 4. FOLDER (Jade Emerald Gem)
                BeveledGemCard(
                    title = "FOLDER",
                    count = folderCount,
                    icon = Icons.Default.Folder,
                    primaryColor = Color(0xFF2E7D5B),
                    highlightColor = Color(0xFF4FA87F),
                    deepColor = Color(0xFF1B4E38),
                    modifier = Modifier.weight(1f),
                    tag = "card_folder",
                    onClick = { onSelectView(ActiveView.FOLDER) }
                )

                // 5. RECENT ADD (Deep Cyan / Teal Gem)
                BeveledGemCard(
                    title = "RECENT ADD",
                    count = recentAddCount,
                    icon = Icons.Default.PlaylistAddCheck,
                    primaryColor = Color(0xFF1A6B73),
                    highlightColor = Color(0xFF33959F),
                    deepColor = Color(0xFF104348),
                    modifier = Modifier.weight(1f),
                    tag = "card_recent_add",
                    onClick = { onSelectView(ActiveView.RECENT_ADD) }
                )

                // 6. MOST PLAY (Amethyst Purple Gem)
                BeveledGemCard(
                    title = "MOST PLAY",
                    count = effectiveMostPlayCount,
                    icon = Icons.Default.Equalizer,
                    primaryColor = Color(0xFF6B3A82),
                    highlightColor = Color(0xFF9857B8),
                    deepColor = Color(0xFF421C55),
                    modifier = Modifier.weight(1f),
                    tag = "card_most_play",
                    onClick = { onSelectView(ActiveView.MOST_PLAY) }
                )
            }

            // Decorative Analog Audio Patch Cables connecting Cards to Studio Tools
            AnalogPatchCables(modifier = Modifier.fillMaxWidth().height(22.dp))

            // ==========================================
            // STUDIO TOOLS RACK SECTION (EXACT PICTURE MATCH)
            // ==========================================
            Row(
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF22C55E))
                )
                Text(
                    text = "STUDIO TOOLS",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
            }

            // Integrated Hardware Audio Rack Console (Brushed Metal Enclosure)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0xFF3A3F4D), RoundedCornerShape(16.dp))
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                color = Color(0xFF181B22),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF20242D),
                                    Color(0xFF16181E),
                                    Color(0xFF121419)
                                )
                            )
                        )
                ) {
                    // Corner Mounting Screws on Rack Console
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val screwColor = Color(0xFF4B5563)
                        val slotColor = Color(0xFF1E242C)
                        val radius = 4.dp.toPx()

                        // 4 Corner screws
                        val points = listOf(
                            Offset(12.dp.toPx(), 12.dp.toPx()),
                            Offset(size.width - 12.dp.toPx(), 12.dp.toPx()),
                            Offset(12.dp.toPx(), size.height - 12.dp.toPx()),
                            Offset(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()),
                            // Center divider screws
                            Offset(size.width / 2f, 8.dp.toPx()),
                            Offset(size.width / 2f, size.height - 8.dp.toPx())
                        )

                        points.forEach { p ->
                            drawCircle(color = screwColor, radius = radius, center = p)
                            drawLine(
                                color = slotColor,
                                start = Offset(p.x - radius * 0.7f, p.y),
                                end = Offset(p.x + radius * 0.7f, p.y),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        // Top Row: Music Trim & Karaoke Mode with comfortable spacing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Module 1: Music Trim
                            Box(modifier = Modifier.weight(1f)) {
                                StudioMusicTrimModule(onClick = onOpenMusicTrim)
                            }

                            // Module 2: Karaoke Mode
                            Box(modifier = Modifier.weight(1f)) {
                                StudioKaraokeModule(onClick = onOpenKaraoke)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Bottom Row: Music Bass & Sound Equalizer with comfortable spacing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Module 3: Music Bass
                            Box(modifier = Modifier.weight(1f)) {
                                StudioMusicBassModule(onClick = onOpenMusicBass)
                            }

                            // Module 4: Sound Equalizer
                            Box(modifier = Modifier.weight(1f)) {
                                StudioSoundEqualizerModule(onClick = onOpenEqualizer)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // PLACE 1: CURRENT MUSIC PLAYING SECTION
            // (Circled and labeled 1 by user: shows current music playing)
            // ==========================================
            val activeCurrentTrack = currentTrack ?: recentTracks.firstOrNull() ?: tracks.firstOrNull()
            if (activeCurrentTrack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenFullPlayer?.invoke() }
                            .padding(start = 2.dp, top = 4.dp, bottom = 4.dp, end = 6.dp)
                            .testTag("btn_home_current_playing_header")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp, 16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF22C55E))
                        )
                        Text(
                            text = "CURRENT PLAYING",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF22C55E).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isPlaying && currentTrack?.id == activeCurrentTrack.id) "PLAYING" else "READY",
                                color = Color(0xFF22C55E),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (onOpenFullPlayer != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenFullPlayer.invoke() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("btn_home_open_full_player"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NOW PLAYING",
                                color = Color(0xFF22C55E),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Open Full Player",
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // High-End Hardware Card for Current Music Playing
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.5.dp,
                            color = if (isPlaying && currentTrack?.id == activeCurrentTrack.id) Color(0xFF22C55E) else Color(0xFF22C55E).copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            if (currentTrack?.id == activeCurrentTrack.id) {
                                onOpenFullPlayer?.invoke()
                            } else {
                                onPlayTrack?.invoke(activeCurrentTrack, tracks)
                            }
                        }
                        .testTag("card_current_music_playing"),
                    color = Color(0xFF141922),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cover Art Box
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF22C55E).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (activeCurrentTrack.coverArt.isNotBlank()) {
                                    coil.compose.AsyncImage(
                                        model = activeCurrentTrack.coverArt,
                                        contentDescription = activeCurrentTrack.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                if (isPlaying && currentTrack?.id == activeCurrentTrack.id) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Equalizer,
                                            contentDescription = "Playing",
                                            tint = Color(0xFF22C55E),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeCurrentTrack.title,
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (activeCurrentTrack.artist == "<unknown>") "Unknown Artist" else activeCurrentTrack.artist,
                                        color = Color(0xFF22C55E),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    val m = activeCurrentTrack.duration / 60
                                    val s = activeCurrentTrack.duration % 60
                                    Text(
                                        text = "• ${String.format("%d:%02d", m, s)}",
                                        color = Color(0xFF8E8E93),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Play/Pause and Next Action Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentTrack?.id == activeCurrentTrack.id) {
                                        onTogglePlay?.invoke()
                                    } else {
                                        onPlayTrack?.invoke(activeCurrentTrack, tracks)
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                                    .testTag("btn_home_current_play_pause")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying && currentTrack?.id == activeCurrentTrack.id) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            if (onNextTrack != null) {
                                IconButton(
                                    onClick = onNextTrack,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next Track",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // ==========================================
            // PLACE 2: RECENT PLAY SECTION
            // (Circled and labeled 2 by user: showing strictly 2 recent play songs)
            // ==========================================
            val displayRecentTracks = remember(recentTracks, tracks) {
                val recents = if (recentTracks.isNotEmpty()) recentTracks
                else tracks.filter { it.lastPlayed > 0L || it.playCount > 0 }.sortedByDescending { it.lastPlayed }
                if (recents.size >= 2) {
                    recents.take(2)
                } else if (recents.isNotEmpty()) {
                    val additional = tracks.filter { t -> recents.none { it.id == t.id } }
                    (recents + additional).take(2)
                } else {
                    tracks.take(2)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectView(ActiveView.RECENT_PLAY) }
                        .padding(start = 2.dp, top = 4.dp, bottom = 4.dp, end = 6.dp)
                        .testTag("btn_home_recent_play_header")
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp, 16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF22C55E))
                    )
                    Text(
                        text = "RECENT PLAY",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    if (displayRecentTracks.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF22C55E).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${displayRecentTracks.size}",
                                color = Color(0xFF22C55E),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectView(ActiveView.RECENT_PLAY) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("btn_home_see_all_recent"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SEE ALL",
                        color = Color(0xFF22C55E),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "See all recent plays",
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Recent Play Items List - Strictly 2 Songs as requested
            if (displayRecentTracks.isNotEmpty()) {
                val showcaseRecent = displayRecentTracks.take(2)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    showcaseRecent.forEachIndexed { index, tr ->
                        val isCurrent = (currentTrack?.id == tr.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isCurrent) Color(0xFF1B2C22) else Color(0xFF14171E).copy(alpha = 0.88f))
                                .border(
                                    width = if (isCurrent) 1.5.dp else 1.dp,
                                    color = if (isCurrent) Color(0xFF22C55E) else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    onPlayTrack?.invoke(tr, displayRecentTracks)
                                }
                                .padding(horizontal = 12.dp)
                                .testTag("home_recent_track_${tr.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rank index indicator (#1, #2 based on recent play order)
                                Text(
                                    text = "#${index + 1}",
                                    color = if (index == 0) Color(0xFF22C55E) else Color(0xFF8E8E93),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(26.dp)
                                )

                                // Compact Artwork Box
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF22C55E).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (tr.coverArt.isNotBlank()) {
                                        coil.compose.AsyncImage(
                                            model = tr.coverArt,
                                            contentDescription = tr.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = if (isCurrent) Color(0xFF22C55E) else Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    if (isCurrent && isPlaying) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Equalizer,
                                                contentDescription = "Playing",
                                                tint = Color(0xFF22C55E),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr.title,
                                        color = if (isCurrent) Color(0xFF22C55E) else Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (tr.artist == "<unknown>") "Unknown Artist" else tr.artist,
                                            color = Color(0xFF8E8E93),
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        val m = tr.duration / 60
                                        val s = tr.duration % 60
                                        Text(
                                            text = "• ${String.format("%d:%02d", m, s)}",
                                            color = Color(0xFF8E8E93).copy(alpha = 0.7f),
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }
                            }

                            // Quick Play / Equalizer Button
                            IconButton(
                                onClick = {
                                    onPlayTrack?.invoke(tr, displayRecentTracks)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrent && isPlaying) Icons.Default.Equalizer else Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = if (isCurrent) Color(0xFF22C55E) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty state for Recent Play
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectView(ActiveView.RECENT_PLAY) }
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                    color = Color(0xFF14171E).copy(alpha = 0.85f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Recent Plays Yet",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Play songs from your library to see your history ranked here",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Bottom breathing clearance for FAB
            Spacer(modifier = Modifier.height(90.dp))
        }

        // Vibrant Green Shuffle All Floating Action Button (Matching the picture)
        if (showShuffleButton) {
            FloatingActionButton(
                onClick = onShuffleAll,
                containerColor = Color(0xFF22C55E),
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp, end = 20.dp)
                    .size(56.dp)
                    .testTag("btn_fab_shuffle_all")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle playback",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * 3D Faceted Crystal Gemstone Button with count number displayed on top of each component
 */
@Composable
private fun BeveledGemCard(
    title: String,
    count: Int? = null,
    icon: ImageVector,
    primaryColor: Color,
    highlightColor: Color,
    deepColor: Color,
    modifier: Modifier = Modifier,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Faceted Jewel Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val bevel = 8.dp.toPx()

                // 1. Base Dark Shadow Facet
                drawRoundRect(
                    color = deepColor,
                    size = size,
                    cornerRadius = CornerRadius(16.dp.toPx())
                )

                // 2. Beveled Faceted Top-Left Highlight
                val highlightPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w - bevel, bevel)
                    lineTo(bevel, bevel)
                    lineTo(bevel, h - bevel)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = highlightPath,
                    brush = Brush.linearGradient(
                        colors = listOf(highlightColor.copy(alpha = 0.85f), highlightColor.copy(alpha = 0.3f)),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                )

                // 3. Center Raised Gem Facet
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor, deepColor),
                        startY = bevel,
                        endY = h - bevel
                    ),
                    topLeft = Offset(bevel, bevel),
                    size = Size(w - bevel * 2, h - bevel * 2),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )

                // 4. Crystal Sheen Diagonal Reflection
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(w * 0.35f, h * 0.25f),
                        radius = w * 0.45f
                    ),
                    radius = w * 0.45f,
                    center = Offset(w * 0.35f, h * 0.25f)
                )
            }

            // Top Badge: Prominent Number of music, videos, favorite, folder, recent add, mostly play
            count?.let { cnt ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = "$cnt",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            // Center Content: Iconic Symbol + Capitalized Label
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Decorative Audio Patch Cables connecting the gems down to the Studio Tools rack
 */
@Composable
private fun AnalogPatchCables(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val wireColor1 = Color(0xFF2D3748)
        val wireColor2 = Color(0xFF1F2937)
        val jackColor = Color(0xFF4A5568)

        // Draw 3 subtle curved hanging cables
        val path1 = Path().apply {
            moveTo(w * 0.22f, 0f)
            cubicTo(w * 0.25f, h * 0.9f, w * 0.45f, h * 0.9f, w * 0.48f, h)
        }
        drawPath(path1, color = wireColor1, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        val path2 = Path().apply {
            moveTo(w * 0.5f, 0f)
            cubicTo(w * 0.52f, h * 0.8f, w * 0.50f, h * 0.8f, w * 0.52f, h)
        }
        drawPath(path2, color = wireColor2, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        val path3 = Path().apply {
            moveTo(w * 0.78f, 0f)
            cubicTo(w * 0.75f, h * 0.9f, w * 0.55f, h * 0.9f, w * 0.52f, h)
        }
        drawPath(path3, color = wireColor1, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Small jack terminals at bottom
        drawCircle(color = jackColor, radius = 3.dp.toPx(), center = Offset(w * 0.48f, h - 2.dp.toPx()))
        drawCircle(color = jackColor, radius = 3.dp.toPx(), center = Offset(w * 0.52f, h - 2.dp.toPx()))
    }
}

/**
 * Studio Tool 1: "Music Trim" (Matching Picture Top-Left)
 */
@Composable
private fun StudioMusicTrimModule(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Color(0xFF2D323E), RoundedCornerShape(12.dp))
            .testTag("card_music_trim"),
        color = Color(0xFF14161C),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scissor Icon Badge in Green
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF22C55E).copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Recessed Audio Waveform LCD Display with Trimmer Handles & Time
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0C0E14))
                        .border(1.dp, Color(0xFF1E2330), RoundedCornerShape(6.dp))
                ) {
                    // Audio waveform & trim sliders
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val centerY = h / 2f

                        // Green audio waveform peaks
                        val barCount = 28
                        val barWidth = (w - 20.dp.toPx()) / barCount
                        val heights = listOf(
                            0.2f, 0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.8f, 0.95f, 0.75f, 0.85f,
                            0.6f, 0.9f, 0.4f, 0.7f, 0.85f, 0.6f, 0.5f, 0.75f, 0.9f, 0.4f,
                            0.6f, 0.8f, 0.5f, 0.3f, 0.7f, 0.5f, 0.4f, 0.2f
                        )

                        for (i in 0 until barCount) {
                            val factor = heights[i % heights.size]
                            val barH = (h * 0.75f) * factor
                            val x = 10.dp.toPx() + i * barWidth
                            drawLine(
                                color = Color(0xFF22C55E).copy(alpha = 0.85f),
                                start = Offset(x, centerY - barH / 2),
                                end = Offset(x, centerY + barH / 2),
                                strokeWidth = 1.8.dp.toPx()
                            )
                        }

                        // Left trimmer bracket/handle
                        val leftX = 14.dp.toPx()
                        drawLine(
                            color = Color.White,
                            start = Offset(leftX, 3.dp.toPx()),
                            end = Offset(leftX, h - 3.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawRoundRect(
                            color = Color(0xFFE2E8F0),
                            topLeft = Offset(leftX - 3.dp.toPx(), h / 2f - 4.dp.toPx()),
                            size = Size(6.dp.toPx(), 8.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )

                        // Right trimmer bracket/handle
                        val rightX = w - 14.dp.toPx()
                        drawLine(
                            color = Color.White,
                            start = Offset(rightX, 3.dp.toPx()),
                            end = Offset(rightX, h - 3.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawRoundRect(
                            color = Color(0xFFE2E8F0),
                            topLeft = Offset(rightX - 3.dp.toPx(), h / 2f - 4.dp.toPx()),
                            size = Size(6.dp.toPx(), 8.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                    }

                    // Digital time badge in bottom right of waveform LCD
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 2.dp, end = 4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "00:02:05",
                            color = Color(0xFFA7F3D0),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Title below
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Music Trim",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Studio Tool 2: "Karaoke Mode" (Matching Picture Top-Right)
 */
@Composable
private fun StudioKaraokeModule(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Color(0xFF2D323E), RoundedCornerShape(12.dp))
            .testTag("card_karaoke_studio"),
        color = Color(0xFF14161C),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Microphone Icon Badge in Coral/Red
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE11D48).copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color(0xFFFB7185),
                        modifier = Modifier.size(15.dp)
                    )
                }

                // "AI VOCAL" Pill Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE11D48).copy(alpha = 0.22f))
                        .border(1.dp, Color(0xFFE11D48).copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = "AI VOCAL",
                        color = Color(0xFFFDA4AF),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Karaoke Mode",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Removal Level",
                        color = Color(0xFF8E8E93),
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Hardware VU-Meter / Gradient LED Level Bar with Brushed Slider Knob
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF0C0E14))
                        .border(1.dp, Color(0xFF1E2330), RoundedCornerShape(7.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Smooth gradient LED bar (Green -> Yellow -> Orange -> Red)
                        val levelFrac = 0.72f
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF22C55E),
                                    Color(0xFFFACC15),
                                    Color(0xFFFB923C),
                                    Color(0xFFEF4444)
                                )
                            ),
                            size = Size(w * levelFrac, h),
                            cornerRadius = CornerRadius(7.dp.toPx())
                        )

                        // Brushed Silver Fader Knob
                        val knobX = w * levelFrac
                        drawRoundRect(
                            color = Color(0xFFE2E8F0),
                            topLeft = Offset(knobX - 4.dp.toPx(), 1.dp.toPx()),
                            size = Size(8.dp.toPx(), h - 2.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                        drawLine(
                            color = Color(0xFF475569),
                            start = Offset(knobX, 3.dp.toPx()),
                            end = Offset(knobX, h - 3.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Studio Tool 3: "Music Bass" (Matching Picture Bottom-Left)
 */
@Composable
private fun StudioMusicBassModule(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Color(0xFF2D323E), RoundedCornerShape(12.dp))
            .testTag("card_music_bass"),
        color = Color(0xFF14161C),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Amber Sound Waves Icon Badge
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(15.dp)
                    )
                }

                Text(
                    text = "Music Bass",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Realistic Rotary Audio Potentiometer Dial Knob
            Box(
                modifier = Modifier
                    .size(68.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = size.width / 2f - 2.dp.toPx()
                    val knobRadius = outerRadius * 0.68f

                    // Outer Scale Tick Marks
                    val totalTicks = 24
                    val startAngle = 135f
                    val sweepAngle = 270f
                    for (i in 0..totalTicks) {
                        val angleDeg = startAngle + (i.toFloat() / totalTicks) * sweepAngle
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val r1 = outerRadius - 3.dp.toPx()
                        val r2 = outerRadius
                        val p1 = Offset(
                            (center.x + r1 * cos(angleRad)).toFloat(),
                            (center.y + r1 * sin(angleRad)).toFloat()
                        )
                        val p2 = Offset(
                            (center.x + r2 * cos(angleRad)).toFloat(),
                            (center.y + r2 * sin(angleRad)).toFloat()
                        )
                        drawLine(
                            color = if (i > 14) Color(0xFFF59E0B) else Color(0xFF64748B),
                            start = p1,
                            end = p2,
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }

                    // Rotary Knob Body (Brushed Dark Metallic with Drop Shadow)
                    drawCircle(
                        color = Color(0xFF0F1116),
                        radius = knobRadius + 2.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF2C3240), Color(0xFF161922)),
                            center = center,
                            radius = knobRadius
                        ),
                        radius = knobRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF3E4554),
                        radius = knobRadius,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Indicator Notch Line (pointing to +6dB ~ 60 degrees from start)
                    val indicatorAngleDeg = 240f
                    val indicatorRad = Math.toRadians(indicatorAngleDeg.toDouble())
                    val notchInner = Offset(
                        (center.x + (knobRadius * 0.45f) * cos(indicatorRad)).toFloat(),
                        (center.y + (knobRadius * 0.45f) * sin(indicatorRad)).toFloat()
                    )
                    val notchOuter = Offset(
                        (center.x + (knobRadius * 0.9f) * cos(indicatorRad)).toFloat(),
                        (center.y + (knobRadius * 0.9f) * sin(indicatorRad)).toFloat()
                    )
                    drawLine(
                        color = Color(0xFFF59E0B),
                        start = notchInner,
                        end = notchOuter,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Digital center readout: "+6 dB Boost"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "+6 dB",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Boost",
                        color = Color(0xFF8E8E93),
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Studio Tool 4: "Sound Equalizer" (Matching Picture Bottom-Right)
 */
@Composable
private fun StudioSoundEqualizerModule(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Color(0xFF2D323E), RoundedCornerShape(12.dp))
            .testTag("card_sound_equalizer"),
        color = Color(0xFF14161C),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Purple Tune Icon Badge
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFA855F7).copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Mini Spectrum Visualizer + "10-BAND EQ" Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F1116))
                        .border(1.dp, Color(0xFF2D323E), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    // 5-bar glowing spectrum
                    Canvas(modifier = Modifier.size(18.dp, 10.dp)) {
                        val heights = listOf(0.4f, 0.7f, 1.0f, 0.6f, 0.8f)
                        val barW = size.width / heights.size
                        heights.forEachIndexed { idx, h ->
                            drawLine(
                                color = Color(0xFFC084FC),
                                start = Offset(idx * barW + 1.dp.toPx(), size.height),
                                end = Offset(idx * barW + 1.dp.toPx(), size.height * (1f - h)),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "10-BAND EQ",
                        color = Color(0xFFE9D5FF),
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Hardware Graphic Equalizer Faders Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0C0E14))
                    .border(1.dp, Color(0xFF1E2330), RoundedCornerShape(6.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val sliderCount = 7
                    val spacing = w / (sliderCount + 1)
                    val faderPositions = listOf(0.35f, 0.6f, 0.45f, 0.8f, 0.55f, 0.7f, 0.4f)

                    for (i in 0 until sliderCount) {
                        val x = spacing * (i + 1)
                        // Vertical Groove Track
                        drawLine(
                            color = Color(0xFF232834),
                            start = Offset(x, 4.dp.toPx()),
                            end = Offset(x, h - 4.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Brushed Silver Fader Cap
                        val pos = faderPositions[i]
                        val knobY = 4.dp.toPx() + (h - 8.dp.toPx()) * (1f - pos)
                        drawRoundRect(
                            color = Color(0xFFCBD5E1),
                            topLeft = Offset(x - 4.5.dp.toPx(), knobY - 2.5.dp.toPx()),
                            size = Size(9.dp.toPx(), 5.dp.toPx()),
                            cornerRadius = CornerRadius(1.5.dp.toPx())
                        )
                        drawLine(
                            color = Color(0xFF334155),
                            start = Offset(x - 3.dp.toPx(), knobY),
                            end = Offset(x + 3.dp.toPx(), knobY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }

            // Title below
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sound Equalizer",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
