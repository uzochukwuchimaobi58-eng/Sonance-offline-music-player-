package com.sonance.musicplayer.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.sonance.musicplayer.model.AppTheme
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.ThemeRepository
import kotlinx.coroutines.delay

@Composable
fun ThemeDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    theme: ThemeConfig,
    isPro: Boolean = false,
    onOpenProUpgrade: () -> Unit = {},
    unlockedThemeIds: Set<String> = emptySet(),
    onUnlockThemeByAd: (AppTheme) -> Unit = {},
    customWallpaperUri: String? = null,
    onSetCustomWallpaperUri: (String?) -> Unit = {}
) {
    if (!isOpen) return

    val context = LocalContext.current
    var unlockPromptTheme by remember { mutableStateOf<AppTheme?>(null) }
    var adThemeToUnlock by remember { mutableStateOf<AppTheme?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSetCustomWallpaperUri(uri.toString())
            Toast.makeText(context, "🖼️ Custom wallpaper applied across the entire app and music library!", Toast.LENGTH_SHORT).show()
        }
    }

    val allThemes = remember {
        listOf(
            // 3 Free Wallpapers
            AppTheme.NATURE_FLOWER,
            AppTheme.ANIMAL_PETS,
            AppTheme.DARK_AMOLED,

            // Premium Wallpapers
            AppTheme.CRIMSON_COUNTDOWN,
            AppTheme.SKY_BLOSSOM,
            AppTheme.ROMANTIC_DREAMS,
            AppTheme.SYNTHWAVE_NEON,
            AppTheme.SUNSET_OCEAN,
            AppTheme.MOUNTAIN_BLUR,
            AppTheme.STUDIO_PIANO,
            AppTheme.AUTUMN_BOKEH,
            AppTheme.ROCK_PLAYLIST
        )
    }

    fun isThemeUnlocked(item: AppTheme): Boolean {
        if (!item.isProOnly) return true
        if (isPro) return true
        return unlockedThemeIds.contains(item.idStr)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.bgCanvas)
                .testTag("themes_screen")
        ) {
            // Live Background Wallpaper shining behind the Themes screen
            if (customWallpaperUri != null) {
                AsyncImage(
                    model = customWallpaperUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f)))
            } else if (theme.coverDrawableRes != null) {
                Image(
                    painter = painterResource(id = theme.coverDrawableRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f)))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Header Bar: Back Arrow | Themes | Palette circle & Edit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("btn_close_themes")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Themes",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Current Theme Accent Indicator Circle
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(theme.accentColor)
                                .border(2.dp, Color.White, CircleShape)
                        )

                        // Album upload icon button
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Custom Album Photo",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3-Column Wallpaper Cards Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp)
                ) {
                    // First item is the "↑ Album" custom wallpaper upload card
                    item {
                        AlbumUploadCard(
                            isActive = customWallpaperUri != null,
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onClear = {
                                onSetCustomWallpaperUri(null)
                                Toast.makeText(context, "Custom wallpaper cleared", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    items(allThemes) { item ->
                        val cfg = ThemeRepository.getTheme(item)
                        val isCurrentlyActive = (item == currentTheme && customWallpaperUri == null)
                        val isUnlocked = isThemeUnlocked(item)

                        ThemeWallpaperCard(
                            theme = item,
                            cfg = cfg,
                            isActive = isCurrentlyActive,
                            isUnlocked = isUnlocked,
                            onClick = {
                                if (isUnlocked) {
                                    // Clear custom wallpaper so this template's picture shines through
                                    if (customWallpaperUri != null) {
                                        onSetCustomWallpaperUri(null)
                                    }
                                    onSelectTheme(item)
                                    Toast.makeText(context, "${item.displayName} applied!", Toast.LENGTH_SHORT).show()
                                } else {
                                    // Prompt to unlock with Ad or PRO
                                    unlockPromptTheme = item
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Unlock Option Dialog (Prompt before watching ad or upgrading to pro)
    unlockPromptTheme?.let { targetTheme ->
        val targetCfg = remember(targetTheme) { ThemeRepository.getTheme(targetTheme) }
        ThemeUnlockDialog(
            targetTheme = targetTheme,
            targetCfg = targetCfg,
            onDismiss = { unlockPromptTheme = null },
            onWatchAd = {
                unlockPromptTheme = null
                adThemeToUnlock = targetTheme
            },
            onUpgradePro = {
                unlockPromptTheme = null
                onClose()
                onOpenProUpgrade()
            }
        )
    }

    // Rewarded Ad Simulation Dialog
    adThemeToUnlock?.let { targetTheme ->
        RewardedAdDialog(
            theme = theme,
            targetTheme = targetTheme,
            onDismiss = { adThemeToUnlock = null },
            onRewardEarned = {
                onUnlockThemeByAd(targetTheme)
                if (customWallpaperUri != null) {
                    onSetCustomWallpaperUri(null)
                }
                onSelectTheme(targetTheme)
                Toast.makeText(context, "🎉 ${targetTheme.displayName} unlocked & applied to app body!", Toast.LENGTH_LONG).show()
                adThemeToUnlock = null
            }
        )
    }
}

/**
 * Dialog shown before applying a locked wallpaper theme
 */
@Composable
fun ThemeUnlockDialog(
    targetTheme: AppTheme,
    targetCfg: ThemeConfig,
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit,
    onUpgradePro: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181B),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock Wallpaper",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Large visual preview card of the wallpaper
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    if (targetCfg.coverDrawableRes != null) {
                        Image(
                            painter = painterResource(id = targetCfg.coverDrawableRes),
                            contentDescription = targetTheme.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.70f))
                                    )
                                )
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFFD700)
                        ) {
                            Text(
                                text = targetCfg.previewTag,
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = targetTheme.displayName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (targetCfg.styleDescription.isNotBlank()) {
                            Text(
                                text = targetCfg.styleDescription,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Text(
                    text = "Before applying this wallpaper theme, watch a short 5-second video ad, or unlock all wallpapers permanently with PRO.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Button(
                    onClick = onWatchAd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Watch Short Ad (5s)", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onUpgradePro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock All with PRO", fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}

/**
 * The "↑ Album" card shown as the first card in Screenshot_20260926-085406.jpg
 */
@Composable
private fun AlbumUploadCard(
    isActive: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF2C253B), Color(0xFF1E1929))
                )
            )
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) Color(0xFF10B981) else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .testTag("card_album_upload"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "Upload from Album",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Album",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 3-Column Portrait Wallpaper Card matching Screenshot_20260926-085406.jpg
 */
@Composable
private fun ThemeWallpaperCard(
    theme: AppTheme,
    cfg: ThemeConfig,
    isActive: Boolean,
    isUnlocked: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isActive) 2.5.dp else 0.dp,
                color = if (isActive) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .testTag("theme_card_${theme.name}")
    ) {
        // Full bleed background picture
        if (cfg.coverDrawableRes != null) {
            Image(
                painter = painterResource(id = cfg.coverDrawableRes),
                contentDescription = theme.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Elegant gradient composition for themes without a photographic asset
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                cfg.accentColor.copy(alpha = 0.85f),
                                cfg.headerBg,
                                cfg.bgCanvas
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = theme.displayName,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Active Checkmark Badge (Centered white circle with checkmark, exactly as in screenshot!)
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Applied",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Top-Start Status Badge: FREE or UNLOCKED
        if (!theme.isProOnly) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF10B981)
            ) {
                Text(
                    text = "FREE",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        } else if (isUnlocked) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF06B6D4)
            ) {
                Text(
                    text = "UNLOCKED",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        // Top-End Locked Badge
        if (!isUnlocked) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "AD / PRO",
                        color = Color(0xFFFFD700),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom Title Scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                text = theme.displayName,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun RewardedAdDialog(
    theme: ThemeConfig,
    targetTheme: AppTheme,
    onDismiss: () -> Unit,
    onRewardEarned: () -> Unit
) {
    val targetCfg = remember(targetTheme) { ThemeRepository.getTheme(targetTheme) }
    var countdownSeconds by remember { mutableStateOf(5) }
    var adFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds--
        }
        adFinished = true
    }

    Dialog(
        onDismissRequest = {
            if (adFinished) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = adFinished,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF111827),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF06B6D4)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF06B6D4).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF06B6D4))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color(0xFF06B6D4),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SPONSORED REWARD AD",
                            color = Color(0xFF06B6D4),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1F2937))
                        .border(1.dp, Color(0xFF374151), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (targetCfg.coverDrawableRes != null) {
                        Image(
                            painter = painterResource(id = targetCfg.coverDrawableRes),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = Color(0xFF06B6D4),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Unlocking: ${targetTheme.displayName}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Keep music free • Supporting Sonance Player",
                            color = Color(0xFFE5E7EB),
                            fontSize = 10.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (adFinished) "REWARD READY!" else "Reward in: ${countdownSeconds}s",
                            color = if (adFinished) Color(0xFF10B981) else Color(0xFFFFD700),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { (5 - countdownSeconds) / 5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF06B6D4),
                    trackColor = Color(0xFF374151)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (adFinished) "Awesome! Your reward is ready!" else "Watching video to unlock wallpaper...",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (adFinished) {
                    Button(
                        onClick = onRewardEarned,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Claim & Apply Wallpaper",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF9CA3AF)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel Ad (No Reward)", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
