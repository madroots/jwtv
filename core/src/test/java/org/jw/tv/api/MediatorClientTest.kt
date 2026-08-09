package org.jw.tv.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MediatorClientTest {

    @Test
    fun testFetchVideoOnDemand() = runBlocking {
        val response = MediatorClient.fetchCategory("VideoOnDemand")
        assertNotNull("Response should not be null", response)
        val category = response!!.category
        assertEquals("VideoOnDemand", category.key)
        assertNotNull("Subcategories should not be null", category.subcategories)
        assertTrue("Subcategories should not be empty", category.subcategories!!.isNotEmpty())
        
        println("Fetched root category: ${category.name}")
        for (sub in category.subcategories!!) {
            println("  Subcategory key=${sub.key}, name=${sub.name}")
        }
        
        // Try to fetch one subcategory
        val firstSub = category.subcategories!!.first()
        val subResponse = MediatorClient.fetchCategory(firstSub.key)
        assertNotNull("Subcategory response should not be null", subResponse)
        val subCategory = subResponse!!.category
        println("Fetched subcategory: ${subCategory.name}")
        if (subCategory.media != null) {
            println("  Media count: ${subCategory.media!!.size}")
            for (media in subCategory.media!!.take(3)) {
                println("    Video title: ${media.title}")
                println("      Thumbnail: ${media.getThumbnailUrl()}")
                println("      Files count: ${media.files?.size}")
                media.files?.firstOrNull()?.let {
                    println("      First File Resolution: ${it.label}, URL: ${it.progressiveDownloadURL}")
                }
            }
        }
    }
}
