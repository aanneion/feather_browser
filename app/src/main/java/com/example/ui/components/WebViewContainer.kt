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
import kotlinx.coroutines.flow.SharedFlow

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

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
    isDarkTheme: Boolean,
    currentProfile: BrowserProfile?,
    viewModel: BrowserViewModel,
    actions: SharedFlow<WebViewAction>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
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

    // Sync URL when initialUrl or webViewRef changes
    LaunchedEffect(tabId, initialUrl, webViewRef) {
        if (initialUrl.isNotBlank() && initialUrl != "about:blank") {
            webViewRef?.let { webView ->
                val currentUrl = webView.url ?: ""
                if (currentUrl != initialUrl) {
                    webView.loadUrl(initialUrl)
                }
            }
        }
    }

    var renderCrashCount by remember(tabId) { mutableStateOf(0) }

    // Handle incoming actions from ViewModel
    LaunchedEffect(tabId, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        actions.collect { action ->
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

    key(tabId, renderCrashCount) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AndroidView(
                factory = { ctx ->
                    val targetUiMode = if (effectiveDark) {
                        Configuration.UI_MODE_NIGHT_YES
                    } else {
                        Configuration.UI_MODE_NIGHT_NO
                    }
                    val overrideConfig = Configuration(ctx.resources.configuration).apply {
                        uiMode = targetUiMode or (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
                    }
                    val themedContext = ctx.createConfigurationContext(overrideConfig)

                    WebView(themedContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Eliminate black flashing on init
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)

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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            settings.isAlgorithmicDarkeningAllowed = effectiveDark
                        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, effectiveDark)
                        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                            WebSettingsCompat.setForceDark(
                                settings,
                                if (effectiveDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                            )
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
                                    viewModel.onPageStarted(it)
                                }
                                viewModel.onNavigationStateChanged(
                                    canGoBack = view?.canGoBack() ?: false,
                                    canGoForward = view?.canGoForward() ?: false
                                )
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let {
                                    viewModel.onPageFinished(it)
                                }
                                view?.title?.let {
                                    viewModel.onTitleChanged(it)
                                }
                                viewModel.onNavigationStateChanged(
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
                                    viewModel.onPageLoadError()
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
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
                                viewModel.onProgressChanged(newProgress)
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                title?.let { viewModel.onTitleChanged(it) }
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
                        }

                        webViewRef = this
                        if (initialUrl.isNotBlank() && initialUrl != "about:blank") {
                            loadUrl(initialUrl)
                        }
                    }
                },
                update = { webView ->
                    webViewRef = webView
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
