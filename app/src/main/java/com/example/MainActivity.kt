package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.browser.BrowserViewModel
import com.example.media.MediaSessionManager
import com.example.ui.BrowserScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // No action needed; notification is posted only when media actively begins playing
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeWebViewDirectories()
        enableEdgeToEdge()
        enableHighRefreshRate()

        requestNotificationPermissionIfNeeded()
        handleIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val useMaterialYou by viewModel.useMaterialYou.collectAsStateWithLifecycle()
            val isPrivateMode by viewModel.isPrivateMode.collectAsStateWithLifecycle()

            MyApplicationTheme(
                themeMode = themeMode,
                useMaterialYou = useMaterialYou,
                isPrivateMode = isPrivateMode
            ) {
                BrowserScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun initializeWebViewDirectories() {
        try {
            val cacheDir = applicationContext.cacheDir
            val webViewCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache")
            val codeCacheJs = java.io.File(webViewCache, "Code Cache/js")
            val codeCacheWasm = java.io.File(webViewCache, "Code Cache/wasm")
            val indexDir = java.io.File(webViewCache, "index-dir")
            codeCacheJs.mkdirs()
            codeCacheWasm.mkdirs()
            indexDir.mkdirs()
        } catch (e: Throwable) {
            // Safe fallback
        }
    }

    private fun enableHighRefreshRate() {
        if (com.example.browser.DeviceUtils.isEmulator) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val currentDisplay = display
                val modes = currentDisplay?.supportedModes
                val maxRefreshMode = modes?.maxByOrNull { it.refreshRate }
                if (maxRefreshMode != null && maxRefreshMode.refreshRate > 60f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxRefreshMode.modeId
                    params.preferredRefreshRate = maxRefreshMode.refreshRate
                    window.attributes = params
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val currentDisplay = windowManager.defaultDisplay
                val modes = currentDisplay?.supportedModes
                val maxRefreshMode = modes?.maxByOrNull { it.refreshRate }
                if (maxRefreshMode != null && maxRefreshMode.refreshRate > 60f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxRefreshMode.modeId
                    params.preferredRefreshRate = maxRefreshMode.refreshRate
                    window.attributes = params
                }
            }
        } catch (e: Throwable) {
            // Safe fallback if display mode adjustment is restricted by system
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action
        val data = intent?.dataString
        if (action == Intent.ACTION_VIEW && !data.isNullOrBlank()) {
            viewModel.navigateTo(data)
        }
    }
}
