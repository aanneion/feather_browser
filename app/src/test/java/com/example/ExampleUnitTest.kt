package com.example

import android.net.Uri
import com.example.privacy.ContentBlocker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentBlockerUnitTest {

    @Test
    fun shouldBlock_knownAdHosts_returnsTrue() {
        val adUri = Uri.parse("https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub")
        assertTrue(ContentBlocker.shouldBlock(adUri, isGlobalBlockerEnabled = true, isSiteWhitelisted = false))
    }

    @Test
    fun shouldBlock_trackerKeywords_returnsTrue() {
        val trackerUri = Uri.parse("https://analytics.example.com/gtag/js?id=UA-12345")
        assertTrue(ContentBlocker.shouldBlock(trackerUri, isGlobalBlockerEnabled = true, isSiteWhitelisted = false))
    }

    @Test
    fun shouldBlock_normalSite_returnsFalse() {
        val normalUri = Uri.parse("https://en.wikipedia.org/wiki/Main_Page")
        assertFalse(ContentBlocker.shouldBlock(normalUri, isGlobalBlockerEnabled = true, isSiteWhitelisted = false))
    }

    @Test
    fun shouldBlock_whitelistedSite_returnsFalse() {
        val adUri = Uri.parse("https://googleads.g.doubleclick.net/pagead/ads")
        assertFalse(ContentBlocker.shouldBlock(adUri, isGlobalBlockerEnabled = true, isSiteWhitelisted = true))
    }

    @Test
    fun shouldBlock_disabledBlocker_returnsFalse() {
        val adUri = Uri.parse("https://googleads.g.doubleclick.net/pagead/ads")
        assertFalse(ContentBlocker.shouldBlock(adUri, isGlobalBlockerEnabled = false, isSiteWhitelisted = false))
    }

    @Test
    fun youtubeStream_neverBlocked() {
        val videoStreamUri = Uri.parse("https://rr1---sn-4g5edn6e.googlevideo.com/videoplayback?expire=1700000000&ei=test&ip=0.0.0.0&id=o-test&itag=18")
        assertFalse(com.example.privacy.YouTubeAdBlocker.isYouTubeAdRequest(videoStreamUri))
        assertFalse(ContentBlocker.shouldBlock(videoStreamUri, isGlobalBlockerEnabled = true, isSiteWhitelisted = false))
    }

    @Test
    fun youtubeAds_blockedProperly() {
        val adUri = Uri.parse("https://www.youtube.com/pagead/parallel_ad_stream")
        assertTrue(com.example.privacy.YouTubeAdBlocker.isYouTubeAdRequest(adUri))
        assertTrue(ContentBlocker.shouldBlock(adUri, isGlobalBlockerEnabled = true, isSiteWhitelisted = false))

        val doubleClickUri = Uri.parse("https://googleads.g.doubleclick.net/pagead/ads")
        assertTrue(com.example.privacy.YouTubeAdBlocker.isYouTubeAdRequest(doubleClickUri))
    }

    @Test
    fun browserPreferences_persistsNewTabStyleAcrossRestarts() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs1 = com.example.data.BrowserPreferences(context)
        prefs1.setNewTabStyle(com.example.browser.NewTabStyle.MINIMALIST)

        // Simulate app restart / new instance
        val prefs2 = com.example.data.BrowserPreferences(context)
        assertEquals(com.example.browser.NewTabStyle.MINIMALIST, prefs2.getNewTabStyle())
    }

    @Test
    fun searchSuggestionService_parsesGoogleJsonFormatCorrectly() {
        val json = """["kotlin",["kotlin","kotlin tutorial","kotlin coroutines","kotlin documentation"]]"""
        val suggestions = com.example.browser.SearchSuggestionService.parseSuggestionsJson(json, maxResults = 5)

        assertEquals(4, suggestions.size)
        assertEquals("kotlin", suggestions[0])
        assertEquals("kotlin tutorial", suggestions[1])
        assertEquals("kotlin coroutines", suggestions[2])
        assertEquals("kotlin documentation", suggestions[3])
    }

    @Test
    fun searchSuggestionService_limitsMaxResults() {
        val json = """["ai",["ai tools","ai news","ai generated art","ai stocks","ai video"]]"""
        val suggestions = com.example.browser.SearchSuggestionService.parseSuggestionsJson(json, maxResults = 3)

        assertEquals(3, suggestions.size)
        assertEquals("ai tools", suggestions[0])
        assertEquals("ai news", suggestions[1])
        assertEquals("ai generated art", suggestions[2])
    }

    @Test
    fun searchSuggestionService_handlesMalformedJsonGracefully() {
        val invalidJson = "<html><body>Not JSON</body></html>"
        val suggestions = com.example.browser.SearchSuggestionService.parseSuggestionsJson(invalidJson, maxResults = 5)
        assertTrue(suggestions.isEmpty())
    }
}
