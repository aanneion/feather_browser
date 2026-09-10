package com.example.privacy

import android.net.Uri
import android.webkit.WebResourceResponse
import com.example.data.BrowserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight, high-performance rule-based ad and tracker blocker.
 * Intercepts tracking pixels, telemetry, analytics beacons, ad network calls, and intrusive scripts.
 * Persistently records cumulative stats so the Privacy Dashboard retains history across app restarts.
 */
object ContentBlocker {

    private var browserPreferences: BrowserPreferences? = null
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val persistScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    // Overall persistent blocked items counters (Reactive StateFlows)
    private val _totalBlockedCount = MutableStateFlow(0)
    val totalBlockedCount: StateFlow<Int> = _totalBlockedCount

    private val _totalTrackersCount = MutableStateFlow(0)
    val totalTrackersCount: StateFlow<Int> = _totalTrackersCount

    private val _totalAdsCount = MutableStateFlow(0)
    val totalAdsCount: StateFlow<Int> = _totalAdsCount

    fun initialize(preferences: BrowserPreferences) {
        browserPreferences = preferences
        val savedTotal = preferences.getTotalBlockedCount().toInt()
        val savedTrackers = preferences.getTotalTrackersBlocked().toInt()
        val savedAds = preferences.getTotalAdsBlocked().toInt()

        val currentTotal = _totalBlockedCount.value
        val currentTrackers = _totalTrackersCount.value
        val currentAds = _totalAdsCount.value

        val newTotal = savedTotal + currentTotal
        val newTrackers = savedTrackers + currentTrackers
        val newAds = savedAds + currentAds

        _totalBlockedCount.value = newTotal
        _totalTrackersCount.value = newTrackers
        _totalAdsCount.value = newAds

        if (currentTotal > 0) schedulePersist()
    }

    fun resetStats() {
        _totalBlockedCount.value = 0
        _totalTrackersCount.value = 0
        _totalAdsCount.value = 0
        browserPreferences?.resetPrivacyStats()
    }

    private fun recordBlockedItem(isTracker: Boolean) {
        val total = _totalBlockedCount.updateAndGet { it + 1 }
        val trackers = if (isTracker) _totalTrackersCount.updateAndGet { it + 1 } else _totalTrackersCount.value
        val ads = if (!isTracker) _totalAdsCount.updateAndGet { it + 1 } else _totalAdsCount.value

        schedulePersist()
    }

    /** Coalesce noisy network events into a single small preferences write. */
    private fun schedulePersist() {
        if (!persistScheduled.compareAndSet(false, true)) return
        ioScope.launch {
            delay(750)
            browserPreferences?.let { prefs ->
                prefs.setTotalBlockedCount(_totalBlockedCount.value.toLong())
                prefs.setTotalTrackersBlocked(_totalTrackersCount.value.toLong())
                prefs.setTotalAdsBlocked(_totalAdsCount.value.toLong())
            }
            persistScheduled.set(false)
        }
    }

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

    private val trackerHostSuffixes = hashSetOf(
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

    private val trackerPathKeywords = arrayOf(
        "/pixel.gif",
        "/tr?id=",
        "/analytics.js",
        "/gtag/js"
    )

    // Blocked count per tab ID
    private val tabBlockCounts = ConcurrentHashMap<String, AtomicInteger>()

    fun shouldBlock(uri: Uri, isGlobalBlockerEnabled: Boolean, isSiteWhitelisted: Boolean): Boolean {
        if (!isGlobalBlockerEnabled || isSiteWhitelisted) return false

        val host = uri.host?.lowercase() ?: return false

        // NEVER block googlevideo.com media streams
        if (host.contains("googlevideo.com")) {
            return false
        }

        // Check specialized YouTube ad endpoints
        if (YouTubeAdBlocker.isYouTubeAdRequest(uri)) {
            recordBlockedItem(isTracker = false)
            return true
        }
        val pathAndQuery = (uri.path ?: "") + (uri.query?.let { "?$it" } ?: "")

        // Check host suffix match (e.g. ad.doubleclick.net endsWith doubleclick.net)
        for (blockedHost in blockedHostSuffixes) {
            if (host == blockedHost || host.endsWith(".$blockedHost")) {
                val isTracker = trackerHostSuffixes.contains(blockedHost)
                recordBlockedItem(isTracker = isTracker)
                return true
            }
        }

        // Check path keywords for generic tracking scripts
        val lowerPath = pathAndQuery.lowercase()
        for (kw in blockedPathKeywords) {
            if (lowerPath.contains(kw)) {
                val isTracker = trackerPathKeywords.any { kw.contains(it) }
                recordBlockedItem(isTracker = isTracker)
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
