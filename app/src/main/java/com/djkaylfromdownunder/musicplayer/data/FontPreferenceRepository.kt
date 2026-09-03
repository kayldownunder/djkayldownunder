package com.djkaylfromdownunder.musicplayer.data

import android.content.Context

/**
 * The user's font choices from the Font Settings modal (Settings screen) - fully independent
 * per area: [albumFontFamilyName]/[albumTextSizeScale]/[albumFontColorArgb] drive everything
 * outside Settings (Library/Playlist/Player, i.e. "album" screens), while
 * [settingsFontFamilyName]/[settingsTextSizeScale]/[settingsFontColorArgb] only affect the
 * Settings screen and its dialogs. A `*ColorArgb` of -1 means "no override" - keep the
 * theme's normal text color for that area.
 */
data class FontPrefs(
    val albumFontFamilyName: String = "System Default",
    val albumTextSizeScale: Float = 1f,
    val albumFontColorArgb: Int = -1,
    val settingsFontFamilyName: String = "System Default",
    val settingsTextSizeScale: Float = 1f,
    val settingsFontColorArgb: Int = -1
)

/** Persists [FontPrefs] - a plain SharedPreferences file, same pattern as ViewPreferenceRepository. */
class FontPreferenceRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_font_prefs", Context.MODE_PRIVATE)

    fun get(): FontPrefs {
        // Installs from before the Album/Settings split only ever wrote the legacy keys -
        // fall back to them for both new scopes so upgrading doesn't silently reset an
        // already-customised family/size/color back to defaults.
        val legacyFamily = prefs.getString(KEY_FAMILY_LEGACY, null) ?: "System Default"
        val legacyScale = prefs.getFloat(KEY_SIZE_LEGACY, 1f)
        val legacyColor = prefs.getInt(KEY_COLOR_LEGACY, -1)
        return FontPrefs(
            albumFontFamilyName = prefs.getString(KEY_ALBUM_FAMILY, null) ?: legacyFamily,
            albumTextSizeScale = prefs.getFloat(KEY_ALBUM_SIZE, legacyScale),
            albumFontColorArgb = if (prefs.contains(KEY_ALBUM_COLOR)) prefs.getInt(KEY_ALBUM_COLOR, -1) else legacyColor,
            settingsFontFamilyName = prefs.getString(KEY_SETTINGS_FAMILY, null) ?: legacyFamily,
            settingsTextSizeScale = prefs.getFloat(KEY_SETTINGS_SIZE, legacyScale),
            settingsFontColorArgb = if (prefs.contains(KEY_SETTINGS_COLOR)) prefs.getInt(KEY_SETTINGS_COLOR, -1) else legacyColor
        )
    }

    fun set(value: FontPrefs) {
        prefs.edit()
            .putString(KEY_ALBUM_FAMILY, value.albumFontFamilyName)
            .putFloat(KEY_ALBUM_SIZE, value.albumTextSizeScale)
            .putInt(KEY_ALBUM_COLOR, value.albumFontColorArgb)
            .putString(KEY_SETTINGS_FAMILY, value.settingsFontFamilyName)
            .putFloat(KEY_SETTINGS_SIZE, value.settingsTextSizeScale)
            .putInt(KEY_SETTINGS_COLOR, value.settingsFontColorArgb)
            .apply()
    }

    companion object {
        private const val KEY_FAMILY_LEGACY = "font_family"
        private const val KEY_SIZE_LEGACY = "font_size_scale"
        private const val KEY_COLOR_LEGACY = "font_color_argb"
        private const val KEY_ALBUM_FAMILY = "album_font_family"
        private const val KEY_ALBUM_SIZE = "album_text_size_scale"
        private const val KEY_ALBUM_COLOR = "album_font_color_argb"
        private const val KEY_SETTINGS_FAMILY = "settings_font_family"
        private const val KEY_SETTINGS_SIZE = "settings_text_size_scale"
        private const val KEY_SETTINGS_COLOR = "settings_font_color_argb"
    }
}
