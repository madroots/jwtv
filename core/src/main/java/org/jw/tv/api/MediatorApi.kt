package org.jw.tv.api

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

// @Immutable tells the Compose compiler these types are stable and their
// instances never change after construction, enabling full recomposition
// skipping whenever the same object reference is passed to a composable.

@Immutable
@Serializable
data class CategoryResponse(
    val category: CategoryData
)

@Immutable
@Serializable
data class CategoryData(
    val key: String,
    val type: String,
    val name: String,
    val description: String? = null,
    val subcategories: List<CategoryData>? = null,
    val media: List<MediaItem>? = null
)

@Immutable
@Serializable
data class MediaItem(
    val title: String,
    val duration: Double? = null,
    val images: Map<String, ImageDetail>? = null,
    val files: List<MediaFile>? = null,
    /** Same id for every language version, e.g. "pub-jwb-143_1_VIDEO". */
    val languageAgnosticNaturalKey: String? = null,
    /** Language codes this video exists in (each version has its own subtitles). */
    val availableLanguages: List<String>? = null
) {
    /** This version's own subtitle file, if any. */
    val subtitleUrl: String?
        get() = files?.firstNotNullOfOrNull { it.subtitles?.url }

    fun getThumbnailUrl(small: Boolean = false): String? {
        val preferredKeys = listOf("wss", "lsr", "pnr", "sqr")
        if (small) {
            for (key in preferredKeys) {
                val url = images?.get(key)?.getSmallUrl()
                if (!url.isNullOrBlank()) return url
            }
        }
        for (key in preferredKeys) {
            val url = images?.get(key)?.getAnyUrl()
            if (!url.isNullOrBlank()) return url
        }
        return images?.values?.firstNotNullOfOrNull { it.getAnyUrl() }
    }

    val contentId: String
        get() {
            val firstUrl = files?.firstOrNull { !it.progressiveDownloadURL.isNullOrBlank() }?.progressiveDownloadURL
            if (!firstUrl.isNullOrBlank()) {
                val cleanUrl = firstUrl.substringBefore('?')
                return cleanUrl.hashCode().toString()
            }
            return title.hashCode().toString()
        }
}

@Immutable
@Serializable
data class ImageDetail(
    val lg: String? = null,
    val sm: String? = null,
    val md: String? = null,
    val xs: String? = null,
    val xl: String? = null
) {
    fun getSmallUrl(): String? = sm ?: md ?: xs ?: lg ?: xl
    fun getAnyUrl(): String?  = lg ?: md ?: xl ?: sm ?: xs
}

@Immutable
@Serializable
data class MediaFile(
    val progressiveDownloadURL: String? = null,
    val label: String? = null,
    val mimetype: String? = null,
    val subtitles: SubtitleInfo? = null
)

@Immutable
@Serializable
data class SubtitleInfo(
    val url: String? = null
)

@Immutable
@Serializable
data class LanguageResponse(
    val languages: List<LanguageInfo>
)

@Immutable
@Serializable
data class LanguageInfo(
    val code: String,
    val locale: String? = null,
    val vernacular: String,
    val name: String,
    /** Sign-language versions have no subtitle files. */
    val isSignLanguage: Boolean = false
)

/** Response of `GET /media-items/{lang}/{languageAgnosticNaturalKey}`. */
@Immutable
@Serializable
data class MediaItemsResponse(
    val media: List<MediaItem> = emptyList()
)
