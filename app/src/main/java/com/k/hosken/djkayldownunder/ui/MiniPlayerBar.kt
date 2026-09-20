package com.k.hosken.djkayldownunder.ui

import androidx.media3.common.util.UnstableApi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.k.hosken.djkayldownunder.ui.theme.SurfaceCardElevated

/**
 * Compact, always-visible player bar — same role as Spotify's bottom mini-player.
 * Tapping it (outside the play/pause button) should navigate to the full PlayerScreen.
 */
@UnstableApi
@Composable
fun MiniPlayerBar(playerViewModel: PlayerViewModel, metadataViewModel: MetadataViewModel, onExpand: () -> Unit) {
    val state by playerViewModel.uiState.collectAsState()

    AnimatedVisibility(visible = state.currentTrack != null) {
        val progress = if (state.durationMs > 0) {
            state.positionMs.toFloat() / state.durationMs.toFloat()
        } else 0f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCardElevated)
                .clickable { onExpand() }
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                trackColor = Color.Transparent
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    TrackArtwork(
                        track = state.currentTrack,
                        metadataViewModel = metadataViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = state.currentTrack?.displayName ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play"
                    )
                }
            }
        }
    }
}
