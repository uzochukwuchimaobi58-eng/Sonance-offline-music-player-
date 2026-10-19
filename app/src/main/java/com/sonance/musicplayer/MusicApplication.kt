package com.sonance.musicplayer
 
import android.app.Application
import android.content.Context
import android.util.Log

class MusicApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Clean up any corrupted Chromium Simple Cache directory structure from previous runs
        try {
            val httpCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache")
            val corruptedSubdir = java.io.File(httpCacheDir, "Code Cache")
            if (corruptedSubdir.exists()) {
                httpCacheDir.deleteRecursively()
            }
        } catch (_: Throwable) {}


        // Initialize and connect official Google Play Billing Library on app start
        try {
            com.sonance.musicplayer.billing.BillingManager.getInstance(this).startConnection()
        } catch (t: Throwable) {
            Log.w("BillingManager", "BillingManager startup connection error: ${t.message}")
        }

        // Initialize Google Mobile Ads SDK for production ad serving
        Thread {
            try {
                com.google.android.gms.ads.MobileAds.initialize(this) { initStatus ->
                    Log.d("AdMob", "Google Mobile Ads initialized successfully: $initStatus")
                }
            } catch (t: Throwable) {
                Log.w("AdMob", "Safe catch during MobileAds initialization: ${t.message}")
            }
        }.start()

        // Global uncaught exception handler to prevent silent native crashes and log detailed diagnostic traces
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("FATAL_CRASH", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val prefs = getSharedPreferences("sonance_crash_logs", Context.MODE_PRIVATE)
                val stackTrace = Log.getStackTraceString(throwable)
                prefs.edit()
                    .putString("last_crash", stackTrace)
                    .putLong("crash_time", System.currentTimeMillis())
                    .commit()
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        lateinit var instance: MusicApplication
            private set

        fun isEmulatorDevice(): Boolean {
            val finger = android.os.Build.FINGERPRINT.lowercase()
            val model = android.os.Build.MODEL.lowercase()
            val brand = android.os.Build.BRAND.lowercase()
            val device = android.os.Build.DEVICE.lowercase()
            val product = android.os.Build.PRODUCT.lowercase()
            val hardware = android.os.Build.HARDWARE.lowercase()
            return finger.startsWith("generic")
                || finger.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("emulator")
                || model.contains("android sdk built for x86")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || product.contains("sdk_gphone")
                || product.contains("google_sdk")
                || product.contains("sdk")
                || product.contains("vbox86p")
                || (brand.startsWith("generic") && device.startsWith("generic"))
        }
    }
}
