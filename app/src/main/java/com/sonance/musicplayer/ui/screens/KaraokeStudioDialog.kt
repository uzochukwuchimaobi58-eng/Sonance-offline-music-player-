package com.sonance.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokeStudioDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    track: Track?,
    isKaraokeActive: Boolean,
    onToggleKaraoke: () -> Unit,
    theme: ThemeConfig
) {
    if (!isOpen) return

    var vocalReduction by remember { mutableFloatStateOf(85f) }
    var pitchShift by remember { mutableFloatStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = theme.sidebarBg,
        modifier = Modifier.testTag("karaoke_studio_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color(0xFFFB7185),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Karaoke Studio",
                        color = theme.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = isKaraokeActive,
                    onCheckedChange = { onToggleKaraoke() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFB7185),
                        checkedTrackColor = Color(0xFFFB7185).copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "AI VOCAL REMOVER & SING-ALONG",
                color = theme.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Vocal Reduction Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Vocal Attenuation", color = theme.textPrimary, fontSize = 13.sp)
                Text("${vocalReduction.toInt()}%", color = Color(0xFFFB7185), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = vocalReduction,
                onValueChange = { vocalReduction = it },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFB7185),
                    activeTrackColor = Color(0xFFFB7185)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pitch Shift Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Pitch Key Shift", color = theme.textPrimary, fontSize = 13.sp)
                val pitchStr = if (pitchShift.toInt() > 0) "+${pitchShift.toInt()}" else "${pitchShift.toInt()}"
                Text("$pitchStr semitones", color = Color(0xFFFB7185), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = pitchShift,
                onValueChange = { pitchShift = it },
                valueRange = -6f..6f,
                steps = 11,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFB7185),
                    activeTrackColor = Color(0xFFFB7185)
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB7185)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Apply & Sing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
