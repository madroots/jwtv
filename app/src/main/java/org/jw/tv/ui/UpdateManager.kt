package org.jw.tv.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jw.tv.api.MediatorClient
import java.io.File
import java.io.FileOutputStream

object UpdateManager {
    const val UPDATE_VERSION_URL = "https://plugins.best-web.sk/random/jwtv/version.txt"
    const val UPDATE_APK_URL = "https://plugins.best-web.sk/random/jwtv/app-debug.apk"
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
