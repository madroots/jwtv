package org.jw.tv.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jw.tv.api.GithubRelease
import org.jw.tv.api.MediatorClient
import java.io.File
import java.io.FileOutputStream

object UpdateManager {
    // ── Platform update endpoints ────────────────────────────────────────────
    // Both apps self-update from GitHub release assets. Each release must carry
    // BOTH pairs of artifacts or `releases/latest` 404s for the missing one:
    //   TV:    version.txt        (int = TV versionCode) + app-release.apk
    //   Phone: version-phone.txt  (int = phone versionCode) + app-phone-release.apk
    //
    // TV defaults are the historical constants — :app's flow is byte-identical
    // unless configure() is called. The phone app calls configure() with its
    // own endpoints at startup (JwPhoneApp), so an old TV build can never
    // receive a phone APK and vice versa.
    private const val TV_VERSION_URL =
        "https://github.com/madroots/jwtv/releases/latest/download/version.txt"
    private const val TV_APK_URL =
        "https://github.com/madroots/jwtv/releases/latest/download/app-release.apk"

    const val PHONE_VERSION_URL =
        "https://github.com/madroots/jwtv/releases/latest/download/version-phone.txt"
    const val PHONE_APK_URL =
        "https://github.com/madroots/jwtv/releases/latest/download/app-phone-release.apk"

    // Shared by both channels: version name + release notes for the dialog.
    // Detection itself stays on the version files so already-installed builds
    // keep working unchanged.
    private const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/madroots/jwtv/releases/latest"

    @Volatile
    var versionUrl: String = TV_VERSION_URL
        private set

    @Volatile
    var apkUrl: String = TV_APK_URL
        private set

    /** Switches the update channel. Safe to call once at app startup. */
    fun configure(platformVersionUrl: String, platformApkUrl: String) {
        versionUrl = platformVersionUrl
        apkUrl = platformApkUrl
    }

    /** Latest release metadata, or null if GitHub is unreachable/rate-limited. */
    suspend fun fetchLatestRelease(): GithubRelease? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            MediatorClient.client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    MediatorClient.json.decodeFromString<GithubRelease>(body)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val downloadClient by lazy {
        MediatorClient.client.newBuilder()
            .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    // Takes Application (not Activity) context so it remains valid across the
    // entire download — Activity context can be dead by the time a slow download
    // finishes on TV hardware.
    suspend fun downloadApk(
        app: Application,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(apkUrl).build()
            // Use internal cache only — externalCacheDir may require storage
            // permissions on older TV firmware and can be null/unmounted.
            val apkFile = File(app.cacheDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                onProgress(totalBytesRead.toFloat() / contentLength.toFloat())
                            }
                        }
                        // fsync: flush OS page cache to storage so the
                        // PackageInstaller (a separate process) reads a complete
                        // file even on flash-backed TV storage.
                        output.fd.sync()
                    }
                }
            }
            apkFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun installApk(app: Application, apkFile: File) = withContext(Dispatchers.Main) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            // FLAG_ACTIVITY_NEW_TASK required when starting from Application context.
            // FLAG_ACTIVITY_CLEAR_TOP ensures the PackageInstaller is brought to the
            // foreground if it already exists from a prior (silently-failed) attempt.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        app.startActivity(intent)
    }
}
