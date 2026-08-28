package com.djkaylfromdownunder.musicplayer.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.Playlist
import com.djkaylfromdownunder.musicplayer.data.PlaylistViewMode

@Composable
fun PlayListsScreen(
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    favoritesViewModel: FavoritesViewModel,
    customPlaylistViewModel: CustomPlaylistViewModel,
    viewPreferencesViewModel: ViewPreferencesViewModel,
    buttonColorViewModel: ButtonColorViewModel,
    onPlaylistClick: (Playlist) -> Unit,
    onRandomSkipAllAlbums: (List<Playlist>) -> Unit
) {
    val libraryState by libraryViewModel.state.collectAsState()
    val viewMode by viewPreferencesViewModel.viewMode.collectAsState()
    val customMetas by customPlaylistViewModel.playlists.collectAsState()

    val folderPlaylists = (libraryState as? LibraryState.Loaded)?.playlists.orEmpty()
    val allTracks = remember(folderPlaylists) { folderPlaylists.flatMap { it.tracks } }
    val customPlaylists = remember(customMetas, allTracks) {
        customMetas.map { customPlaylistViewModel.resolve(it, allTracks) }
    }
    val allPlaylists = customPlaylists + folderPlaylists

    // The single playlist currently being deleted, if any - disables its row/card and
    // shows a spinner instead of the trash icon, matching the Library screen's own
    // delete affordance. Custom (hand-built) playlists delete instantly since there's no
    // real folder to remove; folder-backed playlists go through the same recursive
    // on-disk delete the Library screen uses.
    var deletingUri by remember { mutableStateOf<Uri?>(null) }
    fun deletePlaylistItem(playlist: Playlist) {
        deletingUri = playlist.folderUri
        if (playlist.folderUri.scheme == "custom") {
            playlist.folderUri.lastPathSegment?.let { customPlaylistViewModel.delete(it) }
            deletingUri = null
        } else {
            libraryViewModel.deleteFolder(playlist.folderUri) { deletingUri = null }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // A Row with a weighted title (rather than a Box with independently-centered
        // children) so a wide title never overlaps the shortcut on the right - see the
        // same fix on PlayerScreen's header. statusBarsPadding() keeps the shortcut clear
        // of the status bar icons, same reasoning as Settings' own top-right icon button.
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Play Lists",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (folderPlaylists.isNotEmpty()) {
                RandomSkipAllShortcut(
                    onClick = { onRandomSkipAllAlbums(folderPlaylists) },
                    buttonColorViewModel = buttonColorViewModel
                )
            }
        }

        if (allPlaylists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No playlists yet. Choose a music folder in Settings, or create a custom playlist.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else if (viewMode == PlaylistViewMode.LIST) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allPlaylists, key = { it.folderUri.toString() }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        metadataViewModel = metadataViewModel,
                        favoritesViewModel = favoritesViewModel,
                        onClick = { onPlaylistClick(playlist) },
                        isDeleting = deletingUri == playlist.folderUri,
                        onDelete = { deletePlaylistItem(playlist) },
                        showFavorite = false
                    )
                }
            }
        } else {
            val isCompact = viewMode.isCompact
            LazyVerticalGrid(
                columns = GridCells.Fixed(viewMode.gridColumns),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allPlaylists, key = { it.folderUri.toString() }) { playlist ->
                    PlaylistGridCard(
                        playlist = playlist,
                        metadataViewModel = metadataViewModel,
                        favoritesViewModel = favoritesViewModel,
                        compact = isCompact,
                        onClick = { onPlaylistClick(playlist) },
                        isDeleting = deletingUri == playlist.folderUri,
                        onDelete = { deletePlaylistItem(playlist) },
                        showFavorite = false
                    )
                }
            }
        }
    }
}
