package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.media.MediaControlAction
import com.example.media.MediaPlaybackService
import com.example.media.MediaSessionManager
import com.example.privacy.FingerprintScriptGenerator
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
        MediaSessionManager.updatePlaybackState(context, tabId, true)
        assertTrue(MediaSessionManager.isPlaying.value)
        assertEquals(tabId, MediaSessionManager.activeMediaTabId.value)

        // Dispatch pause action
        MediaSessionManager.dispatchAction(MediaControlAction.PAUSE)
        assertFalse(MediaSessionManager.isPlaying.value)

        // Dispatch play action
        MediaSessionManager.dispatchAction(MediaControlAction.PLAY)
        assertTrue(MediaSessionManager.isPlaying.value)

        // Dispatch toggle action
        MediaSessionManager.dispatchAction(MediaControlAction.TOGGLE_PLAY_PAUSE)
        assertFalse(MediaSessionManager.isPlaying.value)

        MediaSessionManager.dispatchAction(MediaControlAction.TOGGLE_PLAY_PAUSE)
        assertTrue(MediaSessionManager.isPlaying.value)

        // Dispatch stop action
        MediaSessionManager.stopPlayback(context)
        assertFalse(MediaSessionManager.isPlaying.value)
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
        assertTrue("Script must hook HTMLMediaElement.prototype.pause", script.contains("HTMLMediaElement.prototype.pause"))

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
    fun mediaPlaybackService_constantsAndIntentActionsAreDefined() {
        assertEquals("neon_media_playback_channel_v3", MediaPlaybackService.CHANNEL_ID)
        assertEquals(2001, MediaPlaybackService.NOTIFICATION_ID)
        assertEquals("com.example.media.PLAY", MediaPlaybackService.ACTION_PLAY)
        assertEquals("com.example.media.PAUSE", MediaPlaybackService.ACTION_PAUSE)
        assertEquals("com.example.media.TOGGLE", MediaPlaybackService.ACTION_TOGGLE)
        assertEquals("com.example.media.NEXT", MediaPlaybackService.ACTION_NEXT)
        assertEquals("com.example.media.PREV", MediaPlaybackService.ACTION_PREV)
        assertEquals("com.example.media.STOP", MediaPlaybackService.ACTION_STOP)
        assertEquals("com.example.media.UPDATE_STATE", MediaPlaybackService.ACTION_UPDATE_STATE)
    }
}
