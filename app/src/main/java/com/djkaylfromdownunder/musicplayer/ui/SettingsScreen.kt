package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.BackgroundTarget
import kotlinx.coroutines.launch

/**
 * Just the functional buttons, no section-title labels above them - which of
 * [ALL_SETTINGS_BLOCKS] appear and in what order comes from [SettingsLayoutViewModel],
 * edited via "Rearrange Settings Layout". Laid out as a compact 2-column grid so every
 * shortcut fits on one screen without scrolling on a typical device (verticalScroll stays
 * on as a fallback only - e.g. a very large chosen font size could still overflow it).
 */
@Composable
fun SettingsScreen(
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    themeViewModel: ThemeViewModel,
    fontPreferencesViewModel: FontPreferencesViewModel,
    settingsLayoutViewModel: SettingsLayoutViewModel,
    onNavigateToSkipReview: () -> Unit,
    onNavigateToCustomizeDock: () -> Unit,
    onNavigateToRearrangeSettings: () -> Unit
) {
    val rootUri by libraryViewModel.rootUri.collectAsState()
    val metadataProgress by metadataViewModel.progress.collectAsState()
    var isScanningLibrary by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var isConsolidatingArtwork by remember { mutableStateOf(false) }
    var consolidateResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val order by settingsLayoutViewModel.order.collectAsState()
    val blocks = remember(order) {
        val byId = ALL_SETTINGS_BLOCKS.associateBy { it.id }
        order.mapNotNull { byId[it] }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Only this row needs it (it's the one screen in the app putting an interactive
        // button, not just title text, right up against the top edge) - a status-bar
        // icon sitting on top of a real tap target would make it unreliable to hit.
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
            IconButton(onClick = { showFontDialog = true }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Font & Appearance Settings",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        blocks.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { block ->
                    Box(modifier = Modifier.weight(1f)) {
                        SettingsBlockButton(
                            block = block,
                            libraryViewModel = libraryViewModel,
                            themeViewModel = themeViewModel,
                            rootUri = rootUri,
                            metadataRunning = metadataProgress.isRunning,
                            isScanningLibrary = isScanningLibrary,
                            isConsolidatingArtwork = isConsolidatingArtwork,
                            consolidateResult = consolidateResult,
                            onFetchAllMetadata = ::fetchAllMetadata,
                            onConsolidateArtwork = ::consolidateArtwork,
                            onNavigateToSkipReview = onNavigateToSkipReview,
                            onNavigateToCustomizeDock = onNavigateToCustomizeDock,
                            onNavigateToRearrangeSettings = onNavigateToRearrangeSettings
                        )
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (metadataProgress.isRunning) {
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

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showFontDialog) {
        FontSettingsDialog(
            fontPreferencesViewModel = fontPreferencesViewModel,
            onDismiss = { showFontDialog = false }
        )
    }
}

@Composable
private fun SettingsBlockButton(
    block: SettingsBlockDef,
    libraryViewModel: MusicLibraryViewModel,
    themeViewModel: ThemeViewModel,
    rootUri: android.net.Uri?,
    metadataRunning: Boolean,
    isScanningLibrary: Boolean,
    isConsolidatingArtwork: Boolean,
    consolidateResult: String?,
    onFetchAllMetadata: () -> Unit,
    onConsolidateArtwork: () -> Unit,
    onNavigateToSkipReview: () -> Unit,
    onNavigateToCustomizeDock: () -> Unit,
    onNavigateToRearrangeSettings: () -> Unit
) {
    val fillWidth = Modifier.fillMaxWidth()
    when (block.id) {
        "music_library" -> ChooseMusicFolderButton(
            viewModel = libraryViewModel,
            modifier = fillWidth,
            label = block.label
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
            OutlinedButton(
                onClick = onFetchAllMetadata,
                enabled = rootUri != null && !metadataRunning && !isScanningLibrary,
                modifier = fillWidth
            ) {
                Text(block.label)
            }
        }
        "consolidate_artwork" -> Column(modifier = fillWidth) {
            OutlinedButton(
                onClick = onConsolidateArtwork,
                enabled = rootUri != null && !isConsolidatingArtwork,
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
            modifier = fillWidth
        )
        "settings_background" -> BackgroundShortcutButton(
            target = BackgroundTarget.SETTINGS,
            label = block.label,
            themeViewModel = themeViewModel,
            modifier = fillWidth
        )
        "customize_dock" -> OutlinedButton(onClick = onNavigateToCustomizeDock, modifier = fillWidth) {
            Text(block.label)
        }
        "rearrange_settings" -> OutlinedButton(onClick = onNavigateToRearrangeSettings, modifier = fillWidth) {
            Text(block.label)
        }
        "skip_review" -> OutlinedButton(onClick = onNavigateToSkipReview, modifier = fillWidth) {
            Text(block.label)
        }
    }
}
