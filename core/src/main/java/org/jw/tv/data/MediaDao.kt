package org.jw.tv.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    // ── Watch Progress ────────────────────────────────────────────────────────

    @Query("SELECT * FROM watch_progress ORDER BY lastWatchedTimestamp DESC")
    fun getWatchProgressFlow(): Flow<List<WatchProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWatchProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE contentId = :contentId")
    suspend fun deleteWatchProgress(contentId: String)

    // ── Favorites ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM favorites ORDER BY addedTimestamp DESC")
    fun getFavoritesFlow(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contentId = :contentId)")
    fun isFavoriteFlow(contentId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contentId = :contentId)")
    suspend fun isFavorite(contentId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE contentId = :contentId")
    suspend fun removeFavorite(contentId: String)

    // ── Downloads ────────────────────────────────────────────────────────────

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE contentId = :contentId AND quality = :quality)")
    suspend fun isDownloaded(contentId: String, quality: String): Boolean

    @Query("SELECT * FROM downloads WHERE contentId = :contentId")
    suspend fun getDownload(contentId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE contentId = :contentId AND quality = :quality")
    suspend fun getDownloadForQuality(contentId: String, quality: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE contentId = :contentId")
    suspend fun removeDownload(contentId: String)

    @Query("SELECT contentId FROM downloads")
    suspend fun getAllDownloadedIds(): List<String>

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getDownloadsFlow(): Flow<List<DownloadEntity>>
}
