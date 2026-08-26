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
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.Playlist
import com.djkaylfromdownunder.musicplayer.data.PlaylistViewMode

@Composable
fun FavoritesScreen(
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    favoritesViewModel: FavoritesViewModel,
    customPlaylistViewModel: CustomPlaylistViewModel,
    viewPreferencesViewModel: ViewPreferencesViewModel,
    onPlaylistClick: (Playlist) -> Unit
) {
    val libraryState by libraryViewModel.state.collectAsState()
    val viewMode by viewPreferencesViewModel.viewMode.collectAsState()
    val customMetas by customPlaylistViewModel.playlists.collectAsState()
    val favoriteKeys by favoritesViewModel.favoriteKeys.collectAsState()

    val folderPlaylists = (libraryState as? LibraryState.Loaded)?.playlists.orEmpty()
    val allTracks = remember(folderPlaylists) { folderPlaylists.flatMap { it.tracks } }
    val customPlaylists = remember(customMetas, allTracks) {
        customMetas.map { customPlaylistViewModel.resolve(it, allTracks) }
    }

    // A favorited key can also be a branching folder (e.g. a whole artist folder with
    // several albums) rather than an actual leaf playlist, since Library lets you
    // favorite either kind. Anything not already covered by a known playlist above is
    // resolved into a single aggregate Playlist of everything nested inside it.
    val knownKeys = remember(customPlaylists, folderPlaylists) {
        (customPlaylists + folderPlaylists).map { it.folderUri.toString() }.toSet()
    }
    val unresolvedKeys = remember(favoriteKeys, knownKeys) { favoriteKeys - knownKeys }
    var aggregateFavorites by remember { mutableStateOf<List<Playlist>>(emptyList()) }

    LaunchedEffect(unresolvedKeys) {
        aggregateFavorites = unresolvedKeys.mapNotNull { key ->
            runCatching { libraryViewModel.buildAggregatePlaylist(Uri.parse(key)) }.getOrNull()
        }
    }

    val favoritePlaylists = remember(customPlaylists, folderPlaylists, aggregateFavorites, favoriteKeys) {
        (customPlaylists + folderPlaylists + aggregateFavorites)
            .filter { favoriteKeys.contains(it.folderUri.toString()) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Favorites", style = MaterialTheme.typography.headlineLarge)
        }

        if (favoritePlaylists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No favorites yet. Tap the heart on any playlist to add it here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else if (viewMode == PlaylistViewMode.LIST) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favoritePlaylists, key = { it.folderUri.toString() }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        metadataViewModel = metadataViewModel,
                        favoritesViewModel = favoritesViewModel,
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
            }
        } else {
            val columns = when (viewMode) {
                PlaylistViewMode.LARGE -> 1
                PlaylistViewMode.SMALL -> 3
                else -> 2
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favoritePlaylists, key = { it.folderUri.toString() }) { playlist ->
                    PlaylistGridCard(
                        playlist = playlist,
                        metadataViewModel = metadataViewModel,
                        favoritesViewModel = favoritesViewModel,
                        compact = viewMode == PlaylistViewMode.SMALL,
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
            }
        }
    }
}
