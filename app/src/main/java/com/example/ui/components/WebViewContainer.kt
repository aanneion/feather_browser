package com.example.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import android.annotation.SuppressLint
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
import com.example.R
import com.example.browser.BrowserViewModel
import com.example.browser.FingerprintPreset
import com.example.browser.WebViewAction
import com.example.data.model.BrowserProfile
import com.example.privacy.ContentBlocker
import com.example.privacy.FingerprintScriptGenerator
import com.example.privacy.YouTubeAdBlocker
import com.example.media.MediaControlAction
import com.example.media.MediaSessionManager
import com.example.media.BrowserPlaybackState
import kotlinx.coroutines.flow.SharedFlow
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.HapticFeedbackConstants
import androidx.webkit.WebViewCompat
import com.example.browser.ContextMenuData
import com.example.browser.ContextMenuType
import com.example.browser.HttpsMode

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

private fun isMediaSite(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = try { Uri.parse(url).host?.lowercase() ?: "" } catch (e: Exception) { "" }
    return host.contains("youtube.com") || host.contains("youtu.be") ||
           host.contains("soundcloud.com") || host.contains("spotify.com") ||
           host.contains("twitch.tv") || host.contains("vimeo.com") ||
           host.contains("bandcamp.com") || host.contains("dailymotion.com") ||
           host.contains("mixcloud.com") || host.contains("audiomack.com") ||
           host.contains("deezer.com")
}

class PersistentWebView(context: Context) : WebView(context) {
    var allowBackgroundPlayback: Boolean = true
        set(value) {
            val wasDisabled = field
            field = value
            // When background playback gets disabled again (e.g. tab close / exit browser),
            // re-sync real visibility so Chromium can release compositor/GPU resources.
            if (wasDisabled && !value) restoreRealVisibility()
        }

    private var isReleased = false

    /**
     * Idempotent teardown for WebView recreation flows (browser theme flip / renderer crash
     * recreation / tab close). Several flows may race to destroy the same instance, e.g. the
     * AndroidView onRelease firing after the tab-close cleanup, so a second call is a no-op.
     */
    fun destroySafely() {
        if (isReleased) return
        isReleased = true
        try {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        } catch (e: Throwable) { }
    }

    var onScrollChangedListener: ((deltaY: Int, scrollY: Int) -> Unit)? = null
    var isAddressBarEditing: Boolean = false
    var onDismissAddressBar: (() -> Unit)? = null

    // Tracks the real (suppressed) visibility values while background playback fakes VISIBLE,
    // so we can replay them once suppression is turned off again.
    private var lastRealViewVisibility: Int? = null
    private var lastRealWindowVisibility: Int? = null

    fun restoreRealVisibility() {
        try {
            lastRealViewVisibility?.let { super.onVisibilityChanged(this, it) }
            lastRealWindowVisibility?.let { super.onWindowVisibilityChanged(it) }
        } catch (e: Throwable) { }
    }

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastTouchY = 0f
    private var isTouchDragging = false
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                lastTouchY = event.rawY
                isTouchDragging = false
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val currentY = event.rawY
                val deltaY = (lastTouchY - currentY).toInt()
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                if (kotlin.math.hypot(dx, dy) > touchSlop) {
                    isTouchDragging = true
                }
                if (isTouchDragging && kotlin.math.abs(deltaY) >= 10) {
                    onScrollChangedListener?.invoke(deltaY, scrollY)
                    lastTouchY = currentY
                }
            }
            android.view.MotionEvent.ACTION_UP -> {
                if (!isTouchDragging && isAddressBarEditing) {
                    checkAndDismissAddressBar(event.x, event.y)
                }
                isTouchDragging = false
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                isTouchDragging = false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun checkAndDismissAddressBar(x: Float, y: Float) {
        if (!isAddressBarEditing) return
        val hit = hitTestResult
        val hitType = hit?.type ?: HitTestResult.UNKNOWN_TYPE
        if (hitType == HitTestResult.SRC_ANCHOR_TYPE ||
            hitType == HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
            hitType == HitTestResult.PHONE_TYPE ||
            hitType == HitTestResult.GEO_TYPE ||
            hitType == HitTestResult.EMAIL_TYPE ||
            hitType == HitTestResult.EDIT_TEXT_TYPE) {
            post { onDismissAddressBar?.invoke() }
            return
        }

        val density = resources.displayMetrics.density
        val cssX = (x / density).toInt()
        val cssY = (y / density).toInt()
        val js = "(function(){" +
                "var el = document.elementFromPoint($cssX, $cssY);" +
                "if (!el) return 'non-interactive';" +
                "var interactive = el.closest('a, button, input, textarea, select, [role=\"button\"], [role=\"link\"], [contenteditable=\"true\"]');" +
                "return interactive ? 'interactive' : 'non-interactive';" +
                "})()"
        evaluateJavascript(js) {
            post { onDismissAddressBar?.invoke() }
        }
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

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        try {
            if (allowBackgroundPlayback) {
                lastRealViewVisibility = visibility
                super.onVisibilityChanged(changedView, View.VISIBLE)
            } else {
                lastRealViewVisibility = null
                super.onVisibilityChanged(changedView, visibility)
            }
        } catch (e: Throwable) { }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        try {
            if (allowBackgroundPlayback) {
                lastRealWindowVisibility = visibility
                super.onWindowVisibilityChanged(View.VISIBLE)
            } else {
                lastRealWindowVisibility = null
                super.onWindowVisibilityChanged(visibility)
            }
        } catch (e: Throwable) { }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (allowBackgroundPlayback && w <= 0 && h <= 0 && ow > 0 && oh > 0) {
            return
        }
        super.onSizeChanged(w, h, ow, oh)
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
    fun updatePlaybackState(stateStr: String) {
        try {
            val state = when (stateStr.trim().uppercase()) {
                "PLAYING", "TRUE" -> BrowserPlaybackState.PLAYING
                "BUFFERING" -> BrowserPlaybackState.BUFFERING
                "STOPPED", "ENDED" -> BrowserPlaybackState.ENDED
                else -> BrowserPlaybackState.PAUSED
            }
            MediaSessionManager.updatePlaybackState(context, tabId, state)
        } catch (e: Throwable) { }
    }

    @JavascriptInterface
    fun updateProgress(positionMs: Long, durationMs: Long) {
        try {
            MediaSessionManager.updateProgress(context, tabId, positionMs, durationMs)
        } catch (e: Throwable) { }
    }

    @JavascriptInterface
    fun updatePlaybackRate(rate: Double) {
        try {
            MediaSessionManager.updatePlaybackRate(context, tabId, rate.toFloat())
        } catch (e: Throwable) { }
    }

    @JavascriptInterface
    fun onMediaPlaying(title: String, artist: String) {
        try {
            MediaSessionManager.updateMetadata(context, tabId, title, artist)
            MediaSessionManager.updatePlaybackState(context, tabId, BrowserPlaybackState.PLAYING)
        } catch (e: Throwable) { }
    }

    @JavascriptInterface
    fun onMediaPaused() {
        try {
            MediaSessionManager.updatePlaybackState(context, tabId, BrowserPlaybackState.PAUSED)
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
    enableBackgroundPlay: Boolean,
    httpsMode: HttpsMode,
    isDarkTheme: Boolean,
    currentProfile: BrowserProfile?,
    viewModel: BrowserViewModel,
    actions: SharedFlow<WebViewAction>,
    isActive: Boolean = true,
    isAddressBarEditing: Boolean = false,
    onDismissAddressBar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var uploadMessageCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = uploadMessageCallback
        uploadMessageCallback = null
        if (result.resultCode == Activity.RESULT_OK) {
            val intentData = result.data
            val results: Array<Uri>? = when {
                intentData?.clipData != null -> {
                    val clip = intentData.clipData!!
                    Array(clip.itemCount) { clip.getItemAt(it).uri }
                }
                intentData?.data != null -> {
                    arrayOf(intentData.data!!)
                }
                else -> null
            }
            callback?.onReceiveValue(results)
        } else {
            callback?.onReceiveValue(null)
        }
    }

    val currentLauncher by rememberUpdatedState(fileChooserLauncher)

    val onShowFileChooserImpl = remember {
        { filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: WebChromeClient.FileChooserParams? ->
            uploadMessageCallback?.onReceiveValue(null)
            uploadMessageCallback = filePathCallback

            val contentIntent = try {
                fileChooserParams?.createIntent()?.apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    if (type.isNullOrBlank()) {
                        type = "*/*"
                    }
                } ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            } catch (e: Throwable) {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            }

            if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                contentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }

            val acceptTypes = fileChooserParams?.acceptTypes?.filter { it.isNotBlank() }
            if (!acceptTypes.isNullOrEmpty()) {
                if (acceptTypes.size == 1) {
                    contentIntent.type = acceptTypes[0]
                } else {
                    contentIntent.type = "*/*"
                    contentIntent.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.toTypedArray())
                }
            }

            val chooserTitle = fileChooserParams?.title?.ifBlank { "Select File" } ?: "Select File"
            val chooserIntent = Intent.createChooser(contentIntent, chooserTitle)

            try {
                currentLauncher.launch(chooserIntent)
                true
            } catch (e: Throwable) {
                uploadMessageCallback?.onReceiveValue(null)
                uploadMessageCallback = null
                false
            }
        }
    }

    // Web content always follows the active browser theme:
    // light browser -> websites render with prefers-color-scheme: light,
    // dark browser -> websites render with prefers-color-scheme: dark.
    // This is enforced via the ContextThemeWrapper used to create the WebView below.
    val effectiveDark = isDarkTheme

    val activePreset = remember(currentProfile?.fingerprintPreset) {
        FingerprintPreset.fromString(currentProfile?.fingerprintPreset)
    }

    val activeSearchEngine = viewModel.searchEngine.collectAsState().value
    val currentHttpsMode by rememberUpdatedState(httpsMode)

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
                    } else if (!currentProfile?.customUserAgent.isNullOrBlank()) {
                        currentProfile?.customUserAgent
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

    // A tab can keep its WebView alive while it is hidden. Re-sync navigation
    // controls when it becomes visible again instead of waiting for a new page
    // callback that may never arrive.
    LaunchedEffect(tabId, isActive, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        viewModel.onNavigationStateChanged(
            tabId = tabId,
            canGoBack = webView.canGoBack(),
            canGoForward = webView.canGoForward()
        )
        webView.url?.takeIf { it.isNotBlank() }?.let { viewModel.onUrlChanged(tabId, it) }
        webView.title?.takeIf { it.isNotBlank() }?.let { viewModel.onTitleChanged(tabId, it) }
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
                uploadMessageCallback?.onReceiveValue(null)
                uploadMessageCallback = null
                if (MediaSessionManager.activeMediaTabId.value == tabId) {
                    MediaSessionManager.onMediaEnded(context.applicationContext, tabId)
                }
                MediaSessionManager.unregisterWebView(tabId)
                webViewRef?.apply {
                    if (this is PersistentWebView) {
                        this.allowBackgroundPlayback = false
                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = true
                        }
                        webChromeClient = WebChromeClient()
                        clearHistory()
                        this.destroySafely()
                    }
                }
                webViewRef = null
            } catch (e: Throwable) { }
        }
    }

    // Key includes `effectiveDark`: `prefers-color-scheme` is resolved by Chromium once,
    // from the theme of the context the WebView is created with, so flipping the browser
    // theme must recreate the WebView (reloading the tab's current URL) to re-resolve it.
    key(tabId, renderCrashCount, effectiveDark) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AndroidView<PersistentWebView>(
                factory = { ctx ->
                    // Direct Activity reference so WebView retains a valid WindowManager token for HTML <select> dropdowns and dialogs
                    val activity = ctx.findActivity() ?: ctx

                    // Chromium derives `prefers-color-scheme` solely from the `android:isLightTheme`
                    // attribute of the theme on the context the WebView is created with — not from
                    // the system day/night setting and not from the Compose theme. Wrapping the
                    // activity context in a pinned theme (see themes.xml) makes websites follow the
                    // browser theme: light browser -> light sites, dark browser -> dark sites.
                    // https://developer.android.com/develop/ui/views/layout/webapps/dark-theme
                    val webThemeContext = ContextThemeWrapper(
                        activity,
                        if (effectiveDark) R.style.Theme_Feather_Web_Dark else R.style.Theme_Feather_Web_Light
                    )

                    PersistentWebView(webThemeContext).apply {
                        this.isAddressBarEditing = isAddressBarEditing
                        this.onDismissAddressBar = onDismissAddressBar
                        allowBackgroundPlayback = enableBackgroundPlay
                        visibility = if (isActive) View.VISIBLE else View.GONE
                        isFocusable = true
                        isFocusableInTouchMode = true
                        onScrollChangedListener = { deltaY, scrollY ->
                            viewModel.onWebScroll(deltaY, scrollY)
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
                            } else if (!currentProfile?.customUserAgent.isNullOrBlank()) {
                                currentProfile?.customUserAgent
                            } else if (activePreset.userAgent.isNotBlank()) {
                                userAgentString = activePreset.userAgent
                            } else {
                                userAgentString = defaultUserAgent
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = true
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                        }

                        // Cookie Policy
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cookieManager.setAcceptThirdPartyCookies(this, !blockThirdPartyCookies)
                        }

                        // Dark mode for web content.
                        // `prefers-color-scheme` is driven by the ContextThemeWrapper used to create
                        // this WebView (android:isLightTheme), so the force-dark APIs are not needed
                        // (they are no-ops anyway for apps targeting API 33+). Algorithmic darkening
                        // additionally darkens pages that ship no dark styles of their own while the
                        // browser theme is dark, and must stay OFF in light mode so pages are never
                        // auto-darkened against the browser theme.
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
                                val isWhitelisted = whitelistedDomains.any {
                                    com.example.browser.UrlUtils.isSameOrSubdomain(host, it)
                                }

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
                                if (isMediaSite(url ?: view?.url)) {
                                    try {
                                        val themeScript = FingerprintScriptGenerator.generateThemeScript(effectiveDark)
                                        view?.evaluateJavascript(themeScript, null)
                                    } catch (e: Exception) { }
                                }

                                if (enableBackgroundPlay && isMediaSite(url ?: view?.url)) {
                                    try {
                                        val bgScript = FingerprintScriptGenerator.generateBackgroundPlayScript()
                                        view?.evaluateJavascript(bgScript, null)
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

                                // Inject Background Audio/Video playback script ONLY on media sites when background play is enabled
                                if (enableBackgroundPlay && isMediaSite(url ?: view?.url)) {
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

                                    val fallbackUrl = viewModel.getHttpsFallbackUrl(
                                        tabId = tabId,
                                        failingUrl = failingUrl,
                                        errorCode = errCode,
                                        description = description
                                    )
                                    if (!fallbackUrl.isNullOrBlank()) {
                                        view?.loadUrl(fallbackUrl)
                                        return
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
                                    viewModel.onPageLoadError(tabId, failingUrl)
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                // Diagnostics for real-device triage of heavy-site crashes
                                // (e.g. github.com). Log renderer/process state, then recreate.
                                try {
                                    val crashedRenderer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        detail?.didCrash() ?: false
                                    } else {
                                        false
                                    }
                                    val rendererPriority = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        detail?.rendererPriorityAtExit()
                                    } else {
                                        null
                                    }
                                    val pkgVersion = androidx.webkit.WebViewCompat
                                        .getCurrentWebViewPackage(context.applicationContext)?.versionName ?: "unknown"
                                    val memClassMb = (context.applicationContext
                                        .getSystemService(android.app.ActivityManager::class.java))
                                        ?.memoryClass ?: -1
                                    val activityManager = context.applicationContext
                                        .getSystemService(android.app.ActivityManager::class.java)
                                    val memInfo = android.app.ActivityManager.MemoryInfo().also {
                                        activityManager?.getMemoryInfo(it)
                                    }
                                    android.util.Log.e(
                                        "FeatherWebView",
                                        "onRenderProcessGone tab=$tabId didCrash=$crashedRenderer " +
                                            "rendererOutdated=$rendererPriority " +
                                            "webViewPkg=$pkgVersion " +
                                            "deviceMemoryClassMb=$memClassMb " +
                                            "availMemMb=${memInfo.availMem / (1024 * 1024)} " +
                                            "lowMemory=${memInfo.lowMemory} " +
                                            "alive=${view != null && view.javaClass.name.isNotEmpty()}",
                                    )
                                } catch (e: Throwable) {
                                    android.util.Log.e("FeatherWebView", "onRenderProcessGone diagnostics failed", e)
                                }
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
                                // Route HTTP links through the ViewModel so Prefer HTTPS can
                                // retry the original address when the upgrade is unavailable.
                                if (url.startsWith("http://", ignoreCase = true) && currentHttpsMode != HttpsMode.NORMAL) {
                                    if (viewModel.activeTabId.value == tabId) {
                                        viewModel.navigateTo(url)
                                    } else {
                                        val upgradedUrl = com.example.browser.UrlUtils.applyHttpsMode(url, currentHttpsMode)
                                        if (upgradedUrl == null) {
                                            android.widget.Toast.makeText(context, "HTTPS-only mode blocked this insecure link", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            view?.loadUrl(upgradedUrl)
                                        }
                                    }
                                    return true
                                }
                                val secureUrl = com.example.browser.UrlUtils.applyHttpsMode(url, currentHttpsMode)
                                if (secureUrl == null) {
                                    android.widget.Toast.makeText(context, "HTTPS-only mode blocked this insecure link", android.widget.Toast.LENGTH_SHORT).show()
                                    return true
                                }
                                if (secureUrl != url) {
                                    view?.loadUrl(secureUrl)
                                    return true
                                }
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
                                // User-initiated target=_blank links should become real
                                // Feather tabs. Reject script-only popups so ad scripts
                                // cannot fill the tab switcher.
                                if (!isUserGesture || resultMsg == null) return false
                                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                val popup = WebView(ctx)
                                popup.settings.javaScriptEnabled = true
                                popup.settings.domStorageEnabled = true
                                view?.settings?.userAgentString?.let { popup.settings.userAgentString = it }
                                var opened = false

                                fun routePopupUrl(url: String) {
                                    if (opened || url.isBlank() || url == "about:blank") return
                                    if (!url.startsWith("http://", ignoreCase = true) &&
                                        !url.startsWith("https://", ignoreCase = true)
                                    ) return
                                    opened = true
                                    viewModel.openLinkInNewTab(url, openInBackground = false)
                                    try {
                                        popup.stopLoading()
                                        popup.destroy()
                                    } catch (_: Throwable) { }
                                }

                                popup.webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(v, url, favicon)
                                        url?.let(::routePopupUrl)
                                    }

                                    override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean {
                                        url?.let(::routePopupUrl)
                                        return true
                                    }
                                }
                                transport.webView = popup
                                resultMsg.sendToTarget()
                                return true
                            }

                            override fun onJsBeforeUnload(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                val activity = ctx.findActivity()
                                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                                    result?.confirm()
                                    return true
                                }
                                try {
                                    android.app.AlertDialog.Builder(activity)
                                        .setTitle("Leave site?")
                                        .setMessage(
                                            if (!message.isNullOrBlank()) message
                                            else "Changes you made may not be saved."
                                        )
                                        .setPositiveButton("Leave") { _, _ ->
                                            result?.confirm()
                                        }
                                        .setNegativeButton("Cancel") { _, _ ->
                                            result?.cancel()
                                            view?.url?.let { currentActualUrl ->
                                                viewModel.onUrlChanged(tabId, currentActualUrl)
                                            }
                                        }
                                        .setOnCancelListener {
                                            result?.cancel()
                                            view?.url?.let { currentActualUrl ->
                                                viewModel.onUrlChanged(tabId, currentActualUrl)
                                            }
                                        }
                                        .show()
                                } catch (e: Exception) {
                                    result?.confirm()
                                }
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
                                viewModel.onProgressChanged(tabId, newProgress)
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                title?.let { viewModel.onTitleChanged(tabId, it) }
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                customVideoView = view
                                customVideoCallback = callback
                            }

                            override fun onHideCustomView() {
                                customVideoView = null
                                customVideoCallback?.onCustomViewHidden()
                                customVideoCallback = null
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                return onShowFileChooserImpl(filePathCallback, fileChooserParams)
                            }
                        }

                        webViewRef = this
                        MediaSessionManager.registerWebView(tabId, this)
                        if (initialUrl.isNotBlank() && initialUrl != "about:blank") {
                            loadUrl(initialUrl)
                        }
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    webView.visibility = if (isActive) View.VISIBLE else View.GONE
                    MediaSessionManager.registerWebView(tabId, webView)
                    if (webView is PersistentWebView) {
                        webView.allowBackgroundPlayback = enableBackgroundPlay
                        webView.isAddressBarEditing = isAddressBarEditing
                        webView.onDismissAddressBar = onDismissAddressBar
                    }

                    // Keep web rendering synchronized with the active browser theme.
                    // The WebView itself is recreated when the theme flips (see key() above) so
                    // `prefers-color-scheme` re-resolves; here we only keep the background color
                    // and algorithmic darkening in sync between recompositions.
                    val targetBg = if (effectiveDark) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE
                    webView.setBackgroundColor(targetBg)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        webView.settings.isAlgorithmicDarkeningAllowed = effectiveDark
                    } else if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, effectiveDark)
                    }
                },
                onRelease = { oldWebView ->
                    // The WebView is recreated whenever the browser theme flips (key above).
                    // Destroy the replaced instance so the old renderer is not leaked; the
                    // current instance is torn down by the tab-close cleanup instead.
                    (oldWebView as? PersistentWebView)?.destroySafely()
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

