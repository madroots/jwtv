package org.jw.phone

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import org.jw.tv.api.MediatorClient
import org.jw.tv.createAppImageLoader
import org.jw.tv.ui.UpdateManager

/**
 * Phone counterpart of [org.jw.tv.JwTvApp]: same network/cache initialization,
 * same Coil configuration (shared via :core so the apps cannot drift apart).
 */
class JwPhoneApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        MediatorClient.init(this)
        // Phone self-update channel (issue #7): per-platform release assets,
        // never the TV APK. TV defaults are untouched.
        UpdateManager.configure(UpdateManager.PHONE_VERSION_URL, UpdateManager.PHONE_APK_URL)
    }

    override fun newImageLoader(): ImageLoader = createAppImageLoader(this)
}
