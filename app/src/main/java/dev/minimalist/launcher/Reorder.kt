package dev.minimalist.launcher

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tap handler that keeps working while the lambda changes between recompositions. */
@Composable
fun Modifier.onTap(key: Any, onTap: () -> Unit): Modifier {
    val latest = rememberUpdatedState(onTap)
    return pointerInput(key) { detectTapGestures(onTap = { latest.value() }) }
}

/**
 * Column whose rows can be reordered: long-press a row and drag it up or down.
 * Releasing without moving counts as a plain long-press ([onLongPress]).
 * Each row gets a `drag` modifier to attach to the part that should start the drag.
 */
@Composable
fun <T> ReorderColumn(
    items: List<T>,
    key: (T) -> String,
    onMove: (item: T, delta: Int) -> Unit,
    onLongPress: (T) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (item: T, drag: Modifier) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val heights: SnapshotStateMap<String, Int> = remember { mutableStateMapOf() }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val keys = items.map(key)
    val latestItems by rememberUpdatedState(items)
    val latestKeys by rememberUpdatedState(keys)
    val latestMove by rememberUpdatedState(onMove)
    val latestLongPress by rememberUpdatedState(onLongPress)

    /** Index the dragged row would land on, from the position of its centre. */
    fun targetFor(ks: List<String>, k: String): Int {
        val from = ks.indexOf(k); if (from < 0) return -1
        fun h(i: Int) = heights[ks[i]] ?: 0
        val centre = (0 until from).sumOf { h(it) } + h(from) / 2f + dragY
        var acc = 0f
        for (i in ks.indices) { acc += h(i); if (centre < acc) return i }
        return ks.lastIndex
    }

    val dragIndex = dragKey?.let { keys.indexOf(it) } ?: -1
    val target = dragKey?.let { targetFor(keys, it) } ?: -1

    Column(modifier) {
        items.forEachIndexed { i, item ->
            val k = keys[i]
            fun h(j: Int) = heights[keys[j]] ?: 0
            val shiftTarget = when {
                dragIndex < 0 || i == dragIndex -> 0
                dragIndex < target && i in (dragIndex + 1)..target -> -h(dragIndex)
                target < dragIndex && i in target until dragIndex -> h(dragIndex)
                else -> 0
            }
            // Rows glide aside while dragging, but snap back when the drag ends and the list reorders.
            val shift by animateIntAsState(shiftTarget, if (dragIndex >= 0) tween(140) else snap(), label = "shift")
            val dragging = i == dragIndex
            // The row this detector sits on moves with the finger, so local positions would shrink as it
            // follows. Measure the finger in root coordinates instead.
            val coords = remember(k) { arrayOfNulls<LayoutCoordinates>(1) }
            var startRootY = 0f
            val drag = Modifier.onGloballyPositioned { coords[0] = it }.pointerInput(k) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { at: Offset ->
                        startRootY = coords[0]?.localToRoot(at)?.y ?: 0f
                        dragKey = k; dragY = 0f; haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        coords[0]?.let { dragY = it.localToRoot(change.position).y - startRootY }
                    },
                    onDragEnd = {
                        val ks = latestKeys
                        val from = ks.indexOf(k)
                        val to = targetFor(ks, k)
                        val moved = abs(dragY) > 12f
                        val it = latestItems.getOrNull(from)
                        dragKey = null; dragY = 0f
                        if (it != null) { if (to != from && to >= 0) latestMove(it, to - from) else if (!moved) latestLongPress(it) }
                    },
                    onDragCancel = { dragKey = null; dragY = 0f },
                )
            }
            Box(
                Modifier.zIndex(if (dragging) 1f else 0f)
                    .onSizeChanged { heights[k] = it.height }
                    .offset { IntOffset(0, if (dragging) dragY.roundToInt() else shift) }
                    .then(if (dragging) Modifier.background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)) else Modifier)
            ) { Column { content(item, drag) } }
        }
    }
}
