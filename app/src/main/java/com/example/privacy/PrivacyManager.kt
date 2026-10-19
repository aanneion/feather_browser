package com.example.privacy

import android.app.DownloadManager
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrivacyManager(private val context: Context, private val database: AppDatabase) {

    private fun removeDownloadFile(fileUri: String?) {
        val uri = fileUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return
        if (uri.scheme != "content") return
        val downloadId = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        runCatching { manager.remove(downloadId) }
    }

    /**
     * Clears specified browsing data.
     */
    suspend fun clearBrowsingData(
        clearHistory: Boolean = true,
        clearCookies: Boolean = true,
        clearCache: Boolean = true,
        clearSiteData: Boolean = true,
        clearDownloads: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (clearHistory) {
            database.historyDao().deleteAllHistory()
        }
        if (clearDownloads) {
            // Remove both the history rows and files owned by Android's
            // DownloadManager; clearing only Room leaves private files behind.
            database.downloadDao().getAllDownloads().forEach { item ->
                removeDownloadFile(item.fileUri)
            }
            database.downloadDao().deleteAllDownloads()
        }
        if (clearCookies) {
            withContext(Dispatchers.Main) {
                WebViewProfileManager.clearAllCookies()
            }
        }
        if (clearSiteData) {
            withContext(Dispatchers.Main) {
                WebViewProfileManager.clearAllSiteData()
            }
        }
        if (clearCache) {
            withContext(Dispatchers.Main) {
                try {
                    // Clear WebView cache on main thread safely
                    val dummyWebView = WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(v: WebView?, detail: RenderProcessGoneDetail?): Boolean = true
                        }
                    }
                    dummyWebView.clearCache(true)
                    try {
                        dummyWebView.destroy()
                    } catch (e: Throwable) { }
                } catch (e: Throwable) {
                    // Guard against headless/virtualized graphic compositor failures
                }
            }
        }
    }

    /**
     * Removes private tab metadata and, on multi-profile WebView, the private session's
     * isolated cookies and site storage. Normal profiles are untouched.
     */
    suspend fun cleanPrivateSessionData() = withContext(Dispatchers.IO) {
        database.tabDao().deleteAllPrivateTabs()
        withContext(Dispatchers.Main) {
            WebViewProfileManager.clearProfileData(
                context,
                WebViewProfileManager.PRIVATE_PROFILE_ID
            )
        }
    }
}
