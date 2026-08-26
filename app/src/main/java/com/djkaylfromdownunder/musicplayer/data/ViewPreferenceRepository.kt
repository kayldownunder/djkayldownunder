package com.djkaylfromdownunder.musicplayer.data

import android.content.Context

enum class PlaylistViewMode { LARGE, MEDIUM, SMALL, LIST }

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
