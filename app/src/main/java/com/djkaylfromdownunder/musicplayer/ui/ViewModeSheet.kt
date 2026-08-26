package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.data.PlaylistViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewModeSheet(
    currentMode: PlaylistViewMode,
    onModeSelected: (PlaylistViewMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                "Playlist View",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            ViewModeOption(
                label = "Large",
                description = "1 per row, big cover art",
                icon = Icons.Default.ViewAgenda,
                selected = currentMode == PlaylistViewMode.LARGE,
                onClick = { onModeSelected(PlaylistViewMode.LARGE) }
            )
            ViewModeOption(
                label = "Medium",
                description = "2 per row (default)",
                icon = Icons.Default.GridView,
                selected = currentMode == PlaylistViewMode.MEDIUM,
                onClick = { onModeSelected(PlaylistViewMode.MEDIUM) }
            )
            ViewModeOption(
                label = "Small",
                description = "3 per row, compact grid",
                icon = Icons.Default.ViewModule,
                selected = currentMode == PlaylistViewMode.SMALL,
                onClick = { onModeSelected(PlaylistViewMode.SMALL) }
            )
            ViewModeOption(
                label = "List",
                description = "One folder per line",
                icon = Icons.Default.List,
                selected = currentMode == PlaylistViewMode.LIST,
                onClick = { onModeSelected(PlaylistViewMode.LIST) }
            )
        }
    }
}

@Composable
private fun ViewModeOption(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
