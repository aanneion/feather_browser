package com.example.media

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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

class MediaPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "browser_media_channel"
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "NeonMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
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
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })

            isActive = true
        }
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
                mediaSession?.isActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATE -> {
                updateNotificationAndSession()
            }
        }

        updateNotificationAndSession()
        return START_NOT_STICKY
    }

    private fun updateNotificationAndSession() {
        val metadata = MediaSessionManager.currentMetadata.value
        val isPlaying = MediaSessionManager.isPlaying.value

        if (metadata == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

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
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, metadata.album.ifBlank { "Neon Browser" })
        mediaSession?.setMetadata(metaBuilder.build())

        // Build Media Notification
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_notification)
            .setContentTitle(metadata.title)
            .setContentText(metadata.artist)
            .setSubText("Neon Browser")
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
            .build()

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
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
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
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }
}
