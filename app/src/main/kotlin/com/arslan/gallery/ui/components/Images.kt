package com.arslan.gallery.ui.components

import android.content.Context
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.video.videoFrameMillis
import com.arslan.gallery.data.Medium

/** Edge length, in px, of the thumbnails decoded for grids. Fixed so the memory cache key is stable. */
const val THUMBNAIL_SIZE_PX = 420

fun thumbnailCacheKey(medium: Medium) = "tmb:${medium.signature}"

fun fullImageCacheKey(medium: Medium) = "full:${medium.signature}"

/**
 * Request for a grid thumbnail. Album covers, dialogs and the viewer reuse the same memory cache key,
 * which makes them show up instantly and keeps the photo flying between grid and viewer seamless.
 */
fun thumbnailRequest(context: Context, medium: Medium, animate: Boolean = false, crossfade: Boolean = true): ImageRequest {
    return ImageRequest.Builder(context)
        .data(medium.model())
        .size(THUMBNAIL_SIZE_PX)
        .precision(Precision.INEXACT)
        .memoryCacheKey(if (animate && medium.isGif) "anim:${medium.signature}" else thumbnailCacheKey(medium))
        .apply {
            if (medium.isVideo) {
                videoFrameMillis(1000)
            }
            if (!animate && medium.isGif) {
                decoderFactory(BitmapFactoryDecoder.Factory())
            }
        }
        .crossfade(if (crossfade) 180 else 0)
        .build()
}

/** Full-size request used by the viewer. The viewer draws the thumbnail below it until it's ready. */
fun fullImageRequest(context: Context, medium: Medium): ImageRequest {
    return ImageRequest.Builder(context)
        .data(medium.model())
        .memoryCacheKey(fullImageCacheKey(medium))
        .crossfade(false)
        .build()
}
