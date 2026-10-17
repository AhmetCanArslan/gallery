package com.arslan.gallery.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity

/**
 * Slides a bottom bar fully off screen while scrolling down and back on scrolling up. Unlike the Material
 * floating toolbar behavior it moves the whole container, including its system bar and margin padding,
 * so nothing of the bar stays visible.
 */
@Stable
class ScrollAwayBarState : NestedScrollConnection {
    private var height by mutableFloatStateOf(0f)

    /** How far the bar is pushed down, between 0 (shown) and its full height (hidden). */
    var offset by mutableFloatStateOf(0f)
        private set

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        offset = (offset - consumed.y).coerceIn(0f, height)
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // Never rest half hidden.
        if (offset > 0f && offset < height) {
            settle(if (offset < height / 2) 0f else height)
        }
        return Velocity.Zero
    }

    suspend fun show() = settle(0f)

    private suspend fun settle(target: Float) {
        animate(offset, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
            offset = value
        }
    }

    internal fun updateHeight(newHeight: Float) {
        height = newHeight
        offset = offset.coerceAtMost(newHeight)
    }
}

@Composable
fun rememberScrollAwayBarState() = remember { ScrollAwayBarState() }

/** Apply before any padding of the bar, so the measured height covers it all. */
fun Modifier.scrollAway(state: ScrollAwayBarState) = this
    .graphicsLayer { translationY = state.offset }
    .onSizeChanged { state.updateHeight(it.height.toFloat()) }
