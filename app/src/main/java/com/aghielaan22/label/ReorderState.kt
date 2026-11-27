package com.aghielaan22.label

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Tracks one row being dragged in a LazyColumn, and reports when it passes another row. */
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    private var delta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    private val draggingInfo
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingIndex }

    /** How far the dragged row should be drawn from where the list currently puts it. */
    val draggedOffset: Float
        get() = draggingInfo?.let { initialOffset + delta - it.offset } ?: 0f

    fun start(index: Int) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggingIndex = index
        initialOffset = info.offset
        delta = 0f
    }

    fun drag(amount: Float) {
        delta += amount
        val dragging = draggingInfo ?: return
        val middle = dragging.offset + draggedOffset + dragging.size / 2f

        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != dragging.index && middle.toInt() in item.offset..(item.offset + item.size)
        } ?: return

        if (dragging.index == listState.firstVisibleItemIndex ||
            target.index == listState.firstVisibleItemIndex
        ) {
            // Stops the list jumping when the top row swaps
            listState.requestScrollToItem(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
        onMove(dragging.index, target.index)
        draggingIndex = target.index
    }

    fun end() {
        draggingIndex = null
        delta = 0f
        initialOffset = 0
    }
}

@Composable
fun DragHandle(index: Int, state: ReorderState, modifier: Modifier = Modifier) {
    val currentIndex by rememberUpdatedState(index)
    Icon(
        imageVector = Icons.Default.DragHandle,
        contentDescription = "Drag to reorder",
        modifier = modifier
            .size(40.dp)
            .padding(8.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { state.start(currentIndex) },
                    onDragEnd = { state.end() },
                    onDragCancel = { state.end() },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        state.drag(amount)
                    }
                )
            }
    )
}