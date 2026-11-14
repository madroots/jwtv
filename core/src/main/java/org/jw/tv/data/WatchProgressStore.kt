package org.jw.tv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jw.tv.api.MediaItem
import org.jw.tv.api.MediatorClient

/**
 * Resume positions, shared by both players and the details screens.
 *
 * The position lives in SharedPreferences (read synchronously when a player
 * starts) and is mirrored into Room [WatchProgressEntity] rows, which feed the
 * Continue Watching row.
 */
object WatchProgressStore {

    const val PREFS_NAME = "jw_tv_prefs"

    /** Below this nothing is worth resuming. */
    private const val MIN_RESUME_MS = 5_000L

    /** At or past this fraction the video counts as finished. */
    private const val FINISHED_FRACTION = 0.95f

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Saved resume position in ms (0 = start), migrating the legacy title-keyed entry. */
    fun read(prefs: SharedPreferences, video: MediaItem): Long {
        val newKey = key(video.contentId)
        val oldKey = "progress_${video.title}"
        return when {
            prefs.contains(newKey) -> prefs.getLong(newKey, 0L)
            prefs.contains(oldKey) -> {
                val oldPos = prefs.getLong(oldKey, 0L)
                prefs.edit().putLong(newKey, oldPos).remove(oldKey).apply()
                oldPos
            }
            else -> 0L
        }
    }

    /**
     * Records [positionMs]: kept when more than 5 s in and less than 95 %
     * watched, otherwise cleared (barely started, or finished).
     */
    fun save(context: Context, video: MediaItem, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val fraction = positionMs.toFloat() / durationMs.toFloat()
        if (positionMs <= MIN_RESUME_MS || fraction >= FINISHED_FRACTION) {
            clear(context, video.contentId)
            return
        }
        prefs(context).edit().putLong(key(video.contentId), positionMs).apply()
        val entity = WatchProgressEntity(
            contentId = video.contentId,
            title = video.title,
            thumbnailUrl = video.getThumbnailUrl(small = true),
            durationSeconds = video.duration,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedTimestamp = System.currentTimeMillis(),
            rawMediaItemJson = try {
                MediatorClient.json.encodeToString(MediaItem.serializer(), video)
            } catch (e: Exception) { "" }
        )
        val dao = AppDatabase.getDatabase(context.applicationContext).mediaDao()
        CoroutineScope(Dispatchers.IO).launch { dao.saveWatchProgress(entity) }
    }

    /** Forgets the resume position and drops the video from Continue Watching. */
    fun clear(context: Context, contentId: String) {
        prefs(context).edit().remove(key(contentId)).apply()
        val dao = AppDatabase.getDatabase(context.applicationContext).mediaDao()
        CoroutineScope(Dispatchers.IO).launch { dao.deleteWatchProgress(contentId) }
    }

    private fun key(contentId: String) = "progress_$contentId"
}

/** The full video stored with the progress row, or null if it can't be decoded. */
fun WatchProgressEntity.toMediaItem(): MediaItem? = try {
    MediatorClient.json.decodeFromString(MediaItem.serializer(), rawMediaItemJson)
} catch (e: Exception) {
    null
}

/** "12 min left", "1 h 5 min left", "Less than a minute left". */
fun formatTimeLeft(remainingMs: Long): String {
    val minutes = remainingMs / 60_000
    return when {
        minutes < 1 -> "Less than a minute left"
        minutes < 60 -> "$minutes min left"
        else -> "${minutes / 60} h ${minutes % 60} min left"
    }
}
