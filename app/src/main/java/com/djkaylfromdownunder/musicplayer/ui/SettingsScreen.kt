package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    themeViewModel: ThemeViewModel,
    onNavigateToSkipReview: () -> Unit
) {
    val rootUri by libraryViewModel.rootUri.collectAsState()
    val metadataProgress by metadataViewModel.progress.collectAsState()
    var isScanningLibrary by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(28.dp))

        Text("Music Library", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        ChooseMusicFolderButton(viewModel = libraryViewModel)

        Spacer(modifier = Modifier.height(24.dp))

        Text("Metadata", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    isScanningLibrary = true
                    val playlists = libraryViewModel.scanAllPlaylists()
                    isScanningLibrary = false
                    if (playlists.isNotEmpty()) {
                        metadataViewModel.fetchMetadataForAllPlaylists(playlists)
                    }
                }
            },
            enabled = rootUri != null && !metadataProgress.isRunning && !isScanningLibrary
        ) {
            Text("Fetch Metadata for All Playlists")
        }

        if (isScanningLibrary) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Scanning library…", style = MaterialTheme.typography.bodyMedium)
        }

        if (metadataProgress.isRunning) {
            Spacer(modifier = Modifier.height(12.dp))

            val label = if (metadataProgress.isBatch) {
                "Playlist ${metadataProgress.playlistIndex}/${metadataProgress.totalPlaylists}: ${metadataProgress.playlistName ?: ""}"
            } else {
                metadataProgress.playlistName ?: "Fetching…"
            }
            Text(label, style = MaterialTheme.typography.bodyMedium)

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

        Spacer(modifier = Modifier.height(32.dp))

        BackgroundSettingsSection(themeViewModel = themeViewModel)

        Spacer(modifier = Modifier.height(32.dp))

        Text("Skipped Songs", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateToSkipReview) {
            Text("Review Skip Items")
        }
    }
}
