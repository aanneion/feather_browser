package com.example.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fetches query predictions from the search engine selected by the user.
 *
 * Suggestions are deliberately best-effort: the address bar still works when a
 * provider is unavailable, and a provider never gets a query for another
 * provider just because its response format is easier to parse.
 */
object SearchSuggestionService {

    private const val GOOGLE_SUGGEST_URL =
        "https://suggestqueries.google.com/complete/search?client=firefox&hl=en&q="
    private const val DUCKDUCKGO_SUGGEST_URL =
        "https://duckduckgo.com/ac/?type=list&q="
    private const val BING_SUGGEST_URL =
        "https://api.bing.com/osjson.aspx?query="
    private const val BRAVE_SUGGEST_URL =
        "https://search.brave.com/api/suggest?q="

    /**
     * Backwards-compatible Google-only entry point used by older callers/tests.
     */
    suspend fun getGoogleSuggestions(query: String, maxResults: Int = 7): List<String> =
        getSuggestions(query, SearchEngine.GOOGLE, maxResults)

    /** Fetches suggestions from the selected search provider. */
    suspend fun getSuggestions(
        query: String,
        searchEngine: SearchEngine,
        maxResults: Int = 7
    ): List<String> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank() || maxResults <= 0) return emptyList()

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(trimmedQuery, "UTF-8")
                val endpoint = when (searchEngine) {
                    SearchEngine.GOOGLE -> GOOGLE_SUGGEST_URL + encodedQuery
                    SearchEngine.DUCKDUCKGO -> DUCKDUCKGO_SUGGEST_URL + encodedQuery
                    SearchEngine.BING -> BING_SUGGEST_URL + encodedQuery
                    SearchEngine.BRAVE -> BRAVE_SUGGEST_URL + encodedQuery
                }
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
                parseSuggestionsResponse(response, searchEngine, maxResults)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            } finally {
                connection?.disconnect()
            }
        }
    }

    /** Parses the response format used by the selected provider. */
    fun parseSuggestionsResponse(
        jsonString: String,
        searchEngine: SearchEngine,
        maxResults: Int
    ): List<String> {
        if (maxResults <= 0) return emptyList()
        return when (searchEngine) {
            SearchEngine.GOOGLE, SearchEngine.BING -> parseSuggestionsJson(jsonString, maxResults)
            SearchEngine.DUCKDUCKGO -> parsePhraseObjects(jsonString, maxResults)
            SearchEngine.BRAVE -> parseBraveSuggestions(jsonString, maxResults)
        }
    }

    /**
     * Parses the Google/Bing OpenSearch response format:
     * ["query", ["suggestion 1", "suggestion 2", ...]]
     */
    fun parseSuggestionsJson(jsonString: String, maxResults: Int): List<String> {
        return try {
            val rootArray = JSONArray(jsonString)
            if (rootArray.length() < 2) return emptyList()
            val suggestionsArray = rootArray.optJSONArray(1) ?: return emptyList()
            suggestionsArray.toDistinctStrings(maxResults)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** DuckDuckGo returns [{"phrase":"..."}, ...]. */
    private fun parsePhraseObjects(jsonString: String, maxResults: Int): List<String> {
        return try {
            val rootArray = JSONArray(jsonString)
            val result = mutableListOf<String>()
            for (i in 0 until rootArray.length()) {
                val phrase = rootArray.optJSONObject(i)?.optString("phrase", "")?.trim().orEmpty()
                if (phrase.isNotBlank() && !result.contains(phrase)) result += phrase
                if (result.size >= maxResults) break
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Supports the current Brave object shape and its simple array fallback. */
    private fun parseBraveSuggestions(jsonString: String, maxResults: Int): List<String> {
        return try {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                val result = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val value = when (val item = array.opt(i)) {
                        is JSONObject -> item.optString("query", item.optString("phrase", ""))
                        else -> item?.toString().orEmpty()
                    }.trim()
                    if (value.isNotBlank() && !result.contains(value)) result += value
                    if (result.size >= maxResults) break
                }
                result
            } else {
                val root = JSONObject(trimmed)
                val suggestions = root.optJSONArray("suggestions")
                    ?: root.optJSONArray("results")
                    ?: return emptyList()
                val result = mutableListOf<String>()
                for (i in 0 until suggestions.length()) {
                    val value = when (val item = suggestions.opt(i)) {
                        is JSONObject -> item.optString("query", item.optString("phrase", ""))
                        else -> item?.toString().orEmpty()
                    }.trim()
                    if (value.isNotBlank() && !result.contains(value)) result += value
                    if (result.size >= maxResults) break
                }
                result
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun JSONArray.toDistinctStrings(maxResults: Int): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until length()) {
            val suggestion = optString(i, "").trim()
            if (suggestion.isNotBlank() && !result.contains(suggestion)) result += suggestion
            if (result.size >= maxResults) break
        }
        return result
    }
}
