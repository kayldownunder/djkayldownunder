package com.djkaylfromdownunder.musicplayer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")

/** The two screens that can each have their own independently-chosen background image. */
enum class BackgroundTarget { LIBRARY, SETTINGS }

data class BackgroundPrefs(val selectedImageUri: String? = null)

/**
 * Stores which background image (a file inside the music folder's "background"
 * subfolder - see BackgroundImageRepository) is selected for the Library screen and the
 * Settings screen, independently of each other.
 */
class ThemeRepository(private val context: Context) {

    private object Keys {
        val LIBRARY_IMAGE = stringPreferencesKey("bg_library_image")
        val SETTINGS_IMAGE = stringPreferencesKey("bg_settings_image")
    }

    fun flowFor(target: BackgroundTarget): Flow<BackgroundPrefs> =
        context.themeDataStore.data.map { prefs ->
            BackgroundPrefs(selectedImageUri = prefs[keyFor(target)])
        }

    suspend fun setImage(target: BackgroundTarget, uri: String) {
        context.themeDataStore.edit { prefs -> prefs[keyFor(target)] = uri }
    }

    suspend fun clearImage(target: BackgroundTarget) {
        context.themeDataStore.edit { prefs -> prefs.remove(keyFor(target)) }
    }

    private fun keyFor(target: BackgroundTarget): Preferences.Key<String> = when (target) {
        BackgroundTarget.LIBRARY -> Keys.LIBRARY_IMAGE
        BackgroundTarget.SETTINGS -> Keys.SETTINGS_IMAGE
    }
}
