package com.sonance.musicplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val ALPHABET_INDEX_CHARS = listOf(
    "#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
    "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
    "V", "W", "X", "Y", "Z"
)

fun getTrackIndexKey(title: String): String {
    val trimmed = title.trim()
    val firstChar = trimmed.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: return "#"
    return if (firstChar in 'A'..'Z') firstChar.toString() else "#"
}

@Composable
fun AlphabetFastScroller(
    tracks: List<Track>,
    listState: LazyListState,
    theme: ThemeConfig,
    modifier: Modifier = Modifier,
    scope: CoroutineScope = rememberCoroutineScope()
) {
    if (tracks.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var activeChar by remember { mutableStateOf<String?>(null) }
    var columnHeightPx by remember { mutableFloatStateOf(0f) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }

    // Build letter to track index map (finding the first track for each letter)
    val letterToIndexMap = remember(tracks) {
        val map = mutableMapOf<String, Int>()
        tracks.forEachIndexed { index, track ->
            val key = getTrackIndexKey(track.title)
            if (!map.containsKey(key)) {
                map[key] = index
            }
        }
        map
    }

    fun onSelectLetterAtY(y: Float) {
        if (columnHeightPx <= 0f) return
        val clampedY = y.coerceIn(0f, columnHeightPx)
        val charIndex = ((clampedY / columnHeightPx) * ALPHABET_INDEX_CHARS.size)
            .toInt()
            .coerceIn(0, ALPHABET_INDEX_CHARS.size - 1)
        val selectedChar = ALPHABET_INDEX_CHARS[charIndex]

        if (activeChar != selectedChar) {
            activeChar = selectedChar
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

            // Find best matching track index: exact match or nearest subsequent
            var targetIndex = letterToIndexMap[selectedChar]
            if (targetIndex == null) {
                val nextCharIndex = ALPHABET_INDEX_CHARS.indexOf(selectedChar)
                for (i in nextCharIndex + 1 until ALPHABET_INDEX_CHARS.size) {
                    val candidate = letterToIndexMap[ALPHABET_INDEX_CHARS[i]]
                    if (candidate != null) {
                        targetIndex = candidate
                        break
                    }
                }
            }

            targetIndex?.let { idx ->
                scope.launch {
                    listState.scrollToItem(idx)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(26.dp)
            .testTag("alphabet_fast_scroller"),
        contentAlignment = Alignment.CenterEnd
    ) {
        // The vertical alphabet strip (all letters # and A-Z guaranteed visible via weighted distribution)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(20.dp)
                .padding(vertical = 2.dp)
                .onGloballyPositioned { coordinates ->
                    columnHeightPx = coordinates.size.height.toFloat()
                }
                .pointerInput(tracks) {
                    detectTapGestures(
                        onPress = { offset ->
                            isDragging = true
                            dismissJob?.cancel()
                            onSelectLetterAtY(offset.y)
                            tryAwaitRelease()
                            dismissJob = scope.launch {
                                delay(600)
                                isDragging = false
                                activeChar = null
                            }
                        }
                    )
                }
                .pointerInput(tracks) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dismissJob?.cancel()
                            onSelectLetterAtY(offset.y)
                        },
                        onDragEnd = {
                            dismissJob = scope.launch {
                                delay(600)
                                isDragging = false
                                activeChar = null
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            activeChar = null
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            onSelectLetterAtY(change.position.y)
                        }
                    )
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ALPHABET_INDEX_CHARS.forEach { char ->
                val isSelected = activeChar == char
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        color = if (isSelected) {
                            theme.accentColor
                        } else {
                            theme.textSecondary.copy(alpha = 0.75f)
                        },
                        fontSize = if (isSelected) 10.sp else 8.5.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 10.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            )
                        )
                    )
                }
            }
        }

        // Floating letter bubble indicator shown when dragging
        AnimatedVisibility(
            visible = isDragging && activeChar != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 44.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(8.dp, CircleShape),
                shape = CircleShape,
                color = theme.accentColor,
                tonalElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = activeChar ?: "",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
