package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Show/hide toggle for the bottom dock's icons - reordering itself now happens live by
 * long-pressing and dragging an icon directly in the dock (see AppBottomNav), so this sheet
 * only needs to handle which icons appear at all. Toggling one off removes it from the dock
 * immediately; toggling one back on appends it at the end (drag it wherever from there).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockVisibilitySheet(
    dockPreferencesViewModel: DockPreferencesViewModel,
    onDismiss: () -> Unit
) {
    val visibleIds by dockPreferencesViewModel.visibleIds.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                "Show/Hide Dock Icons",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Text(
                "At least one icon must stay visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(ALL_DOCK_ITEMS, key = { it.id }) { item ->
                    val isVisible = item.id in visibleIds
                    val canHide = !isVisible || visibleIds.size > 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isVisible,
                            enabled = canHide,
                            onCheckedChange = { checked -> dockPreferencesViewModel.setVisible(item.id, checked) }
                        )
                    }
                }
            }
        }
    }
}
