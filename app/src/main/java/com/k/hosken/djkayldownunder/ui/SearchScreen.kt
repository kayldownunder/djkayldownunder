package com.k.hosken.djkayldownunder.ui

import androidx.media3.common.util.UnstableApi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.k.hosken.djkayldownunder.data.Playlist
import com.k.hosken.djkayldownunder.data.Track

private data class SearchResult(val playlist: Playlist, val track: Track)

@UnstableApi
@Composable
fun SearchScreen(
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onResultSelected: () -> Unit
) {
    val libraryState by libraryViewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val playlists = (libraryState as? LibraryState.Loaded)?.playlists.orEmpty()

    // Recomputes on every keystroke - matches against fetched title/artist/album where
    // available, falling back to the raw filename, so search works even before metadata
    // has been fetched for a track.
    val results = remember(query, playlists) {
        if (query.isBlank()) {
            emptyList()
        } else {
            val q = query.trim().lowercase()
            playlists.flatMap { playlist ->
                playlist.tracks.mapNotNull { track ->
                    val metadata = metadataViewModel.metadataFor(track.uri.toString())
                    val haystack = listOfNotNull(
                        metadata?.title,
                        metadata?.artist,
                        metadata?.album,
                        track.displayName,
                        track.fileName
                    ).joinToString(" ").lowercase()
                    if (haystack.contains(q)) SearchResult(playlist, track) else null
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // statusBarsPadding alone would still leave the search bar sitting right against
        // the status icons; the extra top padding (roughly the bar's own height) pushes it
        // down clear of them instead of just touching their bottom edge.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .padding(top = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search songs, artists…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
        }

        when {
            playlists.isEmpty() -> EmptyMessage("Load your music library first to search.")
            query.isBlank() -> EmptyMessage("Start typing to search your library.", alignTop = true)
            results.isEmpty() -> EmptyMessage("No matches for \"$query\".")
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        results,
                        key = { "${it.playlist.folderUri}:${it.track.uri}" }
                    ) { result ->
                        SearchResultRow(
                            result = result,
                            metadataViewModel = metadataViewModel,
                            onClick = {
                                playerViewModel.playPlaylist(
                                    result.playlist,
                                    forceRestart = false,
                                    startTrackUri = result.track.uri.toString()
                                )
                                onResultSelected()
                            }
                        )
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun EmptyMessage(message: String, alignTop: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = if (alignTop) Alignment.TopCenter else Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@UnstableApi
@Composable
private fun SearchResultRow(
    result: SearchResult,
    metadataViewModel: MetadataViewModel,
    onClick: () -> Unit
) {
    val metadata = metadataViewModel.metadataFor(result.track.uri.toString())
    val title = metadata?.title?.takeIf { it.isNotBlank() } ?: result.track.displayName
    val artist = metadata?.artist?.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            TrackArtwork(
                track = result.track,
                metadataViewModel = metadataViewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1
            )
            Text(
                text = artist?.let { "$it • ${result.playlist.name}" } ?: result.playlist.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
