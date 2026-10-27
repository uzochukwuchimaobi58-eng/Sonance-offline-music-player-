package com.sonance.musicplayer.ui.components

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.rating.InAppRatingManager

@Composable
fun RatingDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    theme: ThemeConfig,
    source: String = "studio_tool"
) {
    if (!isOpen) return

    val context = LocalContext.current
    val activity = context as? Activity
    var selectedStars by remember { mutableIntStateOf(5) }

    val goldAccent = Color(0xFFFFB800)
    val cardBg = Color(0xFF1B1A20)
    val cardBorder = Color(0xFF2C2A34)

    val feedbackText = when (selectedStars) {
        1 -> "We're constantly improving! Let us know how we can do better."
        2 -> "Thanks! We'll keep refining your studio sound."
        3 -> "Good! We have lots of new features on the way."
        4 -> "Awesome! We're glad you're enjoying the music."
        else -> "Loved it! ⭐ Thank you for supporting our music player!"
    }

    Dialog(onDismissRequest = {
        InAppRatingManager.getInstance(context).recordUserDismissed()
        onClose()
    }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                .testTag("dialog_rating"),
            color = cardBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            InAppRatingManager.getInstance(context).recordUserDismissed()
                            onClose()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Studio Icon badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(goldAccent.copy(alpha = 0.15f))
                        .border(1.5.dp, goldAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = goldAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (source == "studio_tool") "Enjoyed the Studio Tools?" else "Rate for Us",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your rating helps us bring more studio sound effects, equalizers, and themes to everyone!",
                    color = Color(0xFFA1A1AA),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Interactive 5 Gold Stars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (starIndex in 1..5) {
                        val isFilled = starIndex <= selectedStars
                        IconButton(
                            onClick = { selectedStars = starIndex },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$starIndex Stars",
                                tint = if (isFilled) goldAccent else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = feedbackText,
                    color = if (selectedStars >= 4) goldAccent else Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Rate on Google Play Button
                Button(
                    onClick = {
                        InAppRatingManager.getInstance(context).recordUserRated()

                        if (activity != null) {
                            InAppRatingManager.getInstance(context).launchReviewFlow(activity) {
                                onClose()
                            }
                        } else {
                            InAppRatingManager.getInstance(context).openPlayStoreListing(context)
                            onClose()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_submit_rating"),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = goldAccent)
                ) {
                    Text(
                        text = "Rate for Us on Google Play",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Maybe Later Text Button
                TextButton(
                    onClick = {
                        InAppRatingManager.getInstance(context).recordUserDismissed()
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Maybe Later",
                        color = Color(0xFFA1A1AA),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
