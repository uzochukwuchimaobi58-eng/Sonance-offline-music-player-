package com.sonance.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.AppTheme
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.ThemeRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    theme: ThemeConfig,
    isPro: Boolean = false,
    onOpenProUpgrade: () -> Unit = {}
) {
    if (!isOpen) return

    var proPromptTheme by remember { mutableStateOf<AppTheme?>(null) }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Pro VIP", "Free"

    val filteredThemes = remember(selectedFilter) {
        when (selectedFilter) {
            "Pro VIP" -> AppTheme.entries.filter { it.isProOnly }
            "Free" -> AppTheme.entries.filter { !it.isProOnly }
            else -> AppTheme.entries
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = theme.sidebarBg,
        modifier = Modifier.testTag("theme_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = theme.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Player Themes",
                        color = theme.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isPro) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFD700).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PRO UNLOCKED",
                                color = Color(0xFFFFD700),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter chips: All, Pro VIP, Free
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Pro VIP", "Free").forEach { filter ->
                    val isChipSelected = selectedFilter == filter
                    FilterChip(
                        selected = isChipSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = if (filter == "Pro VIP") "👑 PRO VIP" else filter,
                                fontSize = 12.sp,
                                fontWeight = if (isChipSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (filter == "Pro VIP") Color(0xFFFFD700).copy(alpha = 0.25f) else theme.accentColor.copy(alpha = 0.2f),
                            selectedLabelColor = if (filter == "Pro VIP") Color(0xFFFFD700) else theme.accentColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(filteredThemes) { item ->
                    val cfg = ThemeRepository.getTheme(item)
                    val isSelected = item == currentTheme
                    val isLocked = item.isProOnly && !isPro

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(cfg.bgCanvas)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) cfg.accentColor else if (item.isProOnly) Color(0xFFFFD700).copy(alpha = 0.5f) else cfg.headerBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (isLocked) {
                                    proPromptTheme = item
                                } else {
                                    onSelectTheme(item)
                                    onClose()
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(cfg.accentColor)
                                )

                                if (item.isProOnly) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFFD700).copy(alpha = 0.25f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isPro) Icons.Default.WorkspacePremium else Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = Color(0xFFFFD700),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "PRO",
                                                color = Color(0xFFFFD700),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = cfg.accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = item.displayName,
                                color = cfg.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Pro Unlock Dialog for non-pro users tapping locked themes
    proPromptTheme?.let { lockedTheme ->
        AlertDialog(
            onDismissRequest = { proPromptTheme = null },
            containerColor = theme.sidebarBg,
            icon = {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "VIP PRO Theme",
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${lockedTheme.displayName} is an exclusive premium theme crafted for Sonance PRO members.",
                        color = theme.textPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Upgrade to Pro to unlock all VIP themes, studio-grade equalizer presets, and enjoy 100% ad-free music.",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        proPromptTheme = null
                        onClose()
                        onOpenProUpgrade()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                ) {
                    Text("Unlock with Pro", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { proPromptTheme = null }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
}
