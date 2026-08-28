package com.djkaylfromdownunder.musicplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class CustomPlaylistMeta(val id: String, val name: String, val trackUris: List<String>)

/**
 * Stores playlists the user builds by hand (picking individual songs), separate from
 * folder-based playlists. Persisted as a small JSON file in app-private storage.
 */
class CustomPlaylistRepository(context: Context) {

    private val file = File(context.filesDir, "custom_playlists.json")

    fun getAll(): List<CustomPlaylistMeta> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val uris = obj.getJSONArray("trackUris")
                CustomPlaylistMeta(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    trackUris = (0 until uris.length()).map { uris.getString(it) }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun create(name: String, trackUris: List<String>): CustomPlaylistMeta {
        val meta = CustomPlaylistMeta(id = UUID.randomUUID().toString(), name = name, trackUris = trackUris)
        saveAll(getAll() + meta)
        return meta
    }

    fun delete(id: String) {
        saveAll(getAll().filterNot { it.id == id })
    }

    fun isFavoriteTrack(trackUri: String): Boolean =
        getAll().find { it.name == FAVORITES_PLAYLIST_NAME }?.trackUris?.contains(trackUri) == true

    /**
     * Adds/removes [trackUri] from the "Favorites" custom playlist, creating it on first use.
     * Returns the new favorited state (true = now in Favorites).
     */
    fun toggleFavoriteTrack(trackUri: String): Boolean {
        val all = getAll()
        val existing = all.find { it.name == FAVORITES_PLAYLIST_NAME }
        if (existing == null) {
            saveAll(all + CustomPlaylistMeta(id = UUID.randomUUID().toString(), name = FAVORITES_PLAYLIST_NAME, trackUris = listOf(trackUri)))
            return true
        }
        val nowFavorite = trackUri !in existing.trackUris
        val updatedTracks = if (nowFavorite) existing.trackUris + trackUri else existing.trackUris - trackUri
        saveAll(all.map { if (it.id == existing.id) it.copy(trackUris = updatedTracks) else it })
        return nowFavorite
    }

    private fun saveAll(list: List<CustomPlaylistMeta>) {
        val arr = JSONArray()
        list.forEach { meta ->
            val obj = JSONObject()
            obj.put("id", meta.id)
            obj.put("name", meta.name)
            val uris = JSONArray()
            meta.trackUris.forEach { uris.put(it) }
            obj.put("trackUris", uris)
            arr.put(obj)
        }
        file.writeText(arr.toString())
    }

    companion object {
        const val FAVORITES_PLAYLIST_NAME = "Favorites"
    }
}
