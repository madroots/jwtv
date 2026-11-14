package org.jw.tv.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubReleaseTest {

    @Test
    fun `parses GitHub API response and renders notes as plain text`() {
        val json = """
            {
              "tag_name": "v3.9.8",
              "name": "JW TV 3.9.8",
              "draft": false,
              "body": "## What's Changed\r\n* **Faster** home screen\r\n- Fix resume\r\n\r\n\r\n\r\nThanks!",
              "assets": []
            }
        """.trimIndent()

        val release = MediatorClient.json.decodeFromString<GithubRelease>(json)

        assertEquals("3.9.8", release.versionName)
        assertEquals(
            "What's Changed\n• Faster home screen\n• Fix resume\n\nThanks!",
            release.plainNotes
        )
    }

    @Test
    fun `empty body and tag yield nulls so the dialog falls back to generic text`() {
        val release = MediatorClient.json.decodeFromString<GithubRelease>(
            """{"tag_name": "", "body": "  \r\n "}"""
        )
        assertNull(release.versionName)
        assertNull(release.plainNotes)
    }
}
