package com.djkaylfromdownunder.musicplayer.data

import android.content.Context

/** The Text Size slider's range and default - see [FontPrefs.albumTextSizeSp]/[FontPrefs.settingsTextSizeSp]. */
const val MIN_TEXT_SIZE_SP = 16f
const val MAX_TEXT_SIZE_SP = 26f

/**
 * The user's font choices from the Font Settings modal (Settings screen) - fully independent
 * per area: [albumFontFamilyName]/[albumTextSizeSp]/[albumFontColorArgb] drive everything
 * outside Settings (Library/Playlist/Player, i.e. "album" screens), while
 * [settingsFontFamilyName]/[settingsTextSizeSp]/[settingsFontColorArgb] only affect the
 * Settings screen and its dialogs. Each `*TextSizeSp` is the literal size (in sp,
 * [MIN_TEXT_SIZE_SP]..[MAX_TEXT_SIZE_SP]) of that area's base body text - every other text
 * style in that area is scaled proportionally around it (see Theme.kt's buildTypography), so
 * headlines stay bigger than body text at every setting. A `*ColorArgb` of -1 means "no
 * override" - keep the theme's normal text color for that area.
 */
data class FontPrefs(
    val albumFontFamilyName: String = "System Default",
    val albumTextSizeSp: Float = MIN_TEXT_SIZE_SP,
    val albumFontColorArgb: Int = -1,
    val settingsFontFamilyName: String = "System Default",
    val settingsTextSizeSp: Float = MIN_TEXT_SIZE_SP,
    val settingsFontColorArgb: Int = -1
)

/** Persists [FontPrefs] - a plain SharedPreferences file, same pattern as ViewPreferenceRepository. */
class FontPreferenceRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_font_prefs", Context.MODE_PRIVATE)

    fun get(): FontPrefs {
        // Installs from before the Album/Settings split only ever wrote the single-scope
        // legacy keys - fall back to them for both new scopes so upgrading doesn't silently
        // reset an already-customised family/color back to defaults.
        val legacyFamily = prefs.getString(KEY_FAMILY_LEGACY, null) ?: "System Default"
        val legacyColor = prefs.getInt(KEY_COLOR_LEGACY, -1)
        return FontPrefs(
            albumFontFamilyName = prefs.getString(KEY_ALBUM_FAMILY, null) ?: legacyFamily,
            albumTextSizeSp = readSp(KEY_ALBUM_SIZE_SP, KEY_ALBUM_SIZE_SCALE_LEGACY),
            albumFontColorArgb = if (prefs.contains(KEY_ALBUM_COLOR)) prefs.getInt(KEY_ALBUM_COLOR, -1) else legacyColor,
            settingsFontFamilyName = prefs.getString(KEY_SETTINGS_FAMILY, null) ?: legacyFamily,
            settingsTextSizeSp = readSp(KEY_SETTINGS_SIZE_SP, KEY_SETTINGS_SIZE_SCALE_LEGACY),
            settingsFontColorArgb = if (prefs.contains(KEY_SETTINGS_COLOR)) prefs.getInt(KEY_SETTINGS_COLOR, -1) else legacyColor
        )
    }

    /**
     * Reads a text size in sp, preferring [spKey] (the current format) but falling back
     * through the two size formats this app has used before it: a per-scope 0.75x-1.5x
     * scale (from the Album/Settings split, before sizes were absolute sp), then the
     * original single 0.75x-1.5x scale shared by the whole app. Either scale is
     * reinterpreted against [MIN_TEXT_SIZE_SP] so an old "bigger text" choice still comes
     * out bigger under the new sp range, rather than resetting to default.
     */
    private fun readSp(spKey: String, legacyScaleKey: String): Float {
        if (prefs.contains(spKey)) return prefs.getFloat(spKey, MIN_TEXT_SIZE_SP)
        val scale = prefs.getFloat(legacyScaleKey, prefs.getFloat(KEY_SIZE_LEGACY, 1f))
        return (scale * MIN_TEXT_SIZE_SP).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)
    }

    fun set(value: FontPrefs) {
        prefs.edit()
            .putString(KEY_ALBUM_FAMILY, value.albumFontFamilyName)
            .putFloat(KEY_ALBUM_SIZE_SP, value.albumTextSizeSp)
            .putInt(KEY_ALBUM_COLOR, value.albumFontColorArgb)
            .putString(KEY_SETTINGS_FAMILY, value.settingsFontFamilyName)
            .putFloat(KEY_SETTINGS_SIZE_SP, value.settingsTextSizeSp)
            .putInt(KEY_SETTINGS_COLOR, value.settingsFontColorArgb)
            .apply()
    }

    companion object {
        private const val KEY_FAMILY_LEGACY = "font_family"
        private const val KEY_SIZE_LEGACY = "font_size_scale"
        private const val KEY_COLOR_LEGACY = "font_color_argb"
        private const val KEY_ALBUM_FAMILY = "album_font_family"
        private const val KEY_ALBUM_SIZE_SCALE_LEGACY = "album_text_size_scale"
        private const val KEY_ALBUM_SIZE_SP = "album_text_size_sp"
        private const val KEY_ALBUM_COLOR = "album_font_color_argb"
        private const val KEY_SETTINGS_FAMILY = "settings_font_family"
        private const val KEY_SETTINGS_SIZE_SCALE_LEGACY = "settings_text_size_scale"
        private const val KEY_SETTINGS_SIZE_SP = "settings_text_size_sp"
        private const val KEY_SETTINGS_COLOR = "settings_font_color_argb"
    }
}
