package com.michael.simplemusic.podcast

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder

data class PodcastSearchResult(
    val collectionName: String,
    val artistName: String,
    val feedUrl: String,
    val artworkUrl: String?
)

/**
 * A standalone client to interface with the iTunes Search API for podcasts.
 * Designed to be easily disabled or removed if needed.
 */
object PodcastSearchClient {
    private val gson = Gson()

    suspend fun searchPodcasts(query: String): List<PodcastSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&entity=podcast&limit=20"
            val json = URL(url).readText()
            
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val response: Map<String, Any> = gson.fromJson(json, type)
            val results = response["results"] as? List<Map<String, Any>> ?: emptyList()
            
            results.mapNotNull {
                val feedUrl = it["feedUrl"] as? String ?: return@mapNotNull null
                val collectionName = it["collectionName"] as? String ?: "Unknown Show"
                val artistName = it["artistName"] as? String ?: "Unknown Artist"
                // Prefer larger artwork, fallback to smaller
                val artworkUrl = (it["artworkUrl600"] as? String) ?: (it["artworkUrl100"] as? String) ?: (it["artworkUrl30"] as? String)
                
                PodcastSearchResult(
                    collectionName = collectionName,
                    artistName = artistName,
                    feedUrl = feedUrl,
                    artworkUrl = artworkUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
