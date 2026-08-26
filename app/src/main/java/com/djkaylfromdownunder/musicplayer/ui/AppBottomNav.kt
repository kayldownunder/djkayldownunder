package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djkaylfromdownunder.musicplayer.ui.theme.SurfaceDark

/**
 * Bottom dock: whichever icons the user has kept visible (see CustomizeDockScreen), split
 * across two compact rows so every icon gets enough width for its label to stay on one
 * line, without the tall single-row bar this used to need to fit them all.
 */
@Composable
fun AppBottomNav(
    dockItems: List<DockItemDef>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onPushNavigate: (String) -> Unit,
    onViewClick: () -> Unit
) {
    val half = (dockItems.size + 1) / 2
    val topRow = dockItems.take(half)
    val bottomRow = dockItems.drop(half)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            // Background still paints all the way down behind the system nav bar (no color
            // seam); this only pushes the actual icon rows up clear of it, since a two-row
            // dock has much less headroom than the old single row did.
            .navigationBarsPadding()
    ) {
        DockRow(topRow, currentRoute, onNavigate, onPushNavigate, onViewClick)
        DockRow(bottomRow, currentRoute, onNavigate, onPushNavigate, onViewClick)
    }
}

@Composable
private fun DockRow(
    items: List<DockItemDef>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onPushNavigate: (String) -> Unit,
    onViewClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { item ->
            val selected = item.actionType != DockActionType.VIEW_SHEET && currentRoute == item.route
            DockIconButton(
                item = item,
                selected = selected,
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
private fun RowScope.DockIconButton(item: DockItemDef, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .weight(1f)
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
