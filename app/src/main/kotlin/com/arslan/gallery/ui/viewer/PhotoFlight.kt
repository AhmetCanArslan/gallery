package com.arslan.gallery.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import coil3.compose.asPainter
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.memory.MemoryCache
import com.arslan.gallery.data.Medium
import com.arslan.gallery.ui.LocalViewer
import com.arslan.gallery.ui.ViewerController
import com.arslan.gallery.ui.components.THUMBNAIL_SIZE_PX
import com.arslan.gallery.ui.components.fullImageCacheKey
import com.arslan.gallery.ui.components.thumbnailRequest

/**
 * Where a photo is drawn, in root coordinates: [image] is where the whole image goes and [clip] the
 * part of it that shows. Both match for an aspect-fit photo, a grid thumbnail crops it to a square.
 */
@Immutable
data class PhotoFrame(val image: Rect, val clip: Rect, val alpha: Float = 1f) {
    /** Shrunk around its center and faded, to appear or vanish where there is no thumbnail to fly to. */
    fun faded(scale: Float) = PhotoFrame(image.scaledAround(clip.center, scale), clip.scaledAround(clip.center, scale), alpha = 0f)

    companion object {
        /** An image of [aspect] fitted into [bounds], centered. Fills them when the aspect isn't known yet. */
        fun fit(bounds: Rect, aspect: Float?): PhotoFrame {
            val image = if (aspect == null) bounds else bounds.centered(minOf(bounds.width, bounds.height * aspect), aspect)
            return PhotoFrame(image, image)
        }

        /** An image of [aspect] cropped to [bounds], centered. */
        fun crop(bounds: Rect, aspect: Float?): PhotoFrame {
            val image = if (aspect == null) bounds else bounds.centered(maxOf(bounds.width, bounds.height * aspect), aspect)
            return PhotoFrame(image, bounds)
        }
    }
}

fun lerp(start: PhotoFrame, stop: PhotoFrame, fraction: Float) = PhotoFrame(
    image = lerp(start.image, stop.image, fraction),
    clip = lerp(start.clip, stop.clip, fraction),
    alpha = lerp(start.alpha, stop.alpha, fraction).coerceIn(0f, 1f),
)

private fun Rect.centered(width: Float, aspect: Float) = Rect(center - Offset(width / 2f, width / aspect / 2f), Size(width, width / aspect))

private fun Rect.scaledAround(pivot: Offset, scale: Float) = Rect(pivot + (topLeft - pivot) * scale, pivot + (bottomRight - pivot) * scale)

/**
 * A closed photo flying back into its grid thumbnail. It lives on its own, apart from the viewer, so
 * the viewer is gone the moment it closes: the grid takes touches right away, and opening another
 * photo (or the same one again, which picks it up mid-air) never fights over a shared transition.
 * The thumbnail is looked up on every frame, so it lands right even while the grid still moves.
 */
@Stable
class PhotoFlight(
    val source: String,
    val medium: Medium,
    private val from: PhotoFrame,
    private val fromScrim: Float,
    /** Of the gesture that closed the viewer, in px/s, it carries on into the flight. */
    private val velocity: Offset,
) {
    private val progress = Animatable(0f)

    /** Whether it still mostly covers the screen, see [ViewerController.isOnScreen]. */
    var isCovering by mutableStateOf(true)
        private set

    fun frame(viewer: ViewerController) = lerp(from, target(viewer), progress.value)

    fun scrim() = fromScrim * (1f - progress.value).coerceIn(0f, 1f)

    private fun target(viewer: ViewerController) = viewer.thumbnailFrame(source, medium.path) ?: from.faded(0.85f)

    suspend fun fly(viewer: ViewerController) {
        // The swipe's speed towards the thumbnail, as progress per second.
        val travel = target(viewer).clip.center - from.clip.center
        val distance = travel.getDistance()
        val initialVelocity = if (distance < 1f) 0f else ((velocity.x * travel.x + velocity.y * travel.y) / (distance * distance)).coerceIn(0f, 8f)
        progress.animateTo(1f, FlightSpring, initialVelocity) {
            if (value > 0.5f) isCovering = false
        }
    }
}

/** Draws the photos flying back into the grid, with what is left of the viewer's backdrop. */
@Composable
fun FlyingPhotos() {
    val viewer = LocalViewer.current
    for (flight in viewer.flights) {
        key(flight) {
            FlyingPhoto(viewer, flight)
        }
    }
}

@Composable
private fun FlyingPhoto(viewer: ViewerController, flight: PhotoFlight) {
    val context = LocalContext.current
    val thumbnail = rememberAsyncImagePainter(remember(flight) { thumbnailRequest(context, flight.medium, crossfade = false) })
    // It starts out at full size, so the viewer's sharper image is better while it's still in memory.
    val full = remember(flight) {
        context.imageLoader.memoryCache?.get(MemoryCache.Key(fullImageCacheKey(flight.medium)))?.image
            ?.takeIf { maxOf(it.width, it.height) > THUMBNAIL_SIZE_PX }
            ?.asPainter(context)
    }
    val painter = full ?: thumbnail
    val path = remember { Path() }

    LaunchedEffect(flight) {
        flight.fly(viewer)
        viewer.flights.remove(flight)
    }

    // No pointer input, touches go right through to the grid.
    Spacer(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color.Black, alpha = flight.scrim())
                val frame = flight.frame(viewer)
                path.reset()
                path.addRoundRect(RoundRect(frame.clip, CornerRadius(viewer.corners.radius(frame.clip.size, this))))
                clipPath(path) {
                    translate(frame.image.left, frame.image.top) {
                        with(painter) { draw(frame.image.size, alpha = frame.alpha) }
                    }
                }
            },
    )
}

/** Shared by opening and closing, so a photo turned around mid-air keeps the same feel. */
val FlightSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 380f)

/**
 * Marks a grid thumbnail as the place the photo [path] of the list [source] opens from and flies
 * back to, see [ViewerController.thumbnailFrame].
 */
fun Modifier.flightAnchor(viewer: ViewerController, source: String, path: String): Modifier =
    this then FlightAnchorElement(viewer, ViewerController.anchorKey(source, path))

private data class FlightAnchorElement(val viewer: ViewerController, val key: String) : ModifierNodeElement<FlightAnchorNode>() {
    override fun create() = FlightAnchorNode(viewer, key)

    override fun update(node: FlightAnchorNode) {
        if (node.viewer == viewer && node.key == key) return
        node.release()
        node.viewer = viewer
        node.key = key
        node.coordinates?.let { viewer.anchors[key] = it }
    }
}

private class FlightAnchorNode(var viewer: ViewerController, var key: String) : Modifier.Node(), LayoutAwareModifierNode {
    var coordinates: LayoutCoordinates? = null
        private set

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        viewer.anchors[key] = coordinates
    }

    override fun onDetach() {
        release()
        coordinates = null
    }

    /** Only drops the anchor if it's still ours, the same photo may sit in another grid too. */
    fun release() {
        if (viewer.anchors[key] === coordinates) viewer.anchors.remove(key)
    }
}
