package org.jw.phone

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import org.jw.tv.api.MediatorClient
import org.jw.tv.createAppImageLoader

/**
 * Phone counterpart of [org.jw.tv.JwTvApp]: same network/cache initialization,
 * same Coil configuration (shared via :core so the apps cannot drift apart).
 */
class JwPhoneApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        MediatorClient.init(this)
    }

    override fun newImageLoader(): ImageLoader = createAppImageLoader(this)
}
