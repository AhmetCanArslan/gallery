package com.arslan.gallery.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Slides a bottom bar fully off screen while scrolling down and back on scrolling up. Unlike the Material
 * floating toolbar behavior it moves the whole container, including its system bar and margin padding,
 * so nothing of the bar stays visible.
 *
 * Settling runs on its own and stops as soon as the finger moves the content again, so it never fights
 * a drag. Pushing on past the end of the content brings the bar back, it never stays hidden there.
 */
@Stable
class ScrollAwayBarState internal constructor(private val scope: CoroutineScope) : NestedScrollConnection {
    private var height by mutableFloatStateOf(0f)
    private var settling: Job? = null

    /** How far the bar is pushed down, between 0 (shown) and its full height (hidden). */
    var offset by mutableFloatStateOf(0f)
        private set

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) stopSettling()
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // What the content couldn't take past its end reveals the bar, without being consumed.
        val overscroll = if (source == NestedScrollSource.UserInput) available.y.coerceAtMost(0f) else 0f
        offset = (offset - consumed.y + overscroll).coerceIn(0f, height)
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // Never rest half hidden.
        if (offset > 0f && offset < height) {
            settleTo(if (offset < height / 2) 0f else height)
        }
        return Velocity.Zero
    }

    fun show() = settleTo(0f)

    private fun settleTo(target: Float) {
        stopSettling()
        if (offset == target) return
        settling = scope.launch {
            animate(offset, target, animationSpec = SettleSpring) { value, _ -> offset = value }
        }
    }

    private fun stopSettling() {
        settling?.cancel()
        settling = null
    }

    internal fun updateHeight(newHeight: Float) {
        height = newHeight
        offset = offset.coerceAtMost(newHeight)
    }
}

@Composable
fun rememberScrollAwayBarState(): ScrollAwayBarState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ScrollAwayBarState(scope) }
}

/** Apply before any padding of the bar, so the measured height covers it all. */
fun Modifier.scrollAway(state: ScrollAwayBarState) = this
    .graphicsLayer { translationY = state.offset }
    .onSizeChanged { state.updateHeight(it.height.toFloat()) }

private val SettleSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
