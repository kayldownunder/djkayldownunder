package com.k.hosken.djkayldownunder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.k.hosken.djkayldownunder.data.AudioNormalizationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Backs the "Audio Normalisation" toggle on Settings - see AudioNormalizationProcessor for what it does. */
class AudioNormalizationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudioNormalizationRepository(application)

    private val _isEnabled = MutableStateFlow(repository.isEnabled())
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun toggle() {
        val enabled = !_isEnabled.value
        repository.setEnabled(enabled)
        _isEnabled.value = enabled
    }
}
