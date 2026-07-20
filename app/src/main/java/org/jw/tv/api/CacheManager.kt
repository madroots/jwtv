package org.jw.tv.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Simple JSON string cache backed by the app's internal cache directory.
 * All IO is dispatched on [Dispatchers.IO] — never call from the main thread.
 */
object CacheManager {
    private var cacheDir: File? = null

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

    suspend fun getJson(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = keyFile(key) ?: return@withContext null
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }
}
