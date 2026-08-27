package com.djkaylfromdownunder.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.djkaylfromdownunder.musicplayer.data.DockPreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the bottom dock (AppBottomNav renders whatever [visibleIds] resolves to). Dragging
 * an icon in the live dock calls [setOrderedIds] immediately on every slot change - there's
 * no separate editor screen or draft/commit step.
 */
class DockPreferencesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DockPreferenceRepository(application)

    private val _visibleIds = MutableStateFlow(resolveStoredIds(repository.getOrderedIds()))
    val visibleIds: StateFlow<List<String>> = _visibleIds.asStateFlow()

    /** Persists a new order/subset of dock item ids - called on every drag reorder. */
    fun setOrderedIds(ids: List<String>) {
        val cleaned = ids.filter { id -> ALL_DOCK_ITEMS.any { it.id == id } }.ifEmpty { DEFAULT_DOCK_ORDER }
        repository.setOrderedIds(cleaned)
        _visibleIds.value = cleaned
    }

    /**
     * Shows or hides one dock icon, keeping every other icon's existing order untouched.
     * A newly-shown icon is appended at the end rather than reinserted at its old spot -
     * simpler than remembering where it used to sit, and it's just a drag away from
     * wherever the user actually wants it.
     */
    fun setVisible(id: String, visible: Boolean) {
        val current = _visibleIds.value
        val updated = if (visible) {
            if (id in current) current else current + id
        } else {
            current - id
        }
        setOrderedIds(updated)
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
