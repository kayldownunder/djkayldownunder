package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.djkaylfromdownunder.musicplayer.ui.theme.SurfaceDark

private const val DOCK_COLUMNS = 4

/**
 * Bottom dock: whichever icons the user has kept visible, laid out as a fixed 4-column grid
 * (two rows at the default 8 icons) so every shortcut gets enough width for its label to
 * stay on one line.
 *
 * Long-pressing and dragging an icon reorders it in place - dragging works across the whole
 * grid (any position to any other position, including far-right to far-left), since it's a
 * single flat ordered list under a fixed-column grid rather than two independent rows. See
 * [DragReorderGrid] for the shared long-press-drag mechanism (also used by the Settings
 * screen's own shortcut grid) - `Modifier.animateItem()` on the non-dragged icons is what
 * gives the "other icons slide out of the way live" feel as one is dragged past them. The
 * shuffle previews live as you drag, but the new order is only persisted once (via
 * [dragState]'s `onReorder`) when the finger lifts - [dragState] is hoisted to the caller
 * (see AppNavHost) rather than created here, so it can also keep the auto-hide bottom bar
 * pinned visible for the duration of the drag.
 */
@Composable
fun AppBottomNav(
    dockItems: List<DockItemDef>,
    currentRoute: String?,
    dragState: GridReorderState,
    onNavigate: (String) -> Unit,
    onPushNavigate: (String) -> Unit,
    onViewClick: () -> Unit
) {
    val ids = dockItems.map { it.id }
    val displayItems = dragState.displayOrder(dockItems) { it.id }

    LazyVerticalGrid(
        columns = GridCells.Fixed(DOCK_COLUMNS),
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            // Background still paints all the way down behind the system nav bar (no color
            // seam); this only pushes the actual icon rows up clear of it.
            .navigationBarsPadding()
            .height(112.dp)
    ) {
        itemsIndexed(displayItems, key = { _, item -> item.id }) { _, item ->
            val selected = item.actionType != DockActionType.VIEW_SHEET && currentRoute == item.route
            val isDragging = dragState.isDragging(item.id)
            DockIconButton(
                item = item,
                selected = selected,
                // Disabled while any icon is dragging (not just this one) so a finger
                // lifting over a neighboring icon mid-drag can't also fire its navigation.
                clickEnabled = !dragState.isAnyDragging,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .dragReorderTransform(dragState, item.id)
                    .dragReorderItem(dragState, item.id) { ids },
                onClick = {
                    when (item.actionType) {
                        DockActionType.TAB -> item.route?.let(onNavigate)
                        DockActionType.PUSH -> item.route?.let(onPushNavigate)
                        DockActionType.VIEW_SHEET -> onViewClick()
                    }
                }
            )
        }
    }
}

@Composable
private fun DockIconButton(
    item: DockItemDef,
    selected: Boolean,
    clickEnabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = clickEnabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
