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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }

    val scrollFraction by remember(totalItems) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val visibleCount = info.visibleItemsInfo.size
            val maxIndex = (totalItems - visibleCount).coerceAtLeast(1)
            (gridState.firstVisibleItemIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
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
    val label by remember(totalItems) { derivedStateOf { currentLabelFor((fraction() * (totalItems - 1)).roundToInt()) } }

    val thumbHeight = 52.dp
    val thumbWidth by animateDpAsState(
        targetValue = if (dragging) 10.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "thumbWidth",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(contentPadding)
            .width(56.dp),
    ) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { (maxHeight - thumbHeight).toPx() }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(56.dp)
                    .pointerInput(totalItems, trackHeightPx) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                dragFraction = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                                scope.launch { gridState.scrollToItem((dragFraction * (totalItems - 1)).roundToInt()) }
                            },
                            onDragEnd = {
                                dragging = false
                            },
                            onDragCancel = { dragging = false },
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                dragFraction = (dragFraction + delta / trackHeightPx).coerceIn(0f, 1f)
                                scope.launch { gridState.scrollToItem((dragFraction * (totalItems - 1)).roundToInt()) }
                            },
                        )
                    },
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, (trackHeightPx * fraction()).roundToInt()) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = dragging && label != null,
                        enter = scaleIn(spring(dampingRatio = 0.55f), transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)) + fadeIn(),
                        exit = scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)) + fadeOut(),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
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
                            .padding(end = 6.dp)
                            .size(width = thumbWidth, height = thumbHeight)
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
