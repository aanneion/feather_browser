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

    fun updateMetadata(
        context: Context,
        tabId: String,
        title: String,
        artist: String,
        album: String = "",
        artworkUrl: String = ""
    ) {
        val cleanTitle = title.trim().ifBlank { "Playing Audio" }
        val cleanArtist = artist.trim().ifBlank { "Neon Browser" }
        val meta = BrowserMediaMetadata(
            title = cleanTitle,
            artist = cleanArtist,
            album = album,
            artworkUrl = artworkUrl,
            tabId = tabId
        )
        _currentMetadata.value = meta
        _activeMediaTabId.value = tabId

        startOrUpdateService(context)
    }

    fun updatePlaybackState(
        context: Context,
        tabId: String,
        playing: Boolean
    ) {
        _isPlaying.value = playing
        _activeMediaTabId.value = tabId

        if (playing) {
            startOrUpdateService(context)
        } else {
            // Keep notification visible in paused state so user can resume
            startOrUpdateService(context)
        }
    }

    fun dispatchAction(action: MediaControlAction) {
        scope.launch {
            _controlActions.emit(action)
        }
    }

    fun onMediaEnded(context: Context, tabId: String) {
        if (_activeMediaTabId.value == tabId) {
            _isPlaying.value = false
            stopService(context)
        }
    }

    fun stopPlayback(context: Context) {
        _isPlaying.value = false
        _currentMetadata.value = null
        _activeMediaTabId.value = null
        stopService(context)
    }

    private fun startOrUpdateService(context: Context) {
        try {
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
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) { }
    }
}
