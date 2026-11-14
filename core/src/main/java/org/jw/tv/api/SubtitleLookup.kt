package org.jw.tv.api

import java.util.concurrent.ConcurrentHashMap

/**
 * Subtitles from other language versions of a video.
 *
 * Every language version of a JW video is the same edit (same length to the
 * millisecond) and ships its own WebVTT file, so another language's file can
 * be shown over the video being watched.
 */
object SubtitleLookup {

    /** "lang|key" -> subtitle URL, or "" when that version has no subtitles. */
    private val cache = ConcurrentHashMap<String, String>()

    /** Whether [video] can offer subtitles from other languages at all. */
    fun supportsOtherLanguages(video: MediaItem): Boolean =
        video.languageAgnosticNaturalKey != null && !video.availableLanguages.isNullOrEmpty()

    /**
     * Subtitle URL of the [languageCode] version of [video]; null if that
     * version has none, doesn't exist, or can't be reached.
     */
    suspend fun subtitleUrl(video: MediaItem, languageCode: String): String? {
        val key = video.languageAgnosticNaturalKey ?: return null
        val cacheKey = "$languageCode|$key"
        cache[cacheKey]?.let { return it.ifEmpty { null } }
        val version = MediatorClient.fetchMediaItem(languageCode, key) ?: return null // not cached: retry later
        val url = version.subtitleUrl
        cache[cacheKey] = url.orEmpty()
        return url
    }

    /**
     * Languages to offer for [video]: the ones it exists in, minus sign
     * languages (no subtitle files) and [ownLanguage]. [recent] picks come
     * first so switching between a few languages doesn't mean scrolling a
     * list of ~300 with the remote; the rest are sorted by name.
     */
    fun otherLanguages(
        video: MediaItem,
        ownLanguage: String,
        languages: List<LanguageInfo>,
        recent: List<String> = emptyList()
    ): List<LanguageInfo> {
        val available = video.availableLanguages?.toHashSet() ?: return emptyList()
        val recentRank = recent.withIndex().associate { (i, code) -> code to i }
        return languages
            .filter { it.code in available && it.code != ownLanguage && !it.isSignLanguage }
            .sortedBy { it.vernacular.lowercase() }
            .sortedBy { recentRank[it.code] ?: Int.MAX_VALUE } // stable: the rest keep name order
    }
}
