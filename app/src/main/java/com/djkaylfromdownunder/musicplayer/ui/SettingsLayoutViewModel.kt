package com.djkaylfromdownunder.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.djkaylfromdownunder.musicplayer.data.SettingsLayoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the Settings screen (renders blocks in whatever [order] resolves to). Dragging a
 * shortcut in place calls [setOrder] immediately on every slot change - there's no separate
 * editor screen or draft/commit step.
 */
class SettingsLayoutViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsLayoutRepository(application)

    private val _order = MutableStateFlow(resolveStoredIds(repository.getOrder()))
    val order: StateFlow<List<String>> = _order.asStateFlow()

    fun setOrder(ids: List<String>) {
        val cleaned = ids.filter { id -> ALL_SETTINGS_BLOCKS.any { it.id == id } }.ifEmpty { DEFAULT_SETTINGS_ORDER }
        repository.setOrder(cleaned)
        _order.value = cleaned
    }

    private fun resolveStoredIds(stored: List<String>?): List<String> {
        if (stored == null) return DEFAULT_SETTINGS_ORDER
        val validKnownIds = stored.filter { id -> ALL_SETTINGS_BLOCKS.any { it.id == id } }
        // Any block that predates the user's stored order (e.g. added in a later app
        // update) gets appended at the end, so it isn't silently missing.
        val missing = DEFAULT_SETTINGS_ORDER.filter { it !in validKnownIds }
        return (validKnownIds + missing).ifEmpty { DEFAULT_SETTINGS_ORDER }
    }
}
