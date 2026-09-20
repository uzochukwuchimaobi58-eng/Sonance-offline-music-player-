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
    val subtitle: String,
    val cta: String,
    val rating: String,
    val accentColor: Color,
    val logoText: String
)

private val SAMPLE_ADS = listOf(
    AdCreative("Betway NG", "Betway NG - Sports Betting", "Get way more with Betway!", "Bet Now", "4.8 ★", Color(0xFF00A651), "betway"),
    AdCreative("Sony Audio", "Sony WH-1000XM5 ANC", "Industry leading noise cancellation", "Shop", "4.8 ★", Color(0xFF4285F4), "SONY"),
    AdCreative("Sennheiser", "HD 660S2 Studio Reference", "Pure sound for audiophiles", "Explore", "4.9 ★", Color(0xFF34A853), "SENN"),
    AdCreative("Audio-Technica", "ATH-M50x Studio Monitor", "Critically acclaimed performance", "View", "4.7 ★", Color(0xFFEA4335), "A-T")
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
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(0.8.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .testTag("admob_banner_container"),
            color = Color(0xFF141416),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Logo with tiny Ad tag (Matching Image 1)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF222226)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ad.logoText,
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    // Tiny Ad badge on bottom-left corner of logo
                    Surface(
                        shape = RoundedCornerShape(topEnd = 3.dp, bottomStart = 6.dp),
                        color = Color(0xFFFFB300),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = "Ad",
                            color = Color.Black,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 2.5.dp, vertical = 0.5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Center: Headline + Subtitle
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = ad.headline,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = ad.subtitle,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: Sleek rounded CTA pill button (Matching Image 1)
                Button(
                    onClick = { showAdInfoDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2E)),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.18f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = ad.cta,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = { showAdInfoDialog = true },
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Ad choices",
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(12.dp)
                    )
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

