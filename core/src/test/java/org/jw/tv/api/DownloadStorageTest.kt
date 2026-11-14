package org.jw.tv.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.jw.tv.data.DownloadEntity
import org.jw.tv.data.FavoriteEntity
import org.jw.tv.data.MediaDao
import org.jw.tv.data.WatchProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dao = FakeDownloadDao()

    private fun file(name: String, bytes: Int): File =
        File(tmp.root, "downloads/$name").apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(bytes))
        }

    private fun row(contentId: String, file: File) = DownloadEntity(
        contentId = contentId,
        title = contentId,
        thumbnailUrl = null,
        durationSeconds = null,
        quality = "720p",
        filePath = file.absolutePath,
        downloadedAt = 0L,
        rawMediaItemJson = ""
    )

    @Test
    fun `files without a row are reported as unlisted and can be deleted`() = runTest {
        val listed = file("a_720p.mp4", 100)
        val orphan = file("b_480p.mp4", 300)       // row lost (DB reset / quality replaced)
        val partial = file("c_720p.mp4.part", 50)  // interrupted download
        dao.addDownload(row("a", listed))

        val before = DownloadManager.scanStorage(tmp.root, dao)
        assertEquals(mapOf("a" to 100L), before.sizes)
        assertEquals(350L, before.unlistedBytes)

        DownloadManager.deleteUnlisted(tmp.root, dao)

        assertTrue(listed.exists())
        assertFalse(orphan.exists())
        assertFalse(partial.exists())
        assertEquals(0L, DownloadManager.scanStorage(tmp.root, dao).unlistedBytes)
    }

    @Test
    fun `rows whose file is gone are dropped so the UI never lists them`() = runTest {
        val kept = file("a_720p.mp4", 10)
        dao.addDownload(row("a", kept))
        dao.addDownload(row("gone", File(tmp.root, "downloads/gone_720p.mp4")))

        val storage = DownloadManager.scanStorage(tmp.root, dao)

        assertEquals(listOf("a"), storage.downloads.map { it.contentId })
        assertEquals(listOf("a"), dao.getAllDownloads().map { it.contentId })
    }

    @Test
    fun `delete one and delete all free the files and rows`() = runTest {
        val a = file("a_720p.mp4", 10)
        val b = file("b_720p.mp4", 10)
        val orphan = file("x.mp4", 10)
        dao.addDownload(row("a", a))
        dao.addDownload(row("b", b))

        DownloadManager.deleteDownload(dao.getDownload("a")!!, dao)
        assertFalse(a.exists())
        assertEquals(listOf("b"), dao.getAllDownloads().map { it.contentId })

        DownloadManager.deleteAll(tmp.root, dao)
        assertFalse(b.exists())
        assertFalse(orphan.exists())
        assertTrue(dao.getAllDownloads().isEmpty())
    }
}

/** In-memory downloads table; other tables are unused by DownloadManager. */
private class FakeDownloadDao : MediaDao {
    private val rows = linkedMapOf<String, DownloadEntity>()

    override suspend fun isDownloaded(contentId: String, quality: String) =
        rows[contentId]?.quality == quality
    override suspend fun getDownload(contentId: String) = rows[contentId]
    override suspend fun getDownloadForQuality(contentId: String, quality: String) =
        rows[contentId]?.takeIf { it.quality == quality }
    override suspend fun addDownload(download: DownloadEntity) { rows[download.contentId] = download }
    override suspend fun removeDownload(contentId: String) { rows.remove(contentId) }
    override suspend fun removeAllDownloads() { rows.clear() }
    override suspend fun getAllDownloadedIds() = rows.keys.toList()
    override suspend fun getAllDownloads() = rows.values.toList()
    override fun getDownloadsFlow(): Flow<List<DownloadEntity>> = flowOf(rows.values.toList())

    override fun getWatchProgressFlow(): Flow<List<WatchProgressEntity>> = flowOf(emptyList())
    override suspend fun saveWatchProgress(progress: WatchProgressEntity) = Unit
    override suspend fun deleteWatchProgress(contentId: String) = Unit
    override fun getFavoritesFlow(): Flow<List<FavoriteEntity>> = flowOf(emptyList())
    override fun isFavoriteFlow(contentId: String): Flow<Boolean> = flowOf(false)
    override suspend fun isFavorite(contentId: String) = false
    override suspend fun addFavorite(favorite: FavoriteEntity) = Unit
    override suspend fun removeFavorite(contentId: String) = Unit
}
