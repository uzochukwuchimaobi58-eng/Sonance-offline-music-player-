package com.sonance.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.ThemeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    sleepTimerSec: Int?,
    onSetTimerMinutes: (Int?) -> Unit,
    theme: ThemeConfig
) {
    if (!isOpen) return

    val presets = listOf(
        15 to "15 minutes",
        30 to "30 minutes",
        45 to "45 minutes",
        60 to "60 minutes (1 hour)",
        90 to "90 minutes"
    )

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = theme.sidebarBg,
        modifier = Modifier.testTag("sleep_timer_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = theme.accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Sleep Timer",
                    color = theme.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (sleepTimerSec != null && sleepTimerSec > 0) {
                val m = sleepTimerSec / 60
                val s = sleepTimerSec % 60
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Timer active: ${String.format("%d:%02d", m, s)} remaining",
                    color = theme.accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            presets.forEach { (mins, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onSetTimerMinutes(mins)
                            onClose()
                        }
                        .padding(vertical = 12.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = label, color = theme.textPrimary, fontSize = 14.sp)
                }
            }

            if (sleepTimerSec != null && sleepTimerSec > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSetTimerMinutes(null)
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Turn Off Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
