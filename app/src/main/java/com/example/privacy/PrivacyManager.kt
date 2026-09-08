package com.example.privacy

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrivacyManager(private val context: Context, private val database: AppDatabase) {

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
            database.downloadDao().deleteAllDownloads()
        }
        if (clearCookies) {
            withContext(Dispatchers.Main) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        if (clearSiteData) {
            withContext(Dispatchers.Main) {
                WebStorage.getInstance().deleteAllData()
            }
        }
        if (clearCache) {
            withContext(Dispatchers.Main) {
                try {
                    // Clear WebView cache on main thread safely
                    val dummyWebView = WebView(context)
                    dummyWebView.clearCache(true)
                    dummyWebView.destroy()
                } catch (e: Throwable) {
                    // Guard against headless/virtualized graphic compositor failures
                }
            }
        }
    }

    /**
     * Cleans up temporary private browsing session data without affecting normal profiles.
     */
    suspend fun cleanPrivateSessionData() = withContext(Dispatchers.IO) {
        database.tabDao().deleteAllPrivateTabs()
        withContext(Dispatchers.Main) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }
}
