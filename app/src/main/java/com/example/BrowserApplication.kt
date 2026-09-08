package com.example

import android.app.Application
import android.system.Os
import java.io.File

class BrowserApplication : Application() {

    init {
        // In containerized and virtualized emulator environments without hardware DRI rendernodes (/dev/dri/renderD128),
        // instruct the Mesa graphics loader to use software rendering directly, avoiding "Failed to open rendernode" errors.
        try {
            val hasDri = File("/dev/dri").exists()
            if (!hasDri) {
                Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
            }
        } catch (e: Throwable) { }
    }

    override fun onCreate() {
        super.onCreate()

        // Ensure Chromium cache directories exist so simple_file_enumerator won't fail with ENOENT
        try {
            val cache = cacheDir
            val jsCodeCache = File(cache, "WebView/Default/HTTP Cache/Code Cache/js")
            val wasmCodeCache = File(cache, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!jsCodeCache.exists()) {
                jsCodeCache.mkdirs()
            }
            if (!wasmCodeCache.exists()) {
                wasmCodeCache.mkdirs()
            }
        } catch (e: Throwable) { }
    }
}
