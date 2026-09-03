package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Full-screen destination (pushed via Routes.DOCK_SETTINGS) for showing, hiding, and
 * reordering the bottom dock's icons. Reordering is long-press-and-tap rather than drag: a
 * long press on a visible icon reveals up/down arrows on that row, and tapping one moves it
 * one slot at a time - the Settings screen's own shortcut list ([SettingsScreen]) uses the
 * same long-press-and-tap pattern.
 *
 * Unlike the rest of the app's settings, changes here are staged locally (both order and
 * show/hide) and only committed via the Save button, which persists and immediately pops
 * back to Settings - [onBack] (used by the header's back arrow) discards anything unsaved.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockSettingsScreen(
    dockPreferencesViewModel: DockPreferencesViewModel,
    onBack: () -> Unit
) {
    val visibleIds by dockPreferencesViewModel.visibleIds.collectAsState()
    val byId = remember { ALL_DOCK_ITEMS.associateBy { it.id } }

    var pendingIds by remember(visibleIds) { mutableStateOf(visibleIds) }
    var expandedItemId by remember { mutableStateOf<String?>(null) }

    val visibleItems = remember(pendingIds) { pendingIds.mapNotNull { byId[it] } }
    val hiddenItems = remember(pendingIds) { ALL_DOCK_ITEMS.filter { it.id !in pendingIds } }
    val hasUnsavedChanges = pendingIds != visibleIds

    fun moveItem(id: String, delta: Int) {
        val list = pendingIds.toMutableList()
        val fromIndex = list.indexOf(id)
        val toIndex = fromIndex + delta
        if (fromIndex < 0 || toIndex < 0 || toIndex >= list.size) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        pendingIds = list
    }

    fun setVisible(id: String, visible: Boolean) {
        pendingIds = if (visible) {
            if (id in pendingIds) pendingIds else pendingIds + id
        } else {
            if (expandedItemId == id) expandedItemId = null
            pendingIds - id
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Customise Bottom Dock",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
        Text(
            "Long-press an icon to move it up or down. At least one icon must stay visible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(visibleItems, key = { _, item -> item.id }) { index, item ->
                DockVisibilityRow(
                    item = item,
                    isVisible = true,
                    canHide = visibleItems.size > 1,
                    onVisibleChange = { setVisible(item.id, false) },
                    isExpanded = expandedItemId == item.id,
                    onLongPress = {
                        expandedItemId = if (expandedItemId == item.id) null else item.id
                    },
                    onMoveUp = { moveItem(item.id, -1) }.takeIf { index > 0 },
                    onMoveDown = { moveItem(item.id, 1) }.takeIf { index < visibleItems.size - 1 }
                )
            }
            if (hiddenItems.isNotEmpty()) {
                item {
                    Text(
                        "Hidden",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                items(hiddenItems, key = { it.id }) { item ->
                    DockVisibilityRow(
                        item = item,
                        isVisible = false,
                        canHide = true,
                        onVisibleChange = { setVisible(item.id, true) }
                    )
                }
            }
        }
        Button(
            onClick = {
                dockPreferencesViewModel.setOrderedIds(pendingIds)
                onBack()
            },
            enabled = hasUnsavedChanges,
            modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()
        ) {
            Text("Save")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockVisibilityRow(
    item: DockItemDef,
    isVisible: Boolean,
    canHide: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    isExpanded: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = isVisible,
                enabled = !isVisible || canHide,
                onCheckedChange = onVisibleChange
            )
        }
        if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onMoveUp ?: {}, enabled = onMoveUp != null) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${item.label} up")
                }
                IconButton(onClick = onMoveDown ?: {}, enabled = onMoveDown != null) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${item.label} down")
                }
            }
        }
    }
}
