package com.djkaylfromdownunder.musicplayer.data

import android.content.Context

/**
 * The user's app-wide font choices from the Font Settings modal (Settings screen).
 * [fontColorArgb] of -1 means "no override" - keep the theme's normal text colors.
 */
data class FontPrefs(
    val fontFamilyName: String = "System Default",
    val fontSizeScale: Float = 1f,
    val fontColorArgb: Int = -1
)

/** Persists [FontPrefs] - a plain SharedPreferences file, same pattern as ViewPreferenceRepository. */
class FontPreferenceRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_font_prefs", Context.MODE_PRIVATE)

    fun get(): FontPrefs = FontPrefs(
        fontFamilyName = prefs.getString(KEY_FAMILY, null) ?: "System Default",
        fontSizeScale = prefs.getFloat(KEY_SIZE, 1f),
        fontColorArgb = prefs.getInt(KEY_COLOR, -1)
    )

    fun set(value: FontPrefs) {
        prefs.edit()
            .putString(KEY_FAMILY, value.fontFamilyName)
            .putFloat(KEY_SIZE, value.fontSizeScale)
            .putInt(KEY_COLOR, value.fontColorArgb)
            .apply()
    }

    companion object {
        private const val KEY_FAMILY = "font_family"
        private const val KEY_SIZE = "font_size_scale"
        private const val KEY_COLOR = "font_color_argb"
    }
}
