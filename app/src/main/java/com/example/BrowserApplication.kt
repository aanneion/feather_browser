package com.example

import android.app.Application
import android.system.Os
import java.io.File

class BrowserApplication : Application() {
    override fun onCreate() {
        super.onCreate()

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
