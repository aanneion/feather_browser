package com.example.ui.components

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.browser.BrowserViewModel
import com.example.browser.FingerprintPreset
import com.example.browser.WebViewAction
import com.example.data.model.BrowserProfile
import com.example.privacy.ContentBlocker
import com.example.privacy.FingerprintScriptGenerator
import com.example.privacy.YouTubeAdBlocker
import com.example.media.MediaControlAction
import com.example.media.MediaSessionManager
import kotlinx.coroutines.flow.SharedFlow
import android.content.Context
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewCompat

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

class PersistentWebView(context: Context) : WebView(context) {
    var allowBackgroundPlayback: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        try {
            // When allowBackgroundPlayback is enabled and view is attached, report View.VISIBLE
            // to keep HTML5 audio/media engine playing in background without breaking detach lifecycle
            val effectiveVisibility = if (allowBackgroundPlayback && isAttachedToWindow) View.VISIBLE else visibility
            super.onWindowVisibilityChanged(effectiveVisibility)
        } catch (e: Throwable) {
            // Guard against Chromium native compositor edge cases during surface attachment
        }
    }

    override fun onPause() {
        if (!allowBackgroundPlayback) {
            try {
                super.onPause()
            } catch (e: Throwable) { }
        }
    }

    override fun onResume() {
        try {
            super.onResume()
        } catch (e: Throwable) { }
    }
}

class FeatherMediaBridge(
    private val context: Context,
    private val tabId: String
) {
    @JavascriptInterface
    fun updateMetadata(title: String, artist: String, album: String, artworkUrl: String) {
        try {
            MediaSessionManager.updateMetadata(context, tabId, title, artist, album, artworkUrl)
        } catch (e: Throwable) { }
    }

    @JavascriptInterface
    fun updatePlaybackState(isPlaying: Boolean) {
        try {
            MediaSessionManager.updatePlaybackState(context, tabId, isPlaying)
        } catch (e: Throwable) { }
    }

    @JavascriptInterface
    fun onMediaPlaying(title: String, artist: String) {
        try {
            MediaSessionManager.updateMetadata(context, tabId, title, artist)
            MediaSessionManager.updatePlaybackState(context, tabId, true)
        } catch (e: Throwable) { }
    }

    @JavascriptInterface
    fun onMediaPaused() {
        try {
            MediaSessionManager.updatePlaybackState(context, tabId, false)
        } catch (e: Throwable) { }
    }

    @JavascriptInterface
    fun onMediaEnded() {
        try {
            MediaSessionManager.onMediaEnded(context, tabId)
        } catch (e: Throwable) { }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WebViewContainer(
    tabId: String,
    initialUrl: String,
    isDesktopMode: Boolean,
    isAdBlockEnabled: Boolean,
    whitelistedDomains: Set<String>,
    blockThirdPartyCookies: Boolean,
    enableWebDarkMode: Boolean,
    enableBackgroundPlay: Boolean,
    isDarkTheme: Boolean,
    currentProfile: BrowserProfile?,
    viewModel: BrowserViewModel,
    actions: SharedFlow<WebViewAction>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var swipeRefreshRef by remember { mutableStateOf<SwipeRefreshLayout?>(null) }
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val effectiveDark = enableWebDarkMode || isDarkTheme

    val activePreset = remember(currentProfile?.fingerprintPreset) {
        try {
            FingerprintPreset.valueOf(currentProfile?.fingerprintPreset ?: "DEFAULT")
        } catch (e: Exception) {
            FingerprintPreset.DEFAULT
        }
    }

    val activeSearchEngine = viewModel.searchEngine.collectAsState().value

    var renderCrashCount by remember(tabId) { mutableStateOf(0) }

    // Handle incoming actions from ViewModel
    LaunchedEffect(tabId, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        actions.collect { action ->
            if (action.targetTabId != null && action.targetTabId != tabId) {
                return@collect
            }
            when (action) {
                is WebViewAction.LoadUrl -> {
                    webView.loadUrl(action.url)
                }
                is WebViewAction.Reload -> {
                    webView.reload()
                }
                is WebViewAction.StopLoading -> {
                    webView.stopLoading()
                    swipeRefreshRef?.isRefreshing = false
                }
                is WebViewAction.GoBack -> {
                    if (webView.canGoBack()) webView.goBack()
                }
                is WebViewAction.GoForward -> {
                    if (webView.canGoForward()) webView.goForward()
                }
                is WebViewAction.SetDesktopMode -> {
                    val ua = if (action.enabled) {
                        DESKTOP_USER_AGENT
                    } else if (activePreset.userAgent.isNotBlank()) {
                        activePreset.userAgent
                    } else {
                        defaultUserAgent
                    }
                    webView.settings.userAgentString = ua
                    webView.reload()
                }
                is WebViewAction.FindAllAsync -> {
                    if (action.query.isNotBlank()) {
                        webView.findAllAsync(action.query)
                    } else {
                        webView.clearMatches()
                    }
                }
                is WebViewAction.FindNext -> {
                    webView.findNext(action.forward)
                }
                is WebViewAction.ClearFindMatches -> {
                    webView.clearMatches()
                }
            }
        }
    }

    LaunchedEffect(tabId) {
        MediaSessionManager.controlActions.collect { action ->
            val playingTab = MediaSessionManager.activeMediaTabId.value
            val isTargetTab = (playingTab == tabId) || (playingTab == null && viewModel.activeTabId.value == tabId)
            if (isTargetTab) {
                when (action) {
                    MediaControlAction.PLAY -> {
                        webViewRef?.evaluateJavascript(
                            "if (window.__feather_media_play) window.__feather_media_play(); else document.querySelector('video, audio')?.play();",
                            null
                        )
                    }
                    MediaControlAction.PAUSE -> {
                        webViewRef?.evaluateJavascript(
                            "if (window.__feather_media_pause) window.__feather_media_pause(); else document.querySelector('video, audio')?.pause();",
                            null
                        )
                    }
                    MediaControlAction.TOGGLE_PLAY_PAUSE -> {
                        val isPlaying = MediaSessionManager.isPlaying.value
                        val script = if (isPlaying) {
                            "if (window.__feather_media_pause) window.__feather_media_pause(); else document.querySelector('video, audio')?.pause();"
                        } else {
                            "if (window.__feather_media_play) window.__feather_media_play(); else document.querySelector('video, audio')?.play();"
                        }
                        webViewRef?.evaluateJavascript(script, null)
                    }
                    MediaControlAction.NEXT -> {
                        webViewRef?.evaluateJavascript(
                            "if (window.__feather_media_next) window.__feather_media_next();",
                            null
                        )
                    }
                    MediaControlAction.PREVIOUS -> {
                        webViewRef?.evaluateJavascript(
                            "if (window.__feather_media_prev) window.__feather_media_prev();",
                            null
                        )
                    }
                    MediaControlAction.STOP -> {
                        webViewRef?.evaluateJavascript(
                            "if (window.__feather_media_pause) window.__feather_media_pause(); else document.querySelector('video, audio')?.pause();",
                            null
                        )
                    }
                }
            }
        }
    }

    // Clean up WebView memory, media players, and textures when tab is closed
    DisposableEffect(tabId) {
        onDispose {
            try {
                if (MediaSessionManager.activeMediaTabId.value == tabId) {
                    MediaSessionManager.onMediaEnded(context.applicationContext, tabId)
                }
                MediaSessionManager.unregisterWebView(tabId)
                swipeRefreshRef?.removeAllViews()
                swipeRefreshRef = null
                webViewRef?.apply {
                    if (this is PersistentWebView) {
                        this.allowBackgroundPlayback = false
                    }
                    stopLoading()
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    clearHistory()
                    (parent as? ViewGroup)?.removeView(this)
                    destroy()
                }
                webViewRef = null
            } catch (e: Throwable) { }
        }
    }

    key(tabId, renderCrashCount) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AndroidView<SwipeRefreshLayout>(
                factory = { ctx ->
                    val uiMode = if (effectiveDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    val config = Configuration(ctx.resources.configuration).apply {
                        this.uiMode = (this.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or uiMode
                    }
                    val themedContext = ctx.createConfigurationContext(config)

                    val swipeRefresh = SwipeRefreshLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val primaryColor = if (effectiveDark) android.graphics.Color.parseColor("#80D8FF") else android.graphics.Color.parseColor("#00668B")
                        val progressBgColor = if (effectiveDark) android.graphics.Color.parseColor("#2C2C2C") else android.graphics.Color.WHITE
                        setColorSchemeColors(primaryColor)
                        setProgressBackgroundColorSchemeColor(progressBgColor)
                    }

                    val webView = PersistentWebView(themedContext).apply {
                        allowBackgroundPlayback = enableBackgroundPlay
                        addJavascriptInterface(FeatherMediaBridge(themedContext.applicationContext, tabId), "FeatherMediaBridge")
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Set opaque background matching current theme to avoid transparent surface compositor overhead
                        val initialBgColor = if (effectiveDark) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE
                        setBackgroundColor(initialBgColor)

                        // In virtualized environments or fallback crashes, ensure rendering stability
                        if (renderCrashCount > 0) {
                            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        }

                        // Touch listener to gain focus away from address bar on tap
                        setOnTouchListener { v, event ->
                            if (event.action == MotionEvent.ACTION_DOWN) {
                                v.requestFocus()
                            }
                            false
                        }

                        // Default user agent capture
                        if (defaultUserAgent == null) {
                            defaultUserAgent = settings.userAgentString
                        }

                        // Configure Settings
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            allowFileAccess = false
                            allowContentAccess = false
                            setSupportMultipleWindows(false)
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = WebSettings.LOAD_DEFAULT

                            // Profile Fingerprint & User-Agent Configuration
                            if (isDesktopMode) {
                                userAgentString = DESKTOP_USER_AGENT
                            } else if (activePreset.userAgent.isNotBlank()) {
                                userAgentString = activePreset.userAgent
                            } else {
                                userAgentString = defaultUserAgent
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = true
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            }
                        }

                        // Cookie Policy
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cookieManager.setAcceptThirdPartyCookies(this, !blockThirdPartyCookies)
                        }

                        // Dark Mode for Web Content (aligned with active browser theme & force-dark setting)
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                            WebSettingsCompat.setForceDark(
                                settings,
                                if (effectiveDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                            )
                        }
                        if (effectiveDark && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                            WebSettingsCompat.setForceDarkStrategy(
                                settings,
                                WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            settings.isAlgorithmicDarkeningAllowed = effectiveDark
                        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, effectiveDark)
                        }

                        // Find in page listener
                        setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                            if (isDoneCounting) {
                                viewModel.onFindMatchResult(activeMatchOrdinal, numberOfMatches)
                            }
                        }

                        // Download Listener
                        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                            viewModel.handleDownloadRequest(url, userAgent, contentDisposition, mimetype, contentLength)
                        }

                        // Custom WebViewClient with Render Process Crash Protection and Custom Error Page
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val uri = request?.url ?: return null
                                val host = uri.host ?: ""
                                val isWhitelisted = whitelistedDomains.contains(host) || whitelistedDomains.contains(host.removePrefix("www."))

                                if (ContentBlocker.shouldBlock(uri, isAdBlockEnabled, isWhitelisted)) {
                                    ContentBlocker.recordBlockForTab(tabId)
                                    return ContentBlocker.createEmptyResponse(uri)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let {
                                    viewModel.onPageStarted(tabId, it)
                                }
                                viewModel.onNavigationStateChanged(
                                    tabId = tabId,
                                    canGoBack = view?.canGoBack() ?: false,
                                    canGoForward = view?.canGoForward() ?: false
                                )

                                // Early injection of Background Audio/Video playback script
                                if (enableBackgroundPlay) {
                                    try {
                                        val bgScript = FingerprintScriptGenerator.generateBackgroundPlayScript()
                                        view?.evaluateJavascript(bgScript, null)
                                    } catch (e: Exception) { }
                                }

                                // Inject YouTube AdBlocker script early
                                if (isAdBlockEnabled && YouTubeAdBlocker.isYouTube(url ?: view?.url)) {
                                    try {
                                        view?.evaluateJavascript(YouTubeAdBlocker.getYouTubeAdBlockScript(), null)
                                    } catch (e: Exception) { }
                                }

                                // Enforce CSS color-scheme matching browser theme mode
                                val colorScheme = if (effectiveDark) "dark" else "light"
                                try {
                                    view?.evaluateJavascript("try { document.documentElement.style.colorScheme = '$colorScheme'; } catch(e){}", null)
                                } catch (e: Exception) { }
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                super.onPageCommitVisible(view, url)
                                if (enableBackgroundPlay) {
                                    try {
                                        val bgScript = FingerprintScriptGenerator.generateBackgroundPlayScript()
                                        view?.evaluateJavascript(bgScript, null)
                                    } catch (e: Exception) { }
                                }
                                if (isAdBlockEnabled && YouTubeAdBlocker.isYouTube(url ?: view?.url)) {
                                    try {
                                        view?.evaluateJavascript(YouTubeAdBlocker.getYouTubeAdBlockScript(), null)
                                    } catch (e: Exception) { }
                                }
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                url?.let {
                                    viewModel.onUrlChanged(tabId, it)
                                }
                                if (enableBackgroundPlay) {
                                    try {
                                        val bgScript = FingerprintScriptGenerator.generateBackgroundPlayScript()
                                        view?.evaluateJavascript(bgScript, null)
                                    } catch (e: Exception) { }
                                }
                                if (isAdBlockEnabled && YouTubeAdBlocker.isYouTube(url ?: view?.url)) {
                                    try {
                                        view?.evaluateJavascript(YouTubeAdBlocker.getYouTubeAdBlockScript(), null)
                                    } catch (e: Exception) { }
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                swipeRefresh.isRefreshing = false
                                url?.let {
                                    viewModel.onPageFinished(tabId, it)
                                }
                                view?.title?.let {
                                    viewModel.onTitleChanged(tabId, it)
                                }
                                viewModel.onNavigationStateChanged(
                                    tabId = tabId,
                                    canGoBack = view?.canGoBack() ?: false,
                                    canGoForward = view?.canGoForward() ?: false
                                )

                                // Safely inject anti-fingerprinting script on page finish
                                if (activePreset != FingerprintPreset.DEFAULT) {
                                    try {
                                        val script = FingerprintScriptGenerator.generateInjectionScript(activePreset)
                                        view?.evaluateJavascript(script, null)
                                    } catch (e: Exception) { }
                                }

                                // Inject Background Audio/Video playback script (YouTube, SoundCloud, etc.)
                                if (enableBackgroundPlay) {
                                    try {
                                        val bgScript = FingerprintScriptGenerator.generateBackgroundPlayScript()
                                        view?.evaluateJavascript(bgScript, null)
                                    } catch (e: Exception) { }
                                }

                                // Inject YouTube AdBlocker script
                                if (isAdBlockEnabled && YouTubeAdBlocker.isYouTube(url ?: view?.url)) {
                                    try {
                                        view?.evaluateJavascript(YouTubeAdBlocker.getYouTubeAdBlockScript(), null)
                                    } catch (e: Exception) { }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    swipeRefresh.isRefreshing = false
                                    val failingUrl = request.url.toString()
                                    val errCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        error?.errorCode ?: -1
                                    } else {
                                        -1
                                    }
                                    val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        error?.description?.toString() ?: "ERR_CONNECTION_FAILED"
                                    } else {
                                        "ERR_CONNECTION_FAILED"
                                    }

                                    val customHtml = ErrorPageHtml.buildErrorPage(
                                        failingUrl = failingUrl,
                                        errorCode = errCode,
                                        description = description,
                                        isDarkTheme = effectiveDark,
                                        searchQueryUrl = activeSearchEngine.searchUrl
                                    )

                                    view?.loadDataWithBaseURL(
                                        failingUrl,
                                        customHtml,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                    viewModel.onPageLoadError(tabId)
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                swipeRefresh.isRefreshing = false
                                try {
                                    (view?.parent as? ViewGroup)?.removeView(view)
                                    view?.destroy()
                                } catch (e: Exception) { }
                                webViewRef = null
                                renderCrashCount++
                                return true
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val uri = request?.url ?: return false
                                return handleUrlOverride(view, uri.toString())
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                if (url.isNullOrBlank()) return false
                                return handleUrlOverride(view, url)
                            }

                            private fun handleUrlOverride(view: WebView?, url: String): Boolean {
                                if (url.startsWith("http://", ignoreCase = true) || 
                                    url.startsWith("https://", ignoreCase = true) ||
                                    url.startsWith("about:", ignoreCase = true) ||
                                    url.startsWith("javascript:", ignoreCase = true) ||
                                    url.startsWith("data:", ignoreCase = true) ||
                                    url.startsWith("blob:", ignoreCase = true)) {
                                    return false
                                }

                                // Handle intent:// URI schemes (YouTube, Play Store, Google Maps, etc.)
                                if (url.startsWith("intent://", ignoreCase = true)) {
                                    try {
                                        val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        return true
                                    } catch (e: Exception) {
                                        try {
                                            val parsedIntent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                                            val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                                            if (!fallbackUrl.isNullOrBlank()) {
                                                view?.loadUrl(fallbackUrl)
                                                return true
                                            }
                                        } catch (ex: Exception) { }
                                        return true
                                    }
                                }

                                // Handle tel:, mailto:, sms:, market:, etc.
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                    return true
                                } catch (e: Exception) {
                                    return true
                                }
                            }
                        }

                        // Custom WebChromeClient
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                if (newProgress >= 100) {
                                    swipeRefresh.isRefreshing = false
                                }
                                viewModel.onProgressChanged(tabId, newProgress)
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                title?.let { viewModel.onTitleChanged(tabId, it) }
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                swipeRefresh.isEnabled = false
                                customVideoView = view
                                customVideoCallback = callback
                            }

                            override fun onHideCustomView() {
                                swipeRefresh.isEnabled = true
                                customVideoView = null
                                customVideoCallback?.onCustomViewHidden()
                                customVideoCallback = null
                            }
                        }

                        webViewRef = this
                        MediaSessionManager.registerWebView(tabId, this)
                        if (initialUrl.isNotBlank() && initialUrl != "about:blank") {
                            loadUrl(initialUrl)
                        }
                    }

                    swipeRefresh.addView(
                        webView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )

                    // Pull-to-refresh: only activate when child WebView is scrolled to the very top
                    swipeRefresh.setOnChildScrollUpCallback { _, _ ->
                        webView.scrollY > 0 || webView.canScrollVertically(-1)
                    }
                    swipeRefresh.setOnRefreshListener {
                        webView.reload()
                    }

                    swipeRefreshRef = swipeRefresh
                    swipeRefresh
                },
                update = { swipeRefresh ->
                    swipeRefreshRef = swipeRefresh
                    val webView = (0 until swipeRefresh.childCount)
                        .map { swipeRefresh.getChildAt(it) }
                        .filterIsInstance<PersistentWebView>()
                        .firstOrNull() ?: return@AndroidView

                    webViewRef = webView
                    MediaSessionManager.registerWebView(tabId, webView)
                    if (webView is PersistentWebView) {
                        webView.allowBackgroundPlayback = enableBackgroundPlay
                    }

                    // Pull-to-refresh enabled unless custom video view is active
                    swipeRefresh.isEnabled = (customVideoView == null)

                    val primaryColor = if (effectiveDark) android.graphics.Color.parseColor("#80D8FF") else android.graphics.Color.parseColor("#00668B")
                    val progressBgColor = if (effectiveDark) android.graphics.Color.parseColor("#2C2C2C") else android.graphics.Color.WHITE
                    swipeRefresh.setColorSchemeColors(primaryColor)
                    swipeRefresh.setProgressBackgroundColorSchemeColor(progressBgColor)

                    // Keep web dark mode & theme styling synchronized dynamically
                    val targetBg = if (effectiveDark) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE
                    webView.setBackgroundColor(targetBg)
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                        WebSettingsCompat.setForceDark(
                            webView.settings,
                            if (effectiveDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                        )
                    }
                    if (effectiveDark && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                        WebSettingsCompat.setForceDarkStrategy(
                            webView.settings,
                            WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        webView.settings.isAlgorithmicDarkeningAllowed = effectiveDark
                    } else if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, effectiveDark)
                    }

                    if (initialUrl.isNotBlank() && initialUrl != "about:blank") {
                        val cur = webView.url ?: ""
                        if (cur.isEmpty() || cur == "about:blank") {
                            webView.loadUrl(initialUrl)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Fullscreen Video overlay
            if (customVideoView != null) {
                AndroidView(
                    factory = { customVideoView!! },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black)
                )
            }
        }
    }
}
