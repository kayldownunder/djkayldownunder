package com.djkaylfromdownunder.musicplayer.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.Playlist
import com.djkaylfromdownunder.musicplayer.data.Track
import com.djkaylfromdownunder.musicplayer.data.parentFolderKey
import com.djkaylfromdownunder.musicplayer.ui.theme.AccentCoralDim
import com.djkaylfromdownunder.musicplayer.ui.theme.BackgroundBlack
import com.djkaylfromdownunder.musicplayer.ui.theme.SurfaceCard

// Artwork shrinks from this height down to nothing as the track list below is scrolled.
private val MAX_ARTWORK_HEIGHT = 300.dp

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    metadataViewModel: MetadataViewModel,
    skipListViewModel: SkipListViewModel,
    libraryViewModel: MusicLibraryViewModel,
    onCollapse: () -> Unit = {},
    onPlayRecommendation: (Playlist) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    val libraryState by libraryViewModel.state.collectAsState()
    val allPlaylists = remember(libraryState) {
        (libraryState as? LibraryState.Loaded)?.playlists.orEmpty()
    }

    // Other albums to suggest once the user scrolls past the current playlist: sibling
    // folders under the same parent when there are any, otherwise anything else in the
    // library so there's always something to pick from.
    val currentFolderUriStr = state.currentPlaylistFolderUri
    val sameFolderRecommendations = remember(currentFolderUriStr, allPlaylists) {
        if (currentFolderUriStr == null || allPlaylists.isEmpty()) {
            emptyList()
        } else {
            val currentKey = Uri.parse(currentFolderUriStr).parentFolderKey()
            allPlaylists.filter {
                it.folderUri.toString() != currentFolderUriStr && it.folderUri.parentFolderKey() == currentKey
            }
        }
    }
    val recommendations = remember(sameFolderRecommendations, currentFolderUriStr, allPlaylists) {
        sameFolderRecommendations.ifEmpty {
            allPlaylists.filter { it.folderUri.toString() != currentFolderUriStr }
        }.take(15)
    }
    val recommendationsAreFromSameFolder = sameFolderRecommendations.isNotEmpty()

    // Collapsing-artwork state: height tracks scroll delta directly via nested scroll,
    // shrinking the art before the track list underneath it scrolls at all, and growing
    // it back once the list is scrolled back to its own top.
    val density = LocalDensity.current
    val maxArtHeightPx = with(density) { MAX_ARTWORK_HEIGHT.toPx() }
    var artHeightPx by remember { mutableFloatStateOf(maxArtHeightPx) }
    val listState = rememberLazyListState()

    val nestedScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val atListTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (delta < 0 && artHeightPx > 0f) {
                    val newHeight = (artHeightPx + delta).coerceIn(0f, maxArtHeightPx)
                    val consumed = newHeight - artHeightPx
                    artHeightPx = newHeight
                    return Offset(0f, consumed)
                }
                if (delta > 0 && artHeightPx < maxArtHeightPx && atListTop) {
                    val newHeight = (artHeightPx + delta).coerceIn(0f, maxArtHeightPx)
                    val consumed = newHeight - artHeightPx
                    artHeightPx = newHeight
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }
        }
    }

    // Switching tracks (e.g. via Next/Previous or tapping a queue row) re-expands the
    // artwork, so it doesn't stay collapsed from browsing the list on the previous track.
    LaunchedEffect(state.currentTrack?.uri) {
        artHeightPx = maxArtHeightPx
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AccentCoralDim.copy(alpha = 0.35f), BackgroundBlack),
                    endY = 900f
                )
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse")
            }
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(48.dp)) // balance the back icon
        }

        // Skip-this-song controls: tick to always skip this track in this playlist from
        // now on, or tap Skip to just jump past it right now without marking it.
        val folderUri = state.currentPlaylistFolderUri
        val trackUri = state.currentTrack?.uri?.toString()
        var skipRefreshTick by remember { mutableIntStateOf(0) }

        if (folderUri != null && trackUri != null) {
            var isSkipMarked by remember(trackUri) {
                mutableStateOf(skipListViewModel.isSkipped(folderUri, trackUri))
            }
            var showDeleteDialog by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSkipMarked,
                    onCheckedChange = { checked ->
                        isSkipMarked = checked
                        skipListViewModel.setSkipped(folderUri, trackUri, checked)
                        skipRefreshTick++
                    }
                )
                Text(
                    "Skip this song next time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = { viewModel.skipNext() }) {
                    Text("Skip")
                }
                Spacer(modifier = Modifier.weight(1f))
                // Grouped with the other song-management actions (skip) rather than
                // sitting among the transport controls (previous/play/next) below, and
                // pushed to the far right edge to stay clear of Skip.
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (showDeleteDialog) {
                DeleteConfirmationDialog(
                    onConfirm = {
                        showDeleteDialog = false
                        viewModel.deleteCurrentTrack()
                    },
                    onDismiss = { showDeleteDialog = false }
                )
            }
        }

        // Everything below is one nested-scroll region: the track list drives the
        // artwork's collapse/expand, while the playback controls between them stay put.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(nestedScrollConnection)
        ) {
            // Top half: collapsible album artwork. Shrinks to nothing and fades out as
            // the track list is scrolled.
            if (artHeightPx > 0.5f) {
                val artHeightDp = with(density) { artHeightPx.toDp() }
                val artFraction = (artHeightPx / maxArtHeightPx).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artHeightDp)
                        .padding(horizontal = 32.dp, vertical = 8.dp)
                        .graphicsLayer { alpha = artFraction },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        TrackArtwork(
                            track = state.currentTrack,
                            metadataViewModel = metadataViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Bottom half: title/album plus every transport control. Always fully
            // visible regardless of how far the artwork above has collapsed.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                val currentMetadata = state.currentTrack?.let { metadataViewModel.metadataFor(it.uri.toString()) }
                val songTitle = currentMetadata?.title?.takeIf { it.isNotBlank() }
                    ?: state.currentTrack?.displayName
                    ?: "Nothing playing"
                val albumName = currentMetadata?.album?.takeIf { it.isNotBlank() }

                Text(
                    text = songTitle,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (albumName != null) {
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                PlaybackSlider(state = state, viewModel = viewModel)

                Spacer(modifier = Modifier.height(4.dp))

                PlaybackControls(state = state, viewModel = viewModel, buttonSize = 56.dp)

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalVolumeControl(state = state, viewModel = viewModel)
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            )

            // Track list: every track in the folder, with artwork, bold title, and
            // artist name. Skipped tracks show with a line through them; tapping one
            // un-skips it and plays it. Scrolling this list is what drives the artwork
            // collapse above (see nestedScrollConnection). Past the end of the list,
            // suggested albums appear so the user can keep listening without leaving
            // this screen.
            key(skipRefreshTick) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "PLAYLIST",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                        )
                    }
                    itemsIndexed(state.fullTrackList) { _, track ->
                        val trackUriStr = track.uri.toString()
                        val isSkipped = folderUri != null && skipListViewModel.isSkipped(folderUri, trackUriStr)
                        val isCurrent = state.currentTrack?.uri == track.uri
                        QueueRow(
                            track = track,
                            isCurrent = isCurrent,
                            isSkipped = isSkipped,
                            metadataViewModel = metadataViewModel,
                            onClick = {
                                if (isSkipped) {
                                    viewModel.unskipAndPlay(trackUriStr)
                                } else {
                                    val idx = state.queue.indexOfFirst { it.uri == track.uri }
                                    if (idx >= 0) viewModel.playQueueIndex(idx)
                                }
                                skipRefreshTick++
                            }
                        )
                    }
                    if (recommendations.isNotEmpty()) {
                        item {
                            RecommendationsSection(
                                title = if (recommendationsAreFromSameFolder) "MORE FROM THIS FOLDER" else "FROM YOUR LIBRARY",
                                playlists = recommendations,
                                metadataViewModel = metadataViewModel,
                                onPlaylistClick = onPlayRecommendation
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/**
 * End-of-playlist recommendations: a horizontally-scrolling row of other albums, shown
 * once the user has scrolled past the current playlist's own tracks - sibling albums from
 * the same parent folder when there are any, otherwise a sample from the rest of the
 * library.
 */
@Composable
private fun RecommendationsSection(
    title: String,
    playlists: List<Playlist>,
    metadataViewModel: MetadataViewModel,
    onPlaylistClick: (Playlist) -> Unit
) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists, key = { it.folderUri.toString() }) { playlist ->
                RecommendationCard(
                    playlist = playlist,
                    metadataViewModel = metadataViewModel,
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    playlist: Playlist,
    metadataViewModel: MetadataViewModel,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.width(120.dp).clickable(onClick = onClick)) {
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
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A single row in the playlist: album art thumbnail, bold title, and artist name below.
 * Title/artist come from fetched metadata when available, falling back to the raw
 * filename-derived display name if nothing's been fetched for this track yet.
 */
@Composable
private fun QueueRow(
    track: Track,
    isCurrent: Boolean,
    isSkipped: Boolean,
    metadataViewModel: MetadataViewModel,
    onClick: () -> Unit
) {
    // Collecting progress means this row recomposes as fetching completes, so titles/
    // artists/art appear automatically without needing to leave and re-enter the screen.
    val progress by metadataViewModel.progress.collectAsState()
    val metadata = metadataViewModel.metadataFor(track.uri.toString())
    val title = metadata?.title?.takeIf { it.isNotBlank() } ?: track.displayName
    val artist = metadata?.artist?.takeIf { it.isNotBlank() }

    val textColor = when {
        isSkipped -> MaterialTheme.colorScheme.onSurfaceVariant
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground
    }
    val decoration = if (isSkipped) TextDecoration.LineThrough else TextDecoration.None

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
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

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Currently playing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = textColor,
                    textDecoration = decoration,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            if (artist != null) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = decoration,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackSlider(state: PlayerUiState, viewModel: PlayerViewModel) {
    Slider(
        value = state.positionMs.toFloat(),
        onValueChange = { viewModel.seekTo(it.toLong()) },
        valueRange = 0f..(state.durationMs.coerceAtLeast(1L).toFloat()),
        // A smaller, custom thumb instead of Material3's default pill shape - stays
        // visible without dominating the timeline.
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 14.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
        },
        colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatMs(state.positionMs), style = MaterialTheme.typography.bodySmall)
        Text(formatMs(state.durationMs), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlaybackControls(state: PlayerUiState, viewModel: PlayerViewModel, buttonSize: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(28.dp))
        }
        FilledIconButton(
            onClick = { viewModel.togglePlayPause() },
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(buttonSize / 2)
            )
        }
        IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
        }
    }
}

/** Horizontal volume slider - icon, slider, icon - sitting below the transport controls. */
@Composable
private fun HorizontalVolumeControl(state: PlayerUiState, viewModel: PlayerViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Slider(
            value = state.volume,
            onValueChange = { viewModel.setVolume(it) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                activeTrackColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
