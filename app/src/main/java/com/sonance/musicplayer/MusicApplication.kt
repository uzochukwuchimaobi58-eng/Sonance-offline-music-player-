package com.sonance.musicplayer
 
import android.app.Application
import android.content.Context
import android.util.Log

class MusicApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Pre-create WebView and Chromium cache folders to prevent simple_file_enumerator missing directory logs
        try {
            val jsDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            val wasmDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!jsDir.exists()) jsDir.mkdirs()
            if (!wasmDir.exists()) wasmDir.mkdirs()
        } catch (_: Throwable) {}

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
    }
}
