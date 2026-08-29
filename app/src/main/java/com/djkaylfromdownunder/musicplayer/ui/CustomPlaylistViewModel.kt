package com.djkaylfromdownunder.musicplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.djkaylfromdownunder.musicplayer.data.CustomPlaylistMeta
import com.djkaylfromdownunder.musicplayer.data.CustomPlaylistRepository
import com.djkaylfromdownunder.musicplayer.data.Playlist
import com.djkaylfromdownunder.musicplayer.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CustomPlaylistViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CustomPlaylistRepository(application)

    private val _playlists = MutableStateFlow(repository.getAll())
    val playlists: StateFlow<List<CustomPlaylistMeta>> = _playlists.asStateFlow()

    fun create(name: String, trackUris: List<String>) {
        repository.create(name, trackUris)
        _playlists.value = repository.getAll()
    }

    fun delete(id: String) {
        repository.delete(id)
        _playlists.value = repository.getAll()
    }

    fun isFavoriteTrack(trackUri: String): Boolean = repository.isFavoriteTrack(trackUri)

    /** Number of user-created playlists so far (excluding "Favorites"), for default-naming a new one. */
    fun customPlaylistCount(): Int = repository.customPlaylistCount()

    /** Adds/removes a track from the "Favorites" playlist. Returns the new favorited state. */
    fun toggleFavoriteTrack(trackUri: String): Boolean {
        val nowFavorite = repository.toggleFavoriteTrack(trackUri)
        _playlists.value = repository.getAll()
        return nowFavorite
    }

    /**
     * Resolves a saved custom playlist into a real playable Playlist, matching stored
     * track URIs against the full pool of tracks currently known to the library. Uses a
     * "custom:///" URI as the playlist's folderUri - it's never resolved against real
     * storage, just used as a stable key for skip-list and resume-position tracking.
     */
    fun resolve(meta: CustomPlaylistMeta, allTracks: List<Track>): Playlist {
        val trackMap = allTracks.associateBy { it.uri.toString() }
        val tracks = meta.trackUris.mapNotNull { trackMap[it] }
        return Playlist(
            folderUri = Uri.parse("custom:///${meta.id}"),
            name = meta.name,
            tracks = tracks
        )
    }
}
