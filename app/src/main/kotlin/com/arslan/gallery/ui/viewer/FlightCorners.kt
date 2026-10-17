package com.arslan.gallery.ui.viewer

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Corner radius of a photo shrinking between the viewer and the grid, so it softens on the way
 * instead of only rounding once it lands. Driven by how much of the screen the photo fills: square
 * at rest (an aspect-fit photo always fills it in one dimension), the thumbnail's corners at
 * thumbnail size, with a little extra rounding in between. Dragging, releasing and flying all follow
 * the same curve, so the corners never jump from one to the next.
 */
class FlightCorners {
    /** Size of the screen the viewer fills. */
    var viewport = Size.Zero

    /** Width of a grid thumbnail, they all share the same size. */
    var thumbnailSize = 0f

    fun radius(bounds: Size, density: Density): Float {
        val viewport = viewport
        // Written as "not greater" so unspecified (NaN) bounds, e.g. before an image loaded, bail out too.
        if (!(viewport.width > 0f && viewport.height > 0f && bounds.width > 0f && bounds.height > 0f)) return 0f
        val filled = max(bounds.width / viewport.width, bounds.height / viewport.height)
        val landed = (thumbnailSize.takeIf { it > 0f } ?: (viewport.width / 4f)) / viewport.width
        if (landed >= 1f) return 0f
        val t = ((1f - filled) / (1f - landed)).coerceIn(0f, 1f)
        return with(density) { THUMBNAIL_CORNER.toPx() * t + EXTRA_CORNER.toPx() * sin(PI.toFloat() * t) }
    }

    companion object {
        /** Matches the corners of the grid thumbnails. */
        val THUMBNAIL_CORNER = 6.dp
        private val EXTRA_CORNER = 18.dp
    }
}

/** A rounded rect anywhere within the layout, unlike RoundedCornerShape which always fills it. */
internal data class RoundedRectAt(val rect: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}
