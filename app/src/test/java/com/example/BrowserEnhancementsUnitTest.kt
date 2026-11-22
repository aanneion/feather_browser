package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.browser.BrowserViewModel
import com.example.browser.OmniboxBangs
import com.example.browser.OmniboxMatchKind
import com.example.browser.OmniboxMatcher
import com.example.browser.UrlUtils
import com.example.data.BookmarkExportHelper
import com.example.data.BrowserPreferences
import com.example.data.model.Bookmark
import com.example.data.model.HistoryItem
import com.example.privacy.FingerprintScriptGenerator
import com.example.privacy.UrlSanitizer
import com.example.ui.components.QrCodeGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserEnhancementsUnitTest {

    @Test
    fun urlSanitizer_stripsTrackingParamsAndPreservesEssentialQueryAndFragment() {
        val dirty = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc123track&t=42s&utm_source=share&fbclid=IwAR99#comments"
        val result = UrlSanitizer.sanitizeDetailed(dirty, enabled = true)

        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s#comments", result.url)
        assertEquals(3, result.removedCount)
    }

    @Test
    fun urlSanitizer_removesQuestionMarkWhenAllParamsWereTracking() {
        val dirty = "https://example.com/blog/post?utm_medium=social&utm_campaign=launch&gclid=test123"
        val cleaned = UrlSanitizer.sanitize(dirty, enabled = true)
        assertEquals("https://example.com/blog/post", cleaned)
    }

    @Test
    fun urlSanitizer_preservesSignedCloudAndAuthUrlsUntouched() {
        val signedS3 = "https://bucket.s3.amazonaws.com/report.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=deadbeef&utm_source=email"
        assertEquals(signedS3, UrlSanitizer.sanitize(signedS3, enabled = true))
    }

    @Test
    fun urlSanitizer_respectsDisabledFlag() {
        val dirty = "https://example.com/?utm_source=newsletter"
        assertEquals(dirty, UrlSanitizer.sanitize(dirty, enabled = false))
    }

    @Test
    fun privacySignalsScript_definesGlobalPrivacyControlAndDnt() {
        val script = FingerprintScriptGenerator.generatePrivacySignalsScript()
        assertTrue(script.contains("globalPrivacyControl"))
        assertTrue(script.contains("doNotTrack"))
        assertTrue(script.contains("window.__feather_gpc_active"))
    }

    @Test
    fun qrCodeGenerator_encodesShortAndLongUrlsIntoValidSquareMatrixAndBitmap() {
        val shortMatrix = QrCodeGenerator.encodeMatrix("https://github.com/aanneion/feather_browser")
        assertNotNull(shortMatrix)
        assertTrue(shortMatrix!!.size >= 21)
        assertEquals(shortMatrix.size, shortMatrix[0].size)

        // Top-left finder corner (0,0) and (6,6) must be dark per ISO/IEC 18004
        assertTrue(shortMatrix[0][0])
        assertTrue(shortMatrix[6][6])
        assertFalse(shortMatrix[1][1])
        assertTrue(shortMatrix[2][2])

        val bmp = QrCodeGenerator.generateBitmap("https://feather.example/docs?page=1&lang=en", targetSizePx = 320)
        assertNotNull(bmp)
        assertEquals(bmp!!.width, bmp.height)
        assertTrue(bmp.width >= 100)
    }

    @Test
    fun qrCodeGenerator_returnsNullOnBlankOrOversizedInput() {
        assertNull(QrCodeGenerator.encodeMatrix(""))
        val huge = "https://example.com/" + "a".repeat(400)
        assertNull(QrCodeGenerator.encodeMatrix(huge))
    }

    @Test
    fun omniboxMatcher_prioritizesBookmarksAndSuppressesInPrivateMode() {
        val bookmarks = listOf(
            Bookmark(id = 1, profileId = "default_personal", folderName = "Dev", title = "Kotlin Docs", url = "https://kotlinlang.org/docs/home.html")
        )
        val history = listOf(
            HistoryItem(id = 10, profileId = "default_personal", title = "Kotlin Coroutines Guide", url = "https://kotlinlang.org/docs/coroutines-guide.html"),
            HistoryItem(id = 11, profileId = "default_personal", title = "Unrelated Site", url = "https://wikipedia.org")
        )

        val normalResults = OmniboxMatcher.matchLocalSuggestions(
            query = "kotlin",
            bookmarks = bookmarks,
            history = history,
            isPrivateMode = false,
            maxResults = 4
        )
        assertEquals(2, normalResults.size)
        assertEquals(OmniboxMatchKind.BOOKMARK, normalResults[0].kind)
        assertEquals("https://kotlinlang.org/docs/home.html", normalResults[0].url)
        assertEquals(OmniboxMatchKind.HISTORY, normalResults[1].kind)

        val privateResults = OmniboxMatcher.matchLocalSuggestions(
            query = "kotlin",
            bookmarks = bookmarks,
            history = history,
            isPrivateMode = true,
            maxResults = 4
        )
        assertTrue(privateResults.isEmpty())
    }

    @Test
    fun bookmarkExportAndImport_preservesCustomFolders() {
        val original = listOf(
            Bookmark(id = 1, profileId = "p1", folderName = "Reading List", title = "Clean Architecture", url = "https://example.com/clean"),
            Bookmark(id = 2, profileId = "p1", folderName = "Work", title = "Company Portal", url = "https://portal.example.com")
        )

        val html = BookmarkExportHelper.exportToNetscapeHtml(original, profileName = "Personal")
        assertTrue(html.contains("Reading List"))
        assertTrue(html.contains("Work"))

        val reimported = BookmarkExportHelper.parseBookmarks(html, targetProfileId = "p2")
        assertEquals(2, reimported.size)
        assertEquals("Reading List", reimported[0].folderName)
        assertEquals("Work", reimported[1].folderName)
    }

    @Test
    fun browserPreferences_persistsPrivacyEnhancementFlags() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = BrowserPreferences(context)
        assertTrue(prefs.isStripTrackingParamsEnabled())
        assertTrue(prefs.isSendPrivacySignalsEnabled())

        prefs.setStripTrackingParamsEnabled(false)
        prefs.setSendPrivacySignalsEnabled(false)

        val reloaded = BrowserPreferences(context)
        assertFalse(reloaded.isStripTrackingParamsEnabled())
        assertFalse(reloaded.isSendPrivacySignalsEnabled())

        // Restore defaults
        reloaded.setStripTrackingParamsEnabled(true)
        reloaded.setSendPrivacySignalsEnabled(true)
    }

    @Test
    fun viewModel_sanitizesNavigatedUrlsAndSupportsReopenClosedTab() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = BrowserViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()

        vm.navigateTo("https://example.com/article?id=7&utm_source=twitter&fbclid=xyz")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("https://example.com/article?id=7", vm.activeTabState.value?.url)

        val openedTabId = vm.activeTabId.value
        assertTrue(openedTabId.isNotBlank())

        vm.closeTab(openedTabId)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(vm.recentlyClosedTabs.value.any { it.url == "https://example.com/article?id=7" })

        vm.reopenLastClosedTab()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("https://example.com/article?id=7", vm.activeTabState.value?.url)
    }

    @Test
    fun omniboxBangs_resolvesQueriesAndBareShortcuts() {
        val ghSearch = OmniboxBangs.resolve("!gh kotlin coroutines")
        assertNotNull(ghSearch)
        assertEquals("GitHub", ghSearch!!.providerName)
        assertEquals("https://github.com/search?q=kotlin+coroutines", ghSearch.resolvedUrl)
        assertEquals("https://github.com/search?q=kotlin+coroutines", UrlUtils.parseInputToUrl("!gh kotlin coroutines"))

        val bareWiki = OmniboxBangs.resolve("!w")
        assertNotNull(bareWiki)
        assertEquals("https://en.wikipedia.org", bareWiki!!.resolvedUrl)

        assertNull(OmniboxBangs.resolve("!unknownbang query"))
        assertNull(OmniboxBangs.resolve("normal search query"))
    }

    @Test
    fun cookieBannerHiderScript_includesCmpRejectAndHideRules() {
        val script = FingerprintScriptGenerator.generateCookieBannerHiderScript()
        assertTrue(script.contains("#onetrust-reject-all-handler"))
        assertTrue(script.contains("#CybotCookiebotDialogBodyButtonDecline"))
        assertTrue(script.contains("window.__feather_cookie_guard_active"))
    }

    @Test
    fun preferencesAndViewModel_persistZoomCookieGuardAndAdjacentTabSwipe() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = BrowserPreferences(app)
        assertTrue(prefs.isHideCookieBannersEnabled())
        assertEquals(100, prefs.getDefaultTextZoom())

        val vm = BrowserViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()

        vm.setPageTextZoom(130)
        vm.setHideCookieBanners(false)
        assertEquals(130, vm.pageTextZoom.value)
        assertFalse(vm.hideCookieBanners.value)

        // Clamp zoom within 50..200
        vm.setPageTextZoom(350)
        assertEquals(200, vm.pageTextZoom.value)
        vm.setPageTextZoom(100)
        vm.setHideCookieBanners(true)

        // Verify adjacent tab switching wraps cleanly across open tabs
        vm.navigateTo("https://first.example.com")
        shadowOf(Looper.getMainLooper()).idle()
        val firstTabId = vm.activeTabId.value

        vm.createNewTab("https://second.example.com")
        shadowOf(Looper.getMainLooper()).idle()
        val secondTabId = vm.activeTabId.value
        assertNotEquals(firstTabId, secondTabId)

        vm.switchAdjacentTab(next = true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(firstTabId, vm.activeTabId.value)

        vm.switchAdjacentTab(next = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(secondTabId, vm.activeTabId.value)
    }
}
