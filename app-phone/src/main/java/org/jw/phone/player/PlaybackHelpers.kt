package org.jw.phone.player

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jw.tv.api.MediaItem
import org.jw.tv.api.MediatorClient
import org.jw.tv.data.AppDatabase
import org.jw.tv.data.WatchProgressEntity
import java.io.File

/**
 * Phone playback helpers. Deliberately duplicated from the TV player
 * (VideoPlayerScreen.kt) rather than extracted to :core: the TV app must stay
 * untouched this phase. Semantics are identical — same SharedPreferences keys,
 * same Room entities, same URL preference order — so behavior matches the TV
 * app exactly. If this duplication is ever extracted, :app must not change.
 */

const val PLAYER_PREFS_NAME = "jw_tv_prefs"

/** Reads saved resume position, migrating the legacy title-keyed entry. */
fun readSavedProgress(sharedPrefs: SharedPreferences, video: MediaItem): Long {
    val newKey = "progress_${video.contentId}"
    val oldKey = "progress_${video.title}"
    return when {
        sharedPrefs.contains(newKey) -> sharedPrefs.getLong(newKey, 0L)
        sharedPrefs.contains(oldKey) -> {
            val oldPos = sharedPrefs.getLong(oldKey, 0L)
            sharedPrefs.edit().putLong(newKey, oldPos).remove(oldKey).apply()
            oldPos
        }
        else -> 0L
    }
}

/**
 * Resolves the playback source: a locally downloaded file wins, otherwise the
 * best progressive URL for the requested resolution (or Auto preference).
 */
fun resolvePlaybackUrl(context: Context, video: MediaItem, resolution: String?): String {
    val localFile = resolution?.let { res ->
        val f = File(context.filesDir, "downloads/${video.contentId}_${res}.mp4")
        if (f.exists()) f.absolutePath else null
    }
    return localFile ?: (getBestVideoUrl(video, resolution) ?: "")
}

/** Same preference order as the TV player: exact match, then 720/480/360/240. */
fun getBestVideoUrl(video: MediaItem, selectedResolution: String?): String? {
    val files = video.files ?: return null
    if (selectedResolution != null) {
        val file = files.find { it.label?.lowercase() == selectedResolution.lowercase() }
        if (file?.progressiveDownloadURL != null) {
            return file.progressiveDownloadURL
        }
    }
    val preferredLabels = listOf("720p", "480p", "360p", "240p")
    for (label in preferredLabels) {
        val file = files.find { it.label?.lowercase() == label }
        if (file?.progressiveDownloadURL != null) {
            return file.progressiveDownloadURL
        }
    }
    return files.firstOrNull { it.progressiveDownloadURL != null }?.progressiveDownloadURL
}

/** Media3 item with the video's VTT subtitle configuration, like the TV player. */
fun buildMedia3Item(video: MediaItem, url: String): Media3Item {
    val builder = Media3Item.Builder().setUri(url)
    val subtitleUrl = video.files?.firstNotNullOfOrNull { it.subtitles?.url }
    if (subtitleUrl != null) {
        val subtitleConfig = Media3Item.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        builder.setSubtitleConfigurations(listOf(subtitleConfig))
    }
    return builder.build()
}

/**
 * Persists watch progress on player exit — exact TV behavior: >5s played and
 * <95% watched saves (SharedPreferences + Room WatchProgressEntity), otherwise
 * clears the saved progress.
 */
fun saveProgressOnExit(
    context: Context,
    sharedPrefs: SharedPreferences,
    video: MediaItem,
    positionMs: Long,
    durationMs: Long
) {
    if (durationMs <= 0L) return
    val progressPercent = positionMs.toFloat() / durationMs.toFloat()
    val appContext = context.applicationContext
    val dao = AppDatabase.getDatabase(appContext).mediaDao()
    val jsonStr = try {
        MediatorClient.json.encodeToString(MediaItem.serializer(), video)
    } catch (e: Exception) { "" }

    if (positionMs > 5000L && progressPercent < 0.95f) {
        sharedPrefs.edit().putLong("progress_${video.contentId}", positionMs).apply()
        val entity = WatchProgressEntity(
            contentId = video.contentId,
            title = video.title,
            thumbnailUrl = video.getThumbnailUrl(small = true),
            durationSeconds = video.duration,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedTimestamp = System.currentTimeMillis(),
            rawMediaItemJson = jsonStr
        )
        CoroutineScope(Dispatchers.IO).launch { dao.saveWatchProgress(entity) }
    } else {
        sharedPrefs.edit().remove("progress_${video.contentId}").apply()
        CoroutineScope(Dispatchers.IO).launch { dao.deleteWatchProgress(video.contentId) }
    }
}

fun formatProgressTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
