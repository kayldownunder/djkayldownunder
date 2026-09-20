package com.k.hosken.djkayldownunder.data

import android.content.Context

/**
 * Persists which of the app's bottom-dock icons the user has chosen to show, and in what
 * order - edited from the "Customise Bottom Dock" screen (Settings). Stored as a
 * comma-separated list of dock item ids; null means "never customized", so the caller
 * falls back to every known item in its default order.
 */
class DockPreferenceRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_dock_prefs", Context.MODE_PRIVATE)

    fun getOrderedIds(): List<String>? {
        val raw = prefs.getString(KEY, null) ?: return null
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun setOrderedIds(ids: List<String>) {
        prefs.edit().putString(KEY, ids.joinToString(",")).apply()
    }

    companion object {
        private const val KEY = "dock_item_order"
    }
}
