package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/**
 * Shared long-press-drag reorder mechanism for any fixed-column [androidx.compose.foundation.lazy.grid.LazyVerticalGrid]
 * of id-keyed items - used by both the bottom dock (AppBottomNav) and the Settings screen's
 * shortcut grid, so both get the same drag feel and the same free "anywhere to anywhere"
 * range instead of two separate implementations.
 *
 * Each item reports its own on-screen center (in window coordinates, so items in different
 * rows/columns are directly comparable) via [dragReorderItem]. While dragging, the pointer's
 * live position is approximated as "the dragged item's last reported center plus the total
 * drag delta so far" - close enough since a long press starts with the finger already on the
 * item - and every drag tick moves the dragged id to whichever other item's slot is nearest,
 * calling [onReorder] with the full new id order. The grid itself is expected to animate the
 * resulting shuffle via `Modifier.animateItem()` on the non-dragged items (see AppBottomNav /
 * SettingsScreen), which is what gives the "other items slide out of the way live" feel.
 */
class GridReorderState(private val onReorder: (List<String>) -> Unit) {
    var draggingId by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    private var itemCenters by mutableStateOf<Map<String, Offset>>(emptyMap())

    val isAnyDragging: Boolean get() = draggingId != null
    fun isDragging(id: String): Boolean = draggingId == id
    fun dragOffsetFor(id: String): Offset = if (draggingId == id) dragOffset else Offset.Zero

    fun reportPosition(id: String, center: Offset) {
        itemCenters = itemCenters + (id to center)
    }

    fun start(id: String) {
        draggingId = id
        dragOffset = Offset.Zero
    }

    fun drag(delta: Offset, currentIds: List<String>) {
        val draggedId = draggingId ?: return
        dragOffset += delta
        val draggedCenter = itemCenters[draggedId] ?: return
        val pointerPos = draggedCenter + dragOffset
        val nearestId = itemCenters
            .filterKeys { it in currentIds }
            .minByOrNull { (_, center) -> (center - pointerPos).getDistanceSquared() }
            ?.key
        if (nearestId == null || nearestId == draggedId) return

        val fromIndex = currentIds.indexOf(draggedId)
        val toIndex = currentIds.indexOf(nearestId)
        if (fromIndex < 0 || toIndex < 0) return

        val reordered = currentIds.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        onReorder(reordered)
    }

    fun end() {
        draggingId = null
        dragOffset = Offset.Zero
    }
}

@Composable
fun rememberGridReorderState(onReorder: (List<String>) -> Unit): GridReorderState =
    remember { GridReorderState(onReorder) }

/** Reports this item's position and drives long-press-drag on it. [currentIds] is read fresh on every drag tick. */
fun Modifier.dragReorderItem(
    state: GridReorderState,
    id: String,
    currentIds: () -> List<String>
): Modifier = this
    .onGloballyPositioned { coordinates ->
        val topLeft = coordinates.positionInWindow()
        val size = coordinates.size
        state.reportPosition(id, Offset(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f))
    }
    .pointerInput(id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(id) },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
            onDrag = { change, dragAmount ->
                change.consume()
                state.drag(dragAmount, currentIds())
            }
        )
    }

/** Visual follow-the-finger transform for the item currently being dragged; a no-op for every other item. */
fun Modifier.dragReorderTransform(state: GridReorderState, id: String): Modifier = this.graphicsLayer {
    if (state.isDragging(id)) {
        val offset = state.dragOffsetFor(id)
        translationX = offset.x
        translationY = offset.y
        scaleX = 1.08f
        scaleY = 1.08f
        alpha = 0.92f
    }
}
