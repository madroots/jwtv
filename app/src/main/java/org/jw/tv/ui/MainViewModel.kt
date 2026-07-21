package org.jw.tv.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jw.tv.api.CategoryData
import org.jw.tv.api.LanguageInfo
import org.jw.tv.api.MediaItem
import org.jw.tv.api.MediatorClient

// PERF-5: @Immutable enables Compose recomposition skipping when the same
// reference is re-passed to a composable. All fields are val so this is safe.
@Immutable
data class SubcategoryWithMedia(
    val name: String,
    val key: String,
    val videos: List<MediaItem>
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs =
        application.getSharedPreferences("jw_tv_prefs", Context.MODE_PRIVATE)

    // ── Language ─────────────────────────────────────────────────────────────

    var selectedLanguageCode by mutableStateOf(
        sharedPrefs.getString("lang_code", "E") ?: "E"
    )
        private set

    var selectedLanguageName by mutableStateOf(
        sharedPrefs.getString("lang_name", "English") ?: "English"
    )
        private set

    var languagesList by mutableStateOf<List<LanguageInfo>>(emptyList())
        private set

    // ── Playback ─────────────────────────────────────────────────────────────

    var selectedResolution by mutableStateOf<String?>(null)
    var activeVideo by mutableStateOf<MediaItem?>(null)

    // ── Content ──────────────────────────────────────────────────────────────

    var categories by mutableStateOf<List<CategoryData>>(emptyList())
        private set

    var subcategoriesWithMedia by mutableStateOf<List<SubcategoryWithMedia>>(emptyList())
        private set

    // UX-5: Hero video from the root VideoOnDemand category.media field —
    // editorially curated by jw.org, not just the first video in a subcategory.
    var featuredVideo by mutableStateOf<MediaItem?>(null)
        private set

    var currentCategory by mutableStateOf<CategoryData?>(null)
        private set

    var uiState by mutableStateOf<UiState>(UiState.Loading)
        private set
    // ── Room DB: Persistence & Favorites ────────────────────────────────────

    private val mediaDao = org.jw.tv.data.AppDatabase.getDatabase(application).mediaDao()

    var continueWatchingList by mutableStateOf<List<org.jw.tv.data.WatchProgressEntity>>(emptyList())
        private set

    var favoritesList by mutableStateOf<List<org.jw.tv.data.FavoriteEntity>>(emptyList())
        private set

    var favoriteContentIds by mutableStateOf<Set<String>>(emptySet())
        private set
    fun toggleFavorite(video: MediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val contentId = video.contentId
            if (mediaDao.isFavorite(contentId)) {
                mediaDao.removeFavorite(contentId)
            } else {
                val jsonStr = try { MediatorClient.json.encodeToString(MediaItem.serializer(), video) } catch (e: Exception) { "" }
                val entity = org.jw.tv.data.FavoriteEntity(
                    contentId = contentId,
                    title = video.title,
                    thumbnailUrl = video.getThumbnailUrl(small = true),
                    durationSeconds = video.duration,
                    addedTimestamp = System.currentTimeMillis(),
                    rawMediaItemJson = jsonStr
                )
                mediaDao.addFavorite(entity)
            }
        }
    }

    fun selectFavoritesCategory() {
        currentCategory = CategoryData(key = "FAVORITES", type = "custom", name = "Favorites")
        viewModelScope.launch {
            val favVideos = favoritesList.mapNotNull { fav ->
                try { MediatorClient.json.decodeFromString<MediaItem>(fav.rawMediaItemJson) } catch (e: Exception) { null }
            }
            subcategoriesWithMedia = if (favVideos.isNotEmpty()) {
                listOf(SubcategoryWithMedia(name = "Your Favorites", key = "FAVORITES_ROW", videos = favVideos))
            } else emptyList()
            uiState = UiState.Success
        }
    }

    // ── Update ───────────────────────────────────────────────────────────────

    var updateAvailable by mutableStateOf(false)
        private set

    var updateApkUrl by mutableStateOf("")
        private set

    // BUG-3: hoisted; "Later" sets this true; checkForUpdates resets on new version.
    var updateDialogDismissed by mutableStateOf(false)

    var isDownloadingUpdate by mutableStateOf(false)
        private set

    var downloadProgress by mutableStateOf(0f)
        private set

    var updateCheckMessage by mutableStateOf<String?>(null)
        private set

    fun clearUpdateCheckMessage() { updateCheckMessage = null }

    fun dismissUpdateDialog() { updateDialogDismissed = true }

    fun startDownload(apkUrl: String, context: Context) {
        isDownloadingUpdate = true
        viewModelScope.launch {
            UpdateManager.downloadAndInstallApk(
                context, apkUrl,
                onProgress = { progress -> downloadProgress = progress }
            )
            isDownloadingUpdate = false
        }
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    init {
        loadRootCategories()
        loadLanguages()

        // Observe Room Watch Progress
        viewModelScope.launch {
            mediaDao.getWatchProgressFlow().collect { list ->
                continueWatchingList = list
            }
        }
        // Observe Room Favorites
        viewModelScope.launch {
            mediaDao.getFavoritesFlow().collect { list ->
                favoritesList = list
                favoriteContentIds = list.map { it.contentId }.toSet()
            }
        }
    }

    // ── Category loading ─────────────────────────────────────────────────────

    fun loadRootCategories() {
        viewModelScope.launch {
            // Serve cached content immediately.
            val cached = MediatorClient.getCachedCategory("VideoOnDemand", selectedLanguageCode)
            if (cached != null) {
                applyRootResponse(cached.category, fromCache = true)
            } else {
                uiState = UiState.Loading
            }

            // Refresh from network.
            val fresh = MediatorClient.fetchCategory("VideoOnDemand", selectedLanguageCode)
            if (fresh != null) {
                applyRootResponse(fresh.category, fromCache = false)
            } else if (categories.isEmpty()) {
                uiState = UiState.Error(
                    "Failed to fetch video categories. Please check your network connection."
                )
            }
        }
    }

    private suspend fun applyRootResponse(root: CategoryData, fromCache: Boolean) {
        // UX-5: The root media list is the editorially featured video set.
        val featured = root.media?.firstOrNull()
        if (featured != null) featuredVideo = featured

        val list = root.subcategories ?: emptyList()
        if (list.isNotEmpty()) {
            categories = list
            if (currentCategory == null) selectCategory(list.first())
        } else if (!fromCache) {
            subcategoriesWithMedia = emptyList()
            uiState = UiState.Success
        }
    }

    fun selectCategory(category: CategoryData) {
        currentCategory = category
        viewModelScope.launch {
            // Show cached content immediately (all cache reads are IO-dispatched).
            val cachedSubcats = loadCachedSubcategories(category, selectedLanguageCode)
            if (cachedSubcats.isNotEmpty()) {
                subcategoriesWithMedia = cachedSubcats
                uiState = UiState.Success
            } else {
                subcategoriesWithMedia = emptyList()
                uiState = UiState.Loading
            }

            // Refresh from network with incremental display (BUG-5).
            val response = MediatorClient.fetchCategory(category.key, selectedLanguageCode)
            if (response != null) {
                val subcats = response.category.subcategories
                if (!subcats.isNullOrEmpty()) {
                    val ioLimited = Dispatchers.IO.limitedParallelism(4)
                    val fresh = subcats.map { sub ->
                        async(ioLimited) {
                            val subResp = MediatorClient.fetchCategory(sub.key, selectedLanguageCode)
                            val videos = subResp?.category?.media ?: emptyList()
                            if (videos.isNotEmpty()) SubcategoryWithMedia(sub.name, sub.key, videos)
                            else null
                        }
                    }.awaitAll().filterNotNull()

                    if (fresh.isNotEmpty()) {
                        subcategoriesWithMedia = fresh
                        uiState = UiState.Success
                    }
                } else {
                    val videos = response.category.media ?: emptyList()
                    subcategoriesWithMedia = if (videos.isNotEmpty()) {
                        listOf(SubcategoryWithMedia(
                            response.category.name,
                            response.category.key,
                            videos
                        ))
                    } else emptyList()
                    uiState = UiState.Success
                }
            } else if (subcategoriesWithMedia.isEmpty()) {
                uiState = UiState.Error("Failed to load videos for ${category.name}")
            }
        }
    }

    private suspend fun loadCachedSubcategories(
        category: CategoryData,
        langCode: String
    ): List<SubcategoryWithMedia> {
        val cached = MediatorClient.getCachedCategory(category.key, langCode)
            ?: return emptyList()
        val subcats = cached.category.subcategories
        return if (!subcats.isNullOrEmpty()) {
            subcats.mapNotNull { sub ->
                val subCached = MediatorClient.getCachedCategory(sub.key, langCode)
                val videos = subCached?.category?.media ?: emptyList()
                if (videos.isNotEmpty()) SubcategoryWithMedia(sub.name, sub.key, videos)
                else null
            }
        } else {
            val videos = cached.category.media ?: emptyList()
            if (videos.isNotEmpty()) {
                listOf(SubcategoryWithMedia(cached.category.name, cached.category.key, videos))
            } else emptyList()
        }
    }

    // ── Update check ─────────────────────────────────────────────────────────

    fun checkForUpdates(currentVersion: Int, manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) updateCheckMessage = "Checking for updates..."
            try {
                val ts = System.currentTimeMillis()
                val url = "https://plugins.best-web.sk/random/jwtv/version.txt?t=$ts"
                val result = withContext(Dispatchers.IO) {
                    val req = okhttp3.Request.Builder().url(url).build()
                    MediatorClient.client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string()?.trim()?.toIntOrNull() else null
                    }
                }
                when {
                    result == null ->
                        if (manual) updateCheckMessage = "Failed to reach update server."
                    result > currentVersion -> {
                        updateAvailable = true
                        updateDialogDismissed = false
                        updateApkUrl = "https://plugins.best-web.sk/random/jwtv/app-debug.apk?t=$ts"
                        updateCheckMessage = null
                    }
                    else ->
                        if (manual) updateCheckMessage =
                            "Your app is up to date (Version $currentVersion)."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (manual) updateCheckMessage =
                    "Error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            }
        }
    }

    // ── Language ─────────────────────────────────────────────────────────────

    fun loadLanguages() {
        viewModelScope.launch {
            val cached = MediatorClient.getCachedLanguages(selectedLanguageCode)
            if (cached != null) {
                languagesList = cached.languages.sortedBy { it.vernacular.lowercase() }
            }
            val fresh = MediatorClient.fetchLanguages(selectedLanguageCode)
            if (fresh != null) {
                languagesList = fresh.languages.sortedBy { it.vernacular.lowercase() }
            }
        }
    }

    fun changeLanguage(code: String, name: String) {
        selectedLanguageCode = code
        selectedLanguageName = name
        sharedPrefs.edit()
            .putString("lang_code", code)
            .putString("lang_name", name)
            .apply()
        currentCategory = null
        subcategoriesWithMedia = emptyList()
        featuredVideo = null
        loadRootCategories()
        loadLanguages()
    }
}

sealed interface UiState {
    object Loading : UiState
    object Success : UiState
    data class Error(val message: String) : UiState
}
