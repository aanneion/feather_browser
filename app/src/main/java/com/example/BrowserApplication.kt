package com.example

import android.app.Application
import android.system.Os
import com.example.browser.DeviceUtils
import com.example.media.MediaPlaybackService
import java.io.File

class BrowserApplication : Application() {

    init {
        configureGraphicsEnvironment()
    }

    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        configureGraphicsEnvironment()
    }

    override fun onCreate() {
        super.onCreate()
        configureGraphicsEnvironment()

        // Create the media notification channel at process start so it always exists
        // before MediaPlaybackService promotes itself to foreground. On some devices a
        // foreground-service notification posted against a channel created in the same
        // instant can be dropped; pre-creating removes that race.
        try {
            MediaPlaybackService.createNotificationChannel(this)
        } catch (e: Throwable) {
            android.util.Log.e("BrowserApp", "Failed to pre-create media notification channel", e)
        }

        // Clean up any improperly nested Code Cache inside HTTP Cache that breaks Chromium's SimpleIndexFile
        try {
            val cache = cacheDir
            val legacyWrongCache = File(cache, "WebView/Default/HTTP Cache/Code Cache")
            if (legacyWrongCache.exists()) {
                legacyWrongCache.deleteRecursively()
            }

            // Ensure standard root WebView cache directories exist with proper access permissions
            val defaultHttpCache = File(cache, "WebView/Default/HTTP Cache")
            if (!defaultHttpCache.exists()) {
                defaultHttpCache.mkdirs()
            }
            defaultHttpCache.setReadable(true, false)
            defaultHttpCache.setWritable(true, false)
            defaultHttpCache.setExecutable(true, false)

            val defaultCodeCache = File(cache, "WebView/Default/Code Cache")
            if (!defaultCodeCache.exists()) {
                defaultCodeCache.mkdirs()
            }
            defaultCodeCache.setReadable(true, false)
            defaultCodeCache.setWritable(true, false)
            defaultCodeCache.setExecutable(true, false)
        } catch (e: Throwable) { }
    }

    private fun configureGraphicsEnvironment() {
        // In containerized and virtualized emulator environments, silence Mesa debug logging
        // and direct Mesa to software rendering to prevent rendernode ENOENT errors when /dev/dri is absent.
        try {
            Os.setenv("MESA_DEBUG", "silent", true)
            Os.setenv("MESA_LOG_FILE", "/dev/null", true)

            val hasDri = try {
                val driDir = File("/dev/dri")
                driDir.exists() && (driDir.listFiles()?.isNotEmpty() == true)
            } catch (e: Throwable) {
                false
            }

            if (!hasDri || DeviceUtils.isEmulator) {
                Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
                Os.setenv("GALLIUM_DRIVER", "llvmpipe", true)
                Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "swrast", true)
            }
        } catch (e: Throwable) { }
    }
}
