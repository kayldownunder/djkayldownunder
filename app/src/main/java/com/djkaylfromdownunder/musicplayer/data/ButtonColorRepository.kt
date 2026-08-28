package com.djkaylfromdownunder.musicplayer.data

import android.content.Context

/**
 * Persists the single global color applied to every "shortcut" button across the app
 * (Settings' reorderable shortcut grid, plus the Playlist/Now Playing "Random skip all
 * albums" shortcut) - see ButtonColorViewModel, which is what screens actually read from.
 */
class ButtonColorRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_button_color_prefs", Context.MODE_PRIVATE)

    fun getColor(): Int = prefs.getInt(KEY, DEFAULT_COLOR_ARGB)

    fun setColor(argb: Int) {
        prefs.edit().putInt(KEY, argb).apply()
    }

    companion object {
        private const val KEY = "shortcut_button_color"

        // Orange - matches PRESET_COLORS' "Orange" swatch.
        const val DEFAULT_COLOR_ARGB: Int = 0xFFFFA726.toInt()
    }
}
