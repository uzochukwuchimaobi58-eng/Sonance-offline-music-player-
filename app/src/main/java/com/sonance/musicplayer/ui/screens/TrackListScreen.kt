package com.sonance.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sonance.musicplayer.model.ActiveView
import com.sonance.musicplayer.model.Playlist
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import java.io.File

@Composable
fun TrackListScreen(
    view: ActiveView,
    title: String,
    tracks: List<Track>,
    allPlaylists: List<Playlist>,
    currentTrackId: String?,
    isPlaying: Boolean,
    theme: ThemeConfig,
    showShuffleButton: Boolean = true,
    isPro: Boolean = false,
    admobEnabled: Boolean = true,
    onOpenProUpgrade: () -> Unit = {},
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddToPlaylist: (String, String) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onOpenMusicTrim: (Track) -> Unit,
    onOpenLyrics: (Track) -> Unit,
    onShuffleAll: (List<Track>) -> Unit,
    onSelectView: ((ActiveView) -> Unit)? = null,
    activeSortBy: String = "default"
) {
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var sortBy by remember(activeSortBy) { mutableStateOf(activeSortBy) } // default, title, artist, duration, plays
    var activeTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showPlaylistPickerForTrack by remember { mutableStateOf<Track?>(null) }

    // Folder view logic
    val folderGroups = remember(tracks) {
        val map = mutableMapOf<String, MutableList<Track>>()
        tracks.forEach { t ->
            val f = if (t.folder.isNotBlank()) File(t.folder).name.ifEmpty { t.folder } else "Phone Storage"
            map.getOrPut(f) { mutableListOf() }.add(t)
        }
        map
    }

    val displayTracks = remember(tracks, view, selectedFolder, sortBy) {
        val base = if (view == ActiveView.FOLDER && selectedFolder != null) {
            tracks.filter {
                val f = if (it.folder.isNotBlank()) File(it.folder).name.ifEmpty { it.folder } else "Phone Storage"
                f == selectedFolder
            }
        } else {
            tracks
        }

        when (sortBy) {
            "title" -> base.sortedBy { it.title.lowercase() }
            "artist" -> base.sortedBy { it.artist.lowercase() }
            "duration" -> base.sortedByDescending { it.duration }
            "plays" -> base.sortedByDescending { it.playCount }
            else -> base
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bgCanvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 76.dp)
        ) {
            // Category navigation tabs (SONGS, FOLDERS, PLAYLISTS, FAVORITES, RECENT)
            if (onSelectView != null) {
                val tabs = listOf(
                    Triple(ActiveView.LIBRARY, "SONGS", Icons.Default.MusicNote),
                    Triple(ActiveView.FOLDER, "FOLDERS", Icons.Default.Folder),
                    Triple(ActiveView.FAVORITE, "FAVORITES", Icons.Default.Favorite),
                    Triple(ActiveView.RECENT_PLAY, "RECENT", Icons.Default.History),
                    Triple(ActiveView.RECENT_ADD, "RECENT ADD", Icons.Default.LibraryAdd),
                    Triple(ActiveView.MOST_PLAY, "MOST PLAYED", Icons.Default.TrendingUp)
                )

                ScrollableTabRow(
                    selectedTabIndex = tabs.indexOfFirst { it.first == view }.coerceAtLeast(0),
                    containerColor = theme.headerBg,
                    contentColor = theme.accentColor,
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        val index = tabs.indexOfFirst { it.first == view }
                        if (index in tabPositions.indices) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                                color = theme.accentColor
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.15f))
                    }
                ) {
                    tabs.forEach { (tabView, tabTitle, tabIcon) ->
                        val isSelected = view == tabView
                        Tab(
                            selected = isSelected,
                            onClick = { onSelectView(tabView) },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = tabIcon,
                                        contentDescription = null,
                                        tint = if (isSelected) theme.accentColor else theme.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = tabTitle,
                                        color = if (isSelected) theme.accentColor else theme.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Header stats & Play All / Shuffle All toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    if (view == ActiveView.FOLDER && selectedFolder != null) {
                        Text(
                            text = "‹ All Folders",
                            color = theme.accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { selectedFolder = null }
                                .padding(vertical = 2.dp)
                        )
                        Text(
                            text = selectedFolder ?: "",
                            color = theme.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "${displayTracks.size} songs",
                            color = theme.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = {
                            if (displayTracks.isNotEmpty()) {
                                onPlayTrack(displayTracks.first(), displayTracks)
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = theme.accentColor.copy(alpha = 0.2f),
                            contentColor = theme.accentColor
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (showShuffleButton) {
                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { onShuffleAll(displayTracks) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = theme.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Folder list if viewing folder mode and no folder selected
            if (view == ActiveView.FOLDER && selectedFolder == null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp)
                ) {
                    items(folderGroups.keys.toList()) { folderName ->
                        val count = folderGroups[folderName]?.size ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.headerBg)
                                .clickable { selectedFolder = folderName }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = theme.accentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = folderName,
                                        color = theme.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$count tracks",
                                        color = theme.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
                // Song list
                if (displayTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No songs found in this category",
                            color = theme.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                    ) {
                        itemsIndexed(displayTracks, key = { _, it -> it.id }) { index, track ->
                            val isCurrent = track.id == currentTrackId

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) theme.accentColor.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable { onPlayTrack(track, displayTracks) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                    .testTag("track_item_${track.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Artwork
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF27272A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (track.coverArt.isNotEmpty()) {
                                            AsyncImage(
                                                model = track.coverArt,
                                                contentDescription = track.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
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
                                                    tint = theme.accentColor,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            color = if (isCurrent) theme.accentColor else theme.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (track.artist == "<unknown>") "Unknown Artist" else track.artist,
                                                color = theme.textSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )

                                            val m = track.duration / 60
                                            val s = track.duration % 60
                                            Text(
                                                text = "• ${String.format("%d:%02d", m, s)}",
                                                color = theme.textSecondary.copy(alpha = 0.7f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                // Actions (Favorite & More)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onToggleFavorite(track.id) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (track.isFavorite) Color(0xFFF43F5E) else theme.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { activeTrackForMenu = track },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Track options",
                                            tint = theme.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // User request: Inside music library ads should show after 8 tracks (index == 7) and after user scrolling to 100 tracks (index == 99), making 2 ads in total
                            if (!isPro && admobEnabled && (index == 7 || index == 99)) {
                                com.sonance.musicplayer.ui.components.InlineTrackAdCard(
                                    isPro = isPro,
                                    admobEnabled = admobEnabled,
                                    onOpenProUpgrade = onOpenProUpgrade,
                                    theme = theme
                                )
                            }
                        }
                    }
                }
            }
        }

        // Track Options Bottom Sheet / Dialog
        activeTrackForMenu?.let { tr ->
            AlertDialog(
                onDismissRequest = { activeTrackForMenu = null },
                containerColor = theme.sidebarBg,
                title = {
                    Text(
                        text = tr.title,
                        color = theme.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TrackActionRow(
                            icon = Icons.Default.QueueMusic,
                            title = "Add to Playlist",
                            onClick = {
                                activeTrackForMenu = null
                                showPlaylistPickerForTrack = tr
                            },
                            theme = theme
                        )

                        TrackActionRow(
                            icon = Icons.Default.ContentCut,
                            title = "Music Trim (Ringtone)",
                            onClick = {
                                activeTrackForMenu = null
                                onOpenMusicTrim(tr)
                            },
                            theme = theme
                        )

                        TrackActionRow(
                            icon = Icons.Default.Subtitles,
                            title = "View / Edit Lyrics",
                            onClick = {
                                activeTrackForMenu = null
                                onOpenLyrics(tr)
                            },
                            theme = theme
                        )

                        TrackActionRow(
                            icon = Icons.Default.Delete,
                            title = "Delete from Library",
                            onClick = {
                                activeTrackForMenu = null
                                onDeleteTrack(tr.id)
                            },
                            theme = theme,
                            tint = Color(0xFFF43F5E)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeTrackForMenu = null }) {
                        Text("Close", color = theme.accentColor)
                    }
                }
            )
        }

        // Add to Playlist Picker
        showPlaylistPickerForTrack?.let { tr ->
            AlertDialog(
                onDismissRequest = { showPlaylistPickerForTrack = null },
                containerColor = theme.sidebarBg,
                title = {
                    Text("Add to Playlist", color = theme.textPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        allPlaylists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onAddToPlaylist(tr.id, pl.id)
                                        showPlaylistPickerForTrack = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = theme.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = pl.name,
                                    color = theme.textPrimary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlaylistPickerForTrack = null }) {
                        Text("Cancel", color = theme.accentColor)
                    }
                }
            )
        }
    }
}

@Composable
private fun TrackActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    theme: ThemeConfig,
    tint: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: theme.textPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = tint ?: theme.textPrimary,
            fontSize = 14.sp
        )
    }
}
