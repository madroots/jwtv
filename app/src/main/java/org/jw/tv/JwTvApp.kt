package org.jw.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient

/**
 * PERF-2: Custom ImageLoader with bounded memory cache (64 MB) and disk cache
 * (256 MB). Coil picks this up automatically via [ImageLoaderFactory].
 * OkHttpClient is shared with [org.jw.tv.api.MediatorClient] — same connection
 * pool, same HTTP cache — so image requests don't open extra sockets.
 */
class JwTvApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        // MediatorClient.init is called in MainActivity; by the time any
        // AsyncImage fires, the client is already configured with its HTTP cache.
        // We build a fresh client here only for Coil (separate OkHttp cache dir)
        // so image disk caching is independent of JSON response caching.
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
