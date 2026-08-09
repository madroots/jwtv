package org.jw.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
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
        // No custom UncaughtExceptionHandler: swallowing main-thread exceptions
        // kills the Looper but keeps the process alive → frozen zombie app.
        MediatorClient.init(this)
    }

    // Shared configuration lives in :core (createAppImageLoader) so the TV and
    // phone apps cannot drift apart.
    override fun newImageLoader(): ImageLoader = createAppImageLoader(this)
}
