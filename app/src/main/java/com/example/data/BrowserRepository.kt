package com.example.data

import android.content.Context
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BrowserRepository(private val database: AppDatabase) {

    val profiles: Flow<List<BrowserProfile>> = database.profileDao().getAllProfiles()

    fun getTabsForProfile(profileId: String): Flow<List<BrowserTab>> =
        database.tabDao().getTabsForProfile(profileId)

    val privateTabs: Flow<List<BrowserTab>> = database.tabDao().getPrivateTabs()

    fun getBookmarks(profileId: String): Flow<List<Bookmark>> =
        database.bookmarkDao().getBookmarksForProfile(profileId)

    fun searchBookmarks(profileId: String, query: String): Flow<List<Bookmark>> =
        database.bookmarkDao().searchBookmarks(profileId, query)

    fun getHistory(profileId: String): Flow<List<HistoryItem>> =
        database.historyDao().getHistoryForProfile(profileId)

    fun searchHistory(profileId: String, query: String): Flow<List<HistoryItem>> =
        database.historyDao().searchHistory(profileId, query)

    fun getDownloads(profileId: String): Flow<List<DownloadItem>> =
        database.downloadDao().getDownloadsForProfile(profileId)

    fun getTopVisitedSites(profileId: String): Flow<List<TopSiteDto>> =
        database.historyDao().getTopVisitedHistory(profileId)

    fun getCustomShortcuts(profileId: String): Flow<List<CustomShortcut>> =
        database.shortcutDao().getShortcutsForProfile(profileId)

    val exceptions: Flow<List<SiteException>> = database.privacyDao().getAllExceptions()

    suspend fun saveCustomShortcut(profileId: String, title: String, url: String, id: String? = null) = withContext(Dispatchers.IO) {
        val shortcutId = id ?: ("sc_" + System.currentTimeMillis())
        database.shortcutDao().insertShortcut(
            CustomShortcut(
                id = shortcutId,
                profileId = profileId,
                title = title.ifBlank { url },
                url = url
            )
        )
    }

    suspend fun deleteCustomShortcut(id: String) = withContext(Dispatchers.IO) {
        database.shortcutDao().deleteShortcutById(id)
    }

    fun getDismissedShortcuts(profileId: String): Flow<List<String>> =
        database.dismissedShortcutDao().getDismissedUrlsForProfile(profileId)

    suspend fun dismissShortcut(profileId: String, url: String) = withContext(Dispatchers.IO) {
        val id = "dismissed_${profileId}_${url.hashCode()}"
        database.dismissedShortcutDao().insertDismissedShortcut(
            DismissedShortcut(id = id, profileId = profileId, url = url)
        )
    }

    suspend fun initializeDefaultShortcutsIfNeeded(profileId: String) = withContext(Dispatchers.IO) {
        // Pre-populate standard useful quick shortcuts if empty
        val shortcuts = listOf(
            CustomShortcut("sc_google", profileId, "Google", "https://www.google.com", position = 0),
            CustomShortcut("sc_youtube", profileId, "YouTube", "https://www.youtube.com", position = 1),
            CustomShortcut("sc_github", profileId, "GitHub", "https://github.com", position = 2),
            CustomShortcut("sc_reddit", profileId, "Reddit", "https://www.reddit.com", position = 3),
            CustomShortcut("sc_wikipedia", profileId, "Wikipedia", "https://www.wikipedia.org", position = 4)
        )
        for (sc in shortcuts) {
            database.shortcutDao().insertShortcut(sc)
        }
    }

    suspend fun initializeDefaultProfilesIfNeeded() = withContext(Dispatchers.IO) {
        val existing = database.profileDao().getProfileById("default_personal")
        if (existing == null) {
            database.profileDao().insertProfile(
                BrowserProfile(
                    id = "default_personal",
                    displayName = "Personal",
                    iconName = "person",
                    colorHex = "#3B82F6",
                    fingerprintPreset = "DEFAULT"
                )
            )
            database.profileDao().insertProfile(
                BrowserProfile(
                    id = "work",
                    displayName = "Work",
                    iconName = "work",
                    colorHex = "#10B981",
                    fingerprintPreset = "WINDOWS_DESKTOP"
                )
            )
            database.profileDao().insertProfile(
                BrowserProfile(
                    id = "university",
                    displayName = "University",
                    iconName = "school",
                    colorHex = "#8B5CF6",
                    fingerprintPreset = "MAC_DESKTOP"
                )
            )
            database.profileDao().insertProfile(
                BrowserProfile(
                    id = "testing",
                    displayName = "Testing",
                    iconName = "science",
                    colorHex = "#F59E0B",
                    fingerprintPreset = "ANONYMOUS_STEALTH"
                )
            )
        }
    }

    suspend fun createProfile(
        displayName: String,
        iconName: String,
        colorHex: String,
        fingerprintPreset: String = "DEFAULT",
        customUserAgent: String = ""
    ): BrowserProfile = withContext(Dispatchers.IO) {
        val id = "profile_" + System.currentTimeMillis()
        val profile = BrowserProfile(
            id = id,
            displayName = displayName,
            iconName = iconName,
            colorHex = colorHex,
            fingerprintPreset = fingerprintPreset,
            customUserAgent = customUserAgent
        )
        database.profileDao().insertProfile(profile)
        profile
    }

    suspend fun updateProfile(profile: BrowserProfile) = withContext(Dispatchers.IO) {
        database.profileDao().updateProfile(profile)
    }

    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        database.tabDao().deleteTabsForProfile(profileId)
        database.bookmarkDao().deleteBookmarksForProfile(profileId)
        database.historyDao().deleteHistoryForProfile(profileId)
        database.profileDao().deleteProfileById(profileId)
    }

    // Tabs
    suspend fun saveTab(tab: BrowserTab) = withContext(Dispatchers.IO) {
        database.tabDao().insertTab(tab)
    }

    suspend fun deleteTab(tabId: String) = withContext(Dispatchers.IO) {
        database.tabDao().deleteTabById(tabId)
    }

    suspend fun clearPrivateTabs() = withContext(Dispatchers.IO) {
        database.tabDao().deleteAllPrivateTabs()
    }

    // Bookmarks
    suspend fun addBookmark(profileId: String, title: String, url: String) = withContext(Dispatchers.IO) {
        val existing = database.bookmarkDao().getBookmarkByUrl(profileId, url)
        if (existing == null) {
            database.bookmarkDao().insertBookmark(
                Bookmark(profileId = profileId, title = title, url = url)
            )
        }
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        database.bookmarkDao().deleteBookmark(bookmark)
    }

    suspend fun isBookmarked(profileId: String, url: String): Boolean = withContext(Dispatchers.IO) {
        database.bookmarkDao().getBookmarkByUrl(profileId, url) != null
    }

    // History
    suspend fun recordHistory(profileId: String, title: String, url: String, isPrivate: Boolean) = withContext(Dispatchers.IO) {
        if (!isPrivate && url.isNotBlank() && !url.startsWith("about:") && !url.startsWith("data:")) {
            database.historyDao().insertHistory(
                HistoryItem(profileId = profileId, title = title.ifBlank { url }, url = url)
            )
        }
    }

    suspend fun deleteHistoryItem(id: Long) = withContext(Dispatchers.IO) {
        database.historyDao().deleteHistoryById(id)
    }

    suspend fun clearHistoryForProfile(profileId: String) = withContext(Dispatchers.IO) {
        database.historyDao().deleteHistoryForProfile(profileId)
    }

    // Downloads
    suspend fun recordDownload(profileId: String, url: String, filename: String, mimeType: String, sizeBytes: Long = 0, uri: String? = null) = withContext(Dispatchers.IO) {
        database.downloadDao().insertDownload(
            DownloadItem(
                profileId = profileId,
                url = url,
                filename = filename,
                mimeType = mimeType,
                fileSizeBytes = sizeBytes,
                fileUri = uri
            )
        )
    }

    suspend fun deleteDownloadItem(id: Long) = withContext(Dispatchers.IO) {
        database.downloadDao().deleteDownloadById(id)
    }

    // Privacy Exceptions
    suspend fun isSiteAdBlockDisabled(domain: String): Boolean = withContext(Dispatchers.IO) {
        val ex = database.privacyDao().getExceptionForDomain(domain)
        ex?.isAdBlockDisabled == true
    }

    suspend fun toggleSiteAdBlockException(domain: String, disableBlocking: Boolean) = withContext(Dispatchers.IO) {
        if (disableBlocking) {
            database.privacyDao().setException(SiteException(domain = domain, isAdBlockDisabled = true))
        } else {
            database.privacyDao().removeException(domain)
        }
    }
}
