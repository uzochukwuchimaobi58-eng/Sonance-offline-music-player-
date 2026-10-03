package com.sonance.musicplayer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Stats Store: manages play counts, recent plays, and favorites.
 * Persists data inside SharedPreferences so counts are counted and retained
 * even when the app is swiped away from recents.
 */
class MusicStore(private val ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("music_store", Context.MODE_PRIVATE)
    private val repoPlayCountPrefs: SharedPreferences = ctx.getSharedPreferences("sonance_play_counts", Context.MODE_PRIVATE)
    private val repoLastPlayedPrefs: SharedPreferences = ctx.getSharedPreferences("sonance_last_played", Context.MODE_PRIVATE)

    fun playCounts(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()

        // 1. From music_store
        (sp.getString("counts", "") ?: "").split(",").forEach {
            val p = it.split(":")
            if (p.size == 2 && p[0].isNotBlank()) {
                val c = p[1].toIntOrNull() ?: 0
                if (c > 0) map[p[0]] = c
            }
        }

        // 2. From sonance_play_counts
        repoPlayCountPrefs.all.forEach { (k, v) ->
            if (v is Int && v > 0) {
                val existing = map[k] ?: 0
                map[k] = maxOf(existing, v)
            }
        }

        return map
    }

    fun playCountsLong(): Map<Long, Int> =
        playCounts().mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }.toMap()

    fun getCount(id: String): Int = playCounts()[id] ?: 0
    fun getCount(id: Long): Int = getCount(id.toString())

    fun incrementPlay(id: String): Map<String, Int> {
        val m = playCounts().toMutableMap()
        val current = m[id] ?: 0
        val newCount = current + 1
        m[id] = newCount
        sp.edit().putString("counts", m.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
        repoPlayCountPrefs.edit().putInt(id, newCount).apply()
        return m
    }

    fun incrementPlay(id: Long): Map<String, Int> = incrementPlay(id.toString())

    fun recent(): List<String> =
        (sp.getString("recent", "") ?: "").split(",").filter { it.isNotBlank() }

    fun recentLong(): List<Long> =
        recent().mapNotNull { it.toLongOrNull() }

    fun addRecent(id: String): List<String> {
        val l = (listOf(id) + recent().filter { it != id }).take(100)
        sp.edit().putString("recent", l.joinToString(",")).apply()
        repoLastPlayedPrefs.edit().putLong(id, System.currentTimeMillis()).apply()
        return l
    }

    fun addRecent(id: Long): List<String> = addRecent(id.toString())

    fun favorites(): Set<String> =
        (sp.getString("favs", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

    fun favoritesLong(): Set<Long> =
        favorites().mapNotNull { it.toLongOrNull() }.toSet()

    fun toggleFavorite(id: String): Set<String> {
        val f = favorites().toMutableSet()
        if (!f.add(id)) f.remove(id)
        sp.edit().putString("favs", f.joinToString(",")).apply()
        return f
    }

    fun toggleFavorite(id: Long): Set<String> = toggleFavorite(id.toString())

    /**
     * Emits now and whenever counts, recents, or favorites change
     * (even if the background service made the change).
     */
    fun changes(): Flow<Unit> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        sp.registerOnSharedPreferenceChangeListener(l)
        repoPlayCountPrefs.registerOnSharedPreferenceChangeListener(l)
        trySend(Unit)
        awaitClose {
            sp.unregisterOnSharedPreferenceChangeListener(l)
            repoPlayCountPrefs.unregisterOnSharedPreferenceChangeListener(l)
        }
    }
}

/**
 * Counts a play ONLY when a song plays all the way to the end:
 *  - it finishes and moves on to the next song by itself, or
 *  - it finishes and repeats, or
 *  - it is the last song in the queue and the queue ends.
 * Pressing Next/Previous or choosing another song mid-way does NOT count.
 * Also records every song that starts playing for "Recent play".
 */
class PlayStatsTracker(private val store: MusicStore) {
    private var currentId: String? = null
    private var counted = false

    fun onTrackStarted(trackId: String) {
        currentId = trackId
        counted = false
        store.addRecent(trackId)
    }

    fun onTrackFinished(trackId: String? = null) {
        val targetId = trackId ?: currentId
        if (!counted && targetId != null) {
            store.incrementPlay(targetId)
            counted = true
        }
    }

    fun resetCountedForRepeat() {
        counted = false
    }
}
