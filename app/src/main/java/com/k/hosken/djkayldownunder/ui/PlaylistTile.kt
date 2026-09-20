package com.k.hosken.djkayldownunder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k.hosken.djkayldownunder.data.Playlist
import com.k.hosken.djkayldownunder.ui.theme.SurfaceCard

val FavoriteRed = Color(0xFFE53935)

/**
 * Shared text style for every name/subtitle text field on the Library-style screens
 * (Library, Play Lists, Favorites) - one consistent size/weight everywhere (name and track
 * count/progress line alike) rather than each card/row picking its own typography step, so
 * the user's Playlist Text size setting (see FontPrefs.albumTextSizeSp) scales all of it
 * uniformly. Callers still set their own `color` per field to keep secondary text visually
 * muted without reintroducing a size/weight difference.
 */
val LibraryItemTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)

/**
 * Square cover-art card used in grid layouts - shared by Library, Play Lists, and Favorites.
 * [onDelete] is only non-null in the Library, where a playlist card represents a real
 * on-disk folder that can be permanently removed; leave it null elsewhere.
 */
@Composable
fun PlaylistGridCard(
    playlist: Playlist,
    metadataViewModel: MetadataViewModel,
    favoritesViewModel: FavoritesViewModel,
    compact: Boolean,
    onClick: () -> Unit,
    isDeleting: Boolean = false,
    onDelete: (() -> Unit)? = null,
    showFavorite: Boolean = true
) {
    // progress is a single StateFlow shared by every visible card, ticking on every track
    // fetched anywhere in the app. Reading it directly here would recompose every card on
    // every tick; deriving just the "is it me" boolean means only the one card whose
    // fetch state actually flips gets recomposed - everyone else is untouched.
    val progressState = metadataViewModel.progress.collectAsState()
    val isFetchingThis by remember(playlist.name) {
        derivedStateOf {
            val p = progressState.value
            p.isRunning && p.playlistName == playlist.name
        }
    }
    // Fetches are meant to run one at a time (see MetadataFetchService - sequential on
    // purpose, to avoid hammering MusicBrainz). Gating the trigger on the global running
    // flag - not just "is it me" - stops a tap on a different card from starting a second
    // fetch concurrently. Derived separately from isFetchingThis so cards not currently
    // fetching still only recompose when a fetch starts/stops, not on every track tick.
    val isAnyFetchRunning by remember { derivedStateOf { progressState.value.isRunning } }
    val isSynced = !isFetchingThis && metadataViewModel.isPlaylistSynced(playlist)
    val favoriteKeys by favoritesViewModel.favoriteKeys.collectAsState()
    val favoriteKey = playlist.folderUri.toString()
    val isFavorite = favoriteKeys.contains(favoriteKey)
    val badgeSize = if (compact) 24.dp else 32.dp
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.clickable(enabled = !isDeleting, onClick = onClick)) {
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

            // Heart sits top-left, sync sits top-right (as far apart as the card allows),
            // delete sits bottom-right below the sync button - the destructive action is
            // kept clear of both other actions and away from the top row entirely.
            if (showFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                        .clickable { favoritesViewModel.toggleFavorite(favoriteKey) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) FavoriteRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (compact) 14.dp else 18.dp)
                    )
                }
            }
            if (!isSynced) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                        .clickable(enabled = !isAnyFetchRunning) {
                            metadataViewModel.fetchMetadataForPlaylist(playlist)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isFetchingThis) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(if (compact) 12.dp else 16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Fetch metadata for ${playlist.name}",
                            modifier = Modifier.size(if (compact) 14.dp else 18.dp)
                        )
                    }
                }
            }
            if (onDelete != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                        .clickable(enabled = !isDeleting) { showDeleteDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(if (compact) 12.dp else 16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete ${playlist.name}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(if (compact) 14.dp else 18.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
        // Name and subtitle share one text field style (LibraryItemTextStyle, see below)
        // regardless of compact/regular grid density - one consistent size/weight across
        // every text field on the Library page, sized by the user's Playlist Text setting.
        // Only the color differs, to keep the count/progress line visually secondary.
        Text(text = playlist.name, style = LibraryItemTextStyle, maxLines = 1)
        // Dropped in compact/Small mode (unless actively fetching) to keep the dense
        // 3-per-row grid's tiles short, so more albums fit on screen at once.
        if (isFetchingThis || !compact) {
            Text(
                // Reading progressState.value here (rather than a value hoisted above) means
                // only the actively-fetching card's Text recomposes on each tick - every other
                // card takes the untouched trackCount branch below.
                text = if (isFetchingThis) {
                    val p = progressState.value
                    "Fetching ${p.completedTracks}/${p.totalTracks}…"
                } else {
                    "${playlist.trackCount} tracks"
                },
                style = LibraryItemTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                showDeleteDialog = false
                onDelete?.invoke()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/**
 * Single-row layout used in List view mode - heart sits at the end of the row.
 * [onDelete] is only non-null in the Library, where a playlist row represents a real
 * on-disk folder that can be permanently removed; leave it null elsewhere.
 */
@Composable
fun PlaylistRow(
    playlist: Playlist,
    metadataViewModel: MetadataViewModel,
    favoritesViewModel: FavoritesViewModel,
    onClick: () -> Unit,
    isDeleting: Boolean = false,
    onDelete: (() -> Unit)? = null,
    showFavorite: Boolean = true
) {
    val favoriteKeys by favoritesViewModel.favoriteKeys.collectAsState()
    val favoriteKey = playlist.folderUri.toString()
    val isFavorite = favoriteKeys.contains(favoriteKey)
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDeleting, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
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
            Text(text = playlist.name, style = LibraryItemTextStyle, maxLines = 1)
            Text(
                text = "${playlist.trackCount} tracks",
                style = LibraryItemTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showFavorite) {
            IconButton(onClick = { favoritesViewModel.toggleFavorite(favoriteKey) }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) FavoriteRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onDelete != null) {
            Spacer(modifier = Modifier.width(8.dp))
            if (isDeleting) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete ${playlist.name}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                showDeleteDialog = false
                onDelete?.invoke()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
