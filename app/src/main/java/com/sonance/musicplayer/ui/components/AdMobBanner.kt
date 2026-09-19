package com.sonance.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.ThemeConfig
import kotlinx.coroutines.delay

private data class AdCreative(
    val brand: String,
    val headline: String,
    val cta: String,
    val rating: String,
    val accentColor: Color
)

private val SAMPLE_ADS = listOf(
    AdCreative("Sony Audio", "WH-1000XM5 Noise Canceling Hi-Res Headphones", "Shop Now", "4.8 ★", Color(0xFF4285F4)),
    AdCreative("Sennheiser", "HD 660S2 Audiophile Studio Reference Open-Back", "Learn More", "4.9 ★", Color(0xFF34A853)),
    AdCreative("Audio-Technica", "ATH-M50x Professional Studio Monitor Headphones", "View Deal", "4.7 ★", Color(0xFFEA4335)),
    AdCreative("Bose Sound", "QuietComfort Ultra Spatial Audio Earbuds", "Discover", "4.8 ★", Color(0xFFFBBC05))
)

@Composable
fun AdMobBanner(
    isPro: Boolean,
    admobEnabled: Boolean = true,
    onOpenProUpgrade: () -> Unit,
    theme: ThemeConfig,
    modifier: Modifier = Modifier
) {
    // If user has Pro or ads are disabled from backend, render nothing
    if (isPro || !admobEnabled) return

    var currentAdIndex by remember { mutableIntStateOf(0) }
    var showAdInfoDialog by remember { mutableStateOf(false) }

    // Subtle ad rotation
    LaunchedEffect(Unit) {
        while (true) {
            delay(14000)
            currentAdIndex = (currentAdIndex + 1) % SAMPLE_ADS.size
        }
    }

    val ad = SAMPLE_ADS[currentAdIndex]

    AnimatedVisibility(
        visible = !isPro && admobEnabled,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .testTag("admob_banner_container"),
            color = theme.sidebarBg.copy(alpha = 0.95f),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // Header: Ad Tag + AdMob label + Remove Ads Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Google "Ad" badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF1F3F4),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "Ad",
                                color = Color(0xFF202124),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        Text(
                            text = "Google AdMob",
                            color = theme.textSecondary.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { showAdInfoDialog = true },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Ad info",
                                tint = theme.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // "Remove Ads" action pill that opens Pro dialog
                    Surface(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onOpenProUpgrade() }
                            .testTag("btn_remove_ads_pill"),
                        color = Color(0xFFFFD700).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Remove Ads",
                                color = Color(0xFFFFD700),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Ad Content Body
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ad.brand,
                                color = ad.accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = ad.rating,
                                color = Color(0xFFFFB300),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = ad.headline,
                            color = theme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showAdInfoDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ad.accentColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = ad.cta,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showAdInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAdInfoDialog = false },
            containerColor = theme.sidebarBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = theme.accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About Google AdMob", color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This ad is served via the Google AdMob Network to keep Sonance Music Player completely free for guest users.",
                        color = theme.textSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        "Unit: ca-app-pub-6322953088287505/5517813262\nPrivacy: Ad personalization complies with Google Play policy.",
                        color = theme.textSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Want an uninterrupted, zero-ad music experience? Upgrade to Sonance PRO from just $1.00/year or $2.00 lifetime.",
                        color = theme.accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAdInfoDialog = false
                        onOpenProUpgrade()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                ) {
                    Text("Go PRO (Remove Ads)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdInfoDialog = false }) {
                    Text("Close", color = theme.textSecondary)
                }
            }
        )
    }
}

@Composable
fun InlineTrackAdCard(
    isPro: Boolean,
    admobEnabled: Boolean,
    onOpenProUpgrade: () -> Unit,
    theme: ThemeConfig,
    modifier: Modifier = Modifier
) {
    if (isPro || !admobEnabled) return

    val ad = remember { SAMPLE_ADS.random() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .testTag("inline_track_ad_banner"),
        color = theme.sidebarBg.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Ad Icon / Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFFB300).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "AD",
                        color = Color(0xFFFFB300),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = ad.headline,
                        color = theme.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Sponsored • ${ad.brand} (${ad.rating})",
                        color = theme.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenProUpgrade() },
                color = theme.accentColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "Remove Ads",
                    color = theme.accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

