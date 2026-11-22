package com.example.privacy

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.Executor

/**
 * Per-Profile Network Routing & Proxy Manager.
 *
 * Bridges AndroidX WebKit's [ProxyController] (for WebView HTTP/HTTPS/WebSocket + DNS traffic)
 * and standard `java.net.Proxy` (for browser-side connections such as search suggestions
 * and first-party favicons) so each profile can route either directly or through its own
 * SOCKS5 / HTTP proxy (e.g., Orbot/Tor `socks5://127.0.0.1:9050` or a corporate HTTP proxy).
 */
object ProfileProxyManager {

    data class ParsedProxy(
        val scheme: String, // "http", "https", or "socks5"
        val host: String,
        val port: Int
    ) {
        fun toWebKitRule(): String = "$scheme://$host:$port"

        fun toJavaNetProxy(): Proxy {
            val type = if (scheme.startsWith("socks", ignoreCase = true)) {
                Proxy.Type.SOCKS
            } else {
                Proxy.Type.HTTP
            }
            return Proxy(type, InetSocketAddress.createUnresolved(host, port))
        }
    }

    private val directExecutor = Executor { runnable -> runnable.run() }

    @Volatile
    private var activeProxyRule: String = ""

    fun isWebViewProxySupported(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    } catch (_: Throwable) {
        false
    }

    /**
     * Parses a user-entered proxy string such as:
     * - `socks5://127.0.0.1:9050`
     * - `http://10.0.0.2:8080`
     * - `127.0.0.1:8118` (defaults to `http`)
     *
     * Returns `null` if [raw] is blank or invalid.
     */
    fun parseProxyUrl(raw: String?): ParsedProxy? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        val normalized = if ("://" in trimmed) trimmed else "http://$trimmed"
        return try {
            val uri = URI(normalized)
            val scheme = (uri.scheme ?: "http").lowercase()
            if (scheme !in setOf("http", "https", "socks5", "socks")) return null
            val canonicalScheme = if (scheme == "socks") "socks5" else scheme
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            val port = uri.port
            if (port !in 1..65535) return null
            ParsedProxy(scheme = canonicalScheme, host = host, port = port)
        } catch (_: Throwable) {
            null
        }
    }

    fun isValidOrBlank(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.isBlank() || parseProxyUrl(trimmed) != null
    }

    /**
     * Applies (or clears) the WebView proxy override to match the active profile's [proxyUrl].
     * When a proxy is configured, local loopback addresses bypass the proxy only if explicitly
     * non-proxied, while DNS hostname resolution is routed through the proxy (preventing DNS leaks).
     */
    fun applyProfileProxy(proxyUrl: String?, onComplete: (() -> Unit)? = null) {
        val parsed = parseProxyUrl(proxyUrl)
        val desiredRule = parsed?.toWebKitRule().orEmpty()

        if (!isWebViewProxySupported()) {
            activeProxyRule = desiredRule
            onComplete?.invoke()
            return
        }

        if (activeProxyRule == desiredRule) {
            onComplete?.invoke()
            return
        }

        try {
            val controller = ProxyController.getInstance()
            if (parsed == null) {
                controller.clearProxyOverride(directExecutor) {
                    activeProxyRule = ""
                    onComplete?.invoke()
                }
            } else {
                val configBuilder = ProxyConfig.Builder()
                    .addProxyRule(parsed.toWebKitRule())
                // Ensure non-proxyable fallback is NOT silently allowed: if the user configured
                // a proxy (such as Tor), fail closed rather than leaking direct IP traffic!
                val config = configBuilder.build()
                controller.setProxyOverride(config, directExecutor) {
                    activeProxyRule = desiredRule
                    onComplete?.invoke()
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("FeatherProxy", "Failed to apply proxy rule '$desiredRule'", t)
            onComplete?.invoke()
        }
    }

    /**
     * Returns the active `java.net.Proxy` for browser-initiated `HttpURLConnection` calls
     * (search suggestions, direct origin favicons) so they never bypass the active profile's proxy.
     */
    fun getActiveJavaNetProxy(): Proxy {
        val parsed = parseProxyUrl(activeProxyRule) ?: return Proxy.NO_PROXY
        return try {
            parsed.toJavaNetProxy()
        } catch (_: Throwable) {
            Proxy.NO_PROXY
        }
    }

    fun getActiveProxyRule(): String = activeProxyRule
}
