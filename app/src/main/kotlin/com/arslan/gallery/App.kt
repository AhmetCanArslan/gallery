package com.arslan.gallery

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.video.VideoFrameDecoder
import com.arslan.gallery.ui.components.ThumbnailDiskCache
import com.arslan.gallery.ui.theme.Appearance

class App : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Appearance.load(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val isLowRam = getSystemService(ActivityManager::class.java).isLowRamDevice
        return ImageLoader.Builder(context)
            .components {
                add(ThumbnailDiskCache(context))
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, if (isLowRam) 0.2 else 0.3)
                    .build()
            }
            // Half the memory per opaque photo, so low RAM phones keep twice as many thumbnails around.
            .allowRgb565(isLowRam)
            .build()
    }
}
