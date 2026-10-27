package com.sonance.musicplayer.rating

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Manages Google Play In-App Review API flows and provides fallback to direct Play Store rating.
 */
class InAppRatingManager private constructor(private val context: Context) {

    private val reviewManager: ReviewManager = ReviewManagerFactory.create(context)
    private var preloadedReviewInfo: ReviewInfo? = null

    init {
        preloadReviewInfo()
    }

    fun preloadReviewInfo() {
        try {
            reviewManager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    preloadedReviewInfo = task.result
                    Log.d(TAG, "Google Play In-App ReviewInfo preloaded successfully")
                } else {
                    Log.w(TAG, "Failed to preload Google ReviewInfo: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error initiating review flow preload", e)
        }
    }

    /**
     * Launch the Google Play In-App Review flow if available, or fall back to the Play Store listing.
     */
    fun launchReviewFlow(activity: Activity, onCompleted: (() -> Unit)? = null) {
        val reviewInfo = preloadedReviewInfo
        if (reviewInfo != null) {
            try {
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    preloadedReviewInfo = null
                    preloadReviewInfo()
                    onCompleted?.invoke()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error launching cached review flow, opening Play Store directly", e)
                openPlayStoreListing(activity)
                onCompleted?.invoke()
            }
        } else {
            try {
                reviewManager.requestReviewFlow().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val info = task.result
                        val flow = reviewManager.launchReviewFlow(activity, info)
                        flow.addOnCompleteListener {
                            onCompleted?.invoke()
                        }
                    } else {
                        Log.w(TAG, "requestReviewFlow unsuccessful, falling back to direct Play Store listing")
                        openPlayStoreListing(activity)
                        onCompleted?.invoke()
                    }
                }
            } catch (e: Exception) {
                openPlayStoreListing(activity)
                onCompleted?.invoke()
            }
        }
    }

    /**
     * Directly opens the app's Google Play Store listing for guaranteed 5-star rating.
     */
    fun openPlayStoreListing(context: Context) {
        val packageName = context.packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            context.startActivity(webIntent)
        }
    }

    private fun getPrefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasRated(): Boolean = getPrefs().getBoolean(KEY_HAS_RATED, false)

    fun getLastPromptTime(): Long = getPrefs().getLong(KEY_LAST_PROMPT_TIME, 0L)

    fun getStudioExitCount(): Int = getPrefs().getInt(KEY_STUDIO_EXIT_COUNT, 0)

    fun recordPromptShown() {
        getPrefs().edit().putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis()).apply()
    }

    fun recordUserDismissed() {
        // Postpone for another month (30 days)
        getPrefs().edit().putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis()).apply()
    }

    fun recordUserRated() {
        // Mark as rated so user is never automatically prompted again
        getPrefs().edit()
            .putBoolean(KEY_HAS_RATED, true)
            .putLong(KEY_LAST_PROMPT_TIME, System.currentTimeMillis())
            .apply()
    }

    /**
     * Checks if the rating dialog is eligible to be shown.
     * Rules:
     * 1. Never auto-prompt if user already rated.
     * 2. Limit auto-prompts to at most ONCE PER MONTH (30 days interval).
     * 3. Ensure user has actually explored the studio tools (at least 2 studio tool sessions).
     */
    fun shouldPromptOnStudioExit(): Boolean {
        if (hasRated()) return false
        val now = System.currentTimeMillis()
        val lastPrompt = getLastPromptTime()
        // Must be at least 30 days (~1 month) since the last prompt
        if (now - lastPrompt < THIRTY_DAYS_MS) {
            return false
        }
        // User should have explored at least 2 studio tool sessions
        if (getStudioExitCount() < 2) {
            return false
        }
        return true
    }

    /**
     * Called whenever user exits any Studio Tool. Increments studio session count and
     * returns true if the once-a-month rating prompt should be displayed immediately.
     */
    fun onStudioToolExited(): Boolean {
        val currentCount = getStudioExitCount()
        getPrefs().edit().putInt(KEY_STUDIO_EXIT_COUNT, currentCount + 1).apply()

        if (shouldPromptOnStudioExit()) {
            recordPromptShown()
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "InAppRatingManager"
        private const val PREFS_NAME = "sonance_rating_prefs"
        private const val KEY_HAS_RATED = "has_rated"
        private const val KEY_LAST_PROMPT_TIME = "last_prompt_time"
        private const val KEY_STUDIO_EXIT_COUNT = "studio_exit_count"

        // 30 Days in milliseconds (~1 month throttle to prevent annoying the user)
        const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L

        @Volatile
        private var instance: InAppRatingManager? = null

        fun getInstance(context: Context): InAppRatingManager {
            return instance ?: synchronized(this) {
                instance ?: InAppRatingManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
