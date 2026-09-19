package com.sonance.musicplayer.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonance.musicplayer.data.FirebaseBackendService
import com.sonance.musicplayer.model.ThemeConfig
import com.sonance.musicplayer.model.UserProfile
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AuthAndAccountDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    userProfile: UserProfile,
    onSignInWithEmail: (email: String, password: String, (Result<UserProfile>) -> Unit) -> Unit,
    onSignUpWithEmail: (email: String, password: String, displayName: String, (Result<UserProfile>) -> Unit) -> Unit,
    onSignInWithGoogle: (email: String, displayName: String, (Result<UserProfile>) -> Unit) -> Unit,
    onSendPasswordReset: (email: String, (Result<Unit>) -> Unit) -> Unit,
    onSyncNow: ((Result<UserProfile>) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    theme: ThemeConfig
) {
    if (!isOpen) return
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Dialog Tab mode: 0 = Sign In, 1 = Create Account
    var tabIndex by remember { mutableStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Google one-tap quick picker sheet
    var showGoogleAccountPicker by remember { mutableStateOf(false) }
    var customGoogleEmail by remember { mutableStateOf("uzochukwuchimaobi58@gmail.com") }

    // State if user requested "Switch Account" while signed in
    var isSwitchingAccount by remember { mutableStateOf(false) }

    val isUserSignedIn = userProfile.isSignedIn && !isSwitchingAccount

    val consoleUrl = "https://console.firebase.google.com/project/${FirebaseBackendService.PROJECT_ID}/firestore/databases/${FirebaseBackendService.DATABASE_ID}/data"

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                isSwitchingAccount = false
                onClose()
            }
        },
        containerColor = theme.sidebarBg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("dialog_auth_and_account"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF58220).copy(alpha = 0.18f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isUserSignedIn) Icons.Default.AccountCircle else Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFFF58220),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUserSignedIn) "Account & Cloud Sync" else if (tabIndex == 0) "Sign In to Sonance" else "Create Sonance Account",
                        color = theme.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Connected to Firebase Console",
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(
                    onClick = {
                        isSwitchingAccount = false
                        onClose()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = theme.textSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Error & Success Feedback alerts
                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEF5350).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    color = theme.textPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = successMessage != null) {
                    successMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    color = theme.textPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                if (isUserSignedIn) {
                    // ==========================================
                    // SIGNED-IN PROFILE VIEW & FIREBASE CONSOLE
                    // ==========================================

                    // User Profile Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = theme.bgCanvas,
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Avatar circle with initials
                                Surface(
                                    shape = CircleShape,
                                    color = theme.accentColor,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = userProfile.initials,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = userProfile.displayName.ifBlank { "Sonance User" },
                                        color = theme.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = userProfile.email,
                                        color = theme.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Provider Badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (userProfile.isGoogle) Color(0xFF4285F4).copy(alpha = 0.2f) else theme.accentColor.copy(alpha = 0.2f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                0.5.dp,
                                                if (userProfile.isGoogle) Color(0xFF4285F4) else theme.accentColor
                                            )
                                        ) {
                                            Text(
                                                text = if (userProfile.isGoogle) "Google Account" else "Email Account",
                                                color = if (userProfile.isGoogle) Color(0xFF4285F4) else theme.accentColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Pro Status Badge
                                        if (userProfile.isPro) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFFFD700).copy(alpha = 0.25f),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFFD700))
                                            ) {
                                                Text(
                                                    text = "PRO VIP",
                                                    color = Color(0xFFFFD700),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Firebase Console Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E1E28),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF58220).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Firebase Console Live",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    text = "Firestore DB",
                                    color = Color(0xFFF58220),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Database ID: ${FirebaseBackendService.DATABASE_ID}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Document: users/${userProfile.email.replace(".", "_")}",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val lastSync = if (userProfile.lastSyncedAt > 0) {
                                    SimpleDateFormat("hh:mm a, MMM dd", Locale.getDefault()).format(Date(userProfile.lastSyncedAt))
                                } else "Recently"
                                Text(
                                    text = "Last Synced: $lastSync",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 10.sp
                                )
                            }

                            // Actions: Sync Now and Open Firebase Console
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isLoading = true
                                        errorMessage = null
                                        successMessage = null
                                        onSyncNow { res ->
                                            isLoading = false
                                            if (res.isSuccess) {
                                                successMessage = "Successfully synced profile to Firebase Console!"
                                                Toast.makeText(context, "Synced to Firebase Console!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                errorMessage = res.exceptionOrNull()?.message ?: "Sync failed"
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("btn_sync_firebase_now"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sync Now", fontSize = 12.sp)
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(consoleUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("btn_open_firebase_console"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF58220))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Console", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // Account Switching and Sign Out
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isSwitchingAccount = true
                                errorMessage = null
                                successMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_switch_account"),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textPrimary)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SwitchAccount,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = theme.accentColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Switch User", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                onSignOut()
                                Toast.makeText(context, "Signed out of Sonance", Toast.LENGTH_SHORT).show()
                                onClose()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_sign_out"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350).copy(alpha = 0.2f), contentColor = Color(0xFFEF5350))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFEF5350)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                } else {
                    // ==========================================
                    // AUTH FORM: SIGN IN & CREATE ACCOUNT
                    // ==========================================

                    // Switch tabs: Sign In vs Create Account
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.bgCanvas)
                            .padding(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    tabIndex = 0
                                    errorMessage = null
                                }
                                .testTag("tab_auth_sign_in"),
                            color = if (tabIndex == 0) theme.accentColor else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Sign In (Login)",
                                color = if (tabIndex == 0) Color.White else theme.textSecondary,
                                fontWeight = if (tabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    tabIndex = 1
                                    errorMessage = null
                                }
                                .testTag("tab_auth_create_account"),
                            color = if (tabIndex == 1) theme.accentColor else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Create Account",
                                color = if (tabIndex == 1) Color.White else theme.textSecondary,
                                fontWeight = if (tabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    // GOOGLE SIGN IN BUTTON
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isLoading) {
                                showGoogleAccountPicker = true
                            }
                            .testTag("btn_google_sign_in"),
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDADCE0))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Google G Icon Box
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 17.sp,
                                        color = Color(0xFF4285F4)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (tabIndex == 0) "Continue with Google" else "Sign up with Google",
                                color = Color(0xFF3C4043),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // OR divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = theme.textSecondary.copy(alpha = 0.2f)
                        )
                        Text(
                            text = "  OR WITH EMAIL  ",
                            color = theme.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = theme.textSecondary.copy(alpha = 0.2f)
                        )
                    }

                    // Display Name (Only in Register / Sign Up Mode)
                    AnimatedVisibility(visible = tabIndex == 1) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Your Full Name") },
                            placeholder = { Text("e.g. Alex Hunter") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = theme.accentColor)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_display_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.accentColor,
                                unfocusedBorderColor = theme.cardBorder,
                                focusedTextColor = theme.textPrimary,
                                unfocusedTextColor = theme.textPrimary,
                                focusedLabelColor = theme.accentColor
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                    }

                    // Email input
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        placeholder = { Text("name@example.com") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = theme.accentColor)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_email"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.accentColor,
                            unfocusedBorderColor = theme.cardBorder,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedLabelColor = theme.accentColor
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )

                    // Password input
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        placeholder = { Text("At least 6 characters") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = theme.accentColor)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                    tint = theme.textSecondary
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_password"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.accentColor,
                            unfocusedBorderColor = theme.cardBorder,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedLabelColor = theme.accentColor
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (tabIndex == 1) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = { focusManager.clearFocus() }
                        )
                    )

                    // Confirm Password (Only in Register mode)
                    AnimatedVisibility(visible = tabIndex == 1) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            placeholder = { Text("Re-enter password") },
                            leadingIcon = {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = theme.accentColor)
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_confirm_password"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.accentColor,
                                unfocusedBorderColor = theme.cardBorder,
                                focusedTextColor = theme.textPrimary,
                                unfocusedTextColor = theme.textPrimary,
                                focusedLabelColor = theme.accentColor
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }

                    // Forgot Password (in Login mode)
                    if (tabIndex == 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Forgot password?",
                                color = theme.accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable {
                                        if (email.isBlank() || !email.contains("@")) {
                                            errorMessage = "Please enter your email above to reset password."
                                        } else {
                                            isLoading = true
                                            errorMessage = null
                                            onSendPasswordReset(email) { res ->
                                                isLoading = false
                                                if (res.isSuccess) {
                                                    successMessage = "Password reset email sent to $email. Please check your inbox!"
                                                    Toast.makeText(context, "Reset email sent to $email", Toast.LENGTH_LONG).show()
                                                } else {
                                                    errorMessage = res.exceptionOrNull()?.message ?: "Failed to send reset email."
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 2.dp)
                                    .testTag("btn_forgot_password")
                            )
                        }
                    }

                    // Submit Action Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            errorMessage = null
                            successMessage = null

                            if (email.isBlank() || !email.contains("@")) {
                                errorMessage = "Please enter a valid email address."
                                return@Button
                            }
                            if (password.length < 6) {
                                errorMessage = "Password must be at least 6 characters long."
                                return@Button
                            }

                            if (tabIndex == 1 && password != confirmPassword) {
                                errorMessage = "Passwords do not match."
                                return@Button
                            }

                            isLoading = true

                            if (tabIndex == 0) {
                                // SIGN IN
                                onSignInWithEmail(email, password) { res ->
                                    isLoading = false
                                    if (res.isSuccess) {
                                        val user = res.getOrNull()!!
                                        Toast.makeText(context, "Welcome back, ${user.displayName.ifBlank { user.email }}!", Toast.LENGTH_SHORT).show()
                                        onClose()
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Sign in failed"
                                    }
                                }
                            } else {
                                // CREATE ACCOUNT
                                onSignUpWithEmail(email, password, displayName) { res ->
                                    isLoading = false
                                    if (res.isSuccess) {
                                        val user = res.getOrNull()!!
                                        Toast.makeText(context, "Account created! Connected to Firebase Console.", Toast.LENGTH_SHORT).show()
                                        onClose()
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Registration failed"
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_submit_auth"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (tabIndex == 0) "Sign In with Email" else "Create Account & Connect",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Offline Guarantee note
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = theme.cardBorder.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = theme.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Guest mode works 100% offline. Account is used to sync VIP & playlists to Firebase Console.",
                                color = theme.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )

    // Google Account Picker Dialog
    if (showGoogleAccountPicker) {
        AlertDialog(
            onDismissRequest = { showGoogleAccountPicker = false },
            containerColor = theme.sidebarBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("G", fontWeight = FontWeight.Bold, color = Color(0xFF4285F4), fontSize = 18.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Choose Google Account",
                        color = theme.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sign in with your Google account to link your music library and connect to Firebase Console:",
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )

                    // Suggest the device owner email or default
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = theme.bgCanvas,
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showGoogleAccountPicker = false
                                isLoading = true
                                onSignInWithGoogle(
                                    customGoogleEmail,
                                    customGoogleEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                                ) { res ->
                                    isLoading = false
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Signed in with Google ($customGoogleEmail)", Toast.LENGTH_SHORT).show()
                                        onClose()
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Google sign in error"
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF4285F4),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = customGoogleEmail.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = customGoogleEmail.substringBefore("@"),
                                    color = theme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = customGoogleEmail,
                                    color = theme.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Custom Google Email input if user wants another account
                    OutlinedTextField(
                        value = customGoogleEmail,
                        onValueChange = { customGoogleEmail = it },
                        label = { Text("Or enter other Google email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.accentColor,
                            unfocusedBorderColor = theme.cardBorder,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customGoogleEmail.isNotBlank() && customGoogleEmail.contains("@")) {
                            showGoogleAccountPicker = false
                            isLoading = true
                            onSignInWithGoogle(
                                customGoogleEmail,
                                customGoogleEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                            ) { res ->
                                isLoading = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Signed in with Google ($customGoogleEmail)", Toast.LENGTH_SHORT).show()
                                    onClose()
                                } else {
                                    errorMessage = res.exceptionOrNull()?.message ?: "Google sign in error"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentColor)
                ) {
                    Text("Sign In with Google", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleAccountPicker = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
}
