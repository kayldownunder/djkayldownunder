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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.Playlist
import com.djkaylfromdownunder.musicplayer.data.Track
import com.djkaylfromdownunder.musicplayer.data.parentFolderKey
import com.djkaylfromdownunder.musicplayer.ui.theme.AccentCoralDim
import com.djkaylfromdownunder.musicplayer.ui.theme.BackgroundBlack
import com.djkaylfromdownunder.musicplayer.ui.theme.SurfaceCard

// Artwork shrinks from this height down to nothing as the track list below is scrolled.
private val MAX_ARTWORK_HEIGHT = 380.dp

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

    // The shared library scan (libraryState) is normally already warm by the time the
    // user opens a track, but on a very large library it can still be mid-scan (or not
    // yet started) the first time this screen is reached in a session - which used to
    // leave "more from this folder"/"your library" silently empty for whatever happened
    // to be playing at that moment, looking like it only worked for "some" playlists when
    // really it was a timing race, not anything specific to the playlist itself. If the
    // shared scan isn't ready yet, fall back to one dedicated on-demand scan (same call
    // Settings' "Fetch Metadata for All Playlists" already relies on) so recommendations
    // are always populated regardless of that timing.
    val libraryState by libraryViewModel.state.collectAsState()
    var fallbackPlaylists by remember { mutableStateOf<List<Playlist>?>(null) }
    LaunchedEffect(libraryState) {
        if (libraryState !is LibraryState.Loaded && fallbackPlaylists == null) {
            fallbackPlaylists = libraryViewModel.scanAllPlaylists()
        }
    }
    val allPlaylists = remember(libraryState, fallbackPlaylists) {
        (libraryState as? LibraryState.Loaded)?.playlists ?: fallbackPlaylists.orEmpty()
    }

    // Other albums to suggest once the user scrolls past the current playlist: sibling
    // folders under the same parent (shown first, as a horizontal row), then every other
    // playlist in the library so scrolling further keeps going straight into the main
    // library list instead of dead-ending.
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
    val libraryContinuation = remember(sameFolderRecommendations, currentFolderUriStr, allPlaylists) {
        val alreadyShown = (sameFolderRecommendations.map { it.folderUri.toString() } +
            listOfNotNull(currentFolderUriStr)).toSet()
        allPlaylists.filter { it.folderUri.toString() !in alreadyShown }
    }

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
        Box(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp, start = 24.dp, end = 24.dp)
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse")
            }
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.align(Alignment.Center)
            )
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
                // Left to right: volume, then the orange Skip button, then - grouped
                // together at the right edge for functional pairing - the "skip next
                // time" checkbox/label sitting right beside the trash icon.
                CompactVerticalVolumeControl(state = state, viewModel = viewModel)
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { viewModel.skipNext() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Skip")
                }
                Spacer(modifier = Modifier.weight(1f))
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
                Spacer(modifier = Modifier.width(8.dp))
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
                        .padding(horizontal = 24.dp, vertical = 8.dp)
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

            // Title/album - stays with the artwork above the track list. The actual
            // transport controls and progress bar live in the slim bar pinned to the
            // bottom of the whole screen (see CompactPlaybackBar below); volume now sits
            // beside the skip-this-song row above (see CompactVerticalVolumeControl).
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
                    if (sameFolderRecommendations.isNotEmpty()) {
                        item {
                            RecommendationsSection(
                                title = "MORE FROM THIS FOLDER",
                                playlists = sameFolderRecommendations,
                                metadataViewModel = metadataViewModel,
                                onPlaylistClick = onPlayRecommendation
                            )
                        }
                    }
                    // Seamless continuation: past the current playlist (and any
                    // same-folder siblings above), keep scrolling straight into every
                    // other playlist in the library instead of stopping.
                    if (libraryContinuation.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                )
                                Text(
                                    "YOUR LIBRARY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp)
                                )
                            }
                        }
                        items(libraryContinuation, key = { "lib:${it.folderUri}" }) { playlist ->
                            LibraryPlaylistRow(
                                playlist = playlist,
                                metadataViewModel = metadataViewModel,
                                onClick = { onPlayRecommendation(playlist) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        // Slim, always-visible playback bar pinned to the very bottom of the screen -
        // progress line plus previous/play-pause/next, kept minimal so the artwork and
        // track list above get as much room as possible.
        CompactPlaybackBar(state = state, viewModel = viewModel)
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
 * A row in the seamless "keep scrolling into the library" continuation below the
 * recommendations - deliberately lighter than [RecommendationCard] (no favorite/delete,
 * just art, name, and track count) since there can be many of these.
 */
@Composable
private fun LibraryPlaylistRow(
    playlist: Playlist,
    metadataViewModel: MetadataViewModel,
    onClick: () -> Unit
) {
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
                .background(SurfaceCard)
        ) {
            TrackArtwork(
                track = playlist.tracks.firstOrNull(),
                metadataViewModel = metadataViewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

/**
 * Slim, always-visible playback bar pinned to the bottom of the Player screen: a thin
 * seekable progress line with previous/play-pause/next directly beneath it, and nothing
 * else - no time labels, no volume - so it stays a minimal single strip regardless of how
 * far the artwork/track list above it has scrolled.
 */
@Composable
private fun CompactPlaybackBar(state: PlayerUiState, viewModel: PlayerViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        PlaybackSlider(state = state, viewModel = viewModel)
        PlaybackControls(state = state, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackSlider(state: PlayerUiState, viewModel: PlayerViewModel) {
    Slider(
        value = state.positionMs.toFloat(),
        onValueChange = { viewModel.seekTo(it.toLong()) },
        valueRange = 0f..(state.durationMs.coerceAtLeast(1L).toFloat()),
        // A slim custom thumb/track instead of Material3's default pill shape, and no
        // time labels - kept to a single thin line for the bottom bar.
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 10.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(2.dp),
                thumbTrackGapSize = 2.dp,
                colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary)
            )
        },
        modifier = Modifier.fillMaxWidth().height(20.dp)
    )
}

@Composable
private fun PlaybackControls(state: PlayerUiState, viewModel: PlayerViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(22.dp))
        }
        FilledIconButton(
            onClick = { viewModel.togglePlayPause() },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Small vertical volume slider with +/- step buttons above and below it - sits to the
 * left of the skip-this-song row, kept deliberately narrow so it doesn't crowd that row's
 * checkbox/text/Skip button/delete icon.
 */
@Composable
private fun CompactVerticalVolumeControl(state: PlayerUiState, viewModel: PlayerViewModel) {
    fun step(delta: Float) {
        viewModel.setVolume((state.volume + delta).coerceIn(0f, 1f))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { step(0.1f) }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Increase volume",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Slider(
            value = state.volume,
            onValueChange = { viewModel.setVolume(it) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                activeTrackColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            // Built by rotating a standard Slider 270° and swapping its reported
            // width/height, since Material3 has no vertical Slider variant.
            modifier = Modifier
                .height(28.dp)
                .width(16.dp)
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(-placeable.width, 0)
                    }
                }
        )
        IconButton(onClick = { step(-0.1f) }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Decrease volume",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
