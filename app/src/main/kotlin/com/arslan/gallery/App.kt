package com.arslan.gallery

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.arslan.gallery.ui.theme.Appearance

class App : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Appearance.load(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.3)
                    .build()
            }
            .build()
    }
}
