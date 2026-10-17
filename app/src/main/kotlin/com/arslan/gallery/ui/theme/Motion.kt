package com.arslan.gallery.ui.theme

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

/** M3 "emphasized" easing, used for transitions that begin and end on screen. */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

const val CONTAINER_TRANSFORM_DURATION = 480

/**
 * Bounds animation for the album card -> album screen container transform. A tween (not a spring)
 * keeps it well-behaved while predictive back seeks through the transition.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val ContainerTransformBounds = BoundsTransform { _, _ -> tween(CONTAINER_TRANSFORM_DURATION, easing = EmphasizedEasing) }

private const val CONTAINER_CROSSFADE_DURATION = 140

/**
 * The album card's contents only fade in at the end of the closing transform, so while the album
 * shrinks back into its card the album itself is what's seen, not its cover blown up to full size.
 */
val AlbumCardEnter: EnterTransition =
    fadeIn(tween(CONTAINER_CROSSFADE_DURATION, delayMillis = CONTAINER_TRANSFORM_DURATION - CONTAINER_CROSSFADE_DURATION))

/** Counterpart of [AlbumCardEnter], the album stays visible until it has nearly reached the card. */
val AlbumScreenExit: ExitTransition =
    fadeOut(tween(CONTAINER_CROSSFADE_DURATION, delayMillis = CONTAINER_TRANSFORM_DURATION - CONTAINER_CROSSFADE_DURATION))
