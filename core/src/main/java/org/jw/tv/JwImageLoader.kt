package org.jw.tv

import android.app.ActivityManager
import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Shared Coil [ImageLoader] configuration used by both the TV and phone apps.
 * Lives in :core so the two applications cannot drift apart.
 */
fun createAppImageLoader(context: Context): ImageLoader {
    // Low-RAM devices (many TV sticks): 16-bit thumbnails halve bitmap memory,
    // and no crossfade avoids drawing two images per card while it runs.
    val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
        ?.isLowRamDevice == true
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
        // Fades in images that come from disk/network; memory-cache hits
        // (scrolling back) still appear instantly.
        .crossfade(if (lowRam) 0 else 180)
        .allowRgb565(lowRam)
        .respectCacheHeaders(false)     // JW CDN headers are conservative; use our TTL
        .build()
}
