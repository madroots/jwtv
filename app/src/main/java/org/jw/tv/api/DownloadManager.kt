package org.jw.tv.api

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jw.tv.data.DownloadEntity
import org.jw.tv.data.MediaDao
import java.io.File
import java.io.FileOutputStream

object DownloadManager {

    private val downloadClient by lazy {
        MediatorClient.client.newBuilder()
            .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    suspend fun downloadVideo(
        app: Application,
        video: MediaItem,
        quality: String,
        mediaDao: MediaDao,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val file = video.files?.find { it.label?.lowercase() == quality.lowercase() }
                ?: return@withContext null
            val url = file.progressiveDownloadURL ?: return@withContext null

            val dir = File(app.filesDir, "downloads")
            if (!dir.exists()) dir.mkdirs()

            val apkFile = File(dir, "${video.contentId}_${quality}.mp4")
            if (apkFile.exists()) apkFile.delete()

            val request = Request.Builder().url(url).build()
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
                        output.fd.sync()
                    }
                }
            }

            // Save metadata to Room
            val jsonStr = try {
                MediatorClient.json.encodeToString(MediaItem.serializer(), video)
            } catch (e: Exception) { "" }

            val entity = DownloadEntity(
                contentId = video.contentId,
                title = video.title,
                thumbnailUrl = video.getThumbnailUrl(small = true),
                durationSeconds = video.duration,
                quality = quality,
                filePath = apkFile.absolutePath,
                downloadedAt = System.currentTimeMillis(),
                rawMediaItemJson = jsonStr
            )
            mediaDao.addDownload(entity)

            apkFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteDownload(app: Application, download: DownloadEntity) {
        withContext(Dispatchers.IO) {
            val file = File(download.filePath)
            if (file.exists()) file.delete()
        }
    }
}
