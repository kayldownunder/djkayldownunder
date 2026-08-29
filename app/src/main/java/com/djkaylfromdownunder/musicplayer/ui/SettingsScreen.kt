package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.djkaylfromdownunder.musicplayer.data.BackgroundTarget
import kotlinx.coroutines.launch

/**
 * Just the functional buttons, no section-title labels above them - which of
 * [ALL_SETTINGS_BLOCKS] appear and in what order comes from [SettingsLayoutViewModel].
 * Long-press and drag any shortcut to reorder it in place (see [DragReorderGrid], shared
 * with the bottom dock) - the order saves as you drag, no separate editor screen needed.
 * Laid out as a compact 2-column grid so every shortcut fits on one screen without
 * scrolling on a typical device (the grid scrolls on its own as a fallback - e.g. a very
 * large chosen font size could still overflow it).
 */
@Composable
fun SettingsScreen(
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    themeViewModel: ThemeViewModel,
    fontPreferencesViewModel: FontPreferencesViewModel,
    settingsLayoutViewModel: SettingsLayoutViewModel,
    dockPreferencesViewModel: DockPreferencesViewModel,
    buttonColorViewModel: ButtonColorViewModel,
    audioNormalizationViewModel: AudioNormalizationViewModel,
    onNavigateToSkipReview: () -> Unit
) {
    val rootUri by libraryViewModel.rootUri.collectAsState()
    val metadataProgress by metadataViewModel.progress.collectAsState()
    var isScanningLibrary by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showDockVisibilitySheet by remember { mutableStateOf(false) }
    var isConsolidatingArtwork by remember { mutableStateOf(false) }
    var consolidateResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val order by settingsLayoutViewModel.order.collectAsState()
    val blocks = remember(order) {
        val byId = ALL_SETTINGS_BLOCKS.associateBy { it.id }
        order.mapNotNull { byId[it] }
    }
    val blockIds = blocks.map { it.id }
    val dragState = rememberGridReorderState { newOrder -> settingsLayoutViewModel.setOrder(newOrder) }

    fun fetchAllMetadata() {
        scope.launch {
            isScanningLibrary = true
            val playlists = libraryViewModel.scanAllPlaylists()
            isScanningLibrary = false
            if (playlists.isNotEmpty()) {
                metadataViewModel.fetchMetadataForAllPlaylists(playlists)
            }
        }
    }

    fun consolidateArtwork() {
        scope.launch {
            isConsolidatingArtwork = true
            consolidateResult = null
            val moved = libraryViewModel.consolidateArtworkImages()
            isConsolidatingArtwork = false
            consolidateResult = when {
                moved == null -> "Choose a music folder first."
                moved == 0 -> "No stray album art found."
                moved == 1 -> "Moved 1 image into MetaData."
                else -> "Moved $moved images into MetaData."
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Only this row needs it (it's the one screen in the app putting an interactive
            // button, not just title text, right up against the top edge) - a status-bar
            // icon sitting on top of a real tap target would make it unreliable to hit.
            Box(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 24.dp)
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = { showFontDialog = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Font & Appearance Settings",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        val displayBlocks = dragState.displayOrder(blocks) { it.id }
        itemsIndexed(displayBlocks, key = { _, block -> block.id }) { _, block ->
            val isDragging = dragState.isDragging(block.id)
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .dragReorderTransform(dragState, block.id)
                    .dragReorderItem(dragState, block.id) { blockIds }
            ) {
                SettingsBlockButton(
                    block = block,
                    libraryViewModel = libraryViewModel,
                    themeViewModel = themeViewModel,
                    buttonColorViewModel = buttonColorViewModel,
                    rootUri = rootUri,
                    metadataRunning = metadataProgress.isRunning,
                    isScanningLibrary = isScanningLibrary,
                    isConsolidatingArtwork = isConsolidatingArtwork,
                    consolidateResult = consolidateResult,
                    dragEnabled = !dragState.isAnyDragging,
                    audioNormalizationViewModel = audioNormalizationViewModel,
                    onFetchAllMetadata = ::fetchAllMetadata,
                    onConsolidateArtwork = ::consolidateArtwork,
                    onNavigateToSkipReview = onNavigateToSkipReview,
                    onShowDockVisibility = { showDockVisibilitySheet = true }
                )
            }
        }

        if (metadataProgress.isRunning) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))

                    val label = if (metadataProgress.isBatch) {
                        "Playlist ${metadataProgress.playlistIndex}/${metadataProgress.totalPlaylists}: ${metadataProgress.playlistName ?: ""}"
                    } else {
                        metadataProgress.playlistName ?: "Fetching…"
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(4.dp))

                    val trackProgress = if (metadataProgress.totalTracks > 0) {
                        metadataProgress.completedTracks.toFloat() / metadataProgress.totalTracks.toFloat()
                    } else 0f

                    LinearProgressIndicator(
                        progress = { trackProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Track ${metadataProgress.completedTracks}/${metadataProgress.totalTracks}" +
                            (metadataProgress.currentTrackName?.let { " — $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showFontDialog) {
        FontSettingsDialog(
            fontPreferencesViewModel = fontPreferencesViewModel,
            onDismiss = { showFontDialog = false }
        )
    }

    if (showDockVisibilitySheet) {
        DockVisibilitySheet(
            dockPreferencesViewModel = dockPreferencesViewModel,
            onDismiss = { showDockVisibilitySheet = false }
        )
    }
}

@Composable
private fun SettingsBlockButton(
    block: SettingsBlockDef,
    libraryViewModel: MusicLibraryViewModel,
    themeViewModel: ThemeViewModel,
    buttonColorViewModel: ButtonColorViewModel,
    rootUri: android.net.Uri?,
    metadataRunning: Boolean,
    isScanningLibrary: Boolean,
    isConsolidatingArtwork: Boolean,
    consolidateResult: String?,
    dragEnabled: Boolean,
    audioNormalizationViewModel: AudioNormalizationViewModel,
    onFetchAllMetadata: () -> Unit,
    onConsolidateArtwork: () -> Unit,
    onNavigateToSkipReview: () -> Unit,
    onShowDockVisibility: () -> Unit
) {
    val fillWidth = Modifier.fillMaxWidth()
    when (block.id) {
        "music_library" -> ChooseMusicFolderButton(
            viewModel = libraryViewModel,
            modifier = fillWidth,
            label = block.label,
            enabled = dragEnabled,
            buttonColorViewModel = buttonColorViewModel
        )
        "metadata" -> Column(modifier = fillWidth) {
            if (isScanningLibrary) {
                Text(
                    "Scanning library…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Button(
                onClick = onFetchAllMetadata,
                enabled = dragEnabled && rootUri != null && !metadataRunning && !isScanningLibrary,
                colors = shortcutButtonColors(buttonColorViewModel),
                modifier = fillWidth
            ) {
                Text(block.label)
            }
        }
        "consolidate_artwork" -> Column(modifier = fillWidth) {
            Button(
                onClick = onConsolidateArtwork,
                enabled = dragEnabled && rootUri != null && !isConsolidatingArtwork,
                colors = shortcutButtonColors(buttonColorViewModel),
                modifier = fillWidth
            ) {
                Text(if (isConsolidatingArtwork) "Moving images…" else block.label)
            }
            if (consolidateResult != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    consolidateResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        "library_background" -> BackgroundShortcutButton(
            target = BackgroundTarget.LIBRARY,
            label = block.label,
            themeViewModel = themeViewModel,
            buttonColorViewModel = buttonColorViewModel,
            modifier = fillWidth,
            enabled = dragEnabled
        )
        "settings_background" -> BackgroundShortcutButton(
            target = BackgroundTarget.SETTINGS,
            label = block.label,
            themeViewModel = themeViewModel,
            buttonColorViewModel = buttonColorViewModel,
            modifier = fillWidth,
            enabled = dragEnabled
        )
        "dock_visibility" -> Button(
            onClick = onShowDockVisibility,
            enabled = dragEnabled,
            colors = shortcutButtonColors(buttonColorViewModel),
            modifier = fillWidth
        ) {
            Text(block.label)
        }
        "skip_review" -> Button(
            onClick = onNavigateToSkipReview,
            enabled = dragEnabled,
            colors = shortcutButtonColors(buttonColorViewModel),
            modifier = fillWidth
        ) {
            Text(block.label)
        }
        "shortcut_button_color" -> ShortcutButtonColorPicker(
            buttonColorViewModel = buttonColorViewModel,
            modifier = fillWidth,
            label = block.label,
            enabled = dragEnabled
        )
        "audio_normalization" -> {
            val isEnabled by audioNormalizationViewModel.isEnabled.collectAsState()
            Button(
                onClick = { audioNormalizationViewModel.toggle() },
                enabled = dragEnabled,
                colors = shortcutButtonColors(buttonColorViewModel),
                modifier = fillWidth
            ) {
                Text(if (isEnabled) "${block.label}: On" else "${block.label}: Off")
            }
        }
    }
}
