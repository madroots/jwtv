package org.jw.tv

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Shared Coil [ImageLoader] configuration used by both the TV and phone apps.
 * Lives in :core so the two applications cannot drift apart.
 */
fun createAppImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25)   // ≤25 % of app RAM (≈64 MB on 256 MB heap)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024) // 256 MB on-disk thumbnail cache
                .build()
        }
        .crossfade(false)               // crossfade adds a frame delay; skip on TV
        .respectCacheHeaders(false)     // JW CDN headers are conservative; use our TTL
        .build()
}
