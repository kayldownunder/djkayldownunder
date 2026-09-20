package com.k.hosken.djkayldownunder.data

import android.content.Context

class FavoritesRepository(context: Context) {

    private val prefs = context.getSharedPreferences("djkayl_favorites", Context.MODE_PRIVATE)

    fun isFavorite(playlistKey: String): Boolean {
        return (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).contains(playlistKey)
    }

    /** Toggles favorite status and returns the new state (true = now a favorite). */
    fun toggleFavorite(playlistKey: String): Boolean {
        val current = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        val nowFavorite = if (current.contains(playlistKey)) {
            current.remove(playlistKey)
            false
        } else {
            current.add(playlistKey)
            true
        }
        prefs.edit().putStringSet(KEY, current).apply()
        return nowFavorite
    }

    fun getAllFavoriteKeys(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    companion object {
        private const val KEY = "favorite_keys"
    }
}
