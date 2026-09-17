package com.sonance.musicplayer.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.util.Log
import java.io.File

object AppSecurityManager {

    private const val TAG = "SonanceSecurity"

    data class SecurityAudit(
        val isRooted: Boolean,
        val isDebuggable: Boolean,
        val isEmulator: Boolean,
        val isTampered: Boolean,
        val integrityStatus: String
    )

    fun performAudit(context: Context): SecurityAudit {
        val rooted = checkRoot()
        val debuggable = checkDebuggable(context)
        val emulator = checkEmulator()
        val tampered = checkTampering(context)

        val status = when {
            tampered -> "Warning: Modified Package Signature"
            rooted -> "Rooted / Superuser Detected"
            debuggable -> "Debug Mode Active"
            emulator -> "Emulator Environment"
            else -> "Secure & Verified Device"
        }

        return SecurityAudit(
            isRooted = rooted,
            isDebuggable = debuggable,
            isEmulator = emulator,
            isTampered = tampered,
            integrityStatus = status
        )
    }

    private fun checkRoot(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    private fun checkDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 ||
                Debug.isDebuggerConnected()
    }

    private fun checkEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.PRODUCT.contains("sdk_google") ||
                Build.PRODUCT.contains("google_sdk") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("sdk_x86") ||
                Build.PRODUCT.contains("vbox86p") ||
                Build.PRODUCT.contains("emulator") ||
                Build.PRODUCT.contains("simulator")
    }

    private fun checkTampering(context: Context): Boolean {
        // Verify package name hasn't been recompiled to unauthorized name
        val expected = "com.sonance.musicplayer"
        return context.packageName != expected
    }

    /**
     * Crashlytics / Diagnostic Exception Logging
     */
    fun logCrash(tag: String, error: Throwable) {
        Log.e(TAG, "[$tag] Logged Crash Event: ${error.localizedMessage}", error)
    }

    fun logBreadcrumb(breadcrumb: String) {
        Log.i(TAG, "[Breadcrumb] $breadcrumb")
    }
}
