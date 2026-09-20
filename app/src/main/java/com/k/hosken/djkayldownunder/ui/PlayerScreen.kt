package com.k.hosken.djkayldownunder.ui

import androidx.media3.common.util.UnstableApi

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k.hosken.djkayldownunder.data.Playlist
import com.k.hosken.djkayldownunder.data.Track
import com.k.hosken.djkayldownunder.data.parentFolderKey
import com.k.hosken.djkayldownunder.ui.theme.AccentCoral
import com.k.hosken.djkayldownunder.ui.theme.AccentCoralDim
import com.k.hosken.djkayldownunder.ui.theme.BackgroundBlack
import com.k.hosken.djkayldownunder.ui.theme.SurfaceCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

// Artwork shrinks from this height down to nothing as the track list below is scrolled.
private val MAX_ARTWORK_HEIGHT = 380.dp

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    metadataViewModel: MetadataViewModel,
    skipListViewModel: SkipListViewModel,
    libraryViewModel: MusicLibraryViewModel,
    customPlaylistViewModel: CustomPlaylistViewModel,
    buttonColorViewModel: ButtonColorViewModel,
    onPlayRecommendation: (Playlist) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // Skip-this-song and favorite state for the current track, hoisted here so both the
    // top icon row (Skip/Heart/Delete) and the skip-checkbox row under the album artwork
    // can share them.
    val folderUri = state.currentPlaylistFolderUri
    val trackUri = state.currentTrack?.uri?.toString()
    var skipRefreshTick by remember { mutableIntStateOf(0) }
    var isSkipMarked by remember(folderUri, trackUri) {
        mutableStateOf(
            if (folderUri != null && trackUri != null) skipListViewModel.isSkipped(folderUri, trackUri) else false
        )
    }
    var isFavorite by remember(trackUri) {
        mutableStateOf(trackUri?.let { customPlaylistViewModel.isFavoriteTrack(it) } ?: false)
    }
    var showDeleteDialog by remember { mutableStateOf(false) }

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

    // The nested scroll connection above only fires when the track list itself is
    // dragged, since NestedScrollConnection.onPreScroll is only invoked by descendants
    // that are themselves scrollable - a plain Box/Column has nothing to dispatch. This
    // gives the header area (artwork, skip checkbox row, title/album text, divider) its
    // own scrollable node with nothing of its own to scroll, purely so dragging it feeds
    // the exact same nestedScrollConnection.onPreScroll above (same sign convention
    // already proven correct by list-dragging), rather than duplicating that math with a
    // separate draggable and risking a mismatched delta sign.
    val headerScrollState = rememberScrollableState { delta -> delta }

    // Switching tracks (e.g. via Next/Previous or tapping a queue row) re-expands the
    // artwork, so it doesn't stay collapsed from browsing the list on the previous track.
    LaunchedEffect(state.currentTrack?.uri) {
        artHeightPx = maxArtHeightPx
    }

    // "Animate": when selected, jitters the album artwork in a little rhythmic vibration
    // while the track is actually playing (paused tracks sit still). This is a decorative
    // shake, not driven by real audio analysis - PlayerViewModel only holds a MediaController,
    // not the underlying ExoPlayer, so there's no audio session to attach a visualizer to.
    // rememberSaveable (not remember) so the selection survives the screen turning off -
    // which can recreate this screen (config change, or the process being killed in the
    // background while the lock screen is up) - and the shake resumes on its own via the
    // LaunchedEffect below once state.isPlaying goes true again, with no need to reselect it.
    var isAnimateEnabled by rememberSaveable { mutableStateOf(false) }
    val shakeOffsetX = remember { Animatable(0f) }
    val shakeOffsetY = remember { Animatable(0f) }
    val shakeRotation = remember { Animatable(0f) }
    LaunchedEffect(isAnimateEnabled, state.isPlaying) {
        if (isAnimateEnabled && state.isPlaying) {
            val maxOffsetPx = with(density) { 6.dp.toPx() }
            while (isActive) {
                launch { shakeOffsetX.animateTo((Random.nextFloat() * 2f - 1f) * maxOffsetPx, tween(70)) }
                launch { shakeOffsetY.animateTo((Random.nextFloat() * 2f - 1f) * maxOffsetPx, tween(70)) }
                launch { shakeRotation.animateTo(Random.nextFloat() * 4f - 2f, tween(70)) }
                delay(70)
            }
        } else {
            shakeOffsetX.animateTo(0f, tween(150))
            shakeOffsetY.animateTo(0f, tween(150))
            shakeRotation.animateTo(0f, tween(150))
        }
    }

    // Aligns Skip under the "N" of "Now Playing" and Delete under the "g" of "Playing":
    // the title's own left edge plus its text layout's per-character bounding boxes give
    // the exact on-screen x-position of each letter (independent of font/device/whether
    // the shortcut icon is present), and each button's *default* (pre-offset) position -
    // captured once, the first time it's laid out - is what the offset is measured from.
    var titleTextLeftPx by remember { mutableStateOf<Float?>(null) }
    var titleLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var skipDefaultCenterPx by remember { mutableStateOf<Float?>(null) }
    var deleteDefaultCenterPx by remember { mutableStateOf<Float?>(null) }

    val nLetterCenterPx = titleLayoutResult?.let { layout ->
        titleTextLeftPx?.let { left -> left + layout.getBoundingBox(0).center.x }
    }
    val gLetterCenterPx = titleLayoutResult?.let { layout ->
        titleTextLeftPx?.let { left ->
            left + layout.getBoundingBox(layout.layoutInput.text.length - 1).center.x
        }
    }
    val skipDefault = skipDefaultCenterPx
    val deleteDefault = deleteDefaultCenterPx
    val skipOffset = if (nLetterCenterPx != null && skipDefault != null) {
        with(density) { (nLetterCenterPx - skipDefault).toDp() }
    } else 0.dp
    val deleteOffset = if (gLetterCenterPx != null && deleteDefault != null) {
        with(density) { (gLetterCenterPx - deleteDefault).toDp() }
    } else 0.dp

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
        // A Row with a weighted title (rather than a Box with independently-centered
        // children) so the title never overlaps the "Random skip all albums" shortcut -
        // which a Box-based header would do, since "Now Playing" at headlineLarge is
        // nearly as wide as the screen on its own. The shortcut sits at the far right -
        // the screen's pre-existing "shuffle current album" toggle used to sit there too
        // but was removed from here since its icon was easily confused with this new
        // shortcut's own shuffle icon.
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp, start = 24.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Now Playing",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { titleTextLeftPx = it.positionInRoot().x },
                onTextLayout = { titleLayoutResult = it }
            )
            if (allPlaylists.isNotEmpty()) {
                RandomSkipAllShortcut(
                    isActive = state.isShuffleAllActive,
                    onClick = { viewModel.toggleShuffleAllAlbums(allPlaylists) },
                    buttonColorViewModel = buttonColorViewModel
                )
            }
        }

        // Track actions: Skip (now where the volume control used to sit), Heart to
        // favorite the track, and - at the far right, matching the Skip button's size -
        // Delete. The "skip this song next time" checkbox sits separately, directly
        // under the album artwork below.
        if (folderUri != null && trackUri != null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(40.dp)
            ) {
                Button(
                    onClick = { viewModel.skipNext() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .height(40.dp)
                        .onGloballyPositioned {
                            if (skipDefaultCenterPx == null) {
                                skipDefaultCenterPx = it.positionInRoot().x + it.size.width / 2f
                            }
                        }
                        .offset(x = skipOffset)
                ) {
                    Text("Skip")
                }
                // Centered on the row itself (not evenly spaced between Skip and Delete,
                // which would land it off-center since Skip and Delete aren't the same
                // width) so it sits in the true middle of the screen.
                IconButton(
                    onClick = { isFavorite = customPlaylistViewModel.toggleFavoriteTrack(trackUri) },
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        tint = if (isFavorite) FavoriteRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .onGloballyPositioned {
                            if (deleteDefaultCenterPx == null) {
                                deleteDefaultCenterPx = it.positionInRoot().x + it.size.width / 2f
                            }
                        }
                        .offset(x = deleteOffset)
                ) {
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
            // Header area (artwork, skip checkbox, title/album, divider): wrapped in its
            // own vertical drag detector so dragging anywhere here - not just on the
            // track list below - collapses/expands the artwork the same way.
            Column(
                modifier = Modifier.scrollable(
                    state = headerScrollState,
                    orientation = Orientation.Vertical
                )
            ) {
                // Top half: collapsible album artwork, plus the skip-checkbox/Animate row
                // directly under it - both shrink to nothing and fade out together as the
                // track list is scrolled, rather than the row lingering after the art itself
                // has disappeared.
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
                                .graphicsLayer {
                                    translationX = shakeOffsetX.value
                                    translationY = shakeOffsetY.value
                                    rotationZ = shakeRotation.value
                                }
                        ) {
                            TrackArtwork(
                                track = state.currentTrack,
                                metadataViewModel = metadataViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // "Skip This Song Next Time" (left) and the Animate toggle (far right)
                    // - matching pill buttons, same row.
                    if (folderUri != null && trackUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .graphicsLayer { alpha = artFraction },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToggleChip(
                                selected = isSkipMarked,
                                icon = Icons.Default.SkipNext,
                                label = "Skip This Song Next Time",
                                onClick = {
                                    val checked = !isSkipMarked
                                    isSkipMarked = checked
                                    skipListViewModel.setSkipped(folderUri, trackUri, checked)
                                    skipRefreshTick++
                                }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            ToggleChip(
                                selected = isAnimateEnabled,
                                icon = Icons.Default.Vibration,
                                label = "Animate",
                                isPulsing = isAnimateEnabled && state.isPlaying,
                                onClick = { isAnimateEnabled = !isAnimateEnabled }
                            )
                        }
                    }
                }

                // Title/album - stays with the artwork above the track list. The actual
                // transport controls and progress bar live in the slim bar pinned to the
                // bottom of the whole screen (see CompactPlaybackBar below).
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
            }

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
        // progress line plus previous/play-pause/next. Never auto-hidden: unlike the
        // Library/Play Lists dock, these are the only transport controls on this screen,
        // so they must stay reachable at all times.
        CompactPlaybackBar(state = state, viewModel = viewModel)
    }
}

/**
 * A pill toggle used for the Skip/Animate row under the artwork: fills with the app's coral
 * accent gradient once selected (echoing the Skip/Play buttons' own color) instead of a plain
 * Material chip or checkbox. [isPulsing] (Animate only) keeps the icon gently pulsing for as
 * long as the artwork itself is actually vibrating - a passive reminder that it's live even
 * when you're not looking at the artwork above.
 */
@Composable
private fun ToggleChip(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    isPulsing: Boolean = false,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "toggleChipPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val shape = RoundedCornerShape(50)
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(Brush.horizontalGradient(listOf(AccentCoral, AccentCoralDim)), shape)
                } else {
                    Modifier
                        .background(Color.Transparent, shape)
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), shape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
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
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 16.dp)
    ) {
        PlaybackSlider(state = state, viewModel = viewModel)
        // Matches the 16dp bottom padding above, so the controls row sits with equal
        // breathing room between the slider above it and the screen edge below it.
        Spacer(modifier = Modifier.height(16.dp))
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
            // Raised above the prev/next row for a floating look - the 16dp gap above the
            // row (see CompactPlaybackBar) leaves enough headroom that this doesn't
            // overlap the slider the way a bigger offset did before that gap existed.
            modifier = Modifier.offset(y = (-4).dp).size(44.dp),
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
