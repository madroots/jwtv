package org.jw.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun `defaults are the TV channel — old TV builds never receive phone APKs`() {
        assertEquals(
            "https://github.com/madroots/jwtv/releases/latest/download/version.txt",
            UpdateManager.versionUrl
        )
        assertEquals(
            "https://github.com/madroots/jwtv/releases/latest/download/app-release.apk",
            UpdateManager.apkUrl
        )
    }

    @Test
    fun `configure switches to the phone channel`() {
        UpdateManager.configure(UpdateManager.PHONE_VERSION_URL, UpdateManager.PHONE_APK_URL)
        assertEquals(UpdateManager.PHONE_VERSION_URL, UpdateManager.versionUrl)
        assertEquals(UpdateManager.PHONE_APK_URL, UpdateManager.apkUrl)
        // Restore TV defaults so this test is order-independent.
        UpdateManager.configure(
            "https://github.com/madroots/jwtv/releases/latest/download/version.txt",
            "https://github.com/madroots/jwtv/releases/latest/download/app-release.apk"
        )
    }
}
