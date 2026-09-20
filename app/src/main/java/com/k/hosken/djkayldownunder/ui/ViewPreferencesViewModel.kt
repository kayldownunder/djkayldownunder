package com.k.hosken.djkayldownunder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.k.hosken.djkayldownunder.data.PlaylistViewMode
import com.k.hosken.djkayldownunder.data.ViewPreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ViewPreferencesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ViewPreferenceRepository(application)

    private val _viewMode = MutableStateFlow(repository.getViewMode())
    val viewMode: StateFlow<PlaylistViewMode> = _viewMode.asStateFlow()

    fun setViewMode(mode: PlaylistViewMode) {
        repository.setViewMode(mode)
        _viewMode.value = mode
    }
}
