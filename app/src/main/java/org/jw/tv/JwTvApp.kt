package org.jw.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import org.jw.tv.api.MediatorClient

/**
 * ARCH-3: Centralized network & cache initialization.
 * [MediatorClient.init] is called here in [onCreate] so OkHttp, 100 MB HTTP cache,
 * and [org.jw.tv.api.CacheManager] are initialized at the application level before
 * any Activity or background task starts.
 */
class JwTvApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        MediatorClient.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)   // ≤25 % of app RAM (≈64 MB on 256 MB heap)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024) // 256 MB on-disk thumbnail cache
                    .build()
            }
            .crossfade(false)               // crossfade adds a frame delay; skip on TV
            .respectCacheHeaders(false)     // JW CDN headers are conservative; use our TTL
            .build()
    }
}
