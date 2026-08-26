package com.djkaylfromdownunder.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.djkaylfromdownunder.musicplayer.data.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FavoritesRepository(application)

    private val _favoriteKeys = MutableStateFlow(repository.getAllFavoriteKeys())
    val favoriteKeys: StateFlow<Set<String>> = _favoriteKeys.asStateFlow()

    fun toggleFavorite(key: String) {
        repository.toggleFavorite(key)
        _favoriteKeys.value = repository.getAllFavoriteKeys()
    }
}
