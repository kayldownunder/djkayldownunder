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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// How much closer the pointer needs to be to a neighboring slot than to the dragged item's
// own slot before the two swap - stops a slot flickering back and forth when the finger
// hovers right on the boundary between two items.
private val SWAP_MARGIN: Dp = 12.dp

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
 * item. The order shown while dragging (see [displayOrder]) shuffles live as the pointer
 * crosses into another item's slot - past a small hysteresis margin so a twitchy finger right
 * on the boundary doesn't flicker back and forth - but that's only a preview: the real,
 * persisted order (via [onReorder]) is committed once, when the finger lifts, so a drag in
 * progress never repeatedly saves or drags neighboring icons through multiple unintended
 * positions before the user has settled on one.
 */
class GridReorderState(private val onReorder: (List<String>) -> Unit) {
    var draggingId by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var previewIds by mutableStateOf<List<String>>(emptyList())
        private set
    private var baseIds: List<String> = emptyList()
    private var itemCenters by mutableStateOf<Map<String, Offset>>(emptyMap())

    val isAnyDragging: Boolean get() = draggingId != null
    fun isDragging(id: String): Boolean = draggingId == id
    fun dragOffsetFor(id: String): Offset = if (draggingId == id) dragOffset else Offset.Zero

    /** The order to render: the live drag preview while dragging, otherwise [committed] unchanged. */
    fun <T> displayOrder(committed: List<T>, idOf: (T) -> String): List<T> {
        if (!isAnyDragging) return committed
        val byId = committed.associateBy(idOf)
        return previewIds.mapNotNull { byId[it] }
    }

    fun reportPosition(id: String, center: Offset) {
        itemCenters = itemCenters + (id to center)
    }

    fun start(id: String, currentIds: List<String>) {
        draggingId = id
        dragOffset = Offset.Zero
        baseIds = currentIds
        previewIds = currentIds
    }

    fun drag(delta: Offset, swapMarginPx: Float) {
        val draggedId = draggingId ?: return
        dragOffset += delta
        val ownCenter = itemCenters[draggedId] ?: return
        val pointerPos = ownCenter + dragOffset
        val candidates = itemCenters.filterKeys { it in previewIds }
        val ownDistance = (ownCenter - pointerPos).getDistance()
        val nearestEntry = candidates.minByOrNull { (_, center) -> (center - pointerPos).getDistance() } ?: return
        val nearestId = nearestEntry.key
        if (nearestId == draggedId) return
        val nearestDistance = (nearestEntry.value - pointerPos).getDistance()
        // Requires the pointer to be *clearly* closer to the neighboring slot, not just past
        // the midpoint, before the swap actually happens.
        if (ownDistance - nearestDistance < swapMarginPx) return

        val fromIndex = previewIds.indexOf(draggedId)
        val toIndex = previewIds.indexOf(nearestId)
        if (fromIndex < 0 || toIndex < 0) return

        val reordered = previewIds.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        previewIds = reordered
    }

    /** Commits the preview order - if it actually changed - now that the finger has lifted. */
    fun end() {
        if (draggingId != null && previewIds != baseIds) onReorder(previewIds)
        draggingId = null
        dragOffset = Offset.Zero
    }

    /** Drops the live preview and reverts to the committed order, e.g. on a cancelled gesture. */
    fun cancel() {
        draggingId = null
        dragOffset = Offset.Zero
        previewIds = baseIds
    }
}

@Composable
fun rememberGridReorderState(onReorder: (List<String>) -> Unit): GridReorderState =
    remember { GridReorderState(onReorder) }

/** Reports this item's position and drives long-press-drag on it. [currentIds] seeds the drag preview on press. */
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
        val swapMarginPx = SWAP_MARGIN.toPx()
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(id, currentIds()) },
            onDragEnd = { state.end() },
            onDragCancel = { state.cancel() },
            onDrag = { change, dragAmount ->
                change.consume()
                state.drag(dragAmount, swapMarginPx)
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
