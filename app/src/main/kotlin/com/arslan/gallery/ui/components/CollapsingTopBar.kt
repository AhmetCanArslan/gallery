package com.arslan.gallery.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Exit until collapsed, like the Material behavior, but settling never fights the finger: the snap
 * after a fling runs on its own and stops the moment a new scroll starts, instead of running inside
 * the old fling while the next drag moves the same bar.
 *
 * Its connection has to sit between the grid and any pull to refresh, so the bar expands before the
 * refresh indicator gets pulled. Otherwise the indicator takes every drag at the top and a collapsed
 * bar stays stuck collapsed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class CollapsingTopBarBehavior internal constructor(
    override val state: TopAppBarState,
    private val scope: CoroutineScope,
    override val flingAnimationSpec: DecayAnimationSpec<Float>?,
) : TopAppBarScrollBehavior {
    override val isPinned = false

    /** Only used when the bar itself is dragged. */
    override val snapAnimationSpec: AnimationSpec<Float> = SettleSpring

    private var settling: Job? = null

    override val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) stopSettling()
            if (available.y >= 0f) return Offset.Zero
            val before = state.heightOffset
            state.heightOffset = before + available.y
            return Offset(0f, state.heightOffset - before)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            state.contentOffset += consumed.y
            // Scrolling back towards the top: the bar only comes back once the content can't go further.
            if (available.y <= 0f) return Offset.Zero
            val before = state.heightOffset
            state.heightOffset = before + available.y
            return Offset(0f, state.heightOffset - before)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y > 0f) state.contentOffset = 0f
            settle(available.y)
            return Velocity.Zero
        }
    }

    /** Animates the bar to [target] (0 expanded, [TopAppBarState.heightOffsetLimit] collapsed). */
    fun animateTo(target: Float, velocity: Float = 0f) {
        stopSettling()
        if (state.heightOffset == target) return
        settling = scope.launch {
            animate(state.heightOffset, target, velocity, SettleSpring) { value, _ -> state.heightOffset = value }
        }
    }

    fun expand() = animateTo(0f)

    fun collapse() {
        // Not laid out yet, there's nothing to collapse to.
        if (state.heightOffsetLimit == -Float.MAX_VALUE) return
        animateTo(state.heightOffsetLimit)
    }

    /** Never rest half collapsed: carry on in the direction it was flung, or to the nearer end. */
    private fun settle(velocity: Float) {
        val limit = state.heightOffsetLimit
        val offset = state.heightOffset
        if (limit >= 0f || offset >= 0f || offset <= limit) return
        val expand = when {
            abs(velocity) > FLING_THRESHOLD -> velocity > 0f
            else -> state.collapsedFraction < 0.5f
        }
        animateTo(if (expand) 0f else limit, velocity)
    }

    private fun stopSettling() {
        settling?.cancel()
        settling = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCollapsingTopBarBehavior(): CollapsingTopBarBehavior {
    val state = rememberTopAppBarState()
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    return remember(state, scope, decay) { CollapsingTopBarBehavior(state, scope, decay) }
}

private val SettleSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

/** px/s, slower flings settle to whichever end is nearer. */
private const val FLING_THRESHOLD = 600f
