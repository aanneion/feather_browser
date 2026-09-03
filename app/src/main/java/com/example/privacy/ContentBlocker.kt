package com.example.privacy

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight, high-performance rule-based ad and tracker blocker.
 * Intercepts tracking pixels, telemetry, analytics beacons, ad network calls, and intrusive scripts.
 */
object ContentBlocker {

    // Default blocklist of notorious tracking and advertising host patterns
    private val blockedHostSuffixes = hashSetOf(
        // Ad networks & exchanges
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adnxs.com",
        "adsafeprotected.com",
        "adsystem.com",
        "adform.net",
        "admob.com",
        "rubiconproject.com",
        "criteo.com",
        "criteo.net",
        "openx.net",
        "outbrain.com",
        "taboola.com",
        "pubmatic.com",
        "media.net",
        "amazon-adsystem.com",
        "scorecardresearch.com",
        "quantserve.com",
        "moatads.com",
        "casalemedia.com",
        "smartadserver.com",
        "bidswitch.net",
        "yieldmo.com",
        "adroll.com",
        "revcontent.com",
        "mgid.com",
        "popads.net",
        "propellerads.com",
        "infolinks.com",
        "zergnet.com",
        
        // Trackers, Telemetry & Behavioral Analytics
        "google-analytics.com",
        "googletagmanager.com",
        "hotjar.com",
        "clarity.ms",
        "mouseflow.com",
        "mixpanel.com",
        "segment.io",
        "amplitude.com",
        "appsflyer.com",
        "adjust.com",
        "branch.io",
        "chartbeat.com",
        "crazyegg.com",
        "newrelic.com",
        "nr-data.net",
        "optimizely.com",
        "fullstory.com",
        "heapanalytics.com",
        "statcounter.com",
        "yandex.ru/metrika",
        "mc.yandex.ru"
    )

    private val blockedPathKeywords = arrayOf(
        "/pagead/",
        "/ads.js",
        "/advertisement",
        "/adserver",
        "/pixel.gif",
        "/tr?id=",
        "/analytics.js",
        "/gtag/js"
    )

    // Blocked count per tab ID
    private val tabBlockCounts = ConcurrentHashMap<String, AtomicInteger>()
    
    // Overall session blocked items counter (Reactive StateFlow)
    private val _totalBlockedCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val totalBlockedCount: kotlinx.coroutines.flow.StateFlow<Int> = _totalBlockedCount

    fun shouldBlock(uri: Uri, isGlobalBlockerEnabled: Boolean, isSiteWhitelisted: Boolean): Boolean {
        if (!isGlobalBlockerEnabled || isSiteWhitelisted) return false

        val host = uri.host?.lowercase() ?: return false

        // NEVER block googlevideo.com media streams
        if (host.contains("googlevideo.com")) {
            return false
        }

        // Check specialized YouTube ad endpoints
        if (YouTubeAdBlocker.isYouTubeAdRequest(uri)) {
            _totalBlockedCount.value += 1
            return true
        }
        val pathAndQuery = (uri.path ?: "") + (uri.query?.let { "?$it" } ?: "")

        // Check host suffix match (e.g. ad.doubleclick.net endsWith doubleclick.net)
        for (blockedHost in blockedHostSuffixes) {
            if (host == blockedHost || host.endsWith(".$blockedHost")) {
                _totalBlockedCount.value += 1
                return true
            }
        }

        // Check path keywords for generic tracking scripts
        val lowerPath = pathAndQuery.lowercase()
        for (kw in blockedPathKeywords) {
            if (lowerPath.contains(kw)) {
                _totalBlockedCount.value += 1
                return true
            }
        }

        return false
    }

    fun recordBlockForTab(tabId: String) {
        tabBlockCounts.computeIfAbsent(tabId) { AtomicInteger(0) }.incrementAndGet()
    }

    fun getBlockCountForTab(tabId: String): Int {
        return tabBlockCounts[tabId]?.get() ?: 0
    }

    fun resetTabBlockCount(tabId: String) {
        tabBlockCounts[tabId]?.set(0)
    }

    /**
     * Creates an empty response to cleanly drop blocked requests without breaking DOM/JS scripts.
     */
    fun createEmptyResponse(uri: Uri? = null): WebResourceResponse {
        val path = uri?.path?.lowercase() ?: ""
        val (mime, encoding, emptyContent) = when {
            path.endsWith(".js") || path.contains("script") -> Triple("application/javascript", "UTF-8", "/* blocked */".toByteArray())
            path.endsWith(".css") -> Triple("text/css", "UTF-8", "".toByteArray())
            path.endsWith(".json") -> Triple("application/json", "UTF-8", "{}".toByteArray())
            path.endsWith(".png") || path.endsWith(".gif") || path.endsWith(".jpg") || path.endsWith(".webp") -> Triple("image/png", "base64", ByteArray(0))
            else -> Triple("text/plain", "UTF-8", ByteArray(0))
        }

        return WebResourceResponse(
            mime,
            encoding,
            200,
            "OK",
            mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(emptyContent)
        )
    }
}
