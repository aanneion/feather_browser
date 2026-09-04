package com.example.media

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
    val tabId: String = ""
)

enum class MediaControlAction {
    PLAY,
    PAUSE,
    TOGGLE_PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    STOP
}

object MediaSessionManager {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _currentMetadata = MutableStateFlow<BrowserMediaMetadata?>(null)
    val currentMetadata = _currentMetadata.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _activeMediaTabId = MutableStateFlow<String?>(null)
    val activeMediaTabId = _activeMediaTabId.asStateFlow()

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
        artworkUrl: String = ""
    ) {
        val cleanTitle = title.trim().ifBlank { "Playing Audio" }
        val cleanArtist = artist.trim().ifBlank { "Feather Browser" }
        val meta = BrowserMediaMetadata(
            title = cleanTitle,
            artist = cleanArtist,
            album = album,
            artworkUrl = artworkUrl,
            tabId = tabId
        )
        _currentMetadata.value = meta
        _activeMediaTabId.value = tabId

        // Only start or update service if media is currently playing or service is already active
        if (_isPlaying.value || isServiceActive) {
            startOrUpdateService(context)
        }
    }

    fun updatePlaybackState(
        context: Context,
        tabId: String,
        playing: Boolean
    ) {
        if (!playing && !_isPlaying.value && !isServiceActive) {
            return
        }

        _isPlaying.value = playing
        _activeMediaTabId.value = tabId

        if (playing) {
            startOrUpdateService(context)
        } else if (isServiceActive && _currentMetadata.value != null) {
            startOrUpdateService(context)
        } else {
            stopPlayback(context)
        }
    }

    fun dispatchAction(action: MediaControlAction) {
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
                        val isPlaying = _isPlaying.value
                        if (isPlaying) {
                            "if (window.__feather_media_pause) window.__feather_media_pause(); else document.querySelector('video, audio')?.pause();"
                        } else {
                            "if (window.__feather_media_play) window.__feather_media_play(); else document.querySelector('video, audio')?.play();"
                        }
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
            stopPlayback(context)
        }
    }

    fun stopPlayback(context: Context) {
        _isPlaying.value = false
        _currentMetadata.value = null
        _activeMediaTabId.value = null
        isServiceActive = false
        stopService(context)
    }

    fun refreshNotification(context: Context) {
        if (_isPlaying.value && _currentMetadata.value != null) {
            startOrUpdateService(context)
        }
    }

    private fun startOrUpdateService(context: Context) {
        if (!_isPlaying.value && _currentMetadata.value == null) {
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
        } catch (e: Exception) { }
    }
}
