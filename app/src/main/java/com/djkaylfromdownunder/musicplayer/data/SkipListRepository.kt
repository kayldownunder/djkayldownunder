package com.djkaylfromdownunder.musicplayer.data

import android.content.Context

/**
 * Stores which tracks the user has marked to be automatically skipped, per playlist
 * folder. A track's skip flag is remembered independently of the folder's other
 * metadata, so it survives re-scans of the music folder.
 */
class SkipListRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_skip_list", Context.MODE_PRIVATE)

    fun isSkipped(folderUri: String, trackUri: String): Boolean {
        val set = prefs.getStringSet(key(folderUri), emptySet()) ?: emptySet()
        return trackUri in set
    }

    fun setSkipped(folderUri: String, trackUri: String, skipped: Boolean) {
        // SharedPreferences string sets must be copied before mutating, never edited in place.
        val current = (prefs.getStringSet(key(folderUri), emptySet()) ?: emptySet()).toMutableSet()
        if (skipped) current.add(trackUri) else current.remove(trackUri)
        prefs.edit().putStringSet(key(folderUri), current).apply()
    }

    /** All skip entries, folder URI -> set of skipped track URIs. Used by the review screen. */
    fun allSkips(): Map<String, Set<String>> {
        return prefs.all.mapNotNull { (k, v) ->
            if (k.startsWith(PREFIX) && v is Set<*>) {
                k.removePrefix(PREFIX) to v.filterIsInstance<String>().toSet()
            } else null
        }.toMap()
    }

    private fun key(folderUri: String) = "$PREFIX$folderUri"

    companion object {
        private const val PREFIX = "skip:"
    }
}
