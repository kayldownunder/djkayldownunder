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
import com.djkaylfromdownunder.musicplayer.data.BackgroundTarget
import com.djkaylfromdownunder.musicplayer.ui.theme.SurfaceCard

/**
 * "Rearrange Settings Layout": lets the user reorder the Settings screen's own shortcut
 * buttons. Uses the same background as the Settings screen itself (see TargetedBackground
 * call below). Edits happen on a local draft only - [SettingsLayoutViewModel] is written
 * to once, on leaving the screen (back arrow or system back both route through
 * [commitAndBack]), and that order is what survives an app restart.
 */
@Composable
fun RearrangeSettingsScreen(
    settingsLayoutViewModel: SettingsLayoutViewModel,
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit
) {
    val savedOrder by settingsLayoutViewModel.order.collectAsState()

    val draft = remember {
        val byId = ALL_SETTINGS_BLOCKS.associateBy { it.id }
        mutableStateListOf(*savedOrder.mapNotNull { byId[it] }.toTypedArray())
    }

    fun commitAndBack() {
        settingsLayoutViewModel.setOrder(draft.map { it.id })
        onBack()
    }

    BackHandler(onBack = ::commitAndBack)

    fun swap(a: Int, b: Int) {
        val temp = draft[a]
        draft[a] = draft[b]
        draft[b] = temp
    }

    TargetedBackground(target = BackgroundTarget.SETTINGS, themeViewModel = themeViewModel) {
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
                Text("Rearrange Settings Layout", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Drag or use the arrows to reorder the Settings screen's shortcuts. Changes save automatically when you leave this screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(draft, key = { _, block -> block.id }) { index, block ->
                    SettingsBlockEditorRow(
                        block = block,
                        canMoveUp = index > 0,
                        canMoveDown = index < draft.lastIndex,
                        onMoveUp = { swap(index, index - 1) },
                        onMoveDown = { swap(index, index + 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsBlockEditorRow(
    block: SettingsBlockDef,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
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
        Text(block.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
            }
        }
    }
}
