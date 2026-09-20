package com.k.hosken.djkayldownunder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.k.hosken.djkayldownunder.data.FolderBrowseItem
import com.k.hosken.djkayldownunder.data.MetadataStore
import com.k.hosken.djkayldownunder.data.MusicFolderRepository
import com.k.hosken.djkayldownunder.data.Playlist
import com.k.hosken.djkayldownunder.data.PlaylistCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class LibraryState {
    object NoRootChosen : LibraryState()
    object Loading : LibraryState()
    data class Loaded(val playlists: List<Playlist>) : LibraryState()
    data class Error(val message: String) : LibraryState()
}

class MusicLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicFolderRepository(application)
    private val playlistCache = PlaylistCacheRepository(application)

    // Flat, fully-recursive state - used by Search, "Fetch All", and Skip Review, which
    // all need every playable folder in the whole tree regardless of nesting depth.
    private val _state = MutableStateFlow<LibraryState>(LibraryState.NoRootChosen)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    // The chosen root folder, exposed separately so the Library tab can browse it
    // level-by-level rather than working from the flattened list above.
    private val _rootUri = MutableStateFlow<Uri?>(null)
    val rootUri: StateFlow<Uri?> = _rootUri.asStateFlow()

    init {
        // If the user already picked a folder in a previous session, load it automatically.
        repository.getSavedRootFolder()?.let { savedUri ->
            _rootUri.value = savedUri
            // Show last session's scan result immediately (e.g. so the Library tab's
            // "Random Skip All Albums" shortcut doesn't wait on a fresh recursive SAF walk
            // of the whole tree just to appear) - loadPlaylists below then replaces it with
            // a real, up-to-date scan without ever dropping back to a bare loading state.
            val cached = playlistCache.load()
            if (cached.isNotEmpty()) {
                _state.value = LibraryState.Loaded(cached)
            }
            loadPlaylists(savedUri)
            attachMetadataStore(savedUri)
        }
    }

    /** Called after the SAF picker returns a folder URI. */
    fun onRootFolderChosen(uri: Uri) {
        repository.persistRootFolder(uri)
        repository.invalidateFolderCache()
        _rootUri.value = uri
        loadPlaylists(uri)
        attachMetadataStore(uri)
    }

    /**
     * Points the shared metadata cache at this folder's "metadata" subfolder, so fetched
     * titles/artwork live alongside the music itself (surviving an app reinstall or device
     * flash) instead of app-private storage. See MetadataStore.attachRoot for details.
     */
    private fun attachMetadataStore(rootUri: Uri) {
        viewModelScope.launch {
            MetadataStore.getInstance(getApplication()).attachRoot(rootUri)
        }
    }

    fun refresh() {
        repository.invalidateFolderCache()
        repository.getSavedRootFolder()?.let { loadPlaylists(it) }
    }

    /** Lists just one folder's immediate contents, for level-by-level Library browsing. */
    suspend fun listFolderLevel(folderUri: Uri): List<FolderBrowseItem> {
        return repository.listFolderLevel(folderUri)
    }

    /**
     * Fresh, on-demand recursive scan of the whole library tree, independent of the
     * cached [state] flow. Used by Settings' "Fetch Metadata for All Playlists" so it
     * isn't blocked by [state] still being [LibraryState.Loading]/[LibraryState.Error] -
     * the same underlying repository call individual folder sync icons already rely on.
     */
    suspend fun scanAllPlaylists(): List<Playlist> {
        val root = _rootUri.value ?: return emptyList()
        return repository.scanPlaylists(root)
    }

    /**
     * Sweeps every album folder for stray cover-art image files and moves them into the
     * single shared MetaData folder - see MusicFolderRepository.consolidateArtworkImages.
     * Returns how many images were moved, or null if no root folder is chosen yet.
     */
    suspend fun consolidateArtworkImages(): Int? {
        val root = _rootUri.value ?: return null
        return repository.consolidateArtworkImages(root)
    }

    /**
     * Resolves any folder - leaf or branching, any depth - into one playable Playlist of
     * everything nested inside it. Used to favorite/sync a whole branching folder (e.g. an
     * artist folder with several albums) as a single unit.
     */
    suspend fun buildAggregatePlaylist(folderUri: Uri): Playlist? {
        return repository.buildAggregatePlaylist(folderUri)
    }

    /** Existing collage thumbnail for a branching folder, if one's already been generated. */
    suspend fun findCollageThumbnail(folderUri: Uri): Uri? {
        val root = _rootUri.value ?: return null
        return repository.findCollageThumbnail(root, folderUri)
    }

    /**
     * A custom cover image the user dropped directly inside a branching folder, if any -
     * see MusicFolderRepository.findFolderCoverImage. Takes priority over the
     * auto-generated collage when present.
     */
    suspend fun findFolderCoverImage(folderUri: Uri): Uri? {
        return repository.findFolderCoverImage(folderUri)
    }

    /** Builds and saves a collage thumbnail for a branching folder - see MusicFolderRepository.generateCollageThumbnail. */
    suspend fun generateCollageThumbnail(folderUri: Uri): Uri? {
        val root = _rootUri.value ?: return null
        return repository.generateCollageThumbnail(root, folderUri)
    }

    /**
     * Fallback cover art borrowed from a branching folder's first subfolder, for when
     * neither a custom cover nor a generated collage exists yet - see
     * MusicFolderRepository.findFirstChildCoverArt.
     */
    suspend fun findFirstChildCoverArt(folderUri: Uri): ByteArray? {
        return repository.findFirstChildCoverArt(folderUri)
    }

    /**
     * Permanently deletes a folder (and everything inside it) from device storage, then
     * re-scans the library so Search/Play Lists/Favorites/Skip Review all drop the
     * deleted content immediately. [onComplete] fires once the whole operation is done,
     * so the calling screen can clear its own loading state and re-render.
     */
    fun deleteFolder(folderUri: Uri, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteFolderRecursively(folderUri)
            repository.getSavedRootFolder()?.let { loadPlaylists(it) }
            onComplete()
        }
    }

    private fun loadPlaylists(rootUri: Uri) {
        // Don't clobber an already-Loaded state (e.g. the cached list restored in init) with
        // Loading - that would just flash shortcuts like "Random Skip All Albums" off again
        // while this fresh scan runs, for no benefit over leaving the stale list on screen
        // a little longer.
        if (_state.value !is LibraryState.Loaded) {
            _state.value = LibraryState.Loading
        }
        viewModelScope.launch {
            try {
                val playlists = repository.scanPlaylists(rootUri)
                _state.value = LibraryState.Loaded(playlists)
                withContext(Dispatchers.IO) { playlistCache.save(playlists) }
            } catch (e: Exception) {
                _state.value = LibraryState.Error(e.message ?: "Couldn't scan music folder")
            }
        }
    }
}
