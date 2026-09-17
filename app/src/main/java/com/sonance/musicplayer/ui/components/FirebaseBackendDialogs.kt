package com.sonance.musicplayer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.data.FirebaseBackendService
import com.sonance.musicplayer.model.RemoteBackendSettings
import com.sonance.musicplayer.model.ThemeConfig

@Composable
fun FirebaseConsoleGuideDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    theme: ThemeConfig
) {
    if (!isOpen) return
    val context = LocalContext.current

    val consoleUrl = "https://console.firebase.google.com/project/${FirebaseBackendService.PROJECT_ID}/firestore/databases/${FirebaseBackendService.DATABASE_ID}/data"

    val sampleJson = """
    {
      "announcement_enabled": true,
      "announcement_title": "Sonance Studio Update",
      "announcement_message": "Welcome to Sonance! Lossless 10-Band EQ & gapless playback are live.",
      "announcement_type": "info",
      "announcement_action_url": "",
      "latest_version": "1.0.0",
      "min_supported_version": 1,
      "update_url": "https://play.google.com/store/apps/details?id=com.sonance.musicplayer",
      "release_notes": "• Cloud settings integration\n• Equalizer presets\n• Gapless playback",
      "force_update": false,
      "support_email": "uzochukwuchimaobi58@gmail.com",
      "enable_gear_billboard": true,
      "default_crossfade_seconds": 0,
      "show_shuffle_button_default": true
    }
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = theme.sidebarBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = Color(0xFFF58220),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Firebase Console Guide",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "You can remotely manage banners, app updates, and defaults in the Firebase Console without pushing an app store release.",
                    color = theme.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.05f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "Target Firestore Location:", color = theme.accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(text = "• Project: ${FirebaseBackendService.PROJECT_ID}", color = theme.textPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "• Database: ${FirebaseBackendService.DATABASE_ID}", color = theme.textPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "• Collection: app_settings", color = theme.textPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "• Document ID: global", color = theme.textPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Text(
                    text = "Key Fields in Document 'global':",
                    color = theme.accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                FieldInfoItem("announcement_enabled", "boolean", "true / false (controls top broadcast banner)")
                FieldInfoItem("announcement_title", "string", "Banner heading (e.g. New Sound Engine!)")
                FieldInfoItem("announcement_message", "string", "Banner text message")
                FieldInfoItem("announcement_type", "string", "info, promo, alert, or update")
                FieldInfoItem("announcement_action_url", "string", "Optional web URL opened on tap")
                FieldInfoItem("latest_version", "string", "Version check (e.g. 1.0.1 or 2.0.0)")
                FieldInfoItem("update_url", "string", "Play Store or direct APK download URL")
                FieldInfoItem("release_notes", "string", "Changelog displayed in update dialog")
                FieldInfoItem("force_update", "boolean", "Require user to update app immediately")
                FieldInfoItem("support_email", "string", "Inquiry email address")
                FieldInfoItem("enable_gear_billboard", "boolean", "Toggle gear billboard visibility")

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Firestore JSON Template", sampleJson)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "JSON template copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accentColor)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy JSON", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(consoleUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.firebase.google.com/"))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF58220))
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Console", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close", color = theme.accentColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun FieldInfoItem(key: String, type: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = key, color = Color(0xFF90CAF9), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "($type)", color = Color(0xFFB0BEC5), fontSize = 10.sp)
        }
        Text(text = desc, color = Color(0xFFECEFF1), fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseLiveBackendEditorDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    currentSettings: RemoteBackendSettings,
    onSave: (RemoteBackendSettings) -> Unit,
    theme: ThemeConfig
) {
    if (!isOpen) return
    val context = LocalContext.current

    var announcementEnabled by remember(currentSettings) { mutableStateOf(currentSettings.announcementEnabled) }
    var announcementTitle by remember(currentSettings) { mutableStateOf(currentSettings.announcementTitle) }
    var announcementMessage by remember(currentSettings) { mutableStateOf(currentSettings.announcementMessage) }
    var announcementType by remember(currentSettings) { mutableStateOf(currentSettings.announcementType) }
    var announcementActionUrl by remember(currentSettings) { mutableStateOf(currentSettings.announcementActionUrl) }
    var latestVersion by remember(currentSettings) { mutableStateOf(currentSettings.latestVersion) }
    var updateUrl by remember(currentSettings) { mutableStateOf(currentSettings.updateUrl) }
    var releaseNotes by remember(currentSettings) { mutableStateOf(currentSettings.releaseNotes) }
    var forceUpdate by remember(currentSettings) { mutableStateOf(currentSettings.forceUpdate) }
    var supportEmail by remember(currentSettings) { mutableStateOf(currentSettings.supportEmail) }
    var enableGearBillboard by remember(currentSettings) { mutableStateOf(currentSettings.enableGearBillboard) }

    val typeOptions = listOf("info", "promo", "alert", "update")

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = theme.sidebarBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = theme.accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Backend Settings Manager",
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Edit cloud settings directly or push them to Firestore document 'app_settings/global':",
                    color = theme.textSecondary,
                    fontSize = 12.sp
                )

                // Announcement Section
                Text(text = "Announcement Broadcast", color = theme.accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Announcement Banner Enabled", color = theme.textPrimary, fontSize = 13.sp)
                    Switch(
                        checked = announcementEnabled,
                        onCheckedChange = { announcementEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.accentColor)
                    )
                }

                OutlinedTextField(
                    value = announcementTitle,
                    onValueChange = { announcementTitle = it },
                    label = { Text("Banner Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accentColor,
                        unfocusedBorderColor = theme.cardBorder,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )

                OutlinedTextField(
                    value = announcementMessage,
                    onValueChange = { announcementMessage = it },
                    label = { Text("Banner Message") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accentColor,
                        unfocusedBorderColor = theme.cardBorder,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )

                // Type selector
                Column {
                    Text(text = "Banner Type:", color = theme.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        typeOptions.forEach { type ->
                            val isSelected = announcementType.equals(type, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) theme.accentColor else Color.White.copy(alpha = 0.08f))
                                    .clickable { announcementType = type }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else theme.textPrimary
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = announcementActionUrl,
                    onValueChange = { announcementActionUrl = it },
                    label = { Text("Action URL (Optional)") },
                    placeholder = { Text("https://example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accentColor,
                        unfocusedBorderColor = theme.cardBorder,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )

                Divider(color = theme.cardBorder)

                // App Version & Updates
                Text(text = "App Updates & Versions", color = theme.accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                OutlinedTextField(
                    value = latestVersion,
                    onValueChange = { latestVersion = it },
                    label = { Text("Latest Version String") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accentColor,
                        unfocusedBorderColor = theme.cardBorder,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )

                OutlinedTextField(
                    value = updateUrl,
                    onValueChange = { updateUrl = it },
                    label = { Text("Update Download URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accentColor,
                        unfocusedBorderColor = theme.cardBorder,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )

                OutlinedTextField(
                    value = releaseNotes,
                    onValueChange = { releaseNotes = it },
                    label = { Text("Release Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accentColor,
                        unfocusedBorderColor = theme.cardBorder,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Force Update Prompt", color = theme.textPrimary, fontSize = 13.sp)
                    Switch(
                        checked = forceUpdate,
                        onCheckedChange = { forceUpdate = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.accentColor)
                    )
                }

                Divider(color = theme.cardBorder)

                // Additional remote controls
                Text(text = "Other Remote Controls", color = theme.accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                OutlinedTextField(
                    value = supportEmail,
                    onValueChange = { supportEmail = it },
                    label = { Text("Support Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accentColor,
                        unfocusedBorderColor = theme.cardBorder,
                        focusedTextColor = theme.textPrimary,
                        unfocusedTextColor = theme.textPrimary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Enable Audio Gear Billboard", color = theme.textPrimary, fontSize = 13.sp)
                    Switch(
                        checked = enableGearBillboard,
                        onCheckedChange = { enableGearBillboard = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.accentColor)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = currentSettings.copy(
                        announcementEnabled = announcementEnabled,
                        announcementTitle = announcementTitle,
                        announcementMessage = announcementMessage,
                        announcementType = announcementType,
                        announcementActionUrl = announcementActionUrl,
                        latestVersion = latestVersion,
                        updateUrl = updateUrl,
                        releaseNotes = releaseNotes,
                        forceUpdate = forceUpdate,
                        supportEmail = supportEmail,
                        enableGearBillboard = enableGearBillboard
                    )
                    onSave(updated)
                    Toast.makeText(context, "Saved & Pushed to Firebase Backend!", Toast.LENGTH_SHORT).show()
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
            ) {
                Text("Push to Firebase", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}
