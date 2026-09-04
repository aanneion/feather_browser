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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MediaPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "neon_media_playback_channel_v3"
        const val NOTIFICATION_ID = 2001

        const val ACTION_UPDATE_STATE = "com.example.media.UPDATE_STATE"
        const val ACTION_PLAY = "com.example.media.PLAY"
        const val ACTION_PAUSE = "com.example.media.PAUSE"
        const val ACTION_TOGGLE = "com.example.media.TOGGLE"
        const val ACTION_NEXT = "com.example.media.NEXT"
        const val ACTION_PREV = "com.example.media.PREV"
        const val ACTION_STOP = "com.example.media.STOP"
    }

    private var mediaSession: MediaSessionCompat? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var cachedArtworkUrl: String = ""
    private var cachedArtworkBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()

        val metadata = MediaSessionManager.currentMetadata.value
        val isPlaying = MediaSessionManager.isPlaying.value

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

        if (metadata == null && !isPlaying) {
            mediaSession?.setActive(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
            stopSelf()
        }
    }

    private fun setupMediaSession() {
        val session = MediaSessionCompat(this, "NeonMediaSession")
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

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
                updateNotificationAndSession()
                return START_NOT_STICKY
            }
        }

        updateNotificationAndSession()
        return START_NOT_STICKY
    }

    private fun updateNotificationAndSession() {
        val metadata = MediaSessionManager.currentMetadata.value
        val isPlaying = MediaSessionManager.isPlaying.value

        if (metadata == null && !isPlaying) {
            mediaSession?.setActive(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
            stopSelf()
            return
        }

        val title = metadata?.title?.ifBlank { "Media Playing" } ?: "Media Playing"
        val artist = metadata?.artist?.ifBlank { "Neon Browser" } ?: "YouTube"
        val album = metadata?.album?.ifBlank { "Neon Browser" } ?: "Neon Browser"
        val artworkUrl = metadata?.artworkUrl ?: ""

        // Update MediaSession state
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
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

        // Asynchronously fetch artwork if new URL provided
        if (artworkUrl.isNotBlank() && artworkUrl != cachedArtworkUrl) {
            cachedArtworkUrl = artworkUrl
            serviceScope.launch {
                val bmp = fetchBitmap(artworkUrl)
                if (bmp != null) {
                    cachedArtworkBitmap = bmp
                    val updatedNotification = buildNotification(title, artist, album, isPlaying, bmp)
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

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_TOGGLE },
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
        } catch (e: Exception) {
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (ex: Exception) { }
        }
    }

    private suspend fun fetchBitmap(urlString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio and media playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!MediaSessionManager.isPlaying.value) {
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
