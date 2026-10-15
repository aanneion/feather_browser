package com.example.media

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowserMediaMetadata(
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val tabId: String = "",
    val durationMs: Long = 0L
)

enum class MediaControlAction {
    PLAY,
    PAUSE,
    TOGGLE_PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    STOP
}

enum class BrowserPlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED
}

object MediaSessionManager {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _currentMetadata = MutableStateFlow<BrowserMediaMetadata?>(null)
    val currentMetadata = _currentMetadata.asStateFlow()

    private val _playbackState = MutableStateFlow(BrowserPlaybackState.IDLE)
    val playbackState = _playbackState.asStateFlow()

    private val _activeMediaTabId = MutableStateFlow<String?>(null)
    val activeMediaTabId = _activeMediaTabId.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _controlActions = MutableSharedFlow<MediaControlAction>(extraBufferCapacity = 16)
    val controlActions = _controlActions.asSharedFlow()

    // Strong/weak registry of WebViews per tabId so background commands can be directly dispatched
    private val webViewRegistry = java.util.concurrent.ConcurrentHashMap<String, java.lang.ref.WeakReference<android.webkit.WebView>>()

    @Volatile
    private var isServiceActive = false

    fun registerWebView(tabId: String, webView: android.webkit.WebView) {
        webViewRegistry[tabId] = java.lang.ref.WeakReference(webView)
    }

    fun unregisterWebView(tabId: String) {
        webViewRegistry.remove(tabId)
    }

    fun updateMetadata(
        context: Context,
        tabId: String,
        title: String,
        artist: String,
        album: String = "",
        artworkUrl: String = "",
        durationMs: Long = 0L
    ) {
        val cleanTitle = title.trim().ifBlank { "Playing Audio" }
        val cleanArtist = artist.trim().ifBlank { "Feather Browser" }
        val current = _currentMetadata.value
        val isSameTrack = current?.tabId == tabId && current.title == cleanTitle
        val effectiveArtworkUrl = if (artworkUrl.isNotBlank()) {
            artworkUrl
        } else if (isSameTrack && current.artworkUrl.isNotBlank()) {
            current.artworkUrl
        } else {
            ""
        }
        val effectiveDuration = if (durationMs > 0) durationMs else (current?.durationMs ?: 0L)
        val meta = BrowserMediaMetadata(
            title = cleanTitle,
            artist = cleanArtist,
            album = album,
            artworkUrl = effectiveArtworkUrl,
            tabId = tabId,
            durationMs = effectiveDuration
        )
        _currentMetadata.value = meta
        _activeMediaTabId.value = tabId

        // Only start or update service if media is currently playing or service is already active
        if (_playbackState.value == BrowserPlaybackState.PLAYING || _playbackState.value == BrowserPlaybackState.BUFFERING || isServiceActive) {
            startOrUpdateService(context)
        }
    }

    fun updatePlaybackState(
        context: Context,
        tabId: String,
        state: BrowserPlaybackState
    ) {
        val isCurrentlyActive = _playbackState.value == BrowserPlaybackState.PLAYING || _playbackState.value == BrowserPlaybackState.BUFFERING
        val isNewActive = state == BrowserPlaybackState.PLAYING || state == BrowserPlaybackState.BUFFERING

        if (!isNewActive && !isCurrentlyActive && !isServiceActive) {
            return
        }

        if (isNewActive) {
            val prevTabId = _activeMediaTabId.value
            if (prevTabId != null && prevTabId != tabId) {
                // Another tab has started playing media! Pause the previous tab so sound sources don't overlap
                val prevWebView = webViewRegistry[prevTabId]?.get()
                prevWebView?.post {
                    prevWebView.evaluateJavascript(
                        "if (typeof window.__feather_media_pause === 'function') { window.__feather_media_pause(); }",
                        null
                    )
                }
            }
        }

        _playbackState.value = state
        _activeMediaTabId.value = tabId

        if (state == BrowserPlaybackState.ENDED) {
            onMediaEnded(context, tabId)
            return
        }

        if (isNewActive) {
            if (_currentMetadata.value == null) {
                _currentMetadata.value = BrowserMediaMetadata(
                    title = "YouTube Video",
                    artist = "YouTube",
                    album = "Feather Browser",
                    artworkUrl = "",
                    tabId = tabId
                )
            }
            startOrUpdateService(context)
        } else if (isServiceActive) {
            startOrUpdateService(context)
        }
    }

    fun dispatchAction(action: MediaControlAction) {
        when (action) {
            MediaControlAction.PLAY -> _playbackState.value = BrowserPlaybackState.PLAYING
            MediaControlAction.PAUSE -> _playbackState.value = BrowserPlaybackState.PAUSED
            MediaControlAction.TOGGLE_PLAY_PAUSE -> { if (_playbackState.value == BrowserPlaybackState.PLAYING) _playbackState.value = BrowserPlaybackState.PAUSED else _playbackState.value = BrowserPlaybackState.PLAYING }
            MediaControlAction.STOP -> _playbackState.value = BrowserPlaybackState.PAUSED
            else -> {}
        }
        scope.launch {
            _controlActions.emit(action)
        }
        // Also execute directly on the registered WebView on the Main thread to ensure background execution succeeds
        val targetTabId = _activeMediaTabId.value
        val webView = targetTabId?.let { webViewRegistry[it]?.get() }
            ?: webViewRegistry.values.firstNotNullOfOrNull { it.get() }

        if (webView != null) {
            webView.post {
                val script = when (action) {
                    MediaControlAction.PLAY -> {
                        "if (window.__feather_media_play) window.__feather_media_play(); else document.querySelector('video, audio')?.play();"
                    }
                    MediaControlAction.PAUSE -> {
                        "if (window.__feather_media_pause) window.__feather_media_pause(); else document.querySelector('video, audio')?.pause();"
                    }
                    MediaControlAction.TOGGLE_PLAY_PAUSE -> {
                        "if (window.__feather_media_toggle) window.__feather_media_toggle(); else if (window.__feather_media_play) window.__feather_media_play(); else document.querySelector('video, audio')?.play();"
                    }
                    MediaControlAction.NEXT -> {
                        "if (window.__feather_media_next) window.__feather_media_next();"
                    }
                    MediaControlAction.PREVIOUS -> {
                        "if (window.__feather_media_prev) window.__feather_media_prev();"
                    }
                    MediaControlAction.STOP -> {
                        "if (window.__feather_media_pause) window.__feather_media_pause(); else document.querySelector('video, audio')?.pause();"
                    }
                }
                try {
                    webView.evaluateJavascript(script, null)
                } catch (e: Throwable) {}
            }
        }
    }

    fun onMediaEnded(context: Context, tabId: String) {
        if (_activeMediaTabId.value == tabId || _activeMediaTabId.value == null) {
            scope.launch {
                // Short grace period to allow YouTube playlist or next track autoplay to start seamlessly
                kotlinx.coroutines.delay(1000)
                val isActive = _playbackState.value == BrowserPlaybackState.PLAYING || _playbackState.value == BrowserPlaybackState.BUFFERING
                if (!isActive && (_activeMediaTabId.value == tabId || _activeMediaTabId.value == null)) {
                    stopPlayback(context)
                }
            }
        }
    }

    fun stopPlayback(context: Context) {
        _playbackState.value = BrowserPlaybackState.IDLE
        _currentMetadata.value = null
        _activeMediaTabId.value = null
        _currentPositionMs.value = 0L
        isServiceActive = false
        stopService(context)
    }

    fun refreshNotification(context: Context) {
        val isActive = _playbackState.value == BrowserPlaybackState.PLAYING || _playbackState.value == BrowserPlaybackState.BUFFERING
        if (isActive && _currentMetadata.value != null) {
            startOrUpdateService(context)
        }
    }

    /** Human-readable snapshot for logs / debugging the notification pipeline. */
    fun describeState(): String {
        val meta = _currentMetadata.value
        return "state=${_playbackState.value} tab=${_activeMediaTabId.value ?: "-"} " +
            "title='${meta?.title ?: "-"}' artwork=${!meta?.artworkUrl.isNullOrBlank()} " +
            "duration=${meta?.durationMs ?: 0} position=${_currentPositionMs.value} serviceActive=$isServiceActive"
    }

    fun updateProgress(context: Context, tabId: String, positionMs: Long, durationMs: Long) {
        // Ignore keep-alive reports from pages that do not own the media session at all.
        if (_activeMediaTabId.value != null && _activeMediaTabId.value != tabId) {
            return
        }
        if (_activeMediaTabId.value == null && _currentMetadata.value == null &&
            _playbackState.value != BrowserPlaybackState.PLAYING &&
            _playbackState.value != BrowserPlaybackState.BUFFERING
        ) {
            return
        }
        if (positionMs >= 0) {
            _currentPositionMs.value = positionMs
        }
        val current = _currentMetadata.value
        if (current != null && current.tabId == tabId) {
            if (durationMs > 0 && current.durationMs != durationMs) {
                _currentMetadata.value = current.copy(durationMs = durationMs)
            }
        } else if (durationMs > 0) {
            _currentMetadata.value = BrowserMediaMetadata(
                title = "YouTube Video",
                artist = "YouTube",
                album = "Feather Browser",
                artworkUrl = "",
                tabId = tabId,
                durationMs = durationMs
            )
        }
        val isActive = _playbackState.value == BrowserPlaybackState.PLAYING || _playbackState.value == BrowserPlaybackState.BUFFERING
        // Position updates arrive ~once per second. The running service reads this state
        // directly from its own ticker, so only make sure that it is actually running -
        // posting an intent per tick would churn the service for no benefit.
        if (!isServiceActive && isActive && _currentMetadata.value != null) {
            startOrUpdateService(context)
        }
    }

    fun seekTo(context: Context, tabId: String, positionMs: Long) {
        if (positionMs >= 0) {
            _currentPositionMs.value = positionMs
        }
        val targetTabId = tabId.takeIf { it.isNotBlank() } ?: _activeMediaTabId.value
        val webView = targetTabId?.let { webViewRegistry[it]?.get() }
            ?: webViewRegistry.values.firstNotNullOfOrNull { it.get() }
        if (webView != null) {
            webView.post {
                try {
                    webView.evaluateJavascript(
                        "if (window.__feather_media_seek) window.__feather_media_seek($positionMs);",
                        null
                    )
                } catch (e: Throwable) { }
            }
        }
        val isActive = _playbackState.value == BrowserPlaybackState.PLAYING || _playbackState.value == BrowserPlaybackState.BUFFERING
        if (isServiceActive || isActive) {
            startOrUpdateService(context)
        }
    }

    private fun startOrUpdateService(context: Context) {
        val isActive = _playbackState.value == BrowserPlaybackState.PLAYING || _playbackState.value == BrowserPlaybackState.BUFFERING
        if (!isActive && _currentMetadata.value == null) {
            return
        }
        try {
            isServiceActive = true
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_UPDATE_STATE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Safe fallback if background execution limits apply
        }
    }

    private fun stopService(context: Context) {
        try {
            isServiceActive = false
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) {
            // If the service isn't running (or background start limits apply), there is
            // nothing to stop; make sure a stale notification can't linger.
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(MediaPlaybackService.NOTIFICATION_ID)
            } catch (ex: Exception) { }
        }
    }
}
