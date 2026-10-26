package com.arslan.gallery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bouncy spring used for press feedback, tuned to feel like the expressive motion scheme. */
val PressSpring = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)

/**
 * Scales the element down while pressed and springs it back with a little overshoot on release.
 * A modifier node rather than `composed`, it's on every grid thumbnail and those get created on every
 * frame of a fast scroll.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f,
): Modifier = this then PressScaleElement(interactionSource, pressedScale)

private data class PressScaleElement(
    val interactionSource: InteractionSource,
    val pressedScale: Float,
) : ModifierNodeElement<PressScaleNode>() {
    override fun create() = PressScaleNode(interactionSource, pressedScale)

    override fun update(node: PressScaleNode) = node.update(interactionSource, pressedScale)
}

private class PressScaleNode(
    private var interactionSource: InteractionSource,
    private var pressedScale: Float,
) : Modifier.Node(), LayoutModifierNode {
    private val scale = Animatable(1f)
    private val presses = mutableListOf<PressInteraction.Press>()
    private var collecting: Job? = null
    private val layer: GraphicsLayerScope.() -> Unit = {
        scaleX = scale.value
        scaleY = scale.value
    }

    override fun onAttach() = collect()

    override fun onDetach() {
        presses.clear()
        collecting = null
    }

    fun update(interactionSource: InteractionSource, pressedScale: Float) {
        this.pressedScale = pressedScale
        if (this.interactionSource == interactionSource) return
        this.interactionSource = interactionSource
        if (isAttached) {
            collecting?.cancel()
            presses.clear()
            collect()
        }
    }

    private fun collect() {
        collecting = coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                    else -> return@collect
                }
                val target = if (presses.isEmpty()) 1f else pressedScale
                launch { scale.animateTo(target, PressSpring) }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = layer)
        }
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
