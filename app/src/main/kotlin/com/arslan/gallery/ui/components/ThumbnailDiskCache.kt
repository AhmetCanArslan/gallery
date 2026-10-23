package com.arslan.gallery.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import coil3.BitmapImage
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.imageLoader
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the grid thumbnails on disk. Without it every thumbnail that drops out of the memory cache is
 * decoded again from the full photo, or worse a video frame, which is what makes scrolling stutter on
 * slow phones. A small JPEG decodes many times faster than either.
 */
class ThumbnailDiskCache(context: Context) : Interceptor {

    private val appContext = context.applicationContext

    private val cache by lazy {
        DiskCache.Builder()
            .directory(appContext.cacheDir.resolve("thumbnails"))
            .maxSizePercent(0.05)
            .minimumMaxSizeBytes(64L * 1024 * 1024)
            .maximumMaxSizeBytes(512L * 1024 * 1024)
            .build()
    }

    /** Writes happen one at a time, so they never compete with the decodes the grid waits for. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val key = request.memoryCacheKey
        if (key == null || !key.startsWith(THUMBNAIL_PREFIX)) return chain.proceed()

        // Already in memory: the engine returns it right away, don't touch the disk.
        val memoryKey = MemoryCache.Key(key, request.memoryCacheKeyExtras)
        if (appContext.imageLoader.memoryCache?.get(memoryKey) != null) return chain.proceed()

        val snapshot = withContext(Dispatchers.IO) { runCatching { cache.openSnapshot(key) }.getOrNull() }
        if (snapshot != null) {
            val result = snapshot.use {
                chain.withRequest(request.newBuilder().data(it.data.toFile()).build()).proceed()
            }
            if (result is SuccessResult) return result
            // Unreadable entry, drop it and decode the original instead.
            withContext(Dispatchers.IO) { runCatching { cache.remove(key) } }
        }

        val result = chain.proceed()
        val bitmap = ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap
        if (bitmap != null) writes.launch { write(key, bitmap) }
        return result
    }

    private fun write(key: String, bitmap: Bitmap) {
        val editor = runCatching { cache.openEditor(key) }.getOrNull() ?: return
        try {
            val source = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
            val scaled = source.scaledToFit(THUMBNAIL_SIZE_PX)
            val format = when {
                !scaled.hasAlpha() -> Bitmap.CompressFormat.JPEG
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Bitmap.CompressFormat.WEBP_LOSSY
                else -> Bitmap.CompressFormat.PNG
            }
            val ok = editor.data.toFile().outputStream().buffered().use { scaled.compress(format, QUALITY, it) }
            if (scaled !== bitmap && scaled !== source) scaled.recycle()
            if (source !== bitmap) source.recycle()
            if (ok) editor.commit() else editor.abort()
        } catch (e: Exception) {
            runCatching { editor.abort() }
        }
    }

    /** Shrinks the bitmap so its shorter side is [size], the decoder may hand over a larger one. */
    private fun Bitmap.scaledToFit(size: Int): Bitmap {
        val shorter = minOf(width, height)
        if (shorter <= size) return this
        val ratio = size.toFloat() / shorter
        return Bitmap.createScaledBitmap(this, (width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1), true)
    }

    private companion object {
        const val THUMBNAIL_PREFIX = "tmb:"
        const val QUALITY = 82
    }
}
