package com.example.media

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.MainActivity
import com.example.R
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MediaPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "feather_media_playback_channel_v4"
        const val NOTIFICATION_ID = 2001
        private const val TAG = "MediaPlaybackService"

        /** Position is re-published to the media session once per second while playing. */
        private const val TICKER_INTERVAL_MS = 1000L

        /** How long a failed artwork download is remembered before it is retried. */
        private const val ARTWORK_RETRY_COOLDOWN_MS = 60_000L

        const val ACTION_UPDATE_STATE = "com.example.media.UPDATE_STATE"
        const val ACTION_PLAY = "com.example.media.PLAY"
        const val ACTION_PAUSE = "com.example.media.PAUSE"
        const val ACTION_TOGGLE = "com.example.media.TOGGLE"
        const val ACTION_NEXT = "com.example.media.NEXT"
        const val ACTION_PREV = "com.example.media.PREV"
        const val ACTION_STOP = "com.example.media.STOP"

        // Only the artwork of the current / recently played videos is needed; 8 square
        // 512px thumbnails keep the cache well under a couple of megabytes.
        private val artworkCache = LruCache<String, Bitmap>(8)

        /**
         * Creates the media notification channel. Safe to call from anywhere (also invoked
         * early from BrowserApplication.onCreate so the channel exists before the service
         * needs it). Idempotent.
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Media Playback",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Background audio and media playback controls"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        /** One-line diagnostic snapshot of the notification presentation state. */
        private fun logNotificationState(context: Context, where: String) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val enabled = manager.areNotificationsEnabled()
                var postPermission = "n/a"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    postPermission = if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) "GRANTED" else "DENIED"
                }
                val channelImportanceStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = manager.getNotificationChannel(CHANNEL_ID)
                    if (channel != null) "exists(importance=${channel.importance})" else "MISSING"
                } else {
                    "n/a"
                }
                android.util.Log.i(
                    TAG,
                    "[$where] notificationsEnabled=$enabled postNotifications=$postPermission " +
                        "channel=$channelImportanceStr"
                )
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "[$where] notification state diagnostics failed", e)
            }
        }
    }

    private var mediaSession: MediaSessionCompat? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var cachedArtworkUrl: String = ""
    private var cachedArtworkBitmap: Bitmap? = null
    private var cachedArtworkTitleKey: String = ""
    private var artworkRequestId = 0
    private val inFlightArtwork = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val failedArtwork = java.util.Collections.synchronizedMap(mutableMapOf<String, Long>())

    // Keeps the system media seek bar (Android 13+ notification, lock screen, Bluetooth,
    // Android Auto) moving between the once-per-second position reports that arrive from the
    // page, so the bar never freezes or drifts away from the player's own progress bar.
    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastPushedPositionMs = -1L
    private var lastPushedState = -1
    private var tickerRunning = false
    private val progressTicker = object : Runnable {
        override fun run() {
            tickerRunning = false
            try {
                val state = MediaSessionManager.playbackState.value
                val isActive = state == BrowserPlaybackState.PLAYING || state == BrowserPlaybackState.BUFFERING
                if (!isActive) return
                pushPlaybackStateOnly()
                tickerRunning = true
                progressHandler.postDelayed(this, TICKER_INTERVAL_MS)
            } catch (e: Throwable) {
                tickerRunning = false
            }
        }
    }
    // Set when the service is (re)started solely to process a STOP command, so onCreate
    // does not promote an empty service to foreground and briefly post a phantom notification.
    private var pendingStop = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        setupMediaSession()

        val metadata = MediaSessionManager.currentMetadata.value
        val playbackState = MediaSessionManager.playbackState.value
        val isPlaying = playbackState == BrowserPlaybackState.PLAYING || playbackState == BrowserPlaybackState.BUFFERING

        if (metadata == null && !isPlaying) {
            // Nothing to present yet (e.g. service started only to receive ACTION_STOP).
            // Skip foreground promotion; onStartCommand will stop us immediately.
            pendingStop = true
            android.util.Log.i(TAG, "onCreate: no active playback -> skipping foreground promotion")
            return
        }
        logNotificationState(this, "onCreate")

        val title = metadata?.title?.ifBlank { "Media Playback" } ?: "Media Playback"
        val artist = metadata?.artist?.ifBlank { "Feather Browser" } ?: "Feather Browser"
        val album = metadata?.album?.ifBlank { "Feather Browser" } ?: "Feather Browser"

        // Immediate foreground promotion to comply with Android 8+ 5-second deadline
        val initialNotification = buildNotification(
            title = title,
            artist = artist,
            album = album,
            isPlaying = isPlaying,
            artwork = cachedArtworkBitmap
        )
        promoteToForeground(initialNotification)
    }

    private fun setupMediaSession() {
        val session = MediaSessionCompat(this, "FeatherMediaSession")
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session.setSessionActivity(sessionActivityIntent)

        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                MediaSessionManager.dispatchAction(MediaControlAction.PLAY)
            }

            override fun onPause() {
                MediaSessionManager.dispatchAction(MediaControlAction.PAUSE)
            }

            override fun onSkipToNext() {
                MediaSessionManager.dispatchAction(MediaControlAction.NEXT)
            }

            override fun onSkipToPrevious() {
                MediaSessionManager.dispatchAction(MediaControlAction.PREVIOUS)
            }

            override fun onStop() {
                stopProgressTicker()
                MediaSessionManager.stopPlayback(this@MediaPlaybackService)
                session.isActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(NOTIFICATION_ID)
                stopSelf()
            }

            override fun onSeekTo(pos: Long) {
                val tabId = MediaSessionManager.activeMediaTabId.value ?: ""
                MediaSessionManager.seekTo(this@MediaPlaybackService, tabId, pos)
            }
        })

        session.isActive = true
        mediaSession = session
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_UPDATE_STATE
        android.util.Log.i(TAG, "onStartCommand action=$action startId=$startId pendingStop=$pendingStop")

        when (action) {
            ACTION_PLAY -> MediaSessionManager.dispatchAction(MediaControlAction.PLAY)
            ACTION_PAUSE -> MediaSessionManager.dispatchAction(MediaControlAction.PAUSE)
            ACTION_TOGGLE -> MediaSessionManager.dispatchAction(MediaControlAction.TOGGLE_PLAY_PAUSE)
            ACTION_NEXT -> MediaSessionManager.dispatchAction(MediaControlAction.NEXT)
            ACTION_PREV -> MediaSessionManager.dispatchAction(MediaControlAction.PREVIOUS)
            ACTION_STOP -> {
                stopProgressTicker()
                MediaSessionManager.stopPlayback(this)
                mediaSession?.setActive(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(NOTIFICATION_ID)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATE -> {
                if (pendingStop) {
                    // onCreate skipped foreground promotion because there was no active
                    // playback at that moment. Re-check now: if state arrived in between,
                    // run the normal update path; otherwise shut down cleanly without ever
                    // having posted a notification.
                    val metadata = MediaSessionManager.currentMetadata.value
                    val ps = MediaSessionManager.playbackState.value
                    val active = metadata != null ||
                        ps == BrowserPlaybackState.PLAYING || ps == BrowserPlaybackState.BUFFERING
                    if (!active) {
                        android.util.Log.i(TAG, "ACTION_UPDATE_STATE with no active playback -> stopping without posting notification")
                        mediaSession?.setActive(false)
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    pendingStop = false
                }
                updateNotificationAndSession()
                return START_NOT_STICKY
            }
        }

        updateNotificationAndSession()
        return START_NOT_STICKY
    }

    private fun updateNotificationAndSession() {
        val metadata = MediaSessionManager.currentMetadata.value
        val isPlaying = (MediaSessionManager.playbackState.value == BrowserPlaybackState.PLAYING || MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING)

        if (metadata == null && !isPlaying) {
            stopProgressTicker()
            mediaSession?.setActive(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
            stopSelf()
            return
        }
        if (!isPlaying) stopProgressTicker()

        val title = metadata?.title?.ifBlank { "Media Playing" } ?: "Media Playing"
        val artist = metadata?.artist?.ifBlank { "Feather Browser" } ?: "YouTube"
        val album = metadata?.album?.ifBlank { "Feather Browser" } ?: "Feather Browser"
        val artworkUrl = metadata?.artworkUrl?.trim() ?: ""
        val durationMs = metadata?.durationMs ?: 0L
        val positionMs = MediaSessionManager.currentPositionMs.value

        if (artworkUrl.isNotBlank()) {
            if (artworkUrl != cachedArtworkUrl) {
                // A new video (or a re-resolved thumbnail) always invalidates the previous
                // bitmap so the notification can never keep showing the old thumbnail.
                cachedArtworkUrl = artworkUrl
                cachedArtworkBitmap = artworkCache.get(artworkUrl)
                artworkRequestId++
            }
        } else {
            cachedArtworkUrl = ""
            cachedArtworkBitmap = null
        }
        val trackKey = (metadata?.tabId ?: "") + "|" + title
        if (trackKey != cachedArtworkTitleKey) {
            cachedArtworkTitleKey = trackKey
            artworkRequestId++
        }

        // Update MediaSession state (position + duration drive the system seek bar)
        val isBuffering = MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING
        val playbackState = buildPlaybackState(isPlaying, isBuffering, positionMs)
        mediaSession?.setPlaybackState(playbackState)
        mediaSession?.isActive = true
        lastPushedPositionMs = positionMs
        lastPushedState = playbackState.state

        // Keep the position flowing between the once-per-second reports from the page.
        if (isPlaying) startProgressTicker()

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
        if (durationMs > 0L) {
            metaBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        }

        if (cachedArtworkBitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cachedArtworkBitmap)
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, cachedArtworkBitmap)
        }
        mediaSession?.setMetadata(metaBuilder.build())

        val notification = buildNotification(title, artist, album, isPlaying, cachedArtworkBitmap)
        promoteToForeground(notification)
        android.util.Log.i(TAG, "notify -> ${MediaSessionManager.describeState()}")
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) { }

        // Asynchronously fetch artwork when the URL is new (or a previous attempt failed)
        if (artworkUrl.isNotBlank() && cachedArtworkBitmap == null) {
            val targetUrl = artworkUrl
            val requestId = artworkRequestId
            val lastFailure = failedArtwork[targetUrl] ?: 0L
            val failedRecently = lastFailure > 0L && (System.currentTimeMillis() - lastFailure) < ARTWORK_RETRY_COOLDOWN_MS
            if (!failedRecently && inFlightArtwork.add(targetUrl)) {
                android.util.Log.i(TAG, "Fetching artwork for '$title' <- $targetUrl")
                serviceScope.launch {
                    val bmp = fetchBitmap(targetUrl)
                    inFlightArtwork.remove(targetUrl)
                    if (bmp != null) {
                        artworkCache.put(targetUrl, bmp)
                        failedArtwork.remove(targetUrl)
                    } else {
                        if (failedArtwork.size > 40) failedArtwork.clear()
                        failedArtwork[targetUrl] = System.currentTimeMillis()
                        android.util.Log.w(TAG, "Artwork download failed for $targetUrl")
                    }
                    if (bmp != null && cachedArtworkUrl == targetUrl && requestId == artworkRequestId) {
                        cachedArtworkBitmap = bmp

                        // Update MediaSession metadata with artwork for Android 11+ System Media Carousel
                        val latestMeta = MediaSessionManager.currentMetadata.value
                        val currentPlaybackState = MediaSessionManager.playbackState.value
                        val currentIsPlaying = currentPlaybackState == BrowserPlaybackState.PLAYING || currentPlaybackState == BrowserPlaybackState.BUFFERING
                        val currentIsBuffering = currentPlaybackState == BrowserPlaybackState.BUFFERING
                        val latestTitle = latestMeta?.title ?: title
                        val latestArtist = latestMeta?.artist ?: artist
                        val latestAlbum = latestMeta?.album ?: album
                        val latestDuration = latestMeta?.durationMs ?: durationMs
                        val latestPosition = MediaSessionManager.currentPositionMs.value

                        val updatedMetaBuilder = MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, latestTitle)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, latestArtist)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, latestAlbum)
                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp)
                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bmp)
                        if (latestDuration > 0L) {
                            updatedMetaBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, latestDuration)
                        }
                        mediaSession?.setMetadata(updatedMetaBuilder.build())
                        mediaSession?.setPlaybackState(
                            buildPlaybackState(currentIsPlaying, currentIsBuffering, latestPosition)
                        )

                        val updatedNotification = buildNotification(
                            title = latestTitle,
                            artist = latestArtist,
                            album = latestAlbum,
                            isPlaying = currentIsPlaying,
                            artwork = bmp
                        )
                        promoteToForeground(updatedNotification)
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        manager?.notify(NOTIFICATION_ID, updatedNotification)
                    }
                }
            }
        }
    }

    /**
     * Builds the media session playback state. Position and duration are what Android 13+ uses
     * to render the seek bar in the media notification / lock screen player, so both must be
     * real values (`PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN` hides the bar entirely).
     */
    private fun buildPlaybackState(isPlaying: Boolean, isBuffering: Boolean, positionMs: Long): PlaybackStateCompat {
        val state = when {
            isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, positionMs.coerceAtLeast(0L), if (isPlaying && !isBuffering) 1.0f else 0.0f)
            .build()
    }

    private fun startProgressTicker() {
        if (tickerRunning) return
        tickerRunning = true
        progressHandler.removeCallbacks(progressTicker)
        progressHandler.postDelayed(progressTicker, TICKER_INTERVAL_MS)
    }

    private fun stopProgressTicker() {
        tickerRunning = false
        progressHandler.removeCallbacks(progressTicker)
        lastPushedPositionMs = -1L
        lastPushedState = -1
    }

    /**
     * Re-publishes only the playback state (position / rate) of the media session. The system
     * media controls listen to the session itself, so this updates the seek bar without
     * rebuilding and re-posting the notification every second.
     */
    private fun pushPlaybackStateOnly() {
        val session = mediaSession ?: return
        val state = MediaSessionManager.playbackState.value
        val isPlaying = state == BrowserPlaybackState.PLAYING
        val isBuffering = state == BrowserPlaybackState.BUFFERING
        val positionMs = MediaSessionManager.currentPositionMs.value
        val compatState = when {
            isBuffering -> PlaybackStateCompat.STATE_BUFFERING
            isPlaying -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        if (positionMs == lastPushedPositionMs && compatState == lastPushedState) return
        session.setPlaybackState(buildPlaybackState(isPlaying, isBuffering, positionMs))
        lastPushedPositionMs = positionMs
        lastPushedState = compatState
    }

    private fun buildNotification(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        artwork: Bitmap?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MediaPlaybackService::class.java).apply { action = playPauseAction },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setContentIntent(contentPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_media_prev, "Previous", prevIntent)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopIntent)
            )

        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }

        return builder.build()
    }

    private fun promoteToForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            android.util.Log.i(TAG, "startForeground OK id=$NOTIFICATION_ID")
        } catch (e: Exception) {
            // Never let foreground promotion (or artwork state) prevent the notification
            // from being presented: fall back to a plain notify().
            android.util.Log.e(TAG, "startForeground FAILED: ${e.message}", e)
            logNotificationState(this, "promoteToForeground-fallback")
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (ex: Exception) {
                android.util.Log.e(TAG, "fallback notify() also failed", ex)
            }
        }
    }

    /**
     * Downloads a media thumbnail. YouTube only guarantees `hqdefault.jpg` / `mqdefault.jpg`
     * for every upload, so the higher quality variants are tried first and the function walks
     * down to the variants that always exist instead of giving up (which used to leave the
     * notification without artwork for many videos).
     */
    private suspend fun fetchBitmap(urlString: String): Bitmap? = withContext(Dispatchers.IO) {
        for (candidate in artworkCandidates(urlString)) {
            val bitmap = downloadBitmap(candidate)
            if (bitmap != null) return@withContext bitmap
        }
        null
    }

    /** Builds the ordered list of candidate thumbnail URLs for a given artwork URL. */
    private fun artworkCandidates(urlString: String): List<String> {
        val cleanUrl = when {
            urlString.startsWith("//") -> "https:$urlString"
            urlString.startsWith("/") -> "https://www.youtube.com$urlString"
            else -> urlString
        }
        val match = Regex("^https://i\\.ytimg\\.com/(?:vi|vi_webp)/([A-Za-z0-9_-]{11})/").find(cleanUrl)
            ?: return listOf(cleanUrl)
        val videoId = match.groupValues[1]
        val qualities = listOf("maxresdefault", "sddefault", "hq720", "hqdefault", "mqdefault")
        val ordered = LinkedHashSet<String>()
        ordered.add(cleanUrl)
        qualities.forEach { ordered.add("https://i.ytimg.com/vi/$videoId/$it.jpg") }
        return ordered.toList()
    }

    private fun downloadBitmap(urlString: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                doInput = true
                instanceFollowRedirects = true
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                )
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                null
            } else {
                java.io.BufferedInputStream(connection.inputStream).use { stream ->
                    val raw = BitmapFactory.decodeStream(stream)
                    if (raw == null) null else prepareArtwork(raw)
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            try { connection?.disconnect() } catch (e: Exception) { }
        }
    }

    /**
     * Down-scales the downloaded thumbnail and center-crops it to a square: the media
     * notification / lock screen player renders artwork 1:1, so a letterboxed 4:3 thumbnail
     * (hqdefault.jpg) would otherwise show black bars.
     */
    private fun prepareArtwork(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val cropped = if (source.width != source.height) {
            try {
                Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
            } catch (e: Exception) {
                source
            }
        } else {
            source
        }
        val maxSide = 512
        if (cropped.width <= maxSide) return cropped
        return try {
            Bitmap.createScaledBitmap(cropped, maxSide, maxSide, true)
        } catch (e: Exception) {
            cropped
        }
    }

    private fun createNotificationChannel() = createNotificationChannel(this)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!(MediaSessionManager.playbackState.value == BrowserPlaybackState.PLAYING || MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING)) {
            stopProgressTicker()
            MediaSessionManager.stopPlayback(this)
            mediaSession?.setActive(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressTicker()
        mediaSession?.setActive(false)
        mediaSession?.release()
        mediaSession = null
        cachedArtworkBitmap = null
    }
}
