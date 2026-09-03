package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djkaylfromdownunder.musicplayer.ui.theme.SurfaceDark

// How many icons are sized to fit on screen at once - each icon is given 1/4 of the screen
// width, matching the old 4-column grid's sizing, so the row looks the same at a glance.
// Any icons beyond that are still laid out in the same row - just off to the side - and
// reachable by swiping left/right rather than wrapping to a second row.
private const val DOCK_VISIBLE_ICONS = 4

/**
 * Bottom dock: whichever icons the user has kept visible, laid out as a single horizontally
 * scrollable row - sized so [DOCK_VISIBLE_ICONS] fit on screen at once, with any remaining
 * icons reachable by swiping left/right. Which icons show and in what order is set from the
 * Settings screen's "Customise Bottom Dock" sheet (see DockVisibilitySheet) rather than by
 * dragging icons in the dock itself.
 */
@Composable
fun AppBottomNav(
    dockItems: List<DockItemDef>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onPushNavigate: (String) -> Unit,
    onViewClick: () -> Unit
) {
    val itemWidth = LocalConfiguration.current.screenWidthDp.dp / DOCK_VISIBLE_ICONS

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            // Background still paints all the way down behind the system nav bar (no color
            // seam); this only pushes the actual icon row up clear of it.
            .navigationBarsPadding()
            .height(56.dp)
    ) {
        items(dockItems, key = { it.id }) { item ->
            val selected = item.actionType != DockActionType.VIEW_SHEET && currentRoute == item.route
            DockIconButton(
                item = item,
                selected = selected,
                modifier = Modifier.width(itemWidth),
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
    modifier: Modifier,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
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
