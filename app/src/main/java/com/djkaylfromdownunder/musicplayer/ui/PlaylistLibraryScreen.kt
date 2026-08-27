package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.Playlist
import com.djkaylfromdownunder.musicplayer.ui.theme.SurfaceCard

@Composable
fun PlaylistLibraryScreen(
    viewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    onPlaylistClick: (Playlist) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Your Library",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        when (val current = state) {
            is LibraryState.NoRootChosen -> EmptyState(
                message = "No music folder chosen yet. Head to Settings to pick your root music directory.",
                viewModel = viewModel
            )
            is LibraryState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is LibraryState.Loaded -> {
                if (current.playlists.isEmpty()) {
                    EmptyState(
                        message = "No playlists found. Make sure your music folder contains subfolders with audio files.",
                        viewModel = viewModel
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(current.playlists) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                metadataViewModel = metadataViewModel,
                                onClick = { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }
            }
            is LibraryState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${current.message}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    metadataViewModel: MetadataViewModel,
    onClick: () -> Unit
) {
    val progress by metadataViewModel.progress.collectAsState()
    val isFetchingThis = progress.isRunning && progress.playlistName == playlist.name
    // Recomputed each time progress changes, so the icon disappears the moment fetching finishes.
    val isSynced = !isFetchingThis && metadataViewModel.isPlaylistSynced(playlist)

    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
        ) {
            TrackArtwork(
                track = playlist.tracks.firstOrNull(),
                metadataViewModel = metadataViewModel,
                modifier = Modifier.fillMaxSize()
            )

            // Fetch metadata button, top-right corner overlay.
            // Hidden entirely once every track in this playlist already has metadata.
            if (!isSynced) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                        .clickable(enabled = !isFetchingThis) {
                            metadataViewModel.fetchMetadataForPlaylist(playlist)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isFetchingThis) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Fetch metadata for ${playlist.name}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
        Text(
            text = if (isFetchingThis) {
                "Fetching ${progress.completedTracks}/${progress.totalTracks}…"
            } else {
                "${playlist.trackCount} tracks"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(message: String, viewModel: MusicLibraryViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            message,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        ChooseMusicFolderButton(viewModel = viewModel)
    }
}
