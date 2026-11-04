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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import com.sonance.musicplayer.player.PlaybackManager
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokeStudioDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    track: Track?,
    isKaraokeActive: Boolean,
    onToggleKaraoke: () -> Unit,
    theme: ThemeConfig,
    playbackManager: PlaybackManager? = null,
    allTracks: List<Track> = emptyList(),
    isPlaying: Boolean = false,
    onPlayTrack: ((Track) -> Unit)? = null,
    onOpenLyrics: (() -> Unit)? = null
) {
    if (!isOpen) return

    val context = LocalContext.current
    var localIsKaraokeActive by remember(isKaraokeActive) { mutableStateOf(isKaraokeActive) }
    var midAirValue by remember { mutableFloatStateOf(69f) } // Mid Air / Vocal Attenuation: 0..100
    var highAirValue by remember { mutableFloatStateOf(69f) } // High Air / Clarity: 0..100
    var isLinked by remember { mutableStateOf(false) }
    var isTrimActive by remember { mutableStateOf(false) }

    // Live audio update whenever knobs change or power is toggled
    LaunchedEffect(localIsKaraokeActive, midAirValue, highAirValue, isTrimActive) {
        val effectiveReduction = if (isTrimActive) (midAirValue + 10f).coerceAtMost(100f) else midAirValue
        val effectiveHighAir = if (isTrimActive) (highAirValue + 10f).coerceAtMost(100f) else highAirValue
        if (localIsKaraokeActive) {
            playbackManager?.setKaraokeMode(true, effectiveReduction, effectiveHighAir)
        }
    }

    val cyanGlow = Color(0xFF00E5FF)
    val pinkGlow = Color(0xFFFF2A6D)
    val darkPanelBg = Color(0xFF070B13)
    val lightChassisBg = Color(0xFFDCE6EB)

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = darkPanelBg,
        dragHandle = null,
        modifier = Modifier.testTag("karaoke_studio_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Drag pill
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )

            // TOP DARK STUDIO VU METER PANEL (Image 2 Design)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF04070D),
                                Color(0xFF0B1220),
                                Color(0xFF080D17)
                            )
                        )
                    )
            ) {
                // Top header title: F R E S H   A I R
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "F R E S H   A I R",
                            color = Color(0xFFE2E8F0),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 6.sp
                        )
                        Text(
                            text = "KARAOKE VOCAL PROCESSOR",
                            color = cyanGlow.copy(alpha = 0.75f),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Glowing Analog Dynamic Meter Gauge Arc
                FreshAirAnalogMeter(
                    value = (midAirValue + highAirValue) / 2f,
                    isActive = localIsKaraokeActive,
                    cyanColor = cyanGlow,
                    pinkColor = pinkGlow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.BottomCenter)
                )
            }

            // BOTTOM SLEEK OFF-WHITE HARDWARE CHASSIS (Image 2 Design)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = lightChassisBg,
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    // TWO BIG CIRCULAR KNOBS SECTION ("MID AIR" & "HIGH AIR")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Big Knob 1: MID AIR (Vocal Cut)
                        FreshAirBigKnob(
                            label = "MID AIR",
                            value = midAirValue.toInt(),
                            onValueChange = { newVal ->
                                val delta = newVal - midAirValue
                                midAirValue = newVal.toFloat()
                                if (isLinked) {
                                    highAirValue = (highAirValue + delta).coerceIn(0f, 100f)
                                }
                            },
                            isActive = localIsKaraokeActive,
                            cyanColor = cyanGlow,
                            modifier = Modifier.testTag("knob_mid_air")
                        )

                        // Center Link Button: OK / LINK
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                onClick = { isLinked = !isLinked },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isLinked) cyanGlow.copy(alpha = 0.20f) else Color.White,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    if (isLinked) cyanGlow else Color(0xFFB0C4CE)
                                ),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (isLinked) "LINK" else "OK",
                                        color = if (isLinked) Color(0xFF00838F) else Color(0xFF475569),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        // Big Knob 2: HIGH AIR (Clarity / Harmonics)
                        FreshAirBigKnob(
                            label = "HIGH AIR",
                            value = highAirValue.toInt(),
                            onValueChange = { newVal ->
                                val delta = newVal - highAirValue
                                highAirValue = newVal.toFloat()
                                if (isLinked) {
                                    midAirValue = (midAirValue + delta).coerceIn(0f, 100f)
                                }
                            },
                            isActive = localIsKaraokeActive,
                            cyanColor = cyanGlow,
                            modifier = Modifier.testTag("knob_high_air")
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // BOTTOM HARDWARE CONTROLS: POWER & TRIM BUTTONS (Image 2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: POWER button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                val newState = !localIsKaraokeActive
                                localIsKaraokeActive = newState
                                playbackManager?.setKaraokeMode(newState, midAirValue, highAirValue)
                                Toast.makeText(
                                    context,
                                    if (newState) "🎤 Karaoke Mode ON: Vocals attenuated" else "Karaoke Mode turned OFF",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "POWER",
                                    color = Color(0xFF475569),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(3.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(
                                            1.5.dp,
                                            if (localIsKaraokeActive) cyanGlow else Color(0xFFCBD5E1),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = "Power",
                                        tint = if (localIsKaraokeActive) cyanGlow else Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Center Status readout
                        Text(
                            text = if (localIsKaraokeActive) "KARAOKE IS ACTIVE" else "STANDBY",
                            color = if (localIsKaraokeActive) Color(0xFF007788) else Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )

                        // Right: TRIM button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                isTrimActive = !isTrimActive
                                Toast.makeText(
                                    context,
                                    if (isTrimActive) "Trim Boost ON (+10% Air)" else "Trim Boost OFF",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "TRIM",
                                    color = Color(0xFF475569),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(3.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(
                                            1.5.dp,
                                            if (isTrimActive) cyanGlow else Color(0xFFCBD5E1),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isTrimActive) cyanGlow else Color(0xFF94A3B8))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // NOW PLAYING & SING ALONG BAR
                    val activeSong = track ?: allTracks.firstOrNull()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(cyanGlow.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = Color(0xFF00838F),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activeSong?.title ?: "No Song Selected",
                                            color = Color(0xFF0F172A),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isPlaying) "Playing • Sing along live" else "Ready • Tap play to test",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Play / Pause Button
                                IconButton(
                                    onClick = {
                                        if (track != null) {
                                            if (isPlaying) playbackManager?.pause() else playbackManager?.resume()
                                        } else if (activeSong != null) {
                                            onPlayTrack?.invoke(activeSong)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00ACC1))
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (onOpenLyrics != null && activeSong != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        localIsKaraokeActive = true
                                        playbackManager?.setKaraokeMode(true, midAirValue, highAirValue)
                                        if (!isPlaying) {
                                            if (track != null) playbackManager?.resume()
                                            else onPlayTrack?.invoke(activeSong)
                                        }
                                        onClose()
                                        onOpenLyrics()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sing with Synced Lyrics",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top Analog Studio VU Meter / Arc Gauge (Image 2 style)
 */
@Composable
private fun FreshAirAnalogMeter(
    value: Float, // 0..100
    isActive: Boolean,
    cyanColor: Color,
    pinkColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = if (isActive) value else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "meter_anim"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h + 15.dp.toPx())
        val radius = (w * 0.46f).coerceAtMost(h * 1.35f)

        // Arc starts at 195 deg to 345 deg (150 deg sweep)
        val startAngle = 195f
        val sweepAngle = 150f

        // Subtle background guide arc
        drawArc(
            color = Color.White.copy(alpha = 0.08f),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 2.dp.toPx())
        )

        // Outer Radial Tick Marks
        val tickCount = 42
        for (i in 0..tickCount) {
            val progress = i.toFloat() / tickCount
            val angleDeg = startAngle + progress * sweepAngle
            val angleRad = Math.toRadians(angleDeg.toDouble())

            val isPinkZone = progress >= 0.78f
            val baseColor = if (isPinkZone) pinkColor else cyanColor
            val isLit = isActive && progress <= (animatedValue / 100f)

            val tickLen = if (i % 6 == 0) 12.dp.toPx() else 6.dp.toPx()
            val r1 = radius - tickLen
            val r2 = radius

            val p1 = Offset((center.x + r1 * cos(angleRad)).toFloat(), (center.y + r1 * sin(angleRad)).toFloat())
            val p2 = Offset((center.x + r2 * cos(angleRad)).toFloat(), (center.y + r2 * sin(angleRad)).toFloat())

            drawLine(
                color = if (isLit) baseColor else Color.White.copy(alpha = 0.12f),
                start = p1,
                end = p2,
                strokeWidth = if (i % 6 == 0) 2.2.dp.toPx() else 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Active Glowing Arc Line
        val currentSweep = sweepAngle * (animatedValue / 100f).coerceIn(0f, 1f)
        if (isActive && currentSweep > 1f) {
            drawArc(
                brush = Brush.horizontalGradient(
                    listOf(cyanColor, cyanColor, pinkColor),
                    startX = center.x - radius,
                    endX = center.x + radius
                ),
                startAngle = startAngle,
                sweepAngle = currentSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Glowing Needle Pointer (pointing to current level)
        val needleAngleDeg = startAngle + (animatedValue / 100f).coerceIn(0f, 1f) * sweepAngle
        val needleRad = Math.toRadians(needleAngleDeg.toDouble())
        val needleTip = Offset(
            (center.x + (radius - 4.dp.toPx()) * cos(needleRad)).toFloat(),
            (center.y + (radius - 4.dp.toPx()) * sin(needleRad)).toFloat()
        )

        // Needle glow
        if (isActive) {
            drawLine(
                color = cyanColor.copy(alpha = 0.45f),
                start = center,
                end = needleTip,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Needle main line
        drawLine(
            color = if (isActive) cyanColor else Color.White.copy(alpha = 0.4f),
            start = center,
            end = needleTip,
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Pivot center cap
        drawCircle(
            color = Color(0xFF0F172A),
            radius = 16.dp.toPx(),
            center = center
        )
        drawCircle(
            color = if (isActive) cyanColor else Color(0xFF475569),
            radius = 6.dp.toPx(),
            center = center
        )
    }
}

/**
 * Large Raised Off-White Tactile Rotary Knob matching Image 2 ("Fresh Air" style):
 * - Raised white circular disc with drop shadow and tactile bevel
 * - Outer glowing cyan rim arc gauge
 * - Inside readout: Big bold number (e.g. "69"), "MID AIR" or "HIGH AIR", "ON"
 * - Working draggable touch interaction adjusting value from 0 to 100
 */
@Composable
private fun FreshAirBigKnob(
    label: String,
    value: Int, // 0..100
    onValueChange: (Int) -> Unit,
    isActive: Boolean,
    cyanColor: Color,
    modifier: Modifier = Modifier
) {
    val knobSize = 120.dp
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

                                val prevAngle = kotlin.math.atan2(lastPos.y - centerY, lastPos.x - centerX)
                                val currAngle = kotlin.math.atan2(currPos.y - centerY, currPos.x - centerX)
                                var deltaAngle = currAngle - prevAngle
                                if (deltaAngle > Math.PI) deltaAngle -= (2.0 * Math.PI).toFloat()
                                else if (deltaAngle < -Math.PI) deltaAngle += (2.0 * Math.PI).toFloat()
                                val deltaDeg = Math.toDegrees(deltaAngle.toDouble()).toFloat()

                                val linearDelta = (-dy * 1.0f) + (dx * 0.35f)

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
                val knobRadius = outerRadius * 0.85f

                // Arc angles: 135 deg to 405 deg (270 deg sweep)
                val startAngle = 135f
                val sweepAngle = 270f
                val fraction = (value / 100f).coerceIn(0f, 1f)

                // 1. Inactive Rim Arc Track
                drawArc(
                    color = Color(0xFFB0C4CE),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // 2. Active Glowing Cyan Rim Arc
                if (isActive && fraction > 0.01f) {
                    drawArc(
                        color = cyanColor.copy(alpha = 0.35f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * fraction,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = Size(outerRadius * 2, outerRadius * 2),
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = cyanColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * fraction,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = Size(outerRadius * 2, outerRadius * 2),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 3. Tactile 3D Raised White/Silver Disc Body
                // Drop shadow
                drawCircle(
                    color = Color(0xFF94A3B8).copy(alpha = 0.40f),
                    radius = knobRadius + 2.dp.toPx(),
                    center = Offset(center.x, center.y + 2.dp.toPx())
                )

                // White body with subtle radial gradient
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFF1F5F9),
                            Color(0xFFE2E8F0)
                        ),
                        center = center,
                        radius = knobRadius
                    ),
                    radius = knobRadius,
                    center = center
                )

                // Beveled Rim border
                drawCircle(
                    color = Color(0xFFCBD5E1),
                    radius = knobRadius,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // 4. Indicator Notch Line on the Rim Edge
                val pointerAngleDeg = startAngle + sweepAngle * fraction
                val pointerRad = Math.toRadians(pointerAngleDeg.toDouble())
                val n1 = knobRadius * 0.70f
                val n2 = knobRadius * 0.98f
                val notchStart = Offset((center.x + n1 * cos(pointerRad)).toFloat(), (center.y + n1 * sin(pointerRad)).toFloat())
                val notchEnd = Offset((center.x + n2 * cos(pointerRad)).toFloat(), (center.y + n2 * sin(pointerRad)).toFloat())

                drawLine(
                    color = if (isActive) cyanColor else Color(0xFF64748B),
                    start = notchStart,
                    end = notchEnd,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Inside Center Labels: Number (69), Label ("MID AIR"), "ON"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$value",
                    color = Color(0xFF1E293B),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 24.sp
                )

                Text(
                    text = label,
                    color = Color(0xFF475569),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                )

                Text(
                    text = if (isActive && value > 0) "ON" else "OFF",
                    color = if (isActive && value > 0) Color(0xFF00ACC1) else Color(0xFF94A3B8),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Scale numbers: 0 (left) and 100 (right)
        Row(
            modifier = Modifier.width(knobSize),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "0", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "100", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
