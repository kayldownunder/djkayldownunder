package com.djkaylfromdownunder.musicplayer.data

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "djkayl_audio_normalization"
private const val KEY_ENABLED = "enabled"

/**
 * Whether real-time audio normalization (see AudioNormalizationProcessor) is switched on -
 * toggled from the "Audio Normalisation" button on Settings. Read directly by
 * MusicPlaybackService (a separate lifecycle from the Settings screen's ViewModel) via
 * [registerChangeListener] so flipping the toggle takes effect on already-playing audio
 * without needing to restart playback.
 */
class AudioNormalizationRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Invokes [onChange] with the new value whenever [setEnabled] is called (from any instance). */
    fun registerChangeListener(onChange: (Boolean) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == KEY_ENABLED) onChange(sharedPrefs.getBoolean(KEY_ENABLED, false))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
