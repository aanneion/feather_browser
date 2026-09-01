package com.example.ui.components

import java.net.URLEncoder

object ErrorPageHtml {

    /**
     * Generates a modern, elegant, browser-grade error page in HTML/CSS
     * matching the browser's sleek dark/light palette without showing default Android robotic icons.
     */
    fun buildErrorPage(
        failingUrl: String,
        errorCode: Int,
        description: String,
        isDarkTheme: Boolean = true,
        searchQueryUrl: String = ""
    ): String {
        val domain = try {
            val uri = android.net.Uri.parse(failingUrl)
            uri.host ?: failingUrl
        } catch (e: Exception) {
            failingUrl
        }

        val bgColor = if (isDarkTheme) "#0F172A" else "#F8FAFC"
        val cardBg = if (isDarkTheme) "#1E293B" else "#FFFFFF"
        val textPrimary = if (isDarkTheme) "#F1F5F9" else "#0F172A"
        val textSecondary = if (isDarkTheme) "#94A3B8" else "#64748B"
        val accentColor = "#3B82F6"
        val borderColor = if (isDarkTheme) "rgba(255, 255, 255, 0.08)" else "rgba(0, 0, 0, 0.08)"

        val humanTitle = when {
            errorCode == -2 || description.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) || description.contains("NAME_NOT_RESOLVED", ignoreCase = true) -> "This site can’t be reached"
            errorCode == -6 || description.contains("ERR_CONNECTION_REFUSED", ignoreCase = true) -> "Unable to connect"
            errorCode == -8 || description.contains("ERR_TIMED_OUT", ignoreCase = true) -> "Connection timed out"
            errorCode == -11 || description.contains("ERR_SSL", ignoreCase = true) -> "Secure Connection Failed"
            else -> "Website not reachable"
        }

        val humanExplanation = when {
            errorCode == -2 || description.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) -> "Check if <strong>$domain</strong> is spelled correctly, or search for it using your search engine."
            errorCode == -6 -> "The server at <strong>$domain</strong> refused the connection or is currently offline."
            errorCode == -8 -> "The server took too long to respond. Your connection might be slow or interrupted."
            errorCode == -11 -> "The security certificate presented by <strong>$domain</strong> could not be verified."
            else -> "An error occurred while attempting to load <strong>$domain</strong> ($description)."
        }

        val encodedSearchQuery = try {
            URLEncoder.encode(failingUrl.removePrefix("https://").removePrefix("http://"), "UTF-8")
        } catch (e: Exception) {
            domain
        }
        val searchLink = if (searchQueryUrl.isNotBlank()) searchQueryUrl + encodedSearchQuery else "https://www.google.com/search?q=$encodedSearchQuery"

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>$humanTitle</title>
            <style>
                * {
                    box-sizing: border-box;
                    margin: 0;
                    padding: 0;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    -webkit-tap-highlight-color: transparent;
                }
                body {
                    background-color: $bgColor;
                    color: $textPrimary;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    min-height: 100vh;
                    padding: 24px;
                }
                .container {
                    background: $cardBg;
                    border: 1px solid $borderColor;
                    border-radius: 20px;
                    padding: 32px 24px;
                    max-width: 440px;
                    width: 100%;
                    box-shadow: 0 10px 25px rgba(0,0,0,0.15);
                    text-align: center;
                }
                .icon-container {
                    width: 68px;
                    height: 68px;
                    margin: 0 auto 20px auto;
                    border-radius: 50%;
                    background: rgba(239, 68, 68, 0.12);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .icon-container svg {
                    width: 34px;
                    height: 34px;
                    stroke: #EF4444;
                }
                h1 {
                    font-size: 20px;
                    font-weight: 700;
                    margin-bottom: 12px;
                    color: $textPrimary;
                }
                p.explanation {
                    font-size: 13.5px;
                    line-height: 1.55;
                    color: $textSecondary;
                    margin-bottom: 24px;
                }
                p.explanation strong {
                    color: $textPrimary;
                    word-break: break-all;
                }
                .error-code {
                    display: inline-block;
                    font-size: 11px;
                    font-family: monospace;
                    font-weight: 600;
                    padding: 4px 10px;
                    border-radius: 8px;
                    background: ${if (isDarkTheme) "rgba(255,255,255,0.06)" else "rgba(0,0,0,0.05)"};
                    color: $textSecondary;
                    margin-bottom: 24px;
                }
                .actions {
                    display: flex;
                    flex-direction: column;
                    gap: 10px;
                }
                .btn {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    height: 44px;
                    border-radius: 12px;
                    font-size: 14px;
                    font-weight: 600;
                    text-decoration: none;
                    cursor: pointer;
                    transition: transform 0.1s ease, opacity 0.2s ease;
                }
                .btn:active {
                    transform: scale(0.98);
                }
                .btn-primary {
                    background: $accentColor;
                    color: #FFFFFF;
                    border: none;
                }
                .btn-secondary {
                    background: transparent;
                    color: $textPrimary;
                    border: 1px solid $borderColor;
                }
                .btn-search {
                    background: ${if (isDarkTheme) "rgba(59, 130, 246, 0.15)" else "rgba(59, 130, 246, 0.08)"};
                    color: $accentColor;
                    border: 1px solid rgba(59, 130, 246, 0.25);
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="icon-container">
                    <svg fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
                    </svg>
                </div>
                <h1>$humanTitle</h1>
                <p class="explanation">$humanExplanation</p>
                <div class="error-code">ERROR: ${description.ifBlank { "ERR_CONNECTION_FAILED" }}</div>

                <div class="actions">
                    <button class="btn btn-primary" onclick="location.reload();">Try Again</button>
                    <a class="btn btn-search" href="$searchLink">Search for this address</a>
                    <button class="btn btn-secondary" onclick="location.href='about:blank';">Go to Home Page</button>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
}
