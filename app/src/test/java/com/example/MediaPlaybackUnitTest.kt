package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.media.MediaControlAction
import com.example.media.MediaPlaybackService
import com.example.media.MediaSessionManager
import com.example.media.BrowserPlaybackState
import com.example.privacy.FingerprintScriptGenerator
import com.example.privacy.YouTubeAdBlocker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaPlaybackUnitTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MediaSessionManager.stopPlayback(context)
    }

    @Test
    fun mediaSessionManager_updatesMetadataCorrectly() {
        val tabId = "tab_yt_123"
        MediaSessionManager.updateMetadata(
            context = context,
            tabId = tabId,
            title = "Awesome Song",
            artist = "Great Artist",
            album = "YouTube Music",
            artworkUrl = "https://img.youtube.com/vi/test/hqdefault.jpg"
        )

        val metadata = MediaSessionManager.currentMetadata.value
        assertNotNull(metadata)
        assertEquals("Awesome Song", metadata?.title)
        assertEquals("Great Artist", metadata?.artist)
        assertEquals("YouTube Music", metadata?.album)
        assertEquals("https://img.youtube.com/vi/test/hqdefault.jpg", metadata?.artworkUrl)
        assertEquals(tabId, metadata?.tabId)
        assertEquals(tabId, MediaSessionManager.activeMediaTabId.value)
    }

    @Test
    fun mediaSessionManager_handlesFallbackBlankMetadata() {
        val tabId = "tab_yt_456"
        MediaSessionManager.updateMetadata(
            context = context,
            tabId = tabId,
            title = "   ",
            artist = ""
        )

        val metadata = MediaSessionManager.currentMetadata.value
        assertNotNull(metadata)
        assertEquals("Playing Audio", metadata?.title)
        assertEquals("Feather Browser", metadata?.artist)
    }

    @Test
    fun mediaSessionManager_updatesPlaybackStateAndActions() {
        val tabId = "tab_yt_789"
        MediaSessionManager.updatePlaybackState(context, tabId, BrowserPlaybackState.PLAYING)
        assertTrue((MediaSessionManager.playbackState.value == BrowserPlaybackState.PLAYING || MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING))
        assertEquals(tabId, MediaSessionManager.activeMediaTabId.value)

        // Dispatch pause action
        MediaSessionManager.dispatchAction(MediaControlAction.PAUSE)
        assertFalse((MediaSessionManager.playbackState.value == BrowserPlaybackState.PLAYING || MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING))

        // Dispatch play action
        MediaSessionManager.dispatchAction(MediaControlAction.PLAY)
        assertTrue((MediaSessionManager.playbackState.value == BrowserPlaybackState.PLAYING || MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING))

        // Dispatch toggle action
        MediaSessionManager.dispatchAction(MediaControlAction.TOGGLE_PLAY_PAUSE)
        assertFalse((MediaSessionManager.playbackState.value == BrowserPlaybackState.PLAYING || MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING))

        MediaSessionManager.dispatchAction(MediaControlAction.TOGGLE_PLAY_PAUSE)
        assertTrue((MediaSessionManager.playbackState.value == BrowserPlaybackState.PLAYING || MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING))

        // Dispatch stop action
        MediaSessionManager.stopPlayback(context)
        assertFalse((MediaSessionManager.playbackState.value == BrowserPlaybackState.PLAYING || MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING))
        assertNull(MediaSessionManager.currentMetadata.value)
        assertNull(MediaSessionManager.activeMediaTabId.value)
    }

    @Test
    fun backgroundPlayScript_containsAllEssentialYouTubeAndVisibilitySpoofs() {
        val script = FingerprintScriptGenerator.generateBackgroundPlayScript()

        // Visibility and focus spoofing
        assertTrue("Script must spoof document.hidden", script.contains("hidden"))
        assertTrue("Script must spoof visibilityState", script.contains("visibilityState"))
        assertTrue("Script must spoof hasFocus", script.contains("hasFocus"))

        // Event suppression
        assertTrue("Script must suppress visibilitychange", script.contains("visibilitychange"))
        assertTrue("Script must use stopImmediatePropagation", script.contains("stopImmediatePropagation"))

        // HTMLMediaElement pause override
        assertTrue("Script must hook IntersectionObserver", script.contains("IntersectionObserver"))

        // Global media actions for notification controls
        assertTrue("Script must define __feather_media_play", script.contains("window.__feather_media_play"))
        assertTrue("Script must define __feather_media_pause", script.contains("window.__feather_media_pause"))
        assertTrue("Script must define __feather_media_toggle", script.contains("window.__feather_media_toggle"))
        assertTrue("Script must define __feather_media_next", script.contains("window.__feather_media_next"))
        assertTrue("Script must define __feather_media_prev", script.contains("window.__feather_media_prev"))

        // Bridge notification callback
        assertTrue("Script must reference FeatherMediaBridge", script.contains("FeatherMediaBridge"))
    }

    @Test
    fun mediaSessionManager_tracksPositionAndDurationForTheSystemSeekBar() {
        val tabId = "tab_yt_progress"
        MediaSessionManager.updatePlaybackState(context, tabId, BrowserPlaybackState.PLAYING)
        MediaSessionManager.updateMetadata(
            context = context,
            tabId = tabId,
            title = "Ten Minute Video",
            artist = "Some Channel",
            album = "YouTube",
            artworkUrl = "https://i.ytimg.com/vi/abcdefghijk/hq720.jpg",
            durationMs = 600_000L
        )
        MediaSessionManager.updateProgress(context, tabId, 125_000L, 600_000L)

        assertEquals(125_000L, MediaSessionManager.currentPositionMs.value)
        assertEquals(600_000L, MediaSessionManager.currentMetadata.value?.durationMs)
        // Seeking keeps the progress in sync even before the page reports it back.
        MediaSessionManager.seekTo(context, tabId, 300_000L)
        assertEquals(300_000L, MediaSessionManager.currentPositionMs.value)

        MediaSessionManager.stopPlayback(context)
        assertEquals(0L, MediaSessionManager.currentPositionMs.value)
    }

    @Test
    fun mediaSessionManager_ignoresProgressFromOtherTabs() {
        val playingTab = "tab_playing"
        val otherTab = "tab_other"
        MediaSessionManager.updatePlaybackState(context, playingTab, BrowserPlaybackState.PLAYING)
        MediaSessionManager.updateMetadata(context, playingTab, "Track", "Artist", "YouTube", "")
        MediaSessionManager.updateProgress(context, playingTab, 42_000L, 100_000L)
        MediaSessionManager.updateProgress(context, otherTab, 7_000L, 100_000L)

        assertEquals(42_000L, MediaSessionManager.currentPositionMs.value)
        assertEquals(playingTab, MediaSessionManager.activeMediaTabId.value)
    }

    @Test
    fun backgroundPlayScript_reportsPositionAndSupportsSystemSeek() {
        val script = FingerprintScriptGenerator.generateBackgroundPlayScript()

        // Position / duration reporting for the Android media notification seek bar
        assertTrue("Script must push progress to the bridge", script.contains("updateProgress"))
        assertTrue("Script must hook setPositionState", script.contains("setPositionState"))
        assertTrue("Script must expose a seek entry point", script.contains("window.__feather_media_seek"))
        assertTrue("Script must forward seeks to YouTube's seekto handler", script.contains("'seekto'"))
        assertTrue("Script must react to player seeks", script.contains("'seeked'"))
        assertTrue("Script must publish duration", script.contains("durationchange"))

        // Thumbnail resolution based on the video that is playing right now
        assertTrue("Script must resolve the live video id", script.contains("getYouTubeVideoId"))
        assertTrue("Script must be able to rebuild a fresh thumbnail URL", script.contains("hq720.jpg"))
        assertTrue("Script must map media session artwork back to a video id", script.contains("vi_webp"))
    }

    @Test
    fun youTubeAdBlockScript_neverTouchesPlaybackDuringUserSeeking() {
        val script = YouTubeAdBlocker.getYouTubeAdBlockScript()

        // Ads are only acted upon when the player itself reports an ad
        assertTrue("Script must detect real ads", script.contains("ad-showing"))
        assertTrue("Script must guard against scrubbing", script.contains("isUserScrubbing"))
        assertTrue("Script must restore muted/playbackRate after an ad", script.contains("restoreAdPlayback"))
        assertTrue("Script must not fast-forward while the user seeks", script.contains("video.seeking"))

        // The loose overlay match and the document-wide observer that stalled the player
        // (and made in-player seek previews freeze) must stay gone.
        assertFalse(
            "Loose ad overlay detection must be removed",
            script.contains(".ad-showing, .ad-interrupting, .ytp-ad-player-overlay")
        )
        assertFalse(
            "Document-wide class observer must be removed",
            script.contains("subtree: true, attributes: true, attributeFilter: ['class']")
        )
    }

    @Test
    fun mediaPlaybackService_constantsAndIntentActionsAreDefined() {
        assertEquals("feather_media_playback_channel_v4", MediaPlaybackService.CHANNEL_ID)
        assertEquals(2001, MediaPlaybackService.NOTIFICATION_ID)
        assertEquals("com.example.media.PLAY", MediaPlaybackService.ACTION_PLAY)
        assertEquals("com.example.media.PAUSE", MediaPlaybackService.ACTION_PAUSE)
        assertEquals("com.example.media.TOGGLE", MediaPlaybackService.ACTION_TOGGLE)
        assertEquals("com.example.media.NEXT", MediaPlaybackService.ACTION_NEXT)
        assertEquals("com.example.media.PREV", MediaPlaybackService.ACTION_PREV)
        assertEquals("com.example.media.STOP", MediaPlaybackService.ACTION_STOP)
        assertEquals("com.example.media.UPDATE_STATE", MediaPlaybackService.ACTION_UPDATE_STATE)
    }

    @Test
    fun themeScript_generatesLightAndDarkOverrides() {
        val lightScript = FingerprintScriptGenerator.generateThemeScript(isDark = false)
        assertTrue(lightScript.contains("color-scheme: light !important"))
        assertTrue(lightScript.contains("yt-theme', 'light'"))

        val darkScript = FingerprintScriptGenerator.generateThemeScript(isDark = true)
        assertTrue(darkScript.contains("setAttribute('data-theme', 'dark')"))
        assertTrue(darkScript.contains("yt-theme', 'dark'"))
        assertTrue(darkScript.contains("setAttribute('dark', '')"))
    }
}
