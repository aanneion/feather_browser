package com.example

import android.app.Application
import android.system.Os
import java.io.File

class BrowserApplication : Application() {
    companion object {
        init {
            try {
                Os.setenv("MESA_LOG_LEVEL", "none", true)
                Os.setenv("MESA_LOG_FILE", "/dev/null", true)
                Os.setenv("LIBGL_DRI3_DISABLE", "1", true)
            } catch (e: Throwable) { }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            Os.setenv("MESA_LOG_LEVEL", "none", true)
            Os.setenv("MESA_LOG_FILE", "/dev/null", true)
            Os.setenv("LIBGL_DRI3_DISABLE", "1", true)
        } catch (e: Throwable) { }

        // Clean up any corrupted Chromium HTTP Cache directory from previous runs
        try {
            val cache = cacheDir
            val httpCache = File(cache, "WebView/Default/HTTP Cache")
            if (httpCache.exists()) {
                val corruptCodeCache = File(httpCache, "Code Cache")
                if (corruptCodeCache.exists()) {
                    httpCache.deleteRecursively()
                }
            }
        } catch (e: Throwable) { }
    }
}
