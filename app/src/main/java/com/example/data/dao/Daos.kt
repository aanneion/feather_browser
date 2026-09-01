package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM browser_profiles WHERE isPrivate = 0 ORDER BY createdAt ASC")
    fun getAllProfiles(): Flow<List<BrowserProfile>>

    @Query("SELECT * FROM browser_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): BrowserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: BrowserProfile)

    @Update
    suspend fun updateProfile(profile: BrowserProfile)

    @Query("DELETE FROM browser_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)
}

@Dao
interface TabDao {
    @Query("SELECT * FROM browser_tabs WHERE profileId = :profileId AND isPrivate = 0 ORDER BY lastAccessedAt DESC")
    fun getTabsForProfile(profileId: String): Flow<List<BrowserTab>>

    @Query("SELECT * FROM browser_tabs WHERE isPrivate = 1 ORDER BY lastAccessedAt DESC")
    fun getPrivateTabs(): Flow<List<BrowserTab>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: BrowserTab)

    @Update
    suspend fun updateTab(tab: BrowserTab)

    @Query("DELETE FROM browser_tabs WHERE id = :tabId")
    suspend fun deleteTabById(tabId: String)

    @Query("DELETE FROM browser_tabs WHERE profileId = :profileId")
    suspend fun deleteTabsForProfile(profileId: String)

    @Query("DELETE FROM browser_tabs WHERE isPrivate = 1")
    suspend fun deleteAllPrivateTabs()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getBookmarksForProfile(profileId: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId AND (title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchBookmarks(profileId: String, query: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId AND url = :url LIMIT 1")
    suspend fun getBookmarkByUrl(profileId: String, url: String): Bookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Update
    suspend fun updateBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE profileId = :profileId")
    suspend fun deleteBookmarksForProfile(profileId: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries WHERE profileId = :profileId ORDER BY visitedAt DESC LIMIT 200")
    fun getHistoryForProfile(profileId: String): Flow<List<HistoryItem>>

    @Query("SELECT url, title, count(*) as visitCount FROM history_entries WHERE profileId = :profileId GROUP BY url ORDER BY visitCount DESC, visitedAt DESC LIMIT 12")
    fun getTopVisitedHistory(profileId: String): Flow<List<TopSiteDto>>

    @Query("SELECT * FROM history_entries WHERE profileId = :profileId AND (title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%') ORDER BY visitedAt DESC LIMIT 50")
    fun searchHistory(profileId: String, query: String): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryItem)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM history_entries WHERE profileId = :profileId")
    suspend fun deleteHistoryForProfile(profileId: String)

    @Query("DELETE FROM history_entries")
    suspend fun deleteAllHistory()
}

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM custom_shortcuts WHERE profileId = :profileId ORDER BY position ASC, createdAt ASC")
    fun getShortcutsForProfile(profileId: String): Flow<List<CustomShortcut>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: CustomShortcut)

    @Update
    suspend fun updateShortcut(shortcut: CustomShortcut)

    @Query("DELETE FROM custom_shortcuts WHERE id = :id")
    suspend fun deleteShortcutById(id: String)

    @Query("DELETE FROM custom_shortcuts WHERE profileId = :profileId")
    suspend fun deleteShortcutsForProfile(profileId: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getDownloadsForProfile(profileId: String): Flow<List<DownloadItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun deleteAllDownloads()
}

@Dao
interface PrivacyDao {
    @Query("SELECT * FROM site_adblock_exceptions")
    fun getAllExceptions(): Flow<List<SiteException>>

    @Query("SELECT * FROM site_adblock_exceptions WHERE domain = :domain LIMIT 1")
    suspend fun getExceptionForDomain(domain: String): SiteException?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setException(exception: SiteException)

    @Query("DELETE FROM site_adblock_exceptions WHERE domain = :domain")
    suspend fun removeException(domain: String)

    @Query("DELETE FROM site_adblock_exceptions")
    suspend fun clearAllExceptions()
}

@Dao
interface DismissedShortcutDao {
    @Query("SELECT url FROM dismissed_shortcuts WHERE profileId = :profileId")
    fun getDismissedUrlsForProfile(profileId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissedShortcut(shortcut: DismissedShortcut)

    @Query("DELETE FROM dismissed_shortcuts WHERE profileId = :profileId AND url = :url")
    suspend fun deleteDismissedShortcut(profileId: String, url: String)

    @Query("DELETE FROM dismissed_shortcuts WHERE profileId = :profileId")
    suspend fun clearDismissedShortcutsForProfile(profileId: String)
}
