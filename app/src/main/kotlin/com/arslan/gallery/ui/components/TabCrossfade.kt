package com.arslan.gallery.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Fades through from one tab to another while keeping every tab that was shown composed. Switching
 * then only animates two layers instead of building a whole grid on the first frame of the
 * transition, and each tab comes back exactly as it was left. Tabs not shown yet get composed in the
 * background a moment after start, so even the first switch is smooth.
 *
 * A hidden tab is measured but not placed: not drawn, not touchable.
 */
@Composable
fun TabCrossfade(
    selected: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable (tab: Int) -> Unit,
) {
    val composed = remember { mutableStateListOf(selected) }
    LaunchedEffect(selected) {
        if (selected !in composed) composed += selected
    }
    LaunchedEffect(Unit) {
        delay(PREWARM_DELAY_MS)
        for (tab in 0 until count) if (tab !in composed) composed += tab
    }

    Box(modifier) {
        for (tab in 0 until count) {
            if (tab != selected && tab !in composed) continue
            key(tab) {
                val isSelected = tab == selected
                val progress = animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = if (isSelected) {
                        tween(FADE_IN_MS, delayMillis = FADE_OUT_MS / 2, easing = LinearOutSlowInEasing)
                    } else {
                        tween(FADE_OUT_MS, easing = FastOutLinearInEasing)
                    },
                    label = "tab$tab",
                )
                Box(
                    Modifier
                        .zIndex(if (isSelected) 1f else 0f)
                        .fillMaxSize()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                if (progress.value > 0f) placeable.place(0, 0)
                            }
                        }
                        .graphicsLayer {
                            val p = progress.value
                            alpha = p
                            val scale = 0.96f + 0.04f * p
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    content(tab)
                }
            }
        }
    }
}

private const val FADE_IN_MS = 240
private const val FADE_OUT_MS = 100
private const val PREWARM_DELAY_MS = 800L
