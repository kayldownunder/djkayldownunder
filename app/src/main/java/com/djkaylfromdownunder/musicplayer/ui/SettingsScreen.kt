package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.BackgroundTarget
import com.djkaylfromdownunder.musicplayer.ui.theme.SettingsTypography
import kotlinx.coroutines.launch

/**
 * Just the functional shortcuts, no section-title labels above them - which of
 * [ALL_SETTINGS_BLOCKS] appear and in what order comes from [SettingsLayoutViewModel].
 * Reordering is long-press-and-tap, matching [DockSettingsScreen]: a long press on a shortcut
 * reveals up/down arrows on that row, moves are staged locally, and nothing is persisted to
 * [SettingsLayoutViewModel] until the Save button at the bottom is tapped - tapping it commits
 * the new order and collapses back to normal (non-editing) operation.
 * Each shortcut is a single full-width row (icon on the left, label next to it, transparent
 * background - see [SettingsShortcutRow]) rather than a colored button, so the list reads
 * like a plain settings menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    themeViewModel: ThemeViewModel,
    fontPreferencesViewModel: FontPreferencesViewModel,
    settingsLayoutViewModel: SettingsLayoutViewModel,
    buttonColorViewModel: ButtonColorViewModel,
    audioNormalizationViewModel: AudioNormalizationViewModel,
    onNavigateToSkipReview: () -> Unit,
    onNavigateToDockSettings: () -> Unit
) {
    val rootUri by libraryViewModel.rootUri.collectAsState()
    val fontPrefs by fontPreferencesViewModel.fontPrefs.collectAsState()
    val metadataProgress by metadataViewModel.progress.collectAsState()
    var isScanningLibrary by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var isConsolidatingArtwork by remember { mutableStateOf(false) }
    var consolidateResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val order by settingsLayoutViewModel.order.collectAsState()
    val byId = remember { ALL_SETTINGS_BLOCKS.associateBy { it.id } }

    var pendingOrder by remember(order) { mutableStateOf(order) }
    var expandedBlockId by remember { mutableStateOf<String?>(null) }

    val blocks = remember(pendingOrder) { pendingOrder.mapNotNull { byId[it] } }
    val hasUnsavedChanges = pendingOrder != order

    fun moveBlock(id: String, delta: Int) {
        val list = pendingOrder.toMutableList()
        val fromIndex = list.indexOf(id)
        val toIndex = fromIndex + delta
        if (fromIndex < 0 || toIndex < 0 || toIndex >= list.size) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        pendingOrder = list
    }

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

    SettingsTypography(fontPrefs) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 24.dp)
                    ) {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                if (hasUnsavedChanges) {
                    item {
                        Text(
                            "Long-press a shortcut to move it up or down, then tap Save.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                            audioNormalizationViewModel = audioNormalizationViewModel,
                            onFetchAllMetadata = ::fetchAllMetadata,
                            onConsolidateArtwork = ::consolidateArtwork,
                            onNavigateToSkipReview = onNavigateToSkipReview,
                            onShowDockVisibility = onNavigateToDockSettings,
                            onShowFontSettings = { showFontDialog = true },
                            onLongClick = {
                                expandedBlockId = if (expandedBlockId == block.id) null else block.id
                            }
                        )
                        if (expandedBlockId == block.id) {
                            val canMoveUp = index > 0
                            val canMoveDown = index < blocks.size - 1
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                IconButton(
                                    onClick = { moveBlock(block.id, -1) },
                                    enabled = canMoveUp
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move ${block.label} up",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = if (canMoveUp) 1f else 0.38f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { moveBlock(block.id, 1) },
                                    enabled = canMoveDown
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move ${block.label} down",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = if (canMoveDown) 1f else 0.38f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (metadataProgress.isRunning) {
                    item {
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

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (hasUnsavedChanges) {
                Button(
                    onClick = {
                        settingsLayoutViewModel.setOrder(pendingOrder)
                        expandedBlockId = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()
                ) {
                    Text("Save")
                }
            }
        }

        if (showFontDialog) {
            FontSettingsDialog(
                fontPreferencesViewModel = fontPreferencesViewModel,
                onDismiss = { showFontDialog = false }
            )
        }
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
    audioNormalizationViewModel: AudioNormalizationViewModel,
    onFetchAllMetadata: () -> Unit,
    onConsolidateArtwork: () -> Unit,
    onNavigateToSkipReview: () -> Unit,
    onShowDockVisibility: () -> Unit,
    onShowFontSettings: () -> Unit,
    onLongClick: () -> Unit
) {
    val fillWidth = Modifier.fillMaxWidth()
    when (block.id) {
        "font_appearance" -> SettingsShortcutRow(
            icon = block.icon,
            label = block.label,
            onClick = onShowFontSettings,
            onLongClick = onLongClick,
            modifier = fillWidth
        )
        "music_library" -> ChooseMusicFolderButton(
            viewModel = libraryViewModel,
            modifier = fillWidth,
            label = block.label,
            icon = block.icon,
            buttonColorViewModel = buttonColorViewModel,
            onLongClick = onLongClick
        )
        "metadata" -> Column(modifier = fillWidth) {
            SettingsShortcutRow(
                icon = block.icon,
                label = block.label,
                enabled = rootUri != null && !metadataRunning && !isScanningLibrary,
                onClick = onFetchAllMetadata,
                onLongClick = onLongClick
            )
            if (isScanningLibrary) {
                Text(
                    "Scanning library…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 38.dp, bottom = 4.dp)
                )
            }
        }
        "consolidate_artwork" -> Column(modifier = fillWidth) {
            SettingsShortcutRow(
                icon = block.icon,
                label = if (isConsolidatingArtwork) "Moving images…" else block.label,
                enabled = rootUri != null && !isConsolidatingArtwork,
                onClick = onConsolidateArtwork,
                onLongClick = onLongClick
            )
            if (consolidateResult != null) {
                Text(
                    consolidateResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 38.dp, bottom = 4.dp)
                )
            }
        }
        "library_background" -> BackgroundShortcutButton(
            target = BackgroundTarget.LIBRARY,
            label = block.label,
            icon = block.icon,
            themeViewModel = themeViewModel,
            modifier = fillWidth,
            onLongClick = onLongClick
        )
        "settings_background" -> BackgroundShortcutButton(
            target = BackgroundTarget.SETTINGS,
            label = block.label,
            icon = block.icon,
            themeViewModel = themeViewModel,
            modifier = fillWidth,
            onLongClick = onLongClick
        )
        "dock_visibility" -> SettingsShortcutRow(
            icon = block.icon,
            label = block.label,
            onClick = onShowDockVisibility,
            onLongClick = onLongClick,
            modifier = fillWidth
        )
        "skip_review" -> SettingsShortcutRow(
            icon = block.icon,
            label = block.label,
            onClick = onNavigateToSkipReview,
            onLongClick = onLongClick,
            modifier = fillWidth
        )
        "shortcut_button_color" -> ShortcutButtonColorPicker(
            buttonColorViewModel = buttonColorViewModel,
            modifier = fillWidth,
            label = block.label,
            icon = block.icon,
            onLongClick = onLongClick
        )
        "audio_normalization" -> {
            val isEnabled by audioNormalizationViewModel.isEnabled.collectAsState()
            SettingsShortcutRow(
                icon = block.icon,
                label = if (isEnabled) "${block.label}: On" else "${block.label}: Off",
                onClick = { audioNormalizationViewModel.toggle() },
                onLongClick = onLongClick,
                modifier = fillWidth
            )
        }
    }
}

/**
 * A single Settings shortcut: icon on the left, label on one line next to it, transparent
 * background rather than a colored button - every block on the Settings screen renders as
 * one of these (or wraps one, for blocks with an extra status line underneath).
 *
 * [onLongClick] (reorder-mode toggle, when supplied by the Settings screen) shares this same
 * [combinedClickable] with [onClick] rather than being layered on via a wrapping modifier -
 * two separate clickables stacked on the same bounds would race for the gesture, and the
 * inner one (this row's own tap) would always win, silently swallowing every long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsShortcutRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
