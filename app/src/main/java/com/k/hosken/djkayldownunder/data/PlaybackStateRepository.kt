package com.k.hosken.djkayldownunder.data

import android.content.Context

data class PlaybackResumeState(val trackUri: String, val positionMs: Long)

/**
 * Remembers, per playlist folder, which track was playing and how far into it - so
 * reopening a playlist later resumes from there instead of always starting at track 1.
 * Keyed by track URI rather than index so this stays correct even if the skip list
 * changes which tracks are included between sessions.
 */
class PlaybackStateRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_playback_state", Context.MODE_PRIVATE)

    fun save(folderUri: String, trackUri: String, positionMs: Long) {
        prefs.edit()
            .putString(trackKey(folderUri), trackUri)
            .putLong(positionKey(folderUri), positionMs)
            .apply()
    }

    fun load(folderUri: String): PlaybackResumeState? {
        val trackUri = prefs.getString(trackKey(folderUri), null) ?: return null
        val positionMs = prefs.getLong(positionKey(folderUri), 0L)
        return PlaybackResumeState(trackUri, positionMs)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun trackKey(folderUri: String) = "track:$folderUri"
    private fun positionKey(folderUri: String) = "position:$folderUri"
}
