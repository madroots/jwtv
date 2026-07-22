package org.jw.tv.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.jw.tv.api.MediaItem

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Double?,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long,
    val rawMediaItemJson: String
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Double?,
    val addedTimestamp: Long,
    val rawMediaItemJson: String
)
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Double?,
    val quality: String,
    val filePath: String,
    val downloadedAt: Long,
    val rawMediaItemJson: String
)
