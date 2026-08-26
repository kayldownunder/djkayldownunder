package com.djkaylfromdownunder.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.ui.theme.SurfaceCard

/**
 * Combined "Arrange Shortcut Buttons" + "Edit Icon Library" screen: every icon the bottom
 * dock can show, with a switch to include/exclude it and up/down buttons to reorder it.
 * Edits happen on a local draft only - [DockPreferencesViewModel] is only written to once,
 * when leaving the screen (back arrow, system back, or gesture-back all route through
 * [commitAndBack]), so a half-finished rearrangement can't leave the real dock in a
 * confusing state mid-edit.
 */
@Composable
fun CustomizeDockScreen(
    dockPreferencesViewModel: DockPreferencesViewModel,
    onBack: () -> Unit
) {
    val savedIds by dockPreferencesViewModel.visibleIds.collectAsState()

    // Draft rows: saved-visible items first (in their saved order), then every hidden item
    // appended in its default order - so switching a hidden item back on reinserts it
    // somewhere sensible rather than always at the very end.
    val draft = remember {
        val byId = ALL_DOCK_ITEMS.associateBy { it.id }
        val visible = savedIds.mapNotNull { byId[it] }.map { it to true }
        val hiddenIds = ALL_DOCK_ITEMS.map { it.id } - savedIds.toSet()
        val hidden = hiddenIds.mapNotNull { byId[it] }.map { it to false }
        mutableStateListOf(*(visible + hidden).toTypedArray())
    }

    fun commitAndBack() {
        val orderedVisibleIds = draft.filter { it.second }.map { it.first.id }
        dockPreferencesViewModel.setOrderedIds(orderedVisibleIds)
        onBack()
    }

    BackHandler(onBack = ::commitAndBack)

    fun swap(a: Int, b: Int) {
        val temp = draft[a]
        draft[a] = draft[b]
        draft[b] = temp
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = ::commitAndBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Customise Bottom Dock", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Choose which icons appear in the bottom dock and drag their order. Changes save automatically when you leave this screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(draft, key = { _, pair -> pair.first.id }) { index, (item, visible) ->
                DockEditorRow(
                    item = item,
                    visible = visible,
                    canMoveUp = index > 0,
                    canMoveDown = index < draft.lastIndex,
                    onToggleVisible = { draft[index] = item to it },
                    onMoveUp = { swap(index, index - 1) },
                    onMoveDown = { swap(index, index + 1) }
                )
            }
        }
    }
}

@Composable
private fun DockEditorRow(
    item: DockItemDef,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleVisible: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(SurfaceCard, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Switch(checked = visible, onCheckedChange = onToggleVisible)
    }
}
