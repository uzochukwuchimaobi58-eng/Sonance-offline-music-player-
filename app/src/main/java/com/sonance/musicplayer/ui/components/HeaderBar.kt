package com.sonance.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.ThemeConfig

@Composable
fun HeaderBar(
    title: String,
    isHome: Boolean,
    onOpenSidebar: () -> Unit,
    onBack: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isPro: Boolean = false,
    onOpenPro: () -> Unit = {},
    onOpenScanModal: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onSortSelected: ((String) -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    onShuffleAll: (() -> Unit)? = null,
    repeatMode: com.sonance.musicplayer.model.RepeatMode? = null,
    onToggleRepeat: (() -> Unit)? = null,
    theme: ThemeConfig
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val goldColor = Color(0xFFFFD700)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.headerBg.copy(alpha = 0.45f))
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: Menu / Back + Title (+ PRO Badge only on Home)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = !isSearchActive)
        ) {
            if (isHome) {
                IconButton(
                    onClick = onOpenSidebar,
                    modifier = Modifier.testTag("btn_sidebar_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open navigation menu",
                        tint = theme.textPrimary
                    )
                }

                // Top-Left PRO Badge / Action Button (Home only)
                Surface(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenPro() }
                        .testTag("btn_top_left_pro"),
                    color = if (isPro) goldColor else goldColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isPro) goldColor else goldColor.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = "Sonance PRO",
                            tint = if (isPro) Color.Black else goldColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "PRO",
                            color = if (isPro) Color.Black else goldColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("btn_back_to_home")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.textPrimary
                    )
                }
            }

            if (!isSearchActive) {
                Text(
                    text = title,
                    color = theme.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right side: Search bar (when active)
        AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.bgCanvas.copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .widthIn(min = 180.dp, max = 260.dp)
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = theme.textPrimary,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(theme.accentColor),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_tracks_input"),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search songs, artists...",
                                color = theme.textSecondary.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = theme.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            if (searchQuery.isNotEmpty()) {
                                onSearchChange("")
                            } else {
                                isSearchActive = false
                            }
                        }
                        .testTag("btn_clear_search")
                )
            }
        }

        // Action icons on right (Search, 3-dots)
        if (!isSearchActive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Play All & Shuffle icons in header for music lists
                if (!isHome && onPlayAll != null) {
                    IconButton(
                        onClick = onPlayAll,
                        modifier = Modifier.testTag("btn_header_play_all")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play All",
                            tint = theme.accentColor
                        )
                    }
                }
                if (!isHome && onShuffleAll != null) {
                    IconButton(
                        onClick = onShuffleAll,
                        modifier = Modifier.testTag("btn_header_shuffle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle All",
                            tint = theme.accentColor
                        )
                    }
                }

                // Search Icon
                IconButton(
                    onClick = { isSearchActive = true },
                    modifier = Modifier.testTag("btn_search_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search music",
                        tint = theme.textPrimary
                    )
                }

                // 3-Dots Overflow Menu
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.testTag("btn_overflow_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = theme.textPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.background(theme.sidebarBg)
                    ) {
                        if (onPlayAll != null) {
                            DropdownMenuItem(
                                text = { Text("Play All", color = theme.textPrimary) },
                                onClick = {
                                    showOverflowMenu = false
                                    onPlayAll()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = theme.accentColor)
                                }
                            )
                        }

                        if (onShuffleAll != null) {
                            DropdownMenuItem(
                                text = { Text("Shuffle All", color = theme.textPrimary) },
                                onClick = {
                                    showOverflowMenu = false
                                    onShuffleAll()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = theme.accentColor)
                                }
                            )
                        }

                        if (onToggleRepeat != null && repeatMode != null) {
                            DropdownMenuItem(
                                text = { Text("Repeat: ${repeatMode.name}", color = theme.textPrimary) },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleRepeat()
                                },
                                leadingIcon = {
                                    Icon(
                                        when (repeatMode) {
                                            com.sonance.musicplayer.model.RepeatMode.ONE -> Icons.Default.RepeatOne
                                            else -> Icons.Default.Repeat
                                        },
                                        contentDescription = null,
                                        tint = if (repeatMode != com.sonance.musicplayer.model.RepeatMode.OFF) theme.accentColor else theme.textSecondary
                                    )
                                }
                            )
                        }

                        HorizontalDivider(color = theme.headerBorder.copy(alpha = 0.3f))

                        DropdownMenuItem(
                            text = { Text("Sort by Title", color = theme.textPrimary) },
                            onClick = {
                                showOverflowMenu = false
                                onSortSelected?.invoke("title")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.SortByAlpha, contentDescription = null, tint = theme.accentColor)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Sort by Artist", color = theme.textPrimary) },
                            onClick = {
                                showOverflowMenu = false
                                onSortSelected?.invoke("artist")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = theme.accentColor)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Sort by Duration", color = theme.textPrimary) },
                            onClick = {
                                showOverflowMenu = false
                                onSortSelected?.invoke("duration")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = theme.accentColor)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Scan Library", color = theme.textPrimary) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenScanModal()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = theme.accentColor)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Sound Equalizer", color = theme.textPrimary) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenEqualizer()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = theme.accentColor)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Settings", color = theme.textPrimary) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenSettings()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = theme.accentColor)
                            }
                        )
                    }
                }
            }
        }
    }
}
