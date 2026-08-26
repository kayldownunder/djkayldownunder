package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    libraryViewModel: MusicLibraryViewModel,
    metadataViewModel: MetadataViewModel,
    themeViewModel: ThemeViewModel,
    onNavigateToSkipReview: () -> Unit
) {
    val libraryState by libraryViewModel.state.collectAsState()
    val metadataProgress by metadataViewModel.progress.collectAsState()

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

        val playlists = (libraryState as? LibraryState.Loaded)?.playlists.orEmpty()

        OutlinedButton(
            onClick = { metadataViewModel.fetchMetadataForAllPlaylists(playlists) },
            enabled = playlists.isNotEmpty() && !metadataProgress.isRunning
        ) {
            Text("Fetch Metadata for All Playlists")
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
