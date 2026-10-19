package com.example.privacy

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Thin, feature-gated wrapper around WebKit's multi-profile APIs.
 *
 * A named WebKit profile owns its own cookie jar, DOM storage, IndexedDB, cache,
 * geolocation and service workers. That is the only way a WebView-based browser can
 * offer real per-profile isolation; separate Room rows alone only separate history.
 *
 * On older WebView versions the methods quietly fall back to the process-wide profile,
 * preserving the previous behaviour instead of crashing.
 */
object WebViewProfileManager {

    const val DEFAULT_PROFILE_ID = "default_personal"
    const val PRIVATE_PROFILE_ID = "private_session"

    private const val PREFS_NAME = "feather_webview_profiles"
    private const val KEY_PENDING_DELETIONS = "pending_profile_deletions"
    private const val NAME_PREFIX = "feather_"

    fun isSupported(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    } catch (t: Throwable) {
        false
    }

    fun isDefaultProfile(profileId: String): Boolean = profileId == DEFAULT_PROFILE_ID

    fun isPrivateProfile(profileId: String): Boolean = profileId == PRIVATE_PROFILE_ID

    fun profileNameFor(profileId: String): String {
        val safe = profileId
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .take(80)
            .ifBlank { "unnamed" }
        return NAME_PREFIX + safe
    }

    /**
     * Assigns the WebView to its profile. Must be called before the first navigation or
     * JavaScript evaluation; WebKit rejects profile changes after that point.
     */
    fun applyToWebView(webView: WebView, profileId: String) {
        if (!isSupported() || isDefaultProfile(profileId)) return
        try {
            WebViewCompat.setProfile(webView, profileNameFor(profileId))
        } catch (t: Throwable) {
            android.util.Log.w(
                "FeatherProfiles",
                "Unable to assign WebView profile for $profileId; using the default profile",
                t
            )
        }
    }

    /**
     * Configures cookie policy on the profile that owns [webView], not on the global
     * default profile. Falls back to the process-wide CookieManager when isolation is
     * unavailable.
     */
    fun configureCookies(webView: WebView, profileId: String, blockThirdPartyCookies: Boolean) {
        try {
            // Ask the WebView which profile it actually has. If setProfile failed and the
            // WebView fell back to the default profile, this still configures the jar that
            // the WebView will really use instead of a different named profile.
            val manager = if (isSupported()) {
                runCatching { WebViewCompat.getProfile(webView).cookieManager }
                    .getOrElse { CookieManager.getInstance() }
            } else {
                CookieManager.getInstance()
            }
            manager.setAcceptCookie(true)
            manager.setAcceptThirdPartyCookies(webView, !blockThirdPartyCookies)
            manager.flush()
        } catch (t: Throwable) {
            android.util.Log.w("FeatherProfiles", "Unable to configure cookies for $profileId", t)
        }
    }

    fun cookieForUrl(profileId: String, url: String): String? {
        return try {
            cookieManagerFor(profileId).getCookie(url)
        } catch (t: Throwable) {
            null
        }
    }

    /** Clears cookies and site storage for one profile without deleting the profile itself. */
    fun clearProfileData(context: Context, profileId: String) {
        try {
            if (isDefaultProfile(profileId)) {
                clearDefaultProfileData()
                return
            }
            if (!isSupported()) return
            val name = profileNameFor(profileId)
            // A missing profile has no data to clear. In particular, do NOT fall back to
            // the default profile here: that would erase the user's normal session every
            // time the private-session cleanup runs at startup.
            val profile = runCatching { ProfileStore.getInstance().getProfile(name) }.getOrNull()
            if (profile != null) {
                clearProfileObject(profile)
            }
        } catch (t: Throwable) {
            android.util.Log.w("FeatherProfiles", "Unable to clear data for $profileId", t)
        }
    }

    /** Clears cookies for every profile, used by "Clear browsing data". */
    fun clearAllCookies() {
        try {
            clearDefaultCookies()
            if (!isSupported()) return
            val store = ProfileStore.getInstance()
            store.allProfileNames.forEach { name ->
                runCatching {
                    val profile = store.getProfile(name) ?: return@runCatching
                    profile.cookieManager.removeAllCookies(null)
                    profile.cookieManager.flush()
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("FeatherProfiles", "Unable to clear all profile cookies", t)
        }
    }

    /** Clears site storage (localStorage, IndexedDB, service workers, ...) for every profile. */
    fun clearAllSiteData() {
        try {
            clearDefaultSiteData()
            if (!isSupported()) return
            val store = ProfileStore.getInstance()
            store.allProfileNames.forEach { name ->
                runCatching { store.getProfile(name)?.webStorage?.deleteAllData() }
            }
        } catch (t: Throwable) {
            android.util.Log.w("FeatherProfiles", "Unable to clear all profile site data", t)
        }
    }

    /** Clears cookies/site data for every profile, used by profile deletion fallbacks. */
    fun clearAllProfileData() {
        clearAllCookies()
        clearAllSiteData()
    }

    /**
     * Deletes a profile. WebKit refuses to delete a profile that is still open; in that
     * case the cookies/storage are cleared immediately and deletion is retried at the
     * next process start, before any WebView has opened a profile.
     */
    fun deleteProfile(context: Context, profileId: String) {
        if (isDefaultProfile(profileId)) {
            clearDefaultProfileData()
            return
        }
        // Without multi-profile support there is no per-profile jar to delete. Clearing
        // the shared default jar here would log the user out of every site, so leave it
        // to the explicit "Clear browsing data" action.
        if (!isSupported()) return
        val name = profileNameFor(profileId)
        try {
            val store = ProfileStore.getInstance()
            if (store.deleteProfile(name)) {
                removePendingDeletion(context, name)
                return
            }
            // No such profile (for example a profile that was created and deleted before
            // any WebView used it). There is nothing to defer.
            if (store.allProfileNames.none { it == name }) {
                removePendingDeletion(context, name)
                return
            }
        } catch (t: Throwable) {
            android.util.Log.d("FeatherProfiles", "Profile $name still open; deferring deletion")
        }
        // The WebViews that used it may still be tearing down. Clear their private data
        // now and finish the deletion on the next launch.
        try {
            ProfileStore.getInstance().getProfile(name)?.let(::clearProfileObject)
        } catch (t: Throwable) { }
        addPendingDeletion(context, name)
    }

    /** Call once from Application.onCreate, before any WebView is constructed. */
    fun processPendingDeletions(context: Context) {
        if (!isSupported()) return
        val pending = pendingDeletions(context)
        if (pending.isEmpty()) return
        try {
            val store = ProfileStore.getInstance()
            pending.toList().forEach { name ->
                if (store.allProfileNames.none { it == name }) {
                    removePendingDeletion(context, name)
                    return@forEach
                }
                val deleted = runCatching { store.deleteProfile(name) }.getOrDefault(false)
                if (deleted) {
                    removePendingDeletion(context, name)
                } else {
                    runCatching { store.getProfile(name) }.getOrNull()?.let(::clearProfileObject)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("FeatherProfiles", "Unable to process pending profile deletions", t)
        }
    }

    private fun cookieManagerFor(profileId: String): CookieManager {
        if (isDefaultProfile(profileId) || !isSupported()) {
            return CookieManager.getInstance()
        }
        val name = profileNameFor(profileId)
        val store = ProfileStore.getInstance()
        val profile = store.getProfile(name) ?: store.getOrCreateProfile(name)
        return profile.cookieManager
    }

    private fun clearProfileObject(profile: Profile) {
        try {
            profile.cookieManager.removeAllCookies(null)
            profile.cookieManager.flush()
        } catch (t: Throwable) { }
        try {
            profile.webStorage.deleteAllData()
        } catch (t: Throwable) { }
    }

    private fun clearDefaultProfileData() {
        clearDefaultCookies()
        clearDefaultSiteData()
    }

    private fun clearDefaultCookies() {
        try {
            val cookies = CookieManager.getInstance()
            cookies.removeAllCookies(null)
            cookies.flush()
        } catch (t: Throwable) { }
    }

    private fun clearDefaultSiteData() {
        try {
            WebStorage.getInstance().deleteAllData()
        } catch (t: Throwable) { }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun pendingDeletions(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PENDING_DELETIONS, emptySet())?.toSet() ?: emptySet()

    private fun addPendingDeletion(context: Context, name: String) {
        try {
            val current = pendingDeletions(context).toMutableSet()
            if (current.add(name)) {
                prefs(context).edit().putStringSet(KEY_PENDING_DELETIONS, current).apply()
            }
        } catch (t: Throwable) { }
    }

    private fun removePendingDeletion(context: Context, name: String) {
        try {
            val current = pendingDeletions(context).toMutableSet()
            if (current.remove(name)) {
                prefs(context).edit().putStringSet(KEY_PENDING_DELETIONS, current).apply()
            }
        } catch (t: Throwable) { }
    }
}
