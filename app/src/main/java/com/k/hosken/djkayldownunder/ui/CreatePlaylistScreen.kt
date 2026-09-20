package com.k.hosken.djkayldownunder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.k.hosken.djkayldownunder.data.CustomPlaylistRepository
import com.k.hosken.djkayldownunder.data.Track

@Composable
fun CreatePlaylistScreen(
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    customPlaylistViewModel: CustomPlaylistViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val libraryState by libraryViewModel.state.collectAsState()
    val allTracks = remember(libraryState) {
        (libraryState as? LibraryState.Loaded)?.playlists?.flatMap { it.tracks } ?: emptyList()
    }

    var name by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Track>() }

    // Tapping the heart filters the picker below down to just favorited tracks (the same
    // "Favorites" custom playlist the Now Playing heart writes to), so building a playlist
    // out of favorites doesn't mean hunting for them one by one in the full library list.
    val customMetas by customPlaylistViewModel.playlists.collectAsState()
    val favoriteTrackUris = remember(customMetas) {
        customMetas.find { it.name == CustomPlaylistRepository.FAVORITES_PLAYLIST_NAME }
            ?.trackUris?.toSet() ?: emptySet()
    }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    val displayedTracks = remember(allTracks, showFavoritesOnly, favoriteTrackUris) {
        if (showFavoritesOnly) allTracks.filter { it.uri.toString() in favoriteTrackUris } else allTracks
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Create Playlist",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            // Balances the back arrow on the left so the title is centered on the screen,
            // not just centered in the space left over after the arrow.
            Spacer(modifier = Modifier.width(48.dp))
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Playlist name (optional - defaults to \"Play List N\")") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (showFavoritesOnly) {
                    "Choose songs (${selected.size} selected) — Favorites only"
                } else {
                    "Choose songs (${selected.size} selected)"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showFavoritesOnly = !showFavoritesOnly }) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = if (showFavoritesOnly) "Show all songs" else "Show favorite songs",
                    tint = if (showFavoritesOnly) FavoriteRed else FavoriteRed.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (allTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No songs found. Choose a music folder in Settings first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (displayedTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No favorite songs yet. Tap the heart on a track in Now Playing to favorite it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(displayedTracks) { track ->
                    val isChecked = selected.contains(track)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) selected.remove(track) else selected.add(track)
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                if (isChecked) selected.remove(track) else selected.add(track)
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            TrackArtwork(
                                track = track,
                                metadataViewModel = metadataViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        val metadata = metadataViewModel.metadataFor(track.uri.toString())
                        Text(
                            text = metadata?.title?.takeIf { it.isNotBlank() } ?: track.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                if (selected.isNotEmpty()) {
                    val finalName = name.trim().ifBlank {
                        "Play List ${customPlaylistViewModel.customPlaylistCount() + 1}"
                    }
                    // Songs pulled in from Favorites are now organized into this new
                    // playlist, so they're cleared out of Favorites at the same time.
                    selected.forEach { track ->
                        if (track.uri.toString() in favoriteTrackUris) {
                            customPlaylistViewModel.toggleFavoriteTrack(track.uri.toString())
                        }
                    }
                    customPlaylistViewModel.create(finalName, selected.map { it.uri.toString() })
                    onDone()
                }
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text("Create Playlist")
        }
    }
}
