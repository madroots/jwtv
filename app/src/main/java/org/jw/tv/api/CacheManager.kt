package org.jw.tv.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ARCH-4: TTL-based cache expiry and Stale-While-Revalidate (SWR) support.
 * Uses [File.lastModified] to determine file age without JSON wrapper overhead.
 * All IO is dispatched on [Dispatchers.IO].
 */
object CacheManager {
    private var cacheDir: File? = null

    // ── TTL Constants ────────────────────────────────────────────────────────
    const val TTL_ROOT_CATEGORIES = 24 * 3600 * 1000L      // 24 hours
    const val TTL_SUBCATEGORIES    = 6 * 3600 * 1000L       // 6 hours
    const val TTL_LANGUAGES        = 7 * 24 * 3600 * 1000L  // 7 days
    const val MAX_SWR_AGE          = 7 * 24 * 3600 * 1000L  // 7 days (hard expiry)

    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "json_cache").apply { mkdirs() }
    }

    private fun keyFile(key: String): File? {
        val dir = cacheDir ?: return null
        return File(dir, "${key.hashCode()}.json")
    }

    suspend fun saveJson(key: String, jsonString: String) = withContext(Dispatchers.IO) {
        try {
            keyFile(key)?.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Reads cached JSON. If [maxAgeMillis] is provided and the file is older than [maxAgeMillis],
     * returns null to force a fresh fetch with loading indicator.
     */
    suspend fun getJson(key: String, maxAgeMillis: Long? = MAX_SWR_AGE): String? = withContext(Dispatchers.IO) {
        try {
            val file = keyFile(key) ?: return@withContext null
            if (!file.exists()) return@withContext null
            if (maxAgeMillis != null) {
                val age = System.currentTimeMillis() - file.lastModified()
                if (age > maxAgeMillis) return@withContext null
            }
            file.readText()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the cached file for [key] is older than [ttlMillis].
     * Returns true if missing or stale (triggering background revalidation).
     */
    suspend fun isStale(key: String, ttlMillis: Long): Boolean = withContext(Dispatchers.IO) {
        val file = keyFile(key) ?: return@withContext true
        if (!file.exists()) return@withContext true
        val age = System.currentTimeMillis() - file.lastModified()
        age > ttlMillis
    }
}
