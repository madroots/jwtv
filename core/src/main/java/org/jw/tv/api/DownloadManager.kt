package org.jw.tv.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jw.tv.data.DownloadEntity
import org.jw.tv.data.MediaDao
import java.io.File
import java.io.FileOutputStream

/** Snapshot of what downloads occupy on disk. */
class DownloadStorage(
    /** Rows whose file still exists, newest first. */
    val downloads: List<DownloadEntity>,
    /** contentId -> file size in bytes. */
    val sizes: Map<String, Long>,
    /**
     * Files in the downloads folder no row points at: leftovers of interrupted
     * downloads, of a quality replaced by another one, or of the DB being reset
     * by a schema change. Invisible in the list but still taking space.
     */
    val unlistedBytes: Long,
    val freeBytes: Long
)

object DownloadManager {

    private val downloadClient by lazy {
        MediatorClient.client.newBuilder()
            .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** Temp file of the download in flight; never reported or deleted as unlisted. */
    @Volatile
    private var activePartFile: File? = null

    private fun downloadsDir(filesDir: File) = File(filesDir, "downloads")

    suspend fun downloadVideo(
        filesDir: File,
        video: MediaItem,
        quality: String,
        mediaDao: MediaDao,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val dir = downloadsDir(filesDir)
        val target = File(dir, "${video.contentId}_${quality}.mp4")
        // Written under a temp name and renamed on success, so an interrupted
        // download never leaves a truncated .mp4 the player would pick up.
        val part = File(dir, "${target.name}.part")
        try {
            val file = video.files?.find { it.label?.lowercase() == quality.lowercase() }
                ?: return@withContext null
            val url = file.progressiveDownloadURL ?: return@withContext null

            if (!dir.exists()) dir.mkdirs()
            part.delete()
            activePartFile = part

            val request = Request.Builder().url(url).build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()
                var totalBytesRead = 0L

                body.byteStream().use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
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
                if (contentLength > 0 && totalBytesRead != contentLength) return@withContext null
            }

            // One row per contentId: downloading another quality replaces the
            // row, so delete the previous quality's file instead of orphaning it.
            mediaDao.getDownload(video.contentId)?.let { previous ->
                if (previous.filePath != target.absolutePath) File(previous.filePath).delete()
            }
            target.delete()
            if (!part.renameTo(target)) return@withContext null

            val jsonStr = try {
                MediatorClient.json.encodeToString(MediaItem.serializer(), video)
            } catch (e: Exception) { "" }

            val entity = DownloadEntity(
                contentId = video.contentId,
                title = video.title,
                thumbnailUrl = video.getThumbnailUrl(small = true),
                durationSeconds = video.duration,
                quality = quality,
                filePath = target.absolutePath,
                downloadedAt = System.currentTimeMillis(),
                rawMediaItemJson = jsonStr
            )
            mediaDao.addDownload(entity)

            target
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            activePartFile = null
            part.delete()
        }
    }

    /**
     * Reads the download rows and the downloads folder. Rows whose file is gone
     * are removed so the UI never shows a download that would actually stream.
     */
    suspend fun scanStorage(filesDir: File, mediaDao: MediaDao): DownloadStorage =
        withContext(Dispatchers.IO) {
            val dir = downloadsDir(filesDir)
            val downloads = mediaDao.getAllDownloads()
                .filter { row ->
                    File(row.filePath).exists().also { exists ->
                        if (!exists) mediaDao.removeDownload(row.contentId)
                    }
                }
                .sortedByDescending { it.downloadedAt }
            val listedPaths = downloads.mapTo(HashSet()) { it.filePath }
            DownloadStorage(
                downloads = downloads,
                sizes = downloads.associate { it.contentId to File(it.filePath).length() },
                unlistedBytes = unlistedFiles(dir, listedPaths).sumOf { it.length() },
                freeBytes = filesDir.usableSpace
            )
        }

    suspend fun deleteDownload(download: DownloadEntity, mediaDao: MediaDao) {
        withContext(Dispatchers.IO) {
            File(download.filePath).delete()
            mediaDao.removeDownload(download.contentId)
        }
    }

    /** Deletes every file in the downloads folder that no row points at. */
    suspend fun deleteUnlisted(filesDir: File, mediaDao: MediaDao) {
        withContext(Dispatchers.IO) {
            val listedPaths = mediaDao.getAllDownloads().mapTo(HashSet()) { it.filePath }
            unlistedFiles(downloadsDir(filesDir), listedPaths).forEach { it.deleteRecursively() }
        }
    }

    suspend fun deleteAll(filesDir: File, mediaDao: MediaDao) {
        withContext(Dispatchers.IO) {
            mediaDao.getAllDownloads().forEach { File(it.filePath).delete() }
            mediaDao.removeAllDownloads()
            unlistedFiles(downloadsDir(filesDir), emptySet()).forEach { it.deleteRecursively() }
        }
    }

    private fun unlistedFiles(dir: File, listedPaths: Set<String>): List<File> {
        val active = activePartFile?.absolutePath
        return dir.listFiles().orEmpty().filter {
            it.absolutePath !in listedPaths && it.absolutePath != active
        }
    }
}
