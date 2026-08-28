package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.Playlist
import com.djkaylfromdownunder.musicplayer.data.Track

private data class SkippedGroup(val playlist: Playlist, val skippedTracks: List<Track>)

@Composable
fun SkipReviewScreen(
    libraryViewModel: MusicLibraryViewModel,
    skipListViewModel: SkipListViewModel,
    onBack: () -> Unit
) {
    val libraryState by libraryViewModel.state.collectAsState()
    // Bump this to force recomputation of the skip groups after an unskip action,
    // since the skip list itself isn't a reactive Flow.
    var refreshTick by remember { mutableIntStateOf(0) }

    val playlists = (libraryState as? LibraryState.Loaded)?.playlists.orEmpty()

    val groups = remember(playlists, refreshTick) {
        val allSkips = skipListViewModel.allSkips()
        playlists.mapNotNull { playlist ->
            val skippedUris = allSkips[playlist.folderUri.toString()].orEmpty()
            val skippedTracks = playlist.tracks.filter { it.uri.toString() in skippedUris }
            if (skippedTracks.isNotEmpty()) SkippedGroup(playlist, skippedTracks) else null
        }
    }

    // Pending permanent deletion, awaiting confirmation - distinct from unchecking a box
    // (which only un-skips, leaving the file alone).
    var pendingDelete by remember { mutableStateOf<Pair<Playlist, Track>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Review Skipped Songs",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            // Balances the back arrow on the left so the title is centered on the screen,
            // not just centered in the space left over after the arrow.
            Spacer(modifier = Modifier.width(48.dp))
        }

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Load your music library first to review skipped songs.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No songs are currently marked to skip.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                groups.forEach { group ->
                    item {
                        Text(
                            text = group.playlist.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(group.skippedTracks) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = true,
                                onCheckedChange = { checked ->
                                    // Unchecking just un-skips it - the track stays on disk
                                    // and becomes playable again in its playlist.
                                    skipListViewModel.setSkipped(
                                        group.playlist.folderUri.toString(),
                                        track.uri.toString(),
                                        checked
                                    )
                                    refreshTick++
                                }
                            )
                            Text(
                                text = track.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { pendingDelete = group.playlist to track }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete ${track.displayName}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    pendingDelete?.let { (playlist, track) ->
        DeleteConfirmationDialog(
            onConfirm = {
                skipListViewModel.deleteSkippedTrack(playlist.folderUri.toString(), track.uri)
                refreshTick++
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
