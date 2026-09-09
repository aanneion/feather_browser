package com.example.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
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
import androidx.compose.ui.unit.dp
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
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.HapticFeedbackConstants
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewCompat
import com.example.browser.ContextMenuData
import com.example.browser.ContextMenuType

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

class PersistentWebView(context: Context) : WebView(context) {
    var allowBackgroundPlayback: Boolean = true
    var onScrollChangedListener: ((deltaY: Int, scrollY: Int) -> Unit)? = null

    private var touchStartY = 0f
    private var lastTouchY = 0f
    private var isTouchDragging = false

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                touchStartY = event.rawY
                lastTouchY = event.rawY
                isTouchDragging = false
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val currentY = event.rawY
                val deltaY = (lastTouchY - currentY).toInt()
                if (kotlin.math.abs(currentY - touchStartY) > 20) {
                    isTouchDragging = true
                }
                if (isTouchDragging && kotlin.math.abs(deltaY) >= 10) {
                    onScrollChangedListener?.invoke(deltaY, scrollY)
                    lastTouchY = currentY
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                isTouchDragging = false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (!isTouchDragging) {
            val deltaY = t - oldt
            if (deltaY != 0) {
                onScrollChangedListener?.invoke(deltaY, t)
            }
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
    isActive: Boolean = true,
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
        FingerprintPreset.fromString(currentProfile?.fingerprintPreset)
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
                is WebViewAction.ExtractReaderContent -> {
                    val script = """
                        (function() {
                            try {
                                var title = document.querySelector('h1')?.innerText || document.title || '';
                                var byline = document.querySelector('.byline, .author, [rel="author"], [itemprop="author"]')?.innerText || '';
                                var article = document.querySelector('article, main, [role="main"], .post-content, .article-body, .entry-content');
                                var text = '';
                                if (article) {
                                    text = article.innerText;
                                } else {
                                    var paragraphs = Array.from(document.querySelectorAll('p'))
                                        .map(function(p) { return p.innerText.trim(); })
                                        .filter(function(t) { return t.length > 25; });
                                    text = paragraphs.join('\n\n');
                                }
                                if (!text || text.length < 50) {
                                    text = document.body ? document.body.innerText : '';
                                }
                                return JSON.stringify({
                                    title: title.trim(),
                                    byline: byline.trim(),
                                    text: text.substring(0, 50000).trim()
                                });
                            } catch (e) {
                                return JSON.stringify({ title: document.title, byline: '', text: '' });
                            }
                        })();
                    """.trimIndent()
                    webView.evaluateJavascript(script) { result ->
                        try {
                            val parsedJson = if (result != null && result.startsWith("\"") && result.endsWith("\"")) {
                                org.json.JSONObject(org.json.JSONTokener(result).nextValue().toString())
                            } else if (result != null && result != "null") {
                                org.json.JSONObject(result)
                            } else null

                            if (parsedJson != null) {
                                val currentTitle = webView.title ?: ""
                                val title = parsedJson.optString("title", currentTitle)
                                val byline = parsedJson.optString("byline", "")
                                val text = parsedJson.optString("text", "")
                                val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                                val minutes = (words / 200).coerceAtLeast(1)
                                val article = com.example.browser.ReaderArticle(
                                    title = if (title.isNotBlank()) title else currentTitle,
                                    byline = byline,
                                    domain = com.example.browser.UrlUtils.extractDomain(webView.url ?: ""),
                                    contentText = text,
                                    wordCount = words,
                                    readingTimeMinutes = minutes
                                )
                                action.callback(article)
                            } else {
                                action.callback(null)
                            }
                        } catch (e: Exception) {
                            action.callback(null)
                        }
                    }
                }
            }
        }
    }

    // Clean up WebView memory, media players, and textures when tab is closed
    LaunchedEffect(effectiveDark) {
        val webView = webViewRef ?: return@LaunchedEffect
        try {
            val themeScript = FingerprintScriptGenerator.generateThemeScript(effectiveDark)
            webView.evaluateJavascript(themeScript, null)
        } catch (e: Exception) { }
    }

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
                    webViewClient = object : WebViewClient() {
                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = true
                    }
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
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(if (isActive) Modifier else Modifier.size(0.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            AndroidView<SwipeRefreshLayout>(
                factory = { ctx ->
                    val swipeRefresh = SwipeRefreshLayout(ctx).apply {
                        isNestedScrollingEnabled = true
                        visibility = if (isActive) View.VISIBLE else View.GONE
                        isEnabled = (customVideoView == null) && isActive
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val primaryColor = if (effectiveDark) android.graphics.Color.parseColor("#80D8FF") else android.graphics.Color.parseColor("#00668B")
                        val progressBgColor = if (effectiveDark) android.graphics.Color.parseColor("#2C2C2C") else android.graphics.Color.WHITE
                        setColorSchemeColors(primaryColor)
                        setProgressBackgroundColorSchemeColor(progressBgColor)
                    }

                    // Wrap the Activity context directly so WebView retains a valid WindowManager token for HTML <select> dropdowns and dialogs
                    val activity = ctx.findActivity() ?: ctx
                    val themedContext = ContextThemeWrapper(
                        activity,
                        if (effectiveDark) android.R.style.Theme_DeviceDefault else android.R.style.Theme_DeviceDefault_Light
                    )

                    val webView = PersistentWebView(themedContext).apply {
                        allowBackgroundPlayback = enableBackgroundPlay
                        isFocusable = true
                        isFocusableInTouchMode = true
                        onScrollChangedListener = { deltaY, scrollY ->
                            viewModel.onWebScroll(deltaY, scrollY)
                            swipeRefresh.isEnabled = (scrollY <= 0 && !canScrollVertically(-1))
                        }
                        addJavascriptInterface(FeatherMediaBridge(ctx.applicationContext, tabId), "FeatherMediaBridge")
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Set opaque background matching current theme to avoid transparent surface compositor overhead
                        val initialBgColor = if (effectiveDark) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE
                        setBackgroundColor(initialBgColor)

                        // High refresh rate (90Hz/120Hz) nested scrolling optimization
                        isNestedScrollingEnabled = true
                        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = false

                        // Touch listener to gain focus away from address bar on tap
                        setOnTouchListener { v, event ->
                            if (event.action == MotionEvent.ACTION_DOWN) {
                                v.requestFocus()
                            }
                            false
                        }

                        // Long-press context menu for links, images, and image-links
                        isLongClickable = true
                        setOnLongClickListener { v ->
                            val hitTest = hitTestResult ?: return@setOnLongClickListener false
                            val type = hitTest.type
                            val extra = hitTest.extra

                            when (type) {
                                WebView.HitTestResult.SRC_ANCHOR_TYPE,
                                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                                WebView.HitTestResult.IMAGE_TYPE -> {
                                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    val msg = Message.obtain()
                                    msg.target = object : Handler(Looper.getMainLooper()) {
                                        override fun handleMessage(m: Message) {
                                            val linkUrl = m.data.getString("url")?.takeIf { it.isNotBlank() } ?: (if (type != WebView.HitTestResult.IMAGE_TYPE) extra else null)
                                            val title = m.data.getString("title")?.takeIf { it.isNotBlank() }
                                            val src = m.data.getString("src")?.takeIf { it.isNotBlank() } ?: (if (type == WebView.HitTestResult.IMAGE_TYPE) extra else null)

                                            val menuType = when {
                                                type == WebView.HitTestResult.IMAGE_TYPE -> ContextMenuType.IMAGE
                                                type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> ContextMenuType.IMAGE_LINK
                                                else -> ContextMenuType.LINK
                                            }

                                            viewModel.showContextMenu(
                                                ContextMenuData(
                                                    url = linkUrl,
                                                    title = title,
                                                    imageUrl = src,
                                                    type = menuType
                                                )
                                            )
                                        }
                                    }
                                    requestFocusNodeHref(msg)
                                    true
                                }
                                else -> false
                            }
                        }

                        // Default user agent capture - sanitize Version/4.0 and wv tags for Google sign-in compatibility
                        if (defaultUserAgent == null) {
                            val raw = settings.userAgentString
                            defaultUserAgent = raw.replace("; wv", "")
                                .replace("; wv;", ";")
                                .replace("Version/4.0 ", "")
                        }

                        // Configure Settings
                        settings.apply {
                            javaScriptEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            allowFileAccess = false
                            allowContentAccess = false
                            setSupportMultipleWindows(true)
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

                        // Find in page listener: update match index and count on every search & navigation step
                        setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                            viewModel.onFindMatchResult(activeMatchOrdinal, numberOfMatches)
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
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                super.onPageCommitVisible(view, url)
                                try {
                                    val themeScript = FingerprintScriptGenerator.generateThemeScript(effectiveDark)
                                    view?.evaluateJavascript(themeScript, null)
                                } catch (e: Exception) { }

                                if (activePreset != FingerprintPreset.DEFAULT) {
                                    try {
                                        val script = FingerprintScriptGenerator.generateInjectionScript(activePreset)
                                        view?.evaluateJavascript(script, null)
                                    } catch (e: Exception) { }
                                }
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                url?.let {
                                    viewModel.onUrlChanged(tabId, it)
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

                                // Enforce theme styling on page finish
                                try {
                                    val themeScript = FingerprintScriptGenerator.generateThemeScript(effectiveDark)
                                    view?.evaluateJavascript(themeScript, null)
                                } catch (e: Exception) { }

                                // Inject Background Audio/Video playback script only on media platforms (YouTube, SoundCloud, Vimeo, etc.)
                                val targetUrl = url ?: view?.url
                                val isMediaSite = targetUrl?.let { u ->
                                    val lower = u.lowercase()
                                    lower.contains("youtube.com") || lower.contains("youtu.be") ||
                                    lower.contains("soundcloud.com") || lower.contains("spotify.com") ||
                                    lower.contains("vimeo.com") || lower.contains("twitch.tv") ||
                                    lower.contains("dailymotion.com")
                                } ?: false

                                if (enableBackgroundPlay && isMediaSite) {
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
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                val popupWebView = WebView(view?.context ?: ctx).apply {
                                    webViewClient = object : WebViewClient() {
                                        override fun onRenderProcessGone(v: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                            try { v?.destroy() } catch (e: Exception) { }
                                            return true
                                        }

                                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                            val targetUrl = request?.url?.toString() ?: return false
                                            viewModel.openLinkInNewTab(targetUrl, openInBackground = false)
                                            try { v?.destroy() } catch (e: Exception) { }
                                            return true
                                        }
                                        @Deprecated("Deprecated in Java")
                                        override fun shouldOverrideUrlLoading(v: WebView?, targetUrl: String?): Boolean {
                                            if (!targetUrl.isNullOrBlank()) {
                                                viewModel.openLinkInNewTab(targetUrl, openInBackground = false)
                                            }
                                            try { v?.destroy() } catch (e: Exception) { }
                                            return true
                                        }
                                    }
                                }
                                transport.webView = popupWebView
                                resultMsg.sendToTarget()
                                return true
                            }

                            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                try {
                                    android.app.AlertDialog.Builder(ctx)
                                        .setMessage(message ?: "")
                                        .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                                        .setOnCancelListener { result?.cancel() }
                                        .show()
                                } catch (e: Exception) {
                                    result?.confirm()
                                }
                                return true
                            }

                            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                try {
                                    android.app.AlertDialog.Builder(ctx)
                                        .setMessage(message ?: "")
                                        .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                                        .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                                        .setOnCancelListener { result?.cancel() }
                                        .show()
                                } catch (e: Exception) {
                                    result?.cancel()
                                }
                                return true
                            }

                            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                                try {
                                    val input = android.widget.EditText(ctx).apply {
                                        setText(defaultValue ?: "")
                                    }
                                    android.app.AlertDialog.Builder(ctx)
                                        .setTitle(message ?: "")
                                        .setView(input)
                                        .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm(input.text.toString()) }
                                        .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                                        .setOnCancelListener { result?.cancel() }
                                        .show()
                                } catch (e: Exception) {
                                    result?.cancel()
                                }
                                return true
                            }

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
                    swipeRefresh.visibility = if (isActive) View.VISIBLE else View.GONE
                    val webView = (0 until swipeRefresh.childCount)
                        .map { swipeRefresh.getChildAt(it) }
                        .filterIsInstance<PersistentWebView>()
                        .firstOrNull() ?: return@AndroidView

                    webViewRef = webView
                    MediaSessionManager.registerWebView(tabId, webView)
                    if (webView is PersistentWebView) {
                        webView.allowBackgroundPlayback = enableBackgroundPlay
                    }

                    // Pull-to-refresh enabled unless custom video view is active, and only for active tab
                    swipeRefresh.isEnabled = (customVideoView == null) && isActive

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
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                        WebSettingsCompat.setForceDarkStrategy(
                            webView.settings,
                            if (effectiveDark) {
                                WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                            } else {
                                WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
                            }
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        webView.settings.isAlgorithmicDarkeningAllowed = effectiveDark
                    } else if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, effectiveDark)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Fullscreen Video overlay
            if (customVideoView != null) {
                AndroidView(
                    factory = {
                        customVideoView!!
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black)
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
