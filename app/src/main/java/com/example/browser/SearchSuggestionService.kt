package com.example.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/**
 * Service to fetch real-time search query suggestions and predictions from Google's
 * public search suggestion API.
 */
object SearchSuggestionService {

    private const val GOOGLE_SUGGEST_URL =
        "https://suggestqueries.google.com/complete/search?client=firefox&hl=en&q="

    /**
     * Fetches query suggestions for the given user input from Google Search.
     * Returns an empty list if the query is blank or if a network error occurs.
     */
    suspend fun getGoogleSuggestions(query: String, maxResults: Int = 7): List<String> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(trimmedQuery, "UTF-8")
                val url = URL(GOOGLE_SUGGEST_URL + encodedQuery)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 2500
                    readTimeout = 2500
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    setRequestProperty("Accept", "application/json")
                    instanceFollowRedirects = true
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext emptyList()
                }

                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseSuggestionsJson(response, maxResults)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Parses the Google Firefox client JSON response format:
     * ["query", ["suggestion 1", "suggestion 2", ...]]
     */
    fun parseSuggestionsJson(jsonString: String, maxResults: Int): List<String> {
        return try {
            val rootArray = JSONArray(jsonString)
            if (rootArray.length() < 2) return emptyList()

            val suggestionsArray = rootArray.optJSONArray(1) ?: return emptyList()
            val result = mutableListOf<String>()
            val count = minOf(suggestionsArray.length(), maxResults)

            for (i in 0 until count) {
                val suggestion = suggestionsArray.optString(i, "").trim()
                if (suggestion.isNotBlank() && !result.contains(suggestion)) {
                    result.add(suggestion)
                }
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }
}
