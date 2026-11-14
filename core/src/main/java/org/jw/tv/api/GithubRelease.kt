package org.jw.tv.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Subset of GitHub's `GET /repos/{owner}/{repo}/releases/latest` response. */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null
) {
    /** "v3.9.8" -> "3.9.8"; null when the tag is empty. */
    val versionName: String?
        get() = tagName.removePrefix("v").ifBlank { null }

    /**
     * Release body as plain text for the update dialog: drops markdown heading
     * markers and bold, turns `-`/`*` list items into bullets.
     */
    val plainNotes: String?
        get() = body
            ?.lineSequence()
            ?.map { line ->
                val trimmed = line.trim().trimStart('#').trim().replace("**", "")
                when {
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                        "• " + trimmed.substring(2).trim()
                    else -> trimmed
                }
            }
            ?.joinToString("\n")
            ?.replace(Regex("\n{3,}"), "\n\n")
            ?.trim()
            ?.ifBlank { null }
}
