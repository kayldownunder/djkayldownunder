package com.djkaylfromdownunder.musicplayer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")

/** The two screens that can each have their own independently-chosen background image. */
enum class BackgroundTarget { LIBRARY, SETTINGS }

/**
 * A background is either an image or a flat color, never both - setting one clears the
 * other. Both null falls back to the theme's plain background color.
 */
data class BackgroundPrefs(val selectedImageUri: String? = null, val selectedColorArgb: Int? = null)

/**
 * Stores which background (an image file inside the music folder's "background"
 * subfolder - see BackgroundImageRepository - or a flat color) is selected for the
 * Library screen and the Settings screen, independently of each other.
 */
class ThemeRepository(private val context: Context) {

    private object Keys {
        val LIBRARY_IMAGE = stringPreferencesKey("bg_library_image")
        val SETTINGS_IMAGE = stringPreferencesKey("bg_settings_image")
        val LIBRARY_COLOR = intPreferencesKey("bg_library_color")
        val SETTINGS_COLOR = intPreferencesKey("bg_settings_color")
    }

    fun flowFor(target: BackgroundTarget): Flow<BackgroundPrefs> =
        context.themeDataStore.data.map { prefs ->
            BackgroundPrefs(
                selectedImageUri = prefs[imageKeyFor(target)],
                selectedColorArgb = prefs[colorKeyFor(target)]
            )
        }

    suspend fun setImage(target: BackgroundTarget, uri: String) {
        context.themeDataStore.edit { prefs ->
            prefs[imageKeyFor(target)] = uri
            prefs.remove(colorKeyFor(target))
        }
    }

    suspend fun setColor(target: BackgroundTarget, argb: Int) {
        context.themeDataStore.edit { prefs ->
            prefs[colorKeyFor(target)] = argb
            prefs.remove(imageKeyFor(target))
        }
    }

    suspend fun clearImage(target: BackgroundTarget) {
        context.themeDataStore.edit { prefs ->
            prefs.remove(imageKeyFor(target))
            prefs.remove(colorKeyFor(target))
        }
    }

    private fun imageKeyFor(target: BackgroundTarget): Preferences.Key<String> = when (target) {
        BackgroundTarget.LIBRARY -> Keys.LIBRARY_IMAGE
        BackgroundTarget.SETTINGS -> Keys.SETTINGS_IMAGE
    }

    private fun colorKeyFor(target: BackgroundTarget): Preferences.Key<Int> = when (target) {
        BackgroundTarget.LIBRARY -> Keys.LIBRARY_COLOR
        BackgroundTarget.SETTINGS -> Keys.SETTINGS_COLOR
    }
}
