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

        // Pre-create Chromium cache directories so SimpleFileEnumerator and SimpleIndexFile
        // will find them and open them cleanly without logging opendir ENOENT errors.
        try {
            val cache = cacheDir
            val dirs = listOf(
                File(cache, "WebView"),
                File(cache, "WebView/Default"),
                File(cache, "WebView/Default/HTTP Cache"),
                File(cache, "WebView/Default/HTTP Cache/Code Cache"),
                File(cache, "WebView/Default/HTTP Cache/Code Cache/js"),
                File(cache, "WebView/Default/HTTP Cache/Code Cache/wasm")
            )
            for (dir in dirs) {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                dir.setReadable(true, false)
                dir.setWritable(true, false)
                dir.setExecutable(true, false)
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
