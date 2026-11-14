package org.jw.tv.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live API test (like MediatorClientTest): needs network. */
class SubtitleLookupTest {

    @Test
    fun `finds another language version's subtitles for a video`() = runBlocking {
        val video = MediatorClient.fetchCategory("VODStudio")!!.category
            .subcategories.orEmpty()
            .flatMap { it.media.orEmpty() }
            .first { SubtitleLookup.supportsOtherLanguages(it) && it.subtitleUrl != null }
        assertTrue("S" in video.availableLanguages!!)

        val spanish = SubtitleLookup.subtitleUrl(video, "S")

        assertNotNull(spanish)
        assertNotEquals(video.subtitleUrl, spanish)
        assertTrue(spanish!!.endsWith(".vtt"))
        // Second call is served from the cache with the same answer.
        assertEquals(spanish, SubtitleLookup.subtitleUrl(video, "S"))
    }

    @Test
    fun `offered languages exclude sign languages and the video's own language`() {
        val video = MediaItem(
            title = "t",
            languageAgnosticNaturalKey = "k",
            availableLanguages = listOf("E", "S", "ASL", "F")
        )
        val languages = listOf(
            LanguageInfo("E", vernacular = "English", name = "English"),
            LanguageInfo("S", vernacular = "español", name = "Spanish"),
            LanguageInfo("ASL", vernacular = "American Sign Language", name = "ASL", isSignLanguage = true),
            LanguageInfo("F", vernacular = "Français", name = "French"),
            LanguageInfo("X", vernacular = "Deutsch", name = "German")
        )

        val offered = SubtitleLookup.otherLanguages(video, ownLanguage = "E", languages = languages)

        assertEquals(listOf("S", "F"), offered.map { it.code })
    }

    @Test
    fun `recently used languages come first, the rest stay alphabetical`() {
        val video = MediaItem(
            title = "t",
            languageAgnosticNaturalKey = "k",
            availableLanguages = listOf("E", "S", "F", "X", "J")
        )
        val languages = listOf(
            LanguageInfo("S", vernacular = "español", name = "Spanish"),
            LanguageInfo("F", vernacular = "Français", name = "French"),
            LanguageInfo("X", vernacular = "Deutsch", name = "German"),
            LanguageInfo("J", vernacular = "日本語", name = "Japanese")
        )

        val offered = SubtitleLookup.otherLanguages(video, "E", languages, recent = listOf("J", "F"))

        assertEquals(listOf("J", "F", "X", "S"), offered.map { it.code })
    }
}
