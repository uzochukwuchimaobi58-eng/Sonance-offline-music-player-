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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
    val knobSize = 118.dp
    var isDragging by remember { mutableStateOf(false) }

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnTurnOn by rememberUpdatedState(onTurnOn)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(knobSize)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Main, requireUnconsumed = true)
                        val activePointerId = down.id
                        isDragging = true
                        down.consume()
                        currentOnTurnOn?.invoke()

                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val knobRadiusPx = (size.width / 2f - 4.dp.toPx()) * 0.72f
                        var lastPos = down.position
                        var accum = currentValue.toFloat()

                        try {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == activePointerId } ?: break
                                if (!change.pressed) break
                                change.consume()

                                val currPos = change.position
                                val dy = currPos.y - lastPos.y
                                val dx = currPos.x - lastPos.x

                                val rx = currPos.x - centerX
                                val ry = currPos.y - centerY
                                val dist = kotlin.math.hypot(rx, ry)

                                // 1. Clockwise tangential circular component
                                val tangentialDelta = if (dist > 6f) {
                                    (dx * (-ry / dist) + dy * (rx / dist))
                                } else 0f
                                val circularDelta = (tangentialDelta / (2 * Math.PI.toFloat() * dist.coerceAtLeast(knobRadiusPx * 0.5f))) * (360f / 270f) * 100f * 1.5f

                                // 2. Vertical scroll component (up = increase, down = decrease)
                                val verticalScrollDelta = (-dy / (size.height * 1.4f)) * 100f

                                val delta = if (kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.25f && kotlin.math.abs(dy) >= 1.5f) {
                                    verticalScrollDelta
                                } else if (kotlin.math.abs(circularDelta) >= 0.15f) {
                                    circularDelta
                                } else {
                                    verticalScrollDelta
                                }

                                accum = (accum + delta).coerceIn(0f, 100f)
                                currentOnValueChange(accum.roundToInt())

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
                val knobRadius = outerRadius * 0.72f

                // Outer Scale Arc Range: 135 deg to 405 deg (270 deg sweep)
                val startAngle = 135f
                val sweepAngle = 270f
                val currentFraction = (currentValue / 100f).coerceIn(0f, 1f)

                // 1. Background Inactive Arc Track
                drawArc(
                    color = Color.White.copy(alpha = 0.10f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // 2. Active Glowing Neon Arc
                if (currentFraction > 0.005f && isOn) {
                    // Outer soft glow
                    drawArc(
                        color = activeColor.copy(alpha = 0.35f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * currentFraction,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = Size(outerRadius * 2, outerRadius * 2),
                        style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Sharp neon line
                    drawArc(
                        color = activeColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * currentFraction,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = Size(outerRadius * 2, outerRadius * 2),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 3. Tick Marks Around Perimeter
                val tickCount = 18
                for (i in 0..tickCount) {
                    val tAngle = startAngle + (i.toFloat() / tickCount) * sweepAngle
                    val tRad = Math.toRadians(tAngle.toDouble())
                    val r1 = outerRadius + 1.dp.toPx()
                    val r2 = outerRadius + 4.dp.toPx()
                    val p1 = Offset((center.x + r1 * cos(tRad)).toFloat(), (center.y + r1 * sin(tRad)).toFloat())
                    val p2 = Offset((center.x + r2 * cos(tRad)).toFloat(), (center.y + r2 * sin(tRad)).toFloat())
                    val isPastActive = (i.toFloat() / tickCount) <= currentFraction && isOn
                    drawLine(
                        color = if (isPastActive) activeColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
                        start = p1,
                        end = p2,
                        strokeWidth = 1.2.dp.toPx()
                    )
                }

                // 4. Large Brushed Concentric Metallic Dial Body
                // Deep drop shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.65f),
                    radius = knobRadius + 3.dp.toPx(),
                    center = center
                )

                // Concentric metallic gradients
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF94A3B8),
                            Color(0xFF334155),
                            Color(0xFF0F172A)
                        ),
                        center = center,
                        radius = knobRadius
                    ),
                    radius = knobRadius,
                    center = center
                )

                // Metallic Beveled Rim
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFCBD5E1),
                            Color(0xFF475569),
                            Color(0xFFE2E8F0),
                            Color(0xFF334155),
                            Color(0xFFCBD5E1)
                        ),
                        center = center
                    ),
                    radius = knobRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // 5. Radial Pointer Notch Needle on Dial Face (Direct trigonometric calculation)
                val pointerAngleDeg = startAngle + sweepAngle * currentFraction
                val pointerRad = Math.toRadians(pointerAngleDeg.toDouble())
                val n1 = knobRadius * 0.32f
                val n2 = knobRadius * 0.88f
                val notchStart = Offset((center.x + n1 * cos(pointerRad)).toFloat(), (center.y + n1 * sin(pointerRad)).toFloat())
                val notchEnd = Offset((center.x + n2 * cos(pointerRad)).toFloat(), (center.y + n2 * sin(pointerRad)).toFloat())

                // Glow behind notch
                if (isOn) {
                    drawLine(
                        color = activeColor.copy(alpha = 0.45f),
                        start = notchStart,
                        end = notchEnd,
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    color = if (isOn) Color.White else Color(0xFF94A3B8),
                    start = notchStart,
                    end = notchEnd,
                    strokeWidth = 2.8.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center Cap
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = knobRadius * 0.22f,
                    center = center
                )
                drawCircle(
                    color = if (isOn) activeColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f),
                    radius = knobRadius * 0.22f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title text below knob (Exact match to Image: all-caps bold)
        Text(
            text = title.uppercase(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        Text(
            text = if (value == 0 || !isOn) "OFF" else if (value >= 100) "MAX" else "$value%",
            color = if (value > 0 && isOn) activeColor else Color(0xFF94A3B8),
            fontSize = 11.5.sp,
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
