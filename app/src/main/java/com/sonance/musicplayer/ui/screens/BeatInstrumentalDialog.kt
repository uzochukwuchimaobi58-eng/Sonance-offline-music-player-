package com.sonance.musicplayer.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.data.MusicRepository
import com.sonance.musicplayer.model.MusicBassSettings
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.player.PlaybackManager
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicBassDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    track: Track?,
    theme: ThemeConfig,
    playbackManager: PlaybackManager? = null,
    repository: MusicRepository? = null,
    onConversionFinished: () -> Unit = {}
) {
    if (!isOpen) return

    val context = LocalContext.current
    val currentBassSettings by playbackManager?.musicBassSettings?.collectAsState()
        ?: remember { mutableStateOf(MusicBassSettings()) }

    var isBassEnabled by remember(currentBassSettings.enabled) { mutableStateOf(currentBassSettings.enabled) }
    var bassBoostVal by remember(currentBassSettings.bassBoost) { mutableFloatStateOf(currentBassSettings.bassBoost.toFloat()) }

    var isVirtEnabled by remember(currentBassSettings.virtualizerEnabled) { mutableStateOf(currentBassSettings.virtualizerEnabled) }
    var virtVal by remember(currentBassSettings.virtualizerStrength) { mutableFloatStateOf(currentBassSettings.virtualizerStrength.toFloat()) }

    var isBalanceEnabled by remember(currentBassSettings.soundBalanceEnabled) { mutableStateOf(currentBassSettings.soundBalanceEnabled) }
    var leftBalance by remember(currentBassSettings.soundBalanceLeft) { mutableFloatStateOf(currentBassSettings.soundBalanceLeft) }
    var rightBalance by remember(currentBassSettings.soundBalanceRight) { mutableFloatStateOf(currentBassSettings.soundBalanceRight) }

    // Live real-time audio effects application whenever user toggles switches or rolls knobs
    LaunchedEffect(isBassEnabled, bassBoostVal, isVirtEnabled, virtVal, isBalanceEnabled, leftBalance, rightBalance) {
        playbackManager?.applyMusicBass(
            MusicBassSettings(
                enabled = isBassEnabled,
                bassBoost = bassBoostVal.toInt(),
                virtualizerEnabled = isVirtEnabled,
                virtualizerStrength = virtVal.toInt(),
                soundBalanceEnabled = isBalanceEnabled,
                soundBalanceLeft = leftBalance,
                soundBalanceRight = rightBalance
            )
        )
    }

    val dialogBg = Color(0xFF1E2228) // Exact dark slate background from Picture 1 & 2
    val redColor = Color(0xFFEF4444)

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = dialogBg,
        dragHandle = null,
        modifier = Modifier.testTag("music_bass_dialog")
    ) {
        // NON-SCROLLING CONTAINER (as requested: "remove all those vertical or horizontal scroll")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Drag handle pill
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.20f))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bass & Sound Balance",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // SECTION 1: BASS BOOST & VIRTUALIZER (Picture 1)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Knob 1: Bass Boost
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Switch on top
                    CustomHardwareToggle(
                        checked = isBassEnabled,
                        onCheckedChange = { checked ->
                            isBassEnabled = checked
                            if (checked && bassBoostVal == 0f) {
                                bassBoostVal = 50f
                            }
                            Toast.makeText(
                                context,
                                if (checked) "Bass Boost ON" else "Bass Boost OFF",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        activeColor = redColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Big Charcoal Rotary Knob with dual-oval thumb indents
                    PictureStyleRotaryKnob(
                        title = "Bass Boost",
                        value = if (isBassEnabled) bassBoostVal.toInt() else 0,
                        onValueChange = { newVal ->
                            isBassEnabled = true
                            bassBoostVal = newVal.toFloat()
                        },
                        onTurnOn = {
                            isBassEnabled = true
                            if (bassBoostVal == 0f) bassBoostVal = 45f
                        },
                        isOn = isBassEnabled,
                        activeColor = redColor,
                        modifier = Modifier.testTag("knob_bass_boost")
                    )
                }

                // Knob 2: Virtualizer
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Switch on top
                    CustomHardwareToggle(
                        checked = isVirtEnabled,
                        onCheckedChange = { checked ->
                            isVirtEnabled = checked
                            if (checked && virtVal == 0f) {
                                virtVal = 50f
                            }
                            Toast.makeText(
                                context,
                                if (checked) "Virtualizer ON" else "Virtualizer OFF",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        activeColor = redColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Big Charcoal Rotary Knob with dual-oval thumb indents
                    PictureStyleRotaryKnob(
                        title = "Virtualizer",
                        value = if (isVirtEnabled) virtVal.toInt() else 0,
                        onValueChange = { newVal ->
                            isVirtEnabled = true
                            virtVal = newVal.toFloat()
                        },
                        onTurnOn = {
                            isVirtEnabled = true
                            if (virtVal == 0f) virtVal = 45f
                        },
                        isOn = isVirtEnabled,
                        activeColor = redColor,
                        modifier = Modifier.testTag("knob_virtualizer")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // SECTION 2: SOUND BALANCE (Picture 2)
            // ==========================================
            // Header with title on left and toggle switch on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SOUND BALANCE",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                CustomHardwareToggle(
                    checked = isBalanceEnabled,
                    onCheckedChange = { checked ->
                        isBalanceEnabled = checked
                        if (checked) {
                            if (leftBalance == 0f) leftBalance = 100f
                            if (rightBalance == 0f) rightBalance = 100f
                        }
                        Toast.makeText(
                            context,
                            if (checked) "Sound Balance ON" else "Sound Balance OFF (Normal Stereo)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    activeColor = redColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Two knobs below: Left & Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Channel Knob
                PictureStyleRotaryKnob(
                    title = "Left",
                    value = if (isBalanceEnabled) leftBalance.toInt() else 100,
                    onValueChange = { newVal ->
                        isBalanceEnabled = true
                        leftBalance = newVal.toFloat()
                    },
                    onTurnOn = {
                        isBalanceEnabled = true
                    },
                    isOn = isBalanceEnabled,
                    activeColor = redColor,
                    modifier = Modifier.testTag("knob_balance_left")
                )

                // Right Channel Knob
                PictureStyleRotaryKnob(
                    title = "Right",
                    value = if (isBalanceEnabled) rightBalance.toInt() else 100,
                    onValueChange = { newVal ->
                        isBalanceEnabled = true
                        rightBalance = newVal.toFloat()
                    },
                    onTurnOn = {
                        isBalanceEnabled = true
                    },
                    isOn = isBalanceEnabled,
                    activeColor = redColor,
                    modifier = Modifier.testTag("knob_balance_right")
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Done Button
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = if (isBassEnabled || isVirtEnabled || isBalanceEnabled) redColor else Color(0xFF374151)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Done",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Custom Hardware Toggle Switch (Picture 1 & 2 design)
 */
@Composable
private fun CustomHardwareToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 18f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "switch_thumb_anim"
    )

    Box(
        modifier = modifier
            .width(42.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (checked) activeColor.copy(alpha = 0.35f) else Color(0xFF111418))
            .border(1.2.dp, if (checked) activeColor else Color(0xFF374151), RoundedCornerShape(11.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Toggle Thumb
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (checked) activeColor else Color(0xFF9CA3AF),
                            if (checked) Color(0xFFB91C1C) else Color(0xFF4B5563)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Inner dot
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * Big Charcoal Rotary Knob matching Picture 1 & 2:
 * - Two ergonomic oval thumb indents on the surface
 * - Surrounding circular dot tick marks around the perimeter
 * - Rotatable from 0% to 100%
 * - When turned ON, active tick dots and the indicator dot glow in VIVID RED!
 */
@Composable
private fun PictureStyleRotaryKnob(
    title: String,
    value: Int, // 0..100
    onValueChange: (Int) -> Unit,
    isOn: Boolean,
    onTurnOn: (() -> Unit)? = null,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val knobSize = 114.dp
    var isDragging by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(knobSize)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        isDragging = true
                        down.consume()
                        onTurnOn?.invoke()

                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        var lastPos = down.position
                        var accum = value.toFloat()

                        try {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()

                                val currPos = change.position
                                val dy = currPos.y - lastPos.y
                                val dx = currPos.x - lastPos.x

                                // 1. Circular / angular delta around knob center (clockwise = positive)
                                val prevAngle = kotlin.math.atan2(lastPos.y - centerY, lastPos.x - centerX)
                                val currAngle = kotlin.math.atan2(currPos.y - centerY, currPos.x - centerX)
                                var deltaAngle = currAngle - prevAngle
                                if (deltaAngle > Math.PI) deltaAngle -= (2.0 * Math.PI).toFloat()
                                else if (deltaAngle < -Math.PI) deltaAngle += (2.0 * Math.PI).toFloat()
                                val deltaDeg = Math.toDegrees(deltaAngle.toDouble()).toFloat()

                                // 2. Linear displacement: dragging up (-dy) or right (+dx) increases
                                val linearDelta = (-dy * 1.0f) + (dx * 0.35f)

                                // 3. True rolling gesture:
                                val r = kotlin.math.hypot(currPos.x - centerX, currPos.y - centerY)
                                val isNearCenter = r < 18.dp.toPx()

                                val delta = if (!isNearCenter && kotlin.math.abs(deltaDeg) >= 0.35f) {
                                    (deltaDeg / 270f) * 100f * 1.45f
                                } else {
                                    linearDelta
                                }

                                accum = (accum + delta).coerceIn(0f, 100f)
                                onValueChange(accum.roundToInt())

                                lastPos = currPos
                            }
                        } finally {
                            isDragging = false
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.width / 2f - 4.dp.toPx()
                val knobRadius = outerRadius * 0.76f

                // Tick Dot Arc: from 135 deg to 405 deg (270 deg sweep)
                val startAngle = 135f
                val sweepAngle = 270f
                val currentFraction = (value / 100f).coerceIn(0f, 1f)
                val totalDots = 16

                // 1. Draw Surrounding Circular Dot Tick Marks (Exact match to Picture 1 & 2)
                for (i in 0..totalDots) {
                    val dotFraction = i.toFloat() / totalDots
                    val angleDeg = startAngle + dotFraction * sweepAngle
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val dotCenter = Offset(
                        (center.x + outerRadius * cos(angleRad)).toFloat(),
                        (center.y + outerRadius * sin(angleRad)).toFloat()
                    )

                    val isLit = isOn && dotFraction <= currentFraction
                    if (isLit) {
                        // Soft red glow behind active dot
                        drawCircle(
                            color = activeColor.copy(alpha = 0.35f),
                            radius = 4.2.dp.toPx(),
                            center = dotCenter
                        )
                        // Sharp red dot
                        drawCircle(
                            color = activeColor,
                            radius = 2.8.dp.toPx(),
                            center = dotCenter
                        )
                    } else {
                        // Inactive dark gray dot
                        drawCircle(
                            color = Color(0xFF475569).copy(alpha = 0.65f),
                            radius = 2.4.dp.toPx(),
                            center = dotCenter
                        )
                    }
                }

                // 2. Large Dark Charcoal Knob Body with 3D drop shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.55f),
                    radius = knobRadius + 2.dp.toPx(),
                    center = Offset(center.x, center.y + 2.dp.toPx())
                )

                // Knob Body Radial Gradient (Dark slate / titanium)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF333A44),
                            Color(0xFF242A32),
                            Color(0xFF14181E)
                        ),
                        center = center,
                        radius = knobRadius
                    ),
                    radius = knobRadius,
                    center = center
                )

                // Beveled Outer Rim
                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.45f)
                        ),
                        start = Offset(center.x - knobRadius, center.y - knobRadius),
                        end = Offset(center.x + knobRadius, center.y + knobRadius)
                    ),
                    radius = knobRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 3. Ergonomic Dual-Oval Thumb Indents on Knob Surface (Exact match to Picture 1 & 2!)
                val indentWidth = knobRadius * 0.90f
                val indentHeight = knobRadius * 0.44f
                val indentCorner = CornerRadius(indentHeight / 2f, indentHeight / 2f)

                // Top Oval Indent
                val topIndentCenterY = center.y - knobRadius * 0.30f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF12151A), Color(0xFF282F38)),
                        startY = topIndentCenterY - indentHeight / 2f,
                        endY = topIndentCenterY + indentHeight / 2f
                    ),
                    topLeft = Offset(center.x - indentWidth / 2f, topIndentCenterY - indentHeight / 2f),
                    size = Size(indentWidth, indentHeight),
                    cornerRadius = indentCorner
                )

                // Bottom Oval Indent
                val bottomIndentCenterY = center.y + knobRadius * 0.30f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF12151A), Color(0xFF282F38)),
                        startY = bottomIndentCenterY - indentHeight / 2f,
                        endY = bottomIndentCenterY + indentHeight / 2f
                    ),
                    topLeft = Offset(center.x - indentWidth / 2f, bottomIndentCenterY - indentHeight / 2f),
                    size = Size(indentWidth, indentHeight),
                    cornerRadius = indentCorner
                )

                // 4. Indicator Dot on Dial
                val pointerAngleDeg = startAngle + sweepAngle * currentFraction
                val pointerRad = Math.toRadians(pointerAngleDeg.toDouble())
                val indicatorDist = knobRadius * 0.65f
                val indicatorCenter = Offset(
                    (center.x + indicatorDist * cos(pointerRad)).toFloat(),
                    (center.y + indicatorDist * sin(pointerRad)).toFloat()
                )

                if (isOn) {
                    // Glowing Red Indicator Dot
                    drawCircle(
                        color = activeColor.copy(alpha = 0.50f),
                        radius = 5.dp.toPx(),
                        center = indicatorCenter
                    )
                    drawCircle(
                        color = activeColor,
                        radius = 3.2.dp.toPx(),
                        center = indicatorCenter
                    )
                } else {
                    // Dark gray inactive indicator dot
                    drawCircle(
                        color = Color(0xFF64748B),
                        radius = 2.8.dp.toPx(),
                        center = indicatorCenter
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Title text below knob (Picture 1 & 2 design)
        Text(
            text = title,
            color = if (isOn) Color.White else Color(0xFF94A3B8),
            fontSize = 12.5.sp,
            fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium
        )

        Text(
            text = if (isOn) "$value%" else "OFF",
            color = if (isOn) activeColor else Color(0xFF64748B),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Backward compatibility alias so callers can reference BeatInstrumentalDialog
 */
@Composable
fun BeatInstrumentalDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    track: Track?,
    theme: ThemeConfig,
    playbackManager: PlaybackManager? = null,
    repository: MusicRepository? = null,
    onConversionFinished: () -> Unit = {}
) {
    MusicBassDialog(
        isOpen = isOpen,
        onClose = onClose,
        track = track,
        theme = theme,
        playbackManager = playbackManager,
        repository = repository,
        onConversionFinished = onConversionFinished
    )
}
