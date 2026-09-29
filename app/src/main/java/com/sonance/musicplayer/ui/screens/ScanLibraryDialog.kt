package com.sonance.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.ThemeConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanLibraryDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    totalTrackCount: Int,
    onScan: suspend () -> Int,
    theme: ThemeConfig,
    hasPermission: Boolean = true,
    onRequestPermission: (() -> Unit)? = null
) {
    if (!isOpen) return

    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = theme.sidebarBg,
        modifier = Modifier.testTag("scan_library_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = theme.accentColor,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Scan Music Library",
                color = theme.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Search phone storage, SD card, WhatsApp Audio, Telegram, and downloads for newly added songs and offline music.",
                color = theme.textSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (!hasPermission) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Storage / Audio permission required to read offline music files on your device.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isScanning) {
                CircularProgressIndicator(color = theme.accentColor)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Scanning device storage & folders...", color = theme.textSecondary, fontSize = 12.sp)
            } else if (scanResult != null) {
                Text(
                    text = scanResult ?: "",
                    color = theme.accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Currently indexing $totalTrackCount tracks in library",
                    color = theme.textSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close", color = theme.textPrimary)
                }

                Button(
                    onClick = {
                        if (!hasPermission) {
                            onRequestPermission?.invoke()
                        }
                        isScanning = true
                        scope.launch {
                            val found = onScan()
                            isScanning = false
                            scanResult = "Scan complete! Found $found audio files."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (!hasPermission) "Grant & Scan" else "Scan Now",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
