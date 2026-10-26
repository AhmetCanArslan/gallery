package com.arslan.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Expressive fast scroller: a pill thumb that appears while scrolling, grows when grabbed and shows
 * a date/section bubble. Ticks the haptics each time the bubble label changes.
 */
@Composable
fun FastScroller(
    gridState: LazyGridState,
    totalItems: Int,
    labelFor: (Int) -> String?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (totalItems < 40) return

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    // Set while laying out the handle, only the drag reads it.
    val trackHeightPx = remember { mutableIntStateOf(0) }

    // Last index that can still be the first visible one, scroll and drag both map onto it.
    val maxIndex by remember(totalItems) {
        derivedStateOf { (totalItems - gridState.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1) }
    }
    // Counts the part of the first row scrolled past too, so the thumb glides instead of jumping a row at a time.
    val scrollFraction by remember(totalItems) {
        derivedStateOf {
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            val firstIndex = gridState.firstVisibleItemIndex
            val first = visibleItems.firstOrNull { it.index == firstIndex }
            val within = if (first == null || first.size.height == 0) {
                0f
            } else {
                val perRow = visibleItems.count { it.row == first.row }
                (gridState.firstVisibleItemScrollOffset.toFloat() / first.size.height).coerceIn(0f, 1f) * perRow
            }
            ((firstIndex + within) / maxIndex).coerceIn(0f, 1f)
        }
    }

    val scrolling = gridState.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            visible = true
        } else {
            delay(1400)
            visible = false
        }
    }

    // Read while laying out the thumb, so scrolling only moves it instead of recomposing the scroller
    // for every row. The label only changes from one section to the next.
    val fraction = { if (dragging) dragFraction else scrollFraction }
    val currentLabelFor by rememberUpdatedState(labelFor)
    val label by remember(totalItems) { derivedStateOf { currentLabelFor((fraction() * maxIndex).roundToInt()) } }

    val thumbWidth by animateDpAsState(
        targetValue = if (dragging) 10.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "thumbWidth",
    )

    // Nothing but the handle takes touches: the rest of the grid, its right edge too, keeps scrolling
    // and opening photos while the scroller shows.
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    val track = (constraints.maxHeight - HandleHeight.roundToPx()).coerceAtLeast(0)
                    trackHeightPx.intValue = track
                    layout(placeable.width, constraints.maxHeight) {
                        placeable.place(0, (track * fraction()).roundToInt())
                    }
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = dragging && label != null,
                    enter = scaleIn(spring(dampingRatio = 0.55f), transformOrigin = TransformOrigin(1f, 0.5f)) + fadeIn(),
                    exit = scaleOut(transformOrigin = TransformOrigin(1f, 0.5f)) + fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 28.dp, bottomEnd = 6.dp),
                            )
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = label.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(width = HandleWidth, height = HandleHeight)
                        .pointerInput(totalItems) {
                            fun scrollToFraction() {
                                gridState.requestScrollToItem((dragFraction * maxIndex).roundToInt())
                            }
                            detectVerticalDragGestures(
                                onDragStart = {
                                    // Picks the thumb up where it is, a grab never jumps the grid.
                                    dragFraction = scrollFraction
                                    dragging = true
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, delta ->
                                    change.consume()
                                    val track = trackHeightPx.intValue
                                    if (track > 0) {
                                        dragFraction = (dragFraction + delta / track).coerceIn(0f, 1f)
                                        scrollToFraction()
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(width = thumbWidth, height = ThumbHeight)
                            .background(
                                if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(50),
                            ),
                    )
                }
            }
        }
    }
}

private val ThumbHeight = 52.dp

/** The part that takes touches, a little larger than the thumb so it's easy to grab, no more. */
private val HandleWidth = 32.dp
private val HandleHeight = 68.dp
