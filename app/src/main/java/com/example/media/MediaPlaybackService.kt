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

        const val ACTION_UPDATE_STATE = "com.example.media.UPDATE_STATE"
        const val ACTION_PLAY = "com.example.media.PLAY"
        const val ACTION_PAUSE = "com.example.media.PAUSE"
        const val ACTION_TOGGLE = "com.example.media.TOGGLE"
        const val ACTION_NEXT = "com.example.media.NEXT"
        const val ACTION_PREV = "com.example.media.PREV"
        const val ACTION_STOP = "com.example.media.STOP"

        private val artworkCache = LruCache<String, Bitmap>(20)

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
                val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    manager.getNotificationChannel(CHANNEL_ID) else null
                android.util.Log.i(
                    TAG,
                    "[$where] notificationsEnabled=$enabled postNotifications=$postPermission " +
                        "channel=${if (channel != null) "exists(importance=${channel.importance})" else "MISSING"}"
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
                MediaSessionManager.stopPlayback(this@MediaPlaybackService)
                session.isActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(NOTIFICATION_ID)
                stopSelf()
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
            mediaSession?.setActive(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
            stopSelf()
            return
        }

        val title = metadata?.title?.ifBlank { "Media Playing" } ?: "Media Playing"
        val artist = metadata?.artist?.ifBlank { "Feather Browser" } ?: "YouTube"
        val album = metadata?.album?.ifBlank { "Feather Browser" } ?: "Feather Browser"
        val artworkUrl = metadata?.artworkUrl?.trim() ?: ""
        val cachedBmp = if (artworkUrl.isNotBlank()) artworkCache.get(artworkUrl) else null
        if (cachedBmp != null) {
            cachedArtworkBitmap = cachedBmp
            cachedArtworkUrl = artworkUrl
        }

        // Update MediaSession state
        val isBuffering = MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING; val state = if (isBuffering) PlaybackStateCompat.STATE_BUFFERING else if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
        mediaSession?.isActive = true

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)

        if (cachedArtworkBitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cachedArtworkBitmap)
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, cachedArtworkBitmap)
        }
        mediaSession?.setMetadata(metaBuilder.build())

        val notification = buildNotification(title, artist, album, isPlaying, cachedArtworkBitmap)
        promoteToForeground(notification)
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) { }

        // Asynchronously fetch artwork if new URL provided and not yet cached
        if (artworkUrl.isNotBlank() && cachedArtworkBitmap == null && artworkUrl != cachedArtworkUrl) {
            cachedArtworkUrl = artworkUrl
            serviceScope.launch {
                val bmp = fetchBitmap(artworkUrl)
                if (bmp != null) {
                    artworkCache.put(artworkUrl, bmp)
                    cachedArtworkBitmap = bmp

                    // Update MediaSession metadata with artwork for Android 11+ System Media Carousel
                    val currentPlaybackState = MediaSessionManager.playbackState.value
                    val currentIsPlaying = currentPlaybackState == BrowserPlaybackState.PLAYING || currentPlaybackState == BrowserPlaybackState.BUFFERING

                    val updatedMeta = MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bmp)
                        .build()
                    mediaSession?.setMetadata(updatedMeta)

                    val updatedNotification = buildNotification(title, artist, album, currentIsPlaying, bmp)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, updatedNotification)
                }
            }
        }
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

    private suspend fun fetchBitmap(urlString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = when {
                urlString.startsWith("//") -> "https:$urlString"
                urlString.startsWith("/") -> "https://www.youtube.com$urlString"
                else -> urlString
            }
            val url = URL(cleanUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
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
            if (connection.responseCode in 200..299) {
                java.io.BufferedInputStream(connection.inputStream).use { stream ->
                    val rawBmp = BitmapFactory.decodeStream(stream)
                    if (rawBmp != null) {
                        val maxDim = 512
                        if (rawBmp.width > maxDim || rawBmp.height > maxDim) {
                            val ratio = rawBmp.width.toFloat() / rawBmp.height.toFloat()
                            val targetW = if (ratio >= 1f) maxDim else (maxDim * ratio).toInt().coerceAtLeast(1)
                            val targetH = if (ratio >= 1f) (maxDim / ratio).toInt().coerceAtLeast(1) else maxDim
                            Bitmap.createScaledBitmap(rawBmp, targetW, targetH, true)
                        } else {
                            rawBmp
                        }
                    } else {
                        null
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createNotificationChannel() = createNotificationChannel(this)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!(MediaSessionManager.playbackState.value == BrowserPlaybackState.PLAYING || MediaSessionManager.playbackState.value == BrowserPlaybackState.BUFFERING)) {
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
        mediaSession?.setActive(false)
        mediaSession?.release()
        mediaSession = null
        cachedArtworkBitmap = null
    }
}
