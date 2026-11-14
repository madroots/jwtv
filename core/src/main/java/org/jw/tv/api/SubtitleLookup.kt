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
     * languages (no subtitle files) and [ownLanguage]. [pinned] codes (used
     * before, English) come first, in that order, so the remote doesn't have
     * to scroll ~300 entries; the rest are sorted by name.
     */
    fun otherLanguages(
        video: MediaItem,
        ownLanguage: String,
        languages: List<LanguageInfo>,
        pinned: List<String> = emptyList()
    ): List<LanguageInfo> {
        val available = video.availableLanguages?.toHashSet() ?: return emptyList()
        return pinnedFirst(
            languages.filter { it.code in available && it.code != ownLanguage && !it.isSignLanguage },
            pinned
        )
    }

    /** Every language that can have subtitles (no sign languages), [pinned] first. */
    fun subtitleLanguages(languages: List<LanguageInfo>, pinned: List<String> = emptyList()): List<LanguageInfo> =
        pinnedFirst(languages.filterNot { it.isSignLanguage }, pinned)

    private fun pinnedFirst(languages: List<LanguageInfo>, pinned: List<String>): List<LanguageInfo> {
        val rank = pinned.withIndex().associate { (i, code) -> code to i }
        return languages
            .sortedBy { it.vernacular.lowercase() }
            .sortedBy { rank[it.code] ?: Int.MAX_VALUE } // stable: the rest keep name order
    }
}
