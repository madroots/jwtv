package org.jw.tv.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object MediatorClient {
    private var isInitialized = false

    var client: OkHttpClient = OkHttpClient()
        private set

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val cacheDir = File(context.cacheDir, "http_cache")
            client = OkHttpClient.Builder()
                .cache(Cache(cacheDir, 100L * 1024L * 1024L))
                .build()
            CacheManager.init(context)
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private const val BASE_URL = "https://b.jw-cdn.org/apis/mediator/v1"

    // ── Category ────────────────────────────────────────────────────────────

    suspend fun fetchCategory(
        categoryKey: String,
        languageCode: String = "E"
    ): CategoryResponse? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/categories/$languageCode/$categoryKey?detailed=true"
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                CacheManager.saveJson("cat_${languageCode}_${categoryKey}", body)
                json.decodeFromString<CategoryResponse>(body)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getCachedCategory(
        categoryKey: String,
        languageCode: String = "E",
        maxAgeMillis: Long? = CacheManager.MAX_SWR_AGE
    ): CategoryResponse? {
        val cached = CacheManager.getJson("cat_${languageCode}_${categoryKey}", maxAgeMillis) ?: return null
        return try {
            json.decodeFromString<CategoryResponse>(cached)
        } catch (e: Exception) {
            null
        }
    }

    // ── Languages ───────────────────────────────────────────────────────────

    suspend fun fetchLanguages(languageCode: String = "E"): LanguageResponse? =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/languages/$languageCode/web"
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    CacheManager.saveJson("langs_${languageCode}", body)
                    json.decodeFromString<LanguageResponse>(body)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun getCachedLanguages(
        languageCode: String = "E",
        maxAgeMillis: Long? = CacheManager.TTL_LANGUAGES
    ): LanguageResponse? {
        val cached = CacheManager.getJson("langs_${languageCode}", maxAgeMillis) ?: return null
        return try {
            json.decodeFromString<LanguageResponse>(cached)
        } catch (e: Exception) {
            null
        }
    }
}
