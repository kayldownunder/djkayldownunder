package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.FolderBrowseItem
import com.djkaylfromdownunder.musicplayer.data.Playlist
import com.djkaylfromdownunder.musicplayer.data.PlaylistViewMode
import com.djkaylfromdownunder.musicplayer.ui.theme.SurfaceCard
import android.net.Uri
import kotlinx.coroutines.launch

@Composable
fun FolderBrowserScreen(
    folderUri: Uri,
    folderName: String,
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    favoritesViewModel: FavoritesViewModel,
    viewPreferencesViewModel: ViewPreferencesViewModel,
    onNavigateToSubfolder: (Uri, String) -> Unit,
    onPlayLeaf: (Playlist) -> Unit,
    onShuffleAll: (() -> Unit)? = null
) {
    var items by remember(folderUri) { mutableStateOf<List<FolderBrowseItem>?>(null) }
    // Bumped after a folder delete completes, to force listFolderLevel to be re-fetched
    // (its cache was already invalidated) so the deleted entry disappears immediately.
    var refreshTick by remember(folderUri) { mutableIntStateOf(0) }
    // The single folder currently being deleted, if any - disables its row/card and shows
    // a spinner instead of the trash icon, so a slow delete (many nested files) can't be
    // triggered twice by accident.
    var deletingUri by remember(folderUri) { mutableStateOf<Uri?>(null) }
    val viewMode by viewPreferencesViewModel.viewMode.collectAsState()

    LaunchedEffect(folderUri, refreshTick) {
        items = libraryViewModel.listFolderLevel(folderUri)
    }

    fun deleteFolderItem(uri: Uri) {
        deletingUri = uri
        libraryViewModel.deleteFolder(uri) {
            deletingUri = null
            refreshTick++
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                folderName,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (onShuffleAll != null) {
                IconButton(
                    onClick = onShuffleAll,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle all songs")
                }
            }
        }

        val current = items
        when {
            current == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            current.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No albums or songs found in this folder.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            viewMode == PlaylistViewMode.LIST -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(current, key = { folderItemKey(it) }) { item ->
                        when (item) {
                            is FolderBrowseItem.SubFolder -> SubFolderListRow(
                                item = item,
                                isDeleting = deletingUri == item.uri,
                                libraryViewModel = libraryViewModel,
                                metadataViewModel = metadataViewModel,
                                favoritesViewModel = favoritesViewModel,
                                onClick = { onNavigateToSubfolder(item.uri, item.name) },
                                onDelete = { deleteFolderItem(item.uri) }
                            )
                            is FolderBrowseItem.LeafPlaylist -> PlaylistRow(
                                playlist = item.playlist,
                                metadataViewModel = metadataViewModel,
                                favoritesViewModel = favoritesViewModel,
                                onClick = { onPlayLeaf(item.playlist) },
                                isDeleting = deletingUri == item.playlist.folderUri,
                                onDelete = { deleteFolderItem(item.playlist.folderUri) }
                            )
                        }
                    }
                }
            }
            else -> {
                val isSmall = viewMode == PlaylistViewMode.SMALL
                val columns = if (isSmall) 3 else 2
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isSmall) 6.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isSmall) 10.dp else 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(current, key = { folderItemKey(it) }) { item ->
                        when (item) {
                            is FolderBrowseItem.SubFolder -> SubFolderGridCard(
                                item = item,
                                compact = viewMode == PlaylistViewMode.SMALL,
                                isDeleting = deletingUri == item.uri,
                                libraryViewModel = libraryViewModel,
                                metadataViewModel = metadataViewModel,
                                favoritesViewModel = favoritesViewModel,
                                onClick = { onNavigateToSubfolder(item.uri, item.name) },
                                onDelete = { deleteFolderItem(item.uri) }
                            )
                            is FolderBrowseItem.LeafPlaylist -> PlaylistGridCard(
                                playlist = item.playlist,
                                metadataViewModel = metadataViewModel,
                                favoritesViewModel = favoritesViewModel,
                                compact = viewMode == PlaylistViewMode.SMALL,
                                onClick = { onPlayLeaf(item.playlist) },
                                isDeleting = deletingUri == item.playlist.folderUri,
                                onDelete = { deleteFolderItem(item.playlist.folderUri) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Stable per-item key for Lazy list/grid item reuse, so scrolling doesn't discard remembered state. */
private fun folderItemKey(item: FolderBrowseItem): String = when (item) {
    is FolderBrowseItem.SubFolder -> "sub:${item.uri}"
    is FolderBrowseItem.LeafPlaylist -> "leaf:${item.playlist.folderUri}"
}

/**
 * Resolves this branching folder into a single playable Playlist of everything nested
 * inside it, then kicks off metadata fetching for that whole set - used by the sync icon
 * on a folder that isn't itself a playlist (it has subfolders, not tracks, directly
 * inside). Building the aggregate is a real SAF walk, so it's launched from a coroutine
 * rather than blocking composition. Also (re)builds this folder's collage thumbnail from
 * its subfolders' embedded art, the same way this button already refreshes metadata - see
 * MusicFolderRepository.generateCollageThumbnail.
 */
private fun syncSubFolder(
    scope: kotlinx.coroutines.CoroutineScope,
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    folderUri: Uri,
    isResolving: (Boolean) -> Unit,
    onCollageGenerated: (Uri?) -> Unit
) {
    scope.launch {
        isResolving(true)
        val aggregate = libraryViewModel.buildAggregatePlaylist(folderUri)
        if (aggregate != null) {
            metadataViewModel.fetchMetadataForPlaylist(aggregate)
        }
        val collage = libraryViewModel.generateCollageThumbnail(folderUri)
        if (collage != null) onCollageGenerated(collage)
        isResolving(false)
    }
}

@Composable
private fun SubFolderGridCard(
    item: FolderBrowseItem.SubFolder,
    compact: Boolean,
    isDeleting: Boolean,
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    favoritesViewModel: FavoritesViewModel,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    // isResolving only covers the brief SAF scan that builds the aggregate playlist;
    // the actual fetch runs asynchronously in MetadataFetchService afterwards, so its
    // live status has to be observed separately (and reactively - see isFetchingThis)
    // rather than read once as a plain boolean, or the spinner can get stuck on or off.
    var isResolving by remember(item.uri) { mutableStateOf(false) }
    // A previously-generated collage (see MusicFolderRepository.generateCollageThumbnail)
    // shows in place of the generic folder icon once found/built - null means "none yet",
    // not "still loading", since there may genuinely never be one until synced.
    var collageUri by remember(item.uri) { mutableStateOf<Uri?>(null) }
    // A custom cover image dropped directly into this folder (e.g. a picture placed
    // straight inside "AC/DC" alongside its Album subfolders) overrides the generated
    // collage automatically - no manual "set cover" step needed.
    var customCoverUri by remember(item.uri) { mutableStateOf<Uri?>(null) }
    LaunchedEffect(item.uri) {
        customCoverUri = libraryViewModel.findFolderCoverImage(item.uri)
        collageUri = libraryViewModel.findCollageThumbnail(item.uri)
    }
    val scope = rememberCoroutineScope()
    val favoriteKeys by favoritesViewModel.favoriteKeys.collectAsState()
    val favoriteKey = item.uri.toString()
    val isFavorite = favoriteKeys.contains(favoriteKey)
    val progressState = metadataViewModel.progress.collectAsState()
    val isFetchingThis by remember(item.name) {
        derivedStateOf {
            val p = progressState.value
            p.isRunning && p.playlistName == item.name
        }
    }
    val isFetching = isResolving || isFetchingThis
    // Fetches are meant to run one at a time (see MetadataFetchService - sequential on
    // purpose, to avoid hammering MusicBrainz). Gating the trigger on the global running
    // flag - not just "is it me" - stops a tap on a different card from starting a second
    // fetch concurrently.
    val isAnyFetchRunning by remember { derivedStateOf { progressState.value.isRunning } }
    val canSync = !isResolving && !isAnyFetchRunning
    val badgeSize = if (compact) 24.dp else 32.dp

    Column(modifier = Modifier.clickable(enabled = !isDeleting, onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            val cover = customCoverUri ?: collageUri
            if (cover != null) {
                coil.compose.AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 32.dp else 44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Heart sits top-left, sync sits top-right, delete sits bottom-right - kept
            // clear of both other actions since it's destructive. A whole branching
            // folder can be favorited or synced as one unit this way, even though it has
            // no tracks directly inside itself.
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
            // Folders that contain only nested subfolders (no songs directly inside) have
            // nothing of their own to fetch metadata for, so this shortcut is hidden there.
            if (item.hasDirectTracks) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                        .clickable(enabled = canSync) {
                            syncSubFolder(
                                scope, libraryViewModel, metadataViewModel, item.uri,
                                isResolving = { isResolving = it },
                                onCollageGenerated = { collageUri = it }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(if (compact) 12.dp else 16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Fetch metadata for ${item.name}",
                            modifier = Modifier.size(if (compact) 14.dp else 18.dp)
                        )
                    }
                }
            }
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
                        contentDescription = "Delete ${item.name}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(if (compact) 14.dp else 18.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
        Text(
            text = item.name,
            style = if (compact) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun SubFolderListRow(
    item: FolderBrowseItem.SubFolder,
    isDeleting: Boolean,
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    favoritesViewModel: FavoritesViewModel,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    // isResolving only covers the brief SAF scan that builds the aggregate playlist;
    // the actual fetch runs asynchronously in MetadataFetchService afterwards, so its
    // live status has to be observed separately (and reactively - see isFetchingThis)
    // rather than read once as a plain boolean, or the spinner can get stuck on or off.
    var isResolving by remember(item.uri) { mutableStateOf(false) }
    // A previously-generated collage (see MusicFolderRepository.generateCollageThumbnail)
    // shows in place of the generic folder icon once found/built.
    var collageUri by remember(item.uri) { mutableStateOf<Uri?>(null) }
    // A custom cover image dropped directly into this folder overrides the generated
    // collage automatically - no manual "set cover" step needed.
    var customCoverUri by remember(item.uri) { mutableStateOf<Uri?>(null) }
    LaunchedEffect(item.uri) {
        customCoverUri = libraryViewModel.findFolderCoverImage(item.uri)
        collageUri = libraryViewModel.findCollageThumbnail(item.uri)
    }
    val scope = rememberCoroutineScope()
    val favoriteKeys by favoritesViewModel.favoriteKeys.collectAsState()
    val favoriteKey = item.uri.toString()
    val isFavorite = favoriteKeys.contains(favoriteKey)
    val progressState = metadataViewModel.progress.collectAsState()
    val isFetchingThis by remember(item.name) {
        derivedStateOf {
            val p = progressState.value
            p.isRunning && p.playlistName == item.name
        }
    }
    val isFetching = isResolving || isFetchingThis

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
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            val cover = customCoverUri ?: collageUri
            if (cover != null) {
                coil.compose.AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { favoritesViewModel.toggleFavorite(favoriteKey) }) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) FavoriteRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Folders that contain only nested subfolders (no songs directly inside) have
        // nothing of their own to fetch metadata for, so this shortcut is hidden there.
        if (item.hasDirectTracks) {
            if (isFetching) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = {
                    syncSubFolder(
                        scope, libraryViewModel, metadataViewModel, item.uri,
                        isResolving = { isResolving = it },
                        onCollageGenerated = { collageUri = it }
                    )
                }) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = "Fetch metadata for ${item.name}")
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (isDeleting) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${item.name}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
