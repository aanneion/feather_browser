package com.example.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.privacy.ProfileProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Profile-partitioned, privacy-respecting Favicon Loader.
 *
 * - Partitioned by `(profileId, domain)` so cached favicons never cross-pollinate or reveal
 *   visited domains across isolated profiles.
 * - Fetches directly from the origin site (`https://<domain>/favicon.ico`) routed through the
 *   active profile's proxy (`ProfileProxyManager`) — NEVER sending browsing domains to Google S2
 *   or DuckDuckGo third-party favicon servers!
 * - Suppressed completely in Private Mode (`isPrivate = true`).
 */
object FaviconLoader {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()
    private val failedKeys = ConcurrentHashMap.newKeySet<String>()

    private fun cacheKey(profileId: String, domain: String): String = "${profileId.ifBlank { "default_personal" }}|$domain"

    fun clearForProfile(profileId: String) {
        val prefix = "${profileId}|"
        memoryCache.keys.removeIf { it.startsWith(prefix) }
        failedKeys.removeIf { it.startsWith(prefix) }
    }

    fun clearAll() {
        memoryCache.clear()
        failedKeys.clear()
    }

    fun cacheFromWebViewIcon(profileId: String, url: String?, icon: Bitmap?, isPrivate: Boolean = false) {
        if (isPrivate || url.isNullOrBlank() || icon == null) return
        val domain = UrlUtils.extractDomain(url).trim().lowercase()
        if (domain.isBlank() || domain == "about:blank" || domain == "localhost") return
        if (icon.width > 1 && icon.height > 1) {
            memoryCache[cacheKey(profileId, domain)] = icon.asImageBitmap()
        }
    }

    suspend fun loadFavicon(
        url: String,
        profileId: String = "default_personal",
        isPrivate: Boolean = false
    ): ImageBitmap? {
        if (isPrivate) return null
        val domain = UrlUtils.extractDomain(url).trim().lowercase()
        if (domain.isBlank() || domain == "about:blank" || domain == "localhost") return null

        val key = cacheKey(profileId, domain)
        memoryCache[key]?.let { return it }
        if (failedKeys.contains(key)) return null

        return withContext(Dispatchers.IO) {
            try {
                val bitmap = fetchDirectOriginFavicon(domain)
                val imageBitmap = bitmap?.asImageBitmap()
                if (imageBitmap != null) {
                    memoryCache[key] = imageBitmap
                    imageBitmap
                } else {
                    failedKeys.add(key)
                    null
                }
            } catch (_: Throwable) {
                failedKeys.add(key)
                null
            }
        }
    }

    private fun fetchDirectOriginFavicon(domain: String): Bitmap? {
        val endpoints = listOf(
            "https://$domain/favicon.ico",
            "https://$domain/apple-touch-icon.png"
        )
        val proxy = ProfileProxyManager.getActiveJavaNetProxy()

        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            try {
                val url = URL(endpoint)
                connection = url.openConnection(proxy) as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
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
            } catch (_: Exception) {
                // Try next direct origin path
            } finally {
                try {
                    inputStream?.close()
                } catch (_: Exception) { }
                connection?.disconnect()
            }
        }
        return null
    }
}

@Composable
fun rememberFavicon(
    url: String,
    profileId: String = "default_personal",
    isPrivate: Boolean = false
): State<ImageBitmap?> {
    val faviconState = remember(url, profileId, isPrivate) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, profileId, isPrivate) {
        faviconState.value = FaviconLoader.loadFavicon(url, profileId = profileId, isPrivate = isPrivate)
    }
    return faviconState
}
