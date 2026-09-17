package com.sonance.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.model.ThemeConfig

@Composable
fun CreatePlaylistDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    onCreate: (String) -> Unit,
    theme: ThemeConfig
) {
    if (!isOpen) return

    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = theme.sidebarBg,
        title = {
            Text(
                text = "New Playlist",
                color = theme.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a name for your new playlist",
                    color = theme.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("e.g. Chill Beats, Afro Classics") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accentColor,
                        unfocusedBorderColor = theme.headerBorder,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_playlist_name")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (playlistName.isNotBlank()) {
                        onCreate(playlistName.trim())
                        onClose()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor),
                modifier = Modifier.testTag("btn_confirm_create_playlist")
            ) {
                Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel", color = theme.textSecondary)
            }
        },
        modifier = Modifier.testTag("dialog_create_playlist")
    )
}
