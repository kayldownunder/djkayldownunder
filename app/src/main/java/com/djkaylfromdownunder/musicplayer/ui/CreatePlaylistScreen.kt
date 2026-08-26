package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.Track

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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Playlist", style = MaterialTheme.typography.headlineSmall)
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Playlist name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Choose songs (${selected.size} selected)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (allTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No songs found. Choose a music folder in Settings first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(allTracks) { track ->
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
                if (name.isNotBlank() && selected.isNotEmpty()) {
                    customPlaylistViewModel.create(name.trim(), selected.map { it.uri.toString() })
                    onDone()
                }
            },
            enabled = name.isNotBlank() && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text("Create Playlist")
        }
    }
}
