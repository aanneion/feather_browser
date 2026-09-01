package com.example.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight in-memory and network favicon loader.
 * Uses Google S2 / DuckDuckGo high-res Favicon resolver with fallback to website direct /favicon.ico.
 * Zero heavy external dependencies.
 */
object FaviconLoader {
    // Cache storing loaded icons or null sentinels safely
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()
    private val failedDomains = ConcurrentHashMap.newKeySet<String>()

    suspend fun loadFavicon(url: String): ImageBitmap? {
        val domain = UrlUtils.extractDomain(url).trim().lowercase()
        if (domain.isBlank() || domain == "about:blank" || domain == "localhost") return null

        // Check cache
        val cached = memoryCache[domain]
        if (cached != null) {
            return cached
        }
        if (failedDomains.contains(domain)) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val bitmap = fetchFaviconBitmap(domain)
                val imageBitmap = bitmap?.asImageBitmap()
                if (imageBitmap != null) {
                    memoryCache[domain] = imageBitmap
                    imageBitmap
                } else {
                    failedDomains.add(domain)
                    null
                }
            } catch (e: Throwable) {
                failedDomains.add(domain)
                null
            }
        }
    }

    private fun fetchFaviconBitmap(domain: String): Bitmap? {
        // Try Google S2 Favicon service with 128px high-resolution icon
        val endpoints = listOf(
            "https://www.google.com/s2/favicons?domain=$domain&sz=128",
            "https://icons.duckduckgo.com/ip3/$domain.ico",
            "https://$domain/favicon.ico"
        )

        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            try {
                val url = URL(endpoint)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3500
                connection.readTimeout = 3500
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                connection.connect()

                if (connection.responseCode in 200..299) {
                    inputStream = connection.inputStream
                    val decoded = BitmapFactory.decodeStream(inputStream)
                    if (decoded != null && decoded.width > 1 && decoded.height > 1) {
                        return decoded
                    }
                }
            } catch (e: Exception) {
                // Try next endpoint
            } finally {
                try {
                    inputStream?.close()
                } catch (e: Exception) { }
                connection?.disconnect()
            }
        }
        return null
    }
}

@Composable
fun rememberFavicon(url: String): State<ImageBitmap?> {
    val faviconState = remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        faviconState.value = FaviconLoader.loadFavicon(url)
    }
    return faviconState
}
