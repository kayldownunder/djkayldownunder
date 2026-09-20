package com.k.hosken.djkayldownunder.data

import android.content.Context

/**
 * Persists the order the Settings screen's own shortcut buttons appear in - edited from
 * the "Rearrange Settings Layout" screen. Stored as a comma-separated list of block ids;
 * null means "never customized", so the caller falls back to the default order.
 */
class SettingsLayoutRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_settings_layout_prefs", Context.MODE_PRIVATE)

    fun getOrder(): List<String>? {
        val raw = prefs.getString(KEY, null) ?: return null
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun setOrder(ids: List<String>) {
        prefs.edit().putString(KEY, ids.joinToString(",")).apply()
    }

    companion object {
        private const val KEY = "settings_block_order"
    }
}
