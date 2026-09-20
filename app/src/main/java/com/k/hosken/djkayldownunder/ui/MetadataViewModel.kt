package com.k.hosken.djkayldownunder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.k.hosken.djkayldownunder.data.MetadataStore
import com.k.hosken.djkayldownunder.data.Playlist
import com.k.hosken.djkayldownunder.metadata.MetadataFetchProgress
import com.k.hosken.djkayldownunder.metadata.MetadataFetchService
import kotlinx.coroutines.flow.StateFlow

class MetadataViewModel(application: Application) : AndroidViewModel(application) {

    // Shared with MetadataFetchService, which does the actual writing while fetching -
    // this is the same in-memory cache, so newly-fetched metadata shows up immediately.
    private val store = MetadataStore.getInstance(application)

    // Forwarded straight from the service so every existing collector (artwork, playlist
    // cards, the Settings progress bar) keeps working unchanged.
    val progress: StateFlow<MetadataFetchProgress> = MetadataFetchService.progress

    /** Fetch metadata for a single playlist (used by the per-card download button). */
    fun fetchMetadataForPlaylist(playlist: Playlist) {
        MetadataFetchService.fetchPlaylist(getApplication(), playlist)
    }

    /**
     * Fetches metadata for every playlist in the library, one at a time. Sequential on
     * purpose — running these in parallel would fire many simultaneous requests at
     * MusicBrainz's free API and risk getting rate-limited.
     */
    fun fetchMetadataForAllPlaylists(playlists: List<Playlist>) {
        MetadataFetchService.fetchAll(getApplication(), playlists)
    }

    fun metadataFor(trackUri: String) = store.get(trackUri)

    /** True once every track in this playlist has a metadata entry (from any source). */
    fun isPlaylistSynced(playlist: Playlist): Boolean {
        if (playlist.tracks.isEmpty()) return false
        return playlist.tracks.all { store.get(it.uri.toString()) != null }
    }
}
