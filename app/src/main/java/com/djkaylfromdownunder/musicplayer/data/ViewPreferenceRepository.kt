package com.djkaylfromdownunder.musicplayer.data

import android.content.Context

// Enum constant names are persisted verbatim in SharedPreferences (see setViewMode below) -
// MEDIUM and SMALL keep their original names for that reason even though the picker labels
// them "Large" and "Small" respectively; MEDIUM_GRID is the new tier added between them,
// shown to the user as "Medium".
enum class PlaylistViewMode {
    MEDIUM, MEDIUM_GRID, SMALL, LIST;

    /** Grid column count for this mode - meaningless for LIST, which doesn't use a grid. */
    val gridColumns: Int
        get() = when (this) {
            MEDIUM -> 2
            MEDIUM_GRID -> 3
            SMALL -> 4
            LIST -> 2
        }

    /** Denser badges/icons/text for the higher-density grid tiers. */
    val isCompact: Boolean
        get() = this == MEDIUM_GRID || this == SMALL
}

class ViewPreferenceRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_view_prefs", Context.MODE_PRIVATE)

    fun getViewMode(): PlaylistViewMode {
        val name = prefs.getString(KEY, null) ?: return PlaylistViewMode.MEDIUM
        return runCatching { PlaylistViewMode.valueOf(name) }.getOrDefault(PlaylistViewMode.MEDIUM)
    }

    fun setViewMode(mode: PlaylistViewMode) {
        prefs.edit().putString(KEY, mode.name).apply()
    }

    companion object {
        private const val KEY = "playlist_view_mode"
    }
}
