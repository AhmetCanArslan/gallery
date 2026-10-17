package com.arslan.gallery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bouncy spring used for press feedback, tuned to feel like the expressive motion scheme. */
val PressSpring = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)

/**
 * Scales the element down while pressed and springs it back with a little overshoot on release.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = PressSpring,
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Click + long click with ripple, spring press scale and haptics in one modifier.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.expressiveClickable(
    pressedScale: Float = 0.94f,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val haptics = rememberHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    this
        .pressScale(interactionSource, pressedScale)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onLongClick = onLongClick?.let {
                {
                    haptics.longPress()
                    it()
                }
            },
            onClick = onClick,
        )
}

/**
 * Plays a one-shot staggered "pop in" for grid items: fade + rise + slight scale.
 * Only runs when [enabled] is true, so recycled lazy items don't re-animate while scrolling.
 */
fun Modifier.staggeredEntrance(index: Int, enabled: Boolean): Modifier = composed {
    // Decide once: an entrance that started keeps running even if [enabled] flips afterwards.
    val shouldAnimate = remember { enabled }
    if (!shouldAnimate) return@composed this

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(24) * 22L))
        launch {
            progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
        }
    }

    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 48.dp.toPx()
        val s = 0.86f + 0.14f * p
        scaleX = s
        scaleY = s
    }
}
