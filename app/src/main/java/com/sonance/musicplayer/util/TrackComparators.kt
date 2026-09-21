package com.sonance.musicplayer.util

import com.sonance.musicplayer.model.Track
import java.util.Locale

object TrackComparators {

    /**
     * Categorizes a track title into an Alphabet bar bucket:
     * '#' for numbers, symbols, and non-ASCII/non-Latin starting chars.
     * 'A'..'Z' for standard alphabet characters.
     */
    fun getTrackIndexKey(title: String): String {
        val trimmed = title.trim()
        val firstChar = trimmed.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: return "#"
        return if (firstChar in 'A'..'Z') firstChar.toString() else "#"
    }

    /**
     * Cleans a title for natural sorting:
     * Strips leading brackets, punctuation, quotes, underscores, or spaces so titles like
     * "[Remix] Song", "_track", or "\"Hello\"" sort with their primary alphanumeric character.
     */
    private fun cleanTitle(title: String): String {
        val trimmed = title.trim()
        val firstAlphaIdx = trimmed.indexOfFirst { it.isLetterOrDigit() }
        val effective = if (firstAlphaIdx > 0) trimmed.substring(firstAlphaIdx) else trimmed
        return effective.lowercase(Locale.ROOT)
    }

    /**
     * Primary A-Z sorting comparator for the Music Library.
     * Orders '#' (symbols/numbers) first to match the top '#' on AlphabetFastScroller,
     * followed by 'A' through 'Z' in natural case-insensitive order.
     */
    val TitleComparator = Comparator<Track> { t1, t2 ->
        val k1 = getTrackIndexKey(t1.title)
        val k2 = getTrackIndexKey(t2.title)

        val isHash1 = k1 == "#"
        val isHash2 = k2 == "#"

        if (isHash1 && !isHash2) {
            -1
        } else if (!isHash1 && isHash2) {
            1
        } else {
            val c1 = cleanTitle(t1.title)
            val c2 = cleanTitle(t2.title)
            val cmp = c1.compareTo(c2)
            if (cmp != 0) cmp else t1.artist.trim().compareTo(t2.artist.trim(), ignoreCase = true)
        }
    }

    /**
     * Artist sorting comparator.
     */
    val ArtistComparator = Comparator<Track> { t1, t2 ->
        val a1 = t1.artist.trim().lowercase(Locale.ROOT)
        val a2 = t2.artist.trim().lowercase(Locale.ROOT)
        val cmp = a1.compareTo(a2)
        if (cmp != 0) cmp else TitleComparator.compare(t1, t2)
    }
}
