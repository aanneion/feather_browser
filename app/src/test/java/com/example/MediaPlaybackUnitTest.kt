package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
    fun mediaSessionScript_isIndependentFromBackgroundPlay() {
        val mediaScript = FingerprintScriptGenerator.generateMediaSessionScript()
        val backgroundShim = FingerprintScriptGenerator.generateBackgroundPlayShimScript()

        // The notification bridge must always exist, even when background play is off.
        assertTrue(mediaScript.contains("const featherIncludeBg = false;"))
        assertTrue(mediaScript.contains("const featherIncludeMedia = true;"))
        assertTrue(mediaScript.contains("FeatherMediaBridge"))
        assertTrue(mediaScript.contains("window.__feather_media_active = true;"))

        // The visibility/focus spoof is a separate, opt-in script.
        assertTrue(backgroundShim.contains("const featherIncludeBg = true;"))
        assertTrue(backgroundShim.contains("const featherIncludeMedia = false;"))
        assertTrue(backgroundShim.contains("visibilityState"))

        // Both scripts understand YouTube's ad state, so an ad never reaches the
        // notification as a separate track.
        assertTrue(mediaScript.contains("window.__feather_yt_ad_active"))
        assertTrue(mediaScript.contains("window.__feather_last_ad_end_at"))
        assertTrue(mediaScript.contains("isRecentAdEnd"))
    }

    @Test
    fun youTubeAdBlockScript_publishesAdTransitionsForTheMediaBridge() {
        val script = YouTubeAdBlocker.getYouTubeAdBlockScript()
        assertTrue(script.contains("window.__feather_yt_ad_active = true;"))
        assertTrue(script.contains("window.__feather_yt_ad_active = false;"))
        assertTrue(script.contains("window.__feather_last_ad_end_at = Date.now();"))
    }

    @Test
    fun mediaSessionManager_resetsStaleDurationWhenTheTrackChanges() {
        val tabId = "tab_duration_reset"
        MediaSessionManager.updateMetadata(
            context = context,
            tabId = tabId,
            title = "First Video",
            artist = "Channel A",
            album = "YouTube",
            artworkUrl = "https://i.ytimg.com/vi/first/hq720.jpg",
            durationMs = 600_000L
        )
        assertEquals(600_000L, MediaSessionManager.currentMetadata.value?.durationMs)

        // New video, no duration reported yet: the old 10-minute length must not leak.
        MediaSessionManager.updateMetadata(
            context = context,
            tabId = tabId,
            title = "Second Video",
            artist = "Channel B",
            album = "YouTube",
            artworkUrl = "https://i.ytimg.com/vi/second/hq720.jpg"
        )
        assertEquals(0L, MediaSessionManager.currentMetadata.value?.durationMs)

        MediaSessionManager.updateProgress(context, tabId, 90_000L, 120_000L)
        assertEquals(90_000L, MediaSessionManager.currentPositionMs.value)
        assertEquals(120_000L, MediaSessionManager.currentMetadata.value?.durationMs)
    }

    @Test
    fun mediaSessionManager_clampsProgressToTheReportedDuration() {
        val tabId = "tab_progress_clamp"
        MediaSessionManager.updatePlaybackState(context, tabId, BrowserPlaybackState.PLAYING)
        MediaSessionManager.updateMetadata(
            context = context,
            tabId = tabId,
            title = "Clamp Test",
            artist = "Channel",
            album = "YouTube",
            artworkUrl = "",
            durationMs = 60_000L
        )
        MediaSessionManager.updateProgress(context, tabId, 75_000L, 60_000L)
        assertEquals(60_000L, MediaSessionManager.currentPositionMs.value)
        MediaSessionManager.stopPlayback(context)
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
    fun mediaSessionManager_skipsByFixedStepsAndClampsToTheVideo() {
        val tabId = "tab_yt_skip"
        MediaSessionManager.updatePlaybackState(context, tabId, BrowserPlaybackState.PLAYING)
        MediaSessionManager.updateMetadata(
            context = context,
            tabId = tabId,
            title = "Skip Test",
            artist = "Channel",
            album = "YouTube",
            artworkUrl = "",
            durationMs = 60_000L
        )
        MediaSessionManager.updateProgress(context, tabId, 30_000L, 60_000L)

        // 10 second rewind / fast forward, exactly like the system media player's buttons
        MediaSessionManager.seekBy(context, tabId, 10.0)
        assertEquals(40_000L, MediaSessionManager.currentPositionMs.value)

        MediaSessionManager.seekBy(context, tabId, -10.0)
        assertEquals(30_000L, MediaSessionManager.currentPositionMs.value)

        // Clamped at both ends of the video
        MediaSessionManager.seekBy(context, tabId, -600.0)
        assertEquals(0L, MediaSessionManager.currentPositionMs.value)

        MediaSessionManager.seekBy(context, tabId, 600.0)
        assertEquals(60_000L, MediaSessionManager.currentPositionMs.value)

        // Another tab must not be able to move the active media session
        MediaSessionManager.seekBy(context, "tab_other", 10.0)
        assertEquals(60_000L, MediaSessionManager.currentPositionMs.value)
    }

    @Test
    fun mediaSessionManager_tracksAndResetsPlaybackRate() {
        val tabId = "tab_yt_rate"
        MediaSessionManager.updatePlaybackState(context, tabId, BrowserPlaybackState.PLAYING)
        MediaSessionManager.updateMetadata(context, tabId, "Rate Test", "Channel", "YouTube", "")

        assertEquals(1f, MediaSessionManager.currentPlaybackRate.value)

        MediaSessionManager.updatePlaybackRate(context, tabId, 1.5f)
        assertEquals(1.5f, MediaSessionManager.currentPlaybackRate.value)

        // Garbage rates from the page are ignored, real ones are applied
        MediaSessionManager.updatePlaybackRate(context, tabId, -2f)
        assertEquals(1.5f, MediaSessionManager.currentPlaybackRate.value)

        MediaSessionManager.setPlaybackRate(context, 2f)
        assertEquals(2f, MediaSessionManager.currentPlaybackRate.value)

        MediaSessionManager.setPlaybackRate(context, 0f)
        assertEquals(1f, MediaSessionManager.currentPlaybackRate.value)

        MediaSessionManager.stopPlayback(context)
        assertEquals(1f, MediaSessionManager.currentPlaybackRate.value)
    }

    @Test
    fun backgroundPlayScript_exposesSystemMediaPlayerControls() {
        val script = FingerprintScriptGenerator.generateBackgroundPlayScript()

        // 10 second skip buttons (notification / lock screen / headset), same step as Chromium
        assertTrue("Script must expose a relative seek entry point", script.contains("window.__feather_media_seek_by"))
        assertTrue("Relative seek must clamp at the end of the video", script.contains("Math.min(target, Math.max(0, duration - 1))"))

        // Playback speed control
        assertTrue("Script must expose a playback speed entry point", script.contains("window.__feather_media_set_rate"))
        assertTrue("Playback speed must respect YouTube's supported rates", script.contains("getAvailablePlaybackRates"))

        // Audio focus ducking
        assertTrue("Script must expose volume ducking", script.contains("window.__feather_media_duck"))

        // Playback rate reporting for the system media player's speed chip
        assertTrue("Script must report rate changes", script.contains("ratechange"))
        assertTrue("Script must push the rate to the bridge", script.contains("updatePlaybackRate"))
    }

    @Test
    fun mediaPlaybackService_mediaTabExtraIsDefined() {
        assertEquals("com.example.media.EXTRA_MEDIA_TAB_ID", MediaPlaybackService.EXTRA_MEDIA_TAB_ID)
    }

    @Test
    fun mediaPlaybackService_neverRequestsAudioFocusWhileMirroringPlayback() {
        // Regression test for the YouTube pause-on-play loop. MediaPlaybackService used to
        // call AudioManager.requestAudioFocus() every time the page reported PLAYING. Audio
        // focus ownership is device-wide and the WebView playing the video already holds it,
        // so the request revoked it with AUDIOFOCUS_LOSS and Chromium responded by pausing
        // the page media - the video died a split second after every play press. The service
        // only mirrors playback into the notification and must never take audio focus.
        val tabId = "tab_yt_focus"
        MediaSessionManager.updatePlaybackState(context, tabId, BrowserPlaybackState.PLAYING)
        MediaSessionManager.updateMetadata(context, tabId, "Focus Test", "Channel", "YouTube", "")

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val shadowAudioManager = shadowOf(audioManager)

        val controller = Robolectric.buildService(MediaPlaybackService::class.java).create()
        try {
            controller.get().onStartCommand(
                Intent(context, MediaPlaybackService::class.java)
                    .setAction(MediaPlaybackService.ACTION_UPDATE_STATE),
                0,
                1
            )
        } finally {
            controller.destroy()
            MediaSessionManager.stopPlayback(context)
        }

        assertNull(
            "MediaPlaybackService must never request audio focus - the page's WebView already owns it",
            shadowAudioManager.lastAudioFocusRequest
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
