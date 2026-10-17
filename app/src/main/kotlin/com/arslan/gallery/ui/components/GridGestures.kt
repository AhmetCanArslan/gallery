package com.arslan.gallery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pinch in/out on a grid to change the number of columns. While pinching the grid follows the
 * fingers with a damped "rubber band" scale, then springs back once the column count changes.
 */
fun Modifier.pinchToChangeColumns(
    onChange: (delta: Int) -> Boolean,
): Modifier = composed {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val currentOnChange by rememberUpdatedState(onChange)

    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var accumulated = 1f
                var pinching = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.count { it.pressed }
                    if (pressed >= 2) {
                        if (!pinching) {
                            pinching = true
                        }
                        val zoom = event.calculateZoom()
                        accumulated *= zoom
                        scope.launch { scale.snapTo(1f + (accumulated - 1f) * 0.35f) }
                        when {
                            accumulated > 1.28f -> {
                                if (currentOnChange(-1)) haptics.tick() else haptics.reject()
                                accumulated = 1f
                            }

                            accumulated < 0.78f -> {
                                if (currentOnChange(1)) haptics.tick() else haptics.reject()
                                accumulated = 1f
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })

                if (pinching) {
                    scope.launch {
                        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            transformOrigin = TransformOrigin.Center
        }
}

/**
 * Long press an item and keep dragging to select a range, with auto-scroll near the edges and a
 * haptic tick for every change of the selection. [contentPadding] must match the grid's, item
 * offsets reported by the grid don't include it.
 */
fun Modifier.dragToSelect(
    gridState: LazyGridState,
    indexToKey: (Int) -> String?,
    selection: () -> Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this

    val haptics = rememberHaptics()
    val currentIndexToKey by rememberUpdatedState(indexToKey)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val layoutDirection = LocalLayoutDirection.current
    val startPadding by rememberUpdatedState(contentPadding.calculateStartPadding(layoutDirection))
    val drag = remember { DragSelectState() }

    // Keeps scrolling while the finger rests near an edge, extending the selection to whatever
    // scrolls under it even though no pointer events arrive meanwhile. Steps once per frame, by
    // however long the frame took, so it scrolls evenly instead of beating against the display.
    val autoScrolling = drag.autoScrollSpeed != 0f
    LaunchedEffect(autoScrolling) {
        if (!autoScrolling) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive && drag.autoScrollSpeed != 0f) {
            val now = withFrameNanos { it }
            gridState.scrollBy(drag.autoScrollSpeed * (now - last) / AUTO_SCROLL_STEP_NANOS)
            last = now
            drag.update()
        }
    }

    pointerInput(gridState) {
        // Item offsets are relative to the content, i.e. without the grid's content padding.
        fun itemIndexAt(position: Offset): Int? {
            val info = gridState.layoutInfo
            val padding = IntOffset(startPadding.roundToPx(), info.beforeContentPadding)
            val point = position.round() - padding
            return info.visibleItemsInfo.firstOrNull { item ->
                item.size.toIntRect().contains(point - item.offset)
            }?.index
        }

        // Runs in the Initial pass so that, once the long press fires, the items below never see
        // the release as a click and the grid doesn't scroll along with the finger.
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var ended = false
                while (!ended) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    ended = change == null || !change.pressed || event.changes.size > 1 || change.isConsumed ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                }
                true
            }
            if (cancelled != null) return@awaitEachGesture

            val startIndex = itemIndexAt(down.position) ?: return@awaitEachGesture
            val startKey = currentIndexToKey(startIndex) ?: return@awaitEachGesture
            haptics.longPress()
            val baseSelection = currentSelection()
            val adding = startKey !in baseSelection
            var currentIndex = startIndex
            var lastSelection = if (adding) baseSelection + startKey else baseSelection - startKey
            currentOnSelectionChange(lastSelection)

            var position = down.position
            drag.update = update@{
                val index = itemIndexAt(position) ?: return@update
                if (index == currentIndex) return@update
                currentIndex = index
                val range = if (index >= startIndex) startIndex..index else index..startIndex
                val keys = range.mapNotNull(currentIndexToKey)
                val newSelection = if (adding) baseSelection + keys else baseSelection - keys.toSet()
                if (newSelection != lastSelection) {
                    lastSelection = newSelection
                    haptics.tick()
                    currentOnSelectionChange(newSelection)
                }
            }

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    event.changes.forEach { it.consume() }
                    if (!change.pressed) break
                    position = change.position

                    // Edges of the visible content, the bars drawn over the padding don't count.
                    val edge = 80.dp.toPx()
                    val info = gridState.layoutInfo
                    val top = info.beforeContentPadding.toFloat()
                    val bottom = info.viewportSize.height.toFloat()
                    drag.autoScrollSpeed = when {
                        position.y > bottom - edge -> (position.y - (bottom - edge)).coerceAtMost(edge) / 4f
                        position.y < top + edge -> -((top + edge) - position.y).coerceAtMost(edge) / 4f
                        else -> 0f
                    }
                    drag.update()
                }
            } finally {
                drag.autoScrollSpeed = 0f
                drag.update = {}
            }
        }
    }
}

@Stable
private class DragSelectState {
    var autoScrollSpeed by mutableFloatStateOf(0f)

    /** Re-checks the item under the finger, set while a drag is active. */
    var update: () -> Unit = {}
}

/** [DragSelectState.autoScrollSpeed] is in px per this long. */
private const val AUTO_SCROLL_STEP_NANOS = 10_000_000f
