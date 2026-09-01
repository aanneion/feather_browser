package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "browser_profiles")
data class BrowserProfile(
    @PrimaryKey val id: String,
    val displayName: String,
    val iconName: String = "person",
    val colorHex: String = "#3B82F6",
    val createdAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false,
    val fingerprintPreset: String = "DEFAULT",
    val customUserAgent: String = ""
)

@Entity(tableName = "browser_tabs")
data class BrowserTab(
    @PrimaryKey val id: String,
    val profileId: String,
    val url: String = "",
    val title: String = "New Tab",
    val faviconUrl: String? = null,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false,
    val isDesktopMode: Boolean = false
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val folderName: String = "Mobile Bookmarks",
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history_entries")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val url: String,
    val filename: String,
    val mimeType: String,
    val status: String = "COMPLETED", // DOWNLOADING, COMPLETED, FAILED
    val fileUri: String? = null,
    val fileSizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "site_adblock_exceptions")
data class SiteException(
    @PrimaryKey val domain: String,
    val isAdBlockDisabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_shortcuts")
data class CustomShortcut(
    @PrimaryKey val id: String,
    val profileId: String,
    val title: String,
    val url: String,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dismissed_shortcuts")
data class DismissedShortcut(
    @PrimaryKey val id: String,
    val profileId: String,
    val url: String,
    val dismissedAt: Long = System.currentTimeMillis()
)

data class TopSiteDto(
    val url: String,
    val title: String,
    val visitCount: Int
)
