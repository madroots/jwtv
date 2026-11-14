package org.jw.phone.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import org.jw.tv.api.MediaItem
import java.io.File

/**
 * Phone playback helpers: playback source and Media3 item, same semantics as
 * the TV player. Resume positions live in :core (WatchProgressStore).
 */

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

/** Media3 item for [url] with [subtitleUrl] (WebVTT, any language version) if given. */
fun buildMedia3Item(url: String, subtitleUrl: String?): Media3Item {
    val builder = Media3Item.Builder().setUri(url)
    if (subtitleUrl != null) {
        val subtitleConfig = Media3Item.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        builder.setSubtitleConfigurations(listOf(subtitleConfig))
    }
    return builder.build()
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
