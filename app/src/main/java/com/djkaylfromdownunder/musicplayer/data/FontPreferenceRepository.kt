package com.djkaylfromdownunder.musicplayer.data

import android.content.Context

/**
 * The user's app-wide font choices from the Font Settings modal (Settings screen).
 * [fontColorArgb] of -1 means "no override" - keep the theme's normal text colors.
 * Text size is split into two independent scales: [albumTextSizeScale] drives everything
 * outside Settings (Library/Playlist/Player, i.e. "album" screens), while
 * [settingsTextSizeScale] only affects the Settings screen and its dialogs.
 */
data class FontPrefs(
    val fontFamilyName: String = "System Default",
    val albumTextSizeScale: Float = 1f,
    val settingsTextSizeScale: Float = 1f,
    val fontColorArgb: Int = -1
)

/** Persists [FontPrefs] - a plain SharedPreferences file, same pattern as ViewPreferenceRepository. */
class FontPreferenceRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_font_prefs", Context.MODE_PRIVATE)

    fun get(): FontPrefs {
        // Installs from before the Album/Settings split only ever wrote KEY_SIZE_LEGACY -
        // fall back to it for both new scales so upgrading doesn't silently reset an
        // already-customised size back to 100%.
        val legacyScale = prefs.getFloat(KEY_SIZE_LEGACY, 1f)
        return FontPrefs(
            fontFamilyName = prefs.getString(KEY_FAMILY, null) ?: "System Default",
            albumTextSizeScale = prefs.getFloat(KEY_ALBUM_SIZE, legacyScale),
            settingsTextSizeScale = prefs.getFloat(KEY_SETTINGS_SIZE, legacyScale),
            fontColorArgb = prefs.getInt(KEY_COLOR, -1)
        )
    }

    fun set(value: FontPrefs) {
        prefs.edit()
            .putString(KEY_FAMILY, value.fontFamilyName)
            .putFloat(KEY_ALBUM_SIZE, value.albumTextSizeScale)
            .putFloat(KEY_SETTINGS_SIZE, value.settingsTextSizeScale)
            .putInt(KEY_COLOR, value.fontColorArgb)
            .apply()
    }

    companion object {
        private const val KEY_FAMILY = "font_family"
        private const val KEY_SIZE_LEGACY = "font_size_scale"
        private const val KEY_ALBUM_SIZE = "album_text_size_scale"
        private const val KEY_SETTINGS_SIZE = "settings_text_size_scale"
        private const val KEY_COLOR = "font_color_argb"
    }
}
