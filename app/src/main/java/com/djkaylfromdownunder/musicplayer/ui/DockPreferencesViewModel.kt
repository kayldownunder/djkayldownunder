package com.djkaylfromdownunder.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.djkaylfromdownunder.musicplayer.data.DockPreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs both the bottom dock itself (AppBottomNav renders whatever [visibleIds] resolves
 * to) and the "Customise Bottom Dock" editor screen. Edits in that screen are applied to a
 * local draft and only committed here via [setOrderedIds] on the way out (see
 * CustomizeDockScreen's auto-save-on-back).
 */
class DockPreferencesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DockPreferenceRepository(application)

    private val _visibleIds = MutableStateFlow(resolveStoredIds(repository.getOrderedIds()))
    val visibleIds: StateFlow<List<String>> = _visibleIds.asStateFlow()

    /** Persists a new order/subset of dock item ids - called once, on leaving the editor. */
    fun setOrderedIds(ids: List<String>) {
        val cleaned = ids.filter { id -> ALL_DOCK_ITEMS.any { it.id == id } }.ifEmpty { DEFAULT_DOCK_ORDER }
        repository.setOrderedIds(cleaned)
        _visibleIds.value = cleaned
    }

    fun resolve(ids: List<String>): List<DockItemDef> {
        val byId = ALL_DOCK_ITEMS.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    private fun resolveStoredIds(stored: List<String>?): List<String> {
        if (stored == null) return DEFAULT_DOCK_ORDER
        val validKnownIds = stored.filter { id -> ALL_DOCK_ITEMS.any { it.id == id } }
        // Any dock item that predates the user's stored order (e.g. added in a later app
        // update) gets appended at the end, so it isn't silently missing from the dock.
        val missing = DEFAULT_DOCK_ORDER.filter { it !in validKnownIds }
        return (validKnownIds + missing).ifEmpty { DEFAULT_DOCK_ORDER }
    }
}
