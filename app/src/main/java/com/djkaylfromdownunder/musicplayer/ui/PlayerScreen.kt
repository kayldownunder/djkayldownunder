package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.Track
import com.djkaylfromdownunder.musicplayer.ui.theme.AccentCoralDim
import com.djkaylfromdownunder.musicplayer.ui.theme.BackgroundBlack

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    metadataViewModel: MetadataViewModel,
    skipListViewModel: SkipListViewModel,
    onCollapse: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

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

        // Now-playing controls. No weight here - the section sizes to its actual content
        // (artwork row + slider + controls + volume) rather than a fixed fraction of the
        // screen, so the artwork can be bigger without the playback controls silently
        // clipping off-screen on shorter devices. Since this no longer forces a 50/50
        // split, it ends up noticeably shorter than before while leaving Previous/Play/
        // Delete/Next and the volume slider always fully visible.
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

            Row(verticalAlignment = Alignment.Top) {
                // Artwork, title, and album stacked together so they read as one unit.
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        TrackArtwork(
                            track = state.currentTrack,
                            metadataViewModel = metadataViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = songTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (albumName != null) {
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Vertical so it sits clear of the transport controls below rather than
                // competing with them for width.
                VerticalVolumeControl(state = state, viewModel = viewModel)
            }

            Spacer(modifier = Modifier.height(8.dp))

            PlaybackSlider(state = state, viewModel = viewModel)

            Spacer(modifier = Modifier.height(4.dp))

            PlaybackControls(state = state, viewModel = viewModel, buttonSize = 38.dp)
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )

        // Playlist: every track in the folder, with artwork, bold title, and artist name
        // (pulled from fetched metadata where available). Skipped tracks show with a line
        // through them; tapping one un-skips it and plays it. Touching and dragging
        // anywhere in the list scrolls it, same as any normal scrollable list. weight(1f)
        // here claims all the space the now-playing section above didn't need, so more
        // tracks are visible at once.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "PLAYLIST",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            key(skipRefreshTick) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                }
            }
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

/**
 * Vertical volume slider, sized to sit beside the artwork/title block without reaching
 * down into the transport controls below it. Built by rotating a standard Slider 270°
 * and swapping its reported width/height, since Material3 has no vertical Slider variant.
 */
@Composable
private fun VerticalVolumeControl(state: PlayerUiState, viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = state.volume,
            onValueChange = { viewModel.setVolume(it) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                activeTrackColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .height(72.dp)
                .width(24.dp)
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
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.VolumeDown,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
