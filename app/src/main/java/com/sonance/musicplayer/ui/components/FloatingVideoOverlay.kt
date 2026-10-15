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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.sonance.musicplayer.video.GlobalVideoManager
import com.sonance.musicplayer.video.VideoItem

/**
 * Floating Picture-in-Picture Video Overlay that displays across the entire app
 * when looping or watching a video, staying visible when navigating between views
 * and exit, with an 'X' button to dismiss.
 */
@Composable
fun FloatingVideoOverlay(
    videoManager: GlobalVideoManager,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVideo by videoManager.currentVideo.collectAsState()
    val isPlaying by videoManager.isPlaying.collectAsState()
    val isLooping by videoManager.isLooping.collectAsState()
    val isVisible by videoManager.isFloatingVisible.collectAsState()

    AnimatedVisibility(
        visible = isVisible && currentVideo != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
    ) {
        val video = currentVideo ?: return@AnimatedVisibility

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(end = 12.dp, bottom = 72.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Surface(
                modifier = Modifier
                    .width(180.dp)
                    .height(116.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, if (isLooping) Color(0xFF22C55E) else Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .shadow(12.dp, RoundedCornerShape(14.dp))
                    .testTag("floating_video_overlay"),
                color = Color.Black,
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Live ExoPlayer Surface
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = videoManager.getPlayer()
                                useController = false
                            }
                        },
                        update = { playerView ->
                            playerView.player = videoManager.getPlayer()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onExpand() }
                    )

                    // Top Bar: Looping badge + Close button ('X')
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isLooping) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RepeatOne,
                                    contentDescription = "Looping",
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "LOOP",
                                    color = Color(0xFF22C55E),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = video.title,
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }

                        // Close 'X' Button: Dismisses video across app completely
                        IconButton(
                            onClick = { videoManager.dismissFloating() },
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .testTag("btn_floating_video_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close floating video",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Bottom Bar: Play/Pause and Expand to Fullscreen
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { videoManager.togglePlayPause() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onExpand,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Expand to video screen",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
