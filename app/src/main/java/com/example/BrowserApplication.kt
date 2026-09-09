package com.example

import android.app.Application
import android.system.Os
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

        // Clean up any stray directories accidentally placed inside HTTP Cache from prior sessions
        // so Chromium's SimpleCache index reconstruction will succeed without errors.
        try {
            val strayCodeCache = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            if (strayCodeCache.exists()) {
                strayCodeCache.deleteRecursively()
            }
        } catch (e: Throwable) { }
    }

    private fun configureGraphicsEnvironment() {
        // In containerized and virtualized emulator environments without hardware DRI rendernodes (/dev/dri/renderD128),
        // silence Mesa debug logging and instruct the loader to use software rasterization.
        try {
            Os.setenv("MESA_DEBUG", "silent", true)
            Os.setenv("MESA_LOG_FILE", "/dev/null", true)
            Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
            Os.setenv("GALLIUM_DRIVER", "llvmpipe", true)
        } catch (e: Throwable) { }
    }
}
