package com.djkaylfromdownunder.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.djkaylfromdownunder.musicplayer.data.FontPreferenceRepository
import com.djkaylfromdownunder.musicplayer.data.FontPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared app-wide font state - read by DJKaylTheme (MainActivity) to build Typography, and
 * edited from FontSettingsDialog (Settings). The dialog edits a local draft and only calls
 * [save] once, on close (see its auto-save-on-back), so the whole app doesn't re-theme on
 * every slider tick while the modal is open - only the modal's own preview does that.
 */
class FontPreferencesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FontPreferenceRepository(application)

    private val _fontPrefs = MutableStateFlow(repository.get())
    val fontPrefs: StateFlow<FontPrefs> = _fontPrefs.asStateFlow()

    fun save(prefs: FontPrefs) {
        repository.set(prefs)
        _fontPrefs.value = prefs
    }
}
