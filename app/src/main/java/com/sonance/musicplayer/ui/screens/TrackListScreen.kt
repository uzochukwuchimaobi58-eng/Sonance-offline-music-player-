package com.sonance.musicplayer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.sonance.musicplayer.model.ActiveView
import com.sonance.musicplayer.model.Playlist
import com.sonance.musicplayer.model.RepeatMode
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.ui.components.AlphabetFastScroller
import com.sonance.musicplayer.util.TrackComparators
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
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
    repeatMode: RepeatMode = RepeatMode.OFF,
    onToggleRepeat: () -> Unit = {},
    isShuffle: Boolean = false,
    onToggleShuffle: () -> Unit = {},
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
    activeSortBy: String = "title",
    onPlayTracks: ((List<Track>) -> Unit)? = null,
    onAddTracksToPlaylist: ((trackIds: List<String>, playlistId: String) -> Unit)? = null,
    onDeleteTracks: ((trackIds: List<String>) -> Unit)? = null,
    onAddTracksToFavorites: ((trackIds: List<String>) -> Unit)? = null,
    onEnqueueTracks: ((List<Track>) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var sortBy by remember(activeSortBy) {
        mutableStateOf(if (activeSortBy == "default" || activeSortBy.isBlank()) "title" else activeSortBy)
    } // title, artist, duration, plays
    var activeTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showPlaylistPickerForTrack by remember { mutableStateOf<Track?>(null) }
    var trackToDeletePermanently by remember { mutableStateOf<Track?>(null) }

    // Multi-Select state (Screenshot_20260921-102237.jpg)
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionSearchQuery by remember { mutableStateOf("") }
    var showBatchPlaylistPicker by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchMoreMenu by remember { mutableStateOf(false) }
    var showCreatePlaylistInBatch by remember { mutableStateOf(false) }
    var newPlaylistNameInput by remember { mutableStateOf("") }

    // Back button in selection mode exits selection mode
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedTrackIds = emptySet()
        selectionSearchQuery = ""
    }

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
            "title" -> base.sortedWith(TrackComparators.TitleComparator)
            "artist" -> base.sortedWith(TrackComparators.ArtistComparator)
            "duration" -> base.sortedByDescending { it.duration }
            "plays" -> base.sortedByDescending { it.playCount }
            else -> {
                // In Library, Playlists, and Folders, default to A to Z organized by Title
                if (view == ActiveView.LIBRARY || view == ActiveView.PLAYLIST_DETAIL || view == ActiveView.FOLDER) {
                    base.sortedWith(TrackComparators.TitleComparator)
                } else {
                    base
                }
            }
        }
    }

    val finalTracks = remember(displayTracks, isSelectionMode, selectionSearchQuery) {
        if (isSelectionMode && selectionSearchQuery.isNotBlank()) {
            val q = selectionSearchQuery.trim().lowercase()
            displayTracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        } else {
            displayTracks
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isSelectionMode) {
                // Multi-Selection Top Header & Search Bar (Screenshot_20260921-102237.jpg)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.headerBg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedTrackIds = emptySet()
                                selectionSearchQuery = ""
                            },
                            modifier = Modifier.testTag("btn_back_selection")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Selection Mode",
                                tint = theme.textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "${selectedTrackIds.size} songs selected",
                            color = theme.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        // Top right checklist / select all icon
                        IconButton(
                            onClick = {
                                if (selectedTrackIds.size == finalTracks.size && finalTracks.isNotEmpty()) {
                                    selectedTrackIds = emptySet()
                                } else {
                                    selectedTrackIds = finalTracks.map { it.id }.toSet()
                                }
                            },
                            modifier = Modifier.testTag("btn_select_all_header")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                                contentDescription = "Select All",
                                tint = if (selectedTrackIds.isNotEmpty() && selectedTrackIds.size == finalTracks.size) theme.accentColor else theme.textPrimary
                            )
                        }
                    }

                    // Search library field
                    OutlinedTextField(
                        value = selectionSearchQuery,
                        onValueChange = { selectionSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                            .testTag("input_search_library"),
                        placeholder = {
                            Text("Search library", color = theme.textSecondary.copy(alpha = 0.7f), fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = theme.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (selectionSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { selectionSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = theme.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.sidebarBg.copy(alpha = 0.85f),
                            unfocusedContainerColor = theme.sidebarBg.copy(alpha = 0.65f),
                            focusedBorderColor = theme.accentColor,
                            unfocusedBorderColor = theme.headerBorder.copy(alpha = 0.4f),
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
                        )
                    )
                }
            } else {
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

            // Header stats & Play All / Shuffle All / Repeat toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
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
                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = CircleShape,
                            color = if (isShuffle) theme.accentColor.copy(alpha = 0.25f) else theme.headerBg,
                            border = if (isShuffle) androidx.compose.foundation.BorderStroke(1.dp, theme.accentColor.copy(alpha = 0.7f)) else null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (onToggleShuffle != {}) {
                                        onToggleShuffle()
                                    } else {
                                        onShuffleAll(displayTracks)
                                    }
                                },
                                modifier = Modifier.fillMaxSize().testTag("btn_library_shuffle")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (isShuffle) theme.accentColor else theme.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Prominent Repeat Button matching the toolbar style
                    Surface(
                        shape = CircleShape,
                        color = if (repeatMode != RepeatMode.OFF) theme.accentColor.copy(alpha = 0.25f) else theme.headerBg.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (repeatMode != RepeatMode.OFF) theme.accentColor.copy(alpha = 0.8f) else theme.textSecondary.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        IconButton(
                            onClick = onToggleRepeat,
                            modifier = Modifier.fillMaxSize().testTag("btn_library_repeat")
                        ) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    RepeatMode.ONE -> Icons.Default.RepeatOne
                                    RepeatMode.ALL -> Icons.Default.Repeat
                                    RepeatMode.OFF -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (repeatMode != RepeatMode.OFF) theme.accentColor else theme.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            }

            Box(modifier = Modifier.weight(1f)) {
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
                    if (finalTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectionSearchQuery.isNotEmpty()) "No songs matching '$selectionSearchQuery'" else "No songs found in this category",
                                color = theme.textSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val showAlphabetScroller = finalTracks.size >= 4 && !isSelectionMode

                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 8.dp, end = if (showAlphabetScroller) 26.dp else 8.dp)
                            ) {
                                itemsIndexed(finalTracks, key = { _, it -> it.id }) { index, track ->
                                    val isCurrent = track.id == currentTrackId
                                    val isSelected = selectedTrackIds.contains(track.id)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) theme.accentColor.copy(alpha = 0.22f)
                                                else if (isCurrent) theme.accentColor.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        selectedTrackIds = if (selectedTrackIds.contains(track.id)) {
                                                            selectedTrackIds - track.id
                                                        } else {
                                                            selectedTrackIds + track.id
                                                        }
                                                    } else {
                                                        onPlayTrack(track, finalTracks)
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (!isSelectionMode) {
                                                        isSelectionMode = true
                                                        selectedTrackIds = setOf(track.id)
                                                    } else {
                                                        selectedTrackIds = if (selectedTrackIds.contains(track.id)) {
                                                            selectedTrackIds - track.id
                                                        } else {
                                                            selectedTrackIds + track.id
                                                        }
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .testTag("track_item_${track.id}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Artwork (Compact 38dp thumbnail)
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(5.dp))
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
                                                            tint = theme.accentColor,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = track.title,
                                                    color = if (isCurrent) theme.accentColor else theme.textPrimary,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = if (isCurrent || isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (track.artist == "<unknown>") "Unknown Artist" else track.artist,
                                                        color = theme.textSecondary,
                                                        fontSize = 11.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )

                                                    val m = track.duration / 60
                                                    val s = track.duration % 60
                                                    Text(
                                                        text = "• ${String.format("%d:%02d", m, s)}",
                                                        color = theme.textSecondary.copy(alpha = 0.7f),
                                                        fontSize = 10.5.sp
                                                    )
                                                }
                                            }
                                        }

                                        // Right side: In selection mode, square checkbox matching Screenshot_20260921-102237.jpg
                                        if (isSelectionMode) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(end = 6.dp)
                                                    .size(22.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .border(
                                                        width = if (isSelected) 0.dp else 1.5.dp,
                                                        color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.75f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .background(if (isSelected) theme.accentColor else Color.Transparent),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Actions (Favorite & More)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { onToggleFavorite(track.id) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                        contentDescription = "Favorite",
                                                        tint = if (track.isFavorite) Color(0xFFF43F5E) else theme.textSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { activeTrackForMenu = track },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "Track options",
                                                        tint = theme.textSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Inline Ads
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

                            if (showAlphabetScroller) {
                                AlphabetFastScroller(
                                    tracks = finalTracks,
                                    listState = listState,
                                    theme = theme,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Selection Mode Bottom Bar (Screenshot_20260921-102237.jpg: Play, Add to, Enqueue, Favorite, More)
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color = theme.headerBg.copy(alpha = 0.98f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Play
                        SelectionActionButton(
                            icon = Icons.Default.PlayCircleOutline,
                            label = "Play",
                            theme = theme,
                            onClick = {
                                if (selectedTrackIds.isEmpty()) {
                                    Toast.makeText(context, "Please select songs first", Toast.LENGTH_SHORT).show()
                                } else {
                                    val selTracks = displayTracks.filter { it.id in selectedTrackIds }
                                    if (selTracks.isNotEmpty()) {
                                        if (onPlayTracks != null) onPlayTracks(selTracks)
                                        else onPlayTrack(selTracks.first(), selTracks)
                                    }
                                    isSelectionMode = false
                                    selectedTrackIds = emptySet()
                                }
                            }
                        )

                        // 2. Add to
                        SelectionActionButton(
                            icon = Icons.Default.AddBox,
                            label = "Add to",
                            theme = theme,
                            onClick = {
                                if (selectedTrackIds.isEmpty()) {
                                    Toast.makeText(context, "Please select songs first", Toast.LENGTH_SHORT).show()
                                } else {
                                    showBatchPlaylistPicker = true
                                }
                            }
                        )

                        // 3. Enqueue
                        SelectionActionButton(
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            label = "Enqueue",
                            theme = theme,
                            onClick = {
                                if (selectedTrackIds.isEmpty()) {
                                    Toast.makeText(context, "Please select songs first", Toast.LENGTH_SHORT).show()
                                } else {
                                    val selTracks = displayTracks.filter { it.id in selectedTrackIds }
                                    if (onEnqueueTracks != null) {
                                        onEnqueueTracks(selTracks)
                                    } else {
                                        Toast.makeText(context, "Added ${selTracks.size} songs to queue", Toast.LENGTH_SHORT).show()
                                    }
                                    isSelectionMode = false
                                    selectedTrackIds = emptySet()
                                }
                            }
                        )

                        // 4. Favorite
                        SelectionActionButton(
                            icon = Icons.Default.FavoriteBorder,
                            label = "Favorite",
                            theme = theme,
                            onClick = {
                                if (selectedTrackIds.isEmpty()) {
                                    Toast.makeText(context, "Please select songs first", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (onAddTracksToFavorites != null) {
                                        onAddTracksToFavorites(selectedTrackIds.toList())
                                    } else {
                                        selectedTrackIds.forEach { onToggleFavorite(it) }
                                        Toast.makeText(context, "Added ${selectedTrackIds.size} songs to Favorites", Toast.LENGTH_SHORT).show()
                                    }
                                    isSelectionMode = false
                                    selectedTrackIds = emptySet()
                                }
                            }
                        )

                        // 5. More (3 vertical dots)
                        Box {
                            SelectionActionButton(
                                icon = Icons.Default.MoreVert,
                                label = "More",
                                theme = theme,
                                onClick = { showBatchMoreMenu = true }
                            )

                            DropdownMenu(
                                expanded = showBatchMoreMenu,
                                onDismissRequest = { showBatchMoreMenu = false },
                                modifier = Modifier.background(theme.sidebarBg)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share", color = theme.textPrimary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null, tint = theme.accentColor)
                                    },
                                    onClick = {
                                        showBatchMoreMenu = false
                                        if (selectedTrackIds.isEmpty()) {
                                            Toast.makeText(context, "Please select songs first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val selTracks = displayTracks.filter { it.id in selectedTrackIds }
                                            shareSelectedTracks(context, selTracks)
                                            isSelectionMode = false
                                            selectedTrackIds = emptySet()
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Delete from device", color = Color(0xFFF43F5E)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFF43F5E))
                                    },
                                    onClick = {
                                        showBatchMoreMenu = false
                                        if (selectedTrackIds.isEmpty()) {
                                            Toast.makeText(context, "Please select songs first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            showBatchDeleteConfirm = true
                                        }
                                    }
                                )

                                HorizontalDivider(color = theme.headerBorder.copy(alpha = 0.3f))

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (selectedTrackIds.size == finalTracks.size) "Deselect all" else "Select all",
                                            color = theme.textPrimary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null, tint = theme.accentColor)
                                    },
                                    onClick = {
                                        showBatchMoreMenu = false
                                        if (selectedTrackIds.size == finalTracks.size) {
                                            selectedTrackIds = emptySet()
                                        } else {
                                            selectedTrackIds = finalTracks.map { it.id }.toSet()
                                        }
                                    }
                                )
                            }
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
                            icon = Icons.Default.NotificationsActive,
                            title = "Set as Ringtone",
                            onClick = {
                                activeTrackForMenu = null
                                com.sonance.musicplayer.util.RingtoneHelper.setAsRingtoneImmediately(context, tr)
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
                            title = "Delete Music",
                            onClick = {
                                activeTrackForMenu = null
                                trackToDeletePermanently = tr
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

        // Permanent Delete Confirmation Dialog
        trackToDeletePermanently?.let { tr ->
            AlertDialog(
                onDismissRequest = { trackToDeletePermanently = null },
                containerColor = theme.sidebarBg,
                icon = {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Delete Music Permanently?",
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Do you want to delete '${tr.title}' permanently from your device? This file will be removed permanently and cannot be recovered.",
                        color = theme.textSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            trackToDeletePermanently = null
                            onDeleteTrack(tr.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                    ) {
                        Text("Yes, Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { trackToDeletePermanently = null }) {
                        Text("Cancel", color = theme.textSecondary)
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

        // Multi-Select Batch Delete Confirmation Dialog
        if (showBatchDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirm = false },
                containerColor = theme.sidebarBg,
                icon = {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Delete ${selectedTrackIds.size} Songs Permanently?",
                        color = theme.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Do you want to delete ${selectedTrackIds.size} selected songs permanently from your device? This will permanently delete the audio files from storage and cannot be undone.",
                        color = theme.textSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val ids = selectedTrackIds.toList()
                            showBatchDeleteConfirm = false
                            isSelectionMode = false
                            selectedTrackIds = emptySet()
                            if (onDeleteTracks != null) {
                                onDeleteTracks(ids)
                            } else {
                                ids.forEach { onDeleteTrack(it) }
                                Toast.makeText(context, "Deleted ${ids.size} songs permanently", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                    ) {
                        Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteConfirm = false }) {
                        Text("Cancel", color = theme.textSecondary)
                    }
                }
            )
        }

        // Multi-Select Batch Add to Playlist Dialog
        if (showBatchPlaylistPicker) {
            AlertDialog(
                onDismissRequest = { showBatchPlaylistPicker = false },
                containerColor = theme.sidebarBg,
                title = {
                    Text("Add ${selectedTrackIds.size} Songs to Playlist", color = theme.textPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (allPlaylists.isEmpty()) {
                            Text(
                                text = "No playlists found. Create one from the Playlists tab.",
                                color = theme.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            allPlaylists.forEach { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val ids = selectedTrackIds.toList()
                                            if (onAddTracksToPlaylist != null) {
                                                onAddTracksToPlaylist(ids, pl.id)
                                            } else {
                                                ids.forEach { onAddToPlaylist(it, pl.id) }
                                            }
                                            Toast.makeText(context, "Added ${ids.size} songs to ${pl.name}", Toast.LENGTH_SHORT).show()
                                            showBatchPlaylistPicker = false
                                            isSelectionMode = false
                                            selectedTrackIds = emptySet()
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
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
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBatchPlaylistPicker = false }) {
                        Text("Cancel", color = theme.accentColor)
                    }
                }
            )
        }
    }

@Composable
private fun SelectionActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    theme: ThemeConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = theme.textPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = theme.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun shareSelectedTracks(context: Context, tracksToShare: List<Track>) {
    try {
        if (tracksToShare.isEmpty()) return
        if (tracksToShare.size == 1) {
            val track = tracksToShare.first()
            val uri: Uri = try {
                if (track.contentUri.startsWith("content://")) {
                    Uri.parse(track.contentUri)
                } else if (track.contentUri.isNotBlank()) {
                    val file = File(track.contentUri)
                    if (file.exists()) {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    } else Uri.parse(track.url)
                } else Uri.parse(track.url)
            } catch (_: Exception) {
                Uri.parse(track.url)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, track.title)
                putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share music"))
        } else {
            val uris = ArrayList<Uri>()
            tracksToShare.forEach { track ->
                try {
                    val uri: Uri = if (track.contentUri.startsWith("content://")) {
                        Uri.parse(track.contentUri)
                    } else if (track.contentUri.isNotBlank()) {
                        val file = File(track.contentUri)
                        if (file.exists()) {
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        } else Uri.parse(track.url)
                    } else Uri.parse(track.url)
                    uris.add(uri)
                } catch (_: Exception) {
                    uris.add(Uri.parse(track.url))
                }
            }
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${tracksToShare.size} songs"))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share audio: ${e.message}", Toast.LENGTH_SHORT).show()
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
