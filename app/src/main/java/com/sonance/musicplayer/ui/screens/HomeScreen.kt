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

                // 3. RECENT PLAY (Emerald Hi-Fi Gem)
                val totalRecentPlay = recentPlayCount ?: remember(tracks) { tracks.count { it.lastPlayed > 0L || it.playCount > 0 } }
                BeveledGemCard(
                    title = "RECENT PLAY",
                    count = totalRecentPlay,
                    icon = Icons.Default.History,
                    primaryColor = Color(0xFF1E7E50),
                    highlightColor = Color(0xFF22C55E),
                    deepColor = Color(0xFF10462B),
                    modifier = Modifier.weight(1f),
                    tag = "card_recent_play",
                    onClick = { onSelectView(ActiveView.RECENT_PLAY) }
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

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // STUDIO TOOLS SECTION
            // ==========================================
            Row(
                modifier = Modifier.padding(start = 2.dp, bottom = 12.dp),
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

            // Studio Tools Grid - Clean spacing directly on top of the user selected wallpaper
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Row: Music Trim & Karaoke Mode with spacing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StudioMusicTrimModule(onClick = onOpenMusicTrim)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StudioKaraokeModule(onClick = onOpenKaraoke)
                    }
                }

                // Bottom Row: Music Bass & Sound Equalizer with spacing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StudioMusicBassModule(onClick = onOpenMusicBass)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StudioSoundEqualizerModule(onClick = onOpenEqualizer)
                    }
                }
            }

            // Bottom clearance for Floating Action Button and bottom mini player button
            Spacer(modifier = Modifier.height(100.dp))
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
 * Studio Tool 1: "Music Trim" (Matching Picture Top-Left)
 */
@Composable
private fun StudioMusicTrimModule(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .testTag("card_music_trim"),
        color = Color.Black.copy(alpha = 0.22f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
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
                        .background(Color(0xFF22C55E).copy(alpha = 0.25f)),
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
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
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
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .testTag("card_karaoke_studio"),
        color = Color.Black.copy(alpha = 0.22f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
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
                        .background(Color(0xFFE11D48).copy(alpha = 0.25f)),
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
                        color = Color(0xFFCBD5E1),
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
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(7.dp))
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
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .testTag("card_music_bass"),
        color = Color.Black.copy(alpha = 0.22f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
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
                        .background(Color(0xFFF59E0B).copy(alpha = 0.25f)),
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
                            color = if (i > 14) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                            start = p1,
                            end = p2,
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }

                    // Rotary Knob Body (Translucent Metallic with border)
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.45f),
                        radius = knobRadius + 2.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.50f)),
                            center = center,
                            radius = knobRadius
                        ),
                        radius = knobRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.20f),
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
                        color = Color(0xFFCBD5E1),
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
            .height(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .testTag("card_sound_equalizer"),
        color = Color.Black.copy(alpha = 0.22f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
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
                        .background(Color(0xFFA855F7).copy(alpha = 0.25f)),
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
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
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
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
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
                            color = Color.White.copy(alpha = 0.20f),
                            start = Offset(x, 4.dp.toPx()),
                            end = Offset(x, h - 4.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Brushed Silver Fader Cap
                        val pos = faderPositions[i]
                        val knobY = 4.dp.toPx() + (h - 8.dp.toPx()) * (1f - pos)
                        drawRoundRect(
                            color = Color(0xFFE2E8F0),
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
