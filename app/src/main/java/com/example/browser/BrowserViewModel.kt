package com.example.browser

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BrowserRepository
import com.example.data.model.*
import com.example.privacy.ContentBlocker
import com.example.privacy.PrivacyManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val database = AppDatabase.getDatabase(context)
    val repository = BrowserRepository(database)
    val privacyManager = PrivacyManager(context, database)

    // Current Active Profile
    private val _currentProfileId = MutableStateFlow("default_personal")
    val currentProfileId: StateFlow<String> = _currentProfileId.asStateFlow()

    // Is in Temporary Private / Incognito Mode
    private val _isPrivateMode = MutableStateFlow(false)
    val isPrivateMode: StateFlow<Boolean> = _isPrivateMode.asStateFlow()

    // Profiles Flow from DB
    val profiles: StateFlow<List<BrowserProfile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Active Profile Object
    val currentProfile: StateFlow<BrowserProfile?> = combine(profiles, currentProfileId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Current Normal Tabs for Active Profile
    private val _normalTabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val normalTabs: StateFlow<List<BrowserTab>> = _normalTabs.asStateFlow()

    // Private Tabs Flow
    val privateTabs: StateFlow<List<BrowserTab>> = repository.privateTabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Displayed Tabs depending on mode (Normal or Private)
    val currentTabs: StateFlow<List<BrowserTab>> = combine(_normalTabs, privateTabs, isPrivateMode) { normal, priv, isPriv ->
        if (isPriv) priv else normal
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Active Tab ID
    private val _activeTabId = MutableStateFlow<String>("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    // Active Tab Runtime State (url, title, progress, etc.)
    private val _activeTabState = MutableStateFlow<ActiveTabState?>(null)
    val activeTabState: StateFlow<ActiveTabState?> = _activeTabState.asStateFlow()

    // Navigation / Action Events for WebView (buffered to prevent dropped navigation events)
    private val _webViewActionEvent = MutableSharedFlow<WebViewAction>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val webViewActionEvent: SharedFlow<WebViewAction> = _webViewActionEvent.asSharedFlow()

    // Reactive Total Blocked Items Flow
    val totalBlockedCount: StateFlow<Int> = ContentBlocker.totalBlockedCount

    // Sheets & Dialogs
    private val _activeSheet = MutableStateFlow(ActiveSheet.NONE)
    val activeSheet: StateFlow<ActiveSheet> = _activeSheet.asStateFlow()

    // Find in Page state
    private val _isFindInPageActive = MutableStateFlow(false)
    val isFindInPageActive: StateFlow<Boolean> = _isFindInPageActive.asStateFlow()
    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()

    // Settings State
    val searchEngine = MutableStateFlow(SearchEngine.GOOGLE)
    val themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val useMaterialYou = MutableStateFlow(true)
    val newTabStyle = MutableStateFlow(NewTabStyle.PRODUCTIVITY)
    val isAdBlockEnabled = MutableStateFlow(true)
    val blockThirdPartyCookies = MutableStateFlow(true)
    val httpsMode = MutableStateFlow(HttpsMode.PREFER_HTTPS)
    val enableWebDarkMode = MutableStateFlow(false)
    val enableBackgroundPlay = MutableStateFlow(true)
    val downloadProvider = MutableStateFlow(DownloadProvider.BUILT_IN)

    fun setThemeMode(mode: AppThemeMode) {
        themeMode.value = mode
    }

    fun setUseMaterialYou(enabled: Boolean) {
        useMaterialYou.value = enabled
    }

    fun setNewTabStyle(style: NewTabStyle) {
        newTabStyle.value = style
    }

    fun setBackgroundPlay(enabled: Boolean) {
        enableBackgroundPlay.value = enabled
    }

    fun setDownloadProvider(provider: DownloadProvider) {
        downloadProvider.value = provider
    }

    // Whitelisted domains flow
    val adBlockExceptions: StateFlow<List<SiteException>> = repository.exceptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bookmarks for current profile
    val bookmarks: StateFlow<List<Bookmark>> = currentProfileId.flatMapLatest { profileId ->
        repository.getBookmarks(profileId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // History for current profile
    val history: StateFlow<List<HistoryItem>> = currentProfileId.flatMapLatest { profileId ->
        repository.getHistory(profileId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Downloads for current profile
    val downloads: StateFlow<List<DownloadItem>> = currentProfileId.flatMapLatest { profileId ->
        repository.getDownloads(profileId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Custom Shortcuts & Top Visited Sites
    val customShortcuts: StateFlow<List<CustomShortcut>> = currentProfileId.flatMapLatest { profileId ->
        repository.getCustomShortcuts(profileId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topVisitedSites: StateFlow<List<TopSiteDto>> = currentProfileId.flatMapLatest { profileId ->
        repository.getTopVisitedSites(profileId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dismissedShortcuts: StateFlow<List<String>> = currentProfileId.flatMapLatest { profileId ->
        repository.getDismissedShortcuts(profileId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickShortcuts: StateFlow<List<QuickShortcutItem>> = combine(
        customShortcuts,
        topVisitedSites,
        dismissedShortcuts
    ) { custom, top, dismissed ->
        val dismissedSet = dismissed.toHashSet()
        val list = mutableListOf<QuickShortcutItem>()
        custom.forEach {
            list.add(QuickShortcutItem(id = it.id, title = it.title, url = it.url, isAutoGenerated = false))
        }
        top.forEach { topSite ->
            val domain = UrlUtils.extractDomain(topSite.url)
            val isDismissed = dismissedSet.contains(topSite.url) || dismissedSet.contains(domain)
            if (!isDismissed && list.none { UrlUtils.extractDomain(it.url) == domain || it.url == topSite.url }) {
                list.add(QuickShortcutItem(id = "auto_" + topSite.url.hashCode(), title = topSite.title.ifBlank { domain }, url = topSite.url, isAutoGenerated = true))
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addQuickShortcut(title: String, url: String) {
        val parsedUrl = UrlUtils.parseInputToUrl(url)
        viewModelScope.launch {
            repository.saveCustomShortcut(_currentProfileId.value, title, parsedUrl)
        }
    }

    fun editQuickShortcut(id: String, title: String, url: String) {
        val parsedUrl = UrlUtils.parseInputToUrl(url)
        viewModelScope.launch {
            if (id.startsWith("auto_")) {
                val oldItem = quickShortcuts.value.firstOrNull { it.id == id }
                if (oldItem != null) {
                    repository.dismissShortcut(_currentProfileId.value, oldItem.url)
                }
                repository.saveCustomShortcut(_currentProfileId.value, title, parsedUrl)
            } else {
                repository.saveCustomShortcut(_currentProfileId.value, title, parsedUrl, id = id)
            }
        }
    }

    fun removeQuickShortcut(id: String) {
        viewModelScope.launch {
            if (id.startsWith("auto_")) {
                val item = quickShortcuts.value.firstOrNull { it.id == id }
                if (item != null) {
                    repository.dismissShortcut(_currentProfileId.value, item.url)
                }
            } else {
                repository.deleteCustomShortcut(id)
            }
        }
    }

    // Is Current URL Bookmarked
    val isCurrentUrlBookmarked: StateFlow<Boolean> = combine(bookmarks, _activeTabState) { bList, tab ->
        val url = tab?.url ?: ""
        if (url.isBlank()) false else bList.any { it.url == url }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        val defaultTabId = UUID.randomUUID().toString()
        _activeTabId.value = defaultTabId
        _activeTabState.value = ActiveTabState(
            id = defaultTabId,
            profileId = "default_personal",
            url = "",
            title = "New Tab",
            isPrivate = false
        )
        viewModelScope.launch {
            repository.initializeDefaultProfilesIfNeeded()
            repository.initializeDefaultShortcutsIfNeeded("default_personal")
            loadTabsForProfile(_currentProfileId.value)
        }
    }

    private var tabsJob: kotlinx.coroutines.Job? = null

    private fun loadTabsForProfile(profileId: String) {
        tabsJob?.cancel()
        tabsJob = viewModelScope.launch {
            repository.getTabsForProfile(profileId).collect { tabs ->
                _normalTabs.value = tabs
                if (!_isPrivateMode.value) {
                    if (tabs.isEmpty()) {
                        // Create and persist initial tab for this profile if empty
                        val initialTabId = UUID.randomUUID().toString()
                        val initialTab = BrowserTab(
                            id = initialTabId,
                            profileId = profileId,
                            url = "",
                            title = "New Tab",
                            isPrivate = false
                        )
                        _activeTabId.value = initialTabId
                        _activeTabState.value = ActiveTabState(
                            id = initialTabId,
                            profileId = profileId,
                            url = "",
                            title = "New Tab",
                            isPrivate = false
                        )
                        repository.saveTab(initialTab)
                    } else {
                        val currentTabId = _activeTabId.value
                        val existingTab = tabs.find { it.id == currentTabId }
                        if (existingTab == null) {
                            selectTab(tabs.first().id)
                        } else {
                            val curState = _activeTabState.value
                            if (curState == null || curState.id != existingTab.id) {
                                val currentBlocked = ContentBlocker.getBlockCountForTab(existingTab.id)
                                _activeTabState.value = ActiveTabState(
                                    id = existingTab.id,
                                    profileId = existingTab.profileId,
                                    url = existingTab.url,
                                    title = existingTab.title,
                                    isPrivate = existingTab.isPrivate,
                                    isDesktopMode = existingTab.isDesktopMode,
                                    blockedCount = currentBlocked
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun switchProfile(profileId: String) {
        if (_currentProfileId.value == profileId && !_isPrivateMode.value) {
            dismissSheet()
            return
        }
        _isPrivateMode.value = false
        _currentProfileId.value = profileId
        _activeTabId.value = ""
        _activeTabState.value = null
        loadTabsForProfile(profileId)
        dismissSheet()
    }

    fun togglePrivateMode() {
        val newPrivate = !_isPrivateMode.value
        _isPrivateMode.value = newPrivate
        _activeTabId.value = ""
        _activeTabState.value = null

        if (newPrivate) {
            viewModelScope.launch {
                val privTabs = privateTabs.value
                if (privTabs.isEmpty()) {
                    createNewTab(profileId = "private_session", isPrivate = true)
                } else {
                    selectTab(privTabs.first().id)
                }
            }
        } else {
            // Restore normal profile tabs
            val normal = _normalTabs.value
            if (normal.isNotEmpty()) {
                selectTab(normal.first().id)
            } else {
                createNewTab(profileId = _currentProfileId.value, isPrivate = false)
            }
        }
        dismissSheet()
    }

    fun createNewProfile(
        displayName: String,
        iconName: String,
        colorHex: String,
        fingerprintPreset: String = "DEFAULT"
    ) {
        viewModelScope.launch {
            val newProfile = repository.createProfile(
                displayName = displayName,
                iconName = iconName,
                colorHex = colorHex,
                fingerprintPreset = fingerprintPreset
            )
            switchProfile(newProfile.id)
        }
    }

    fun updateProfile(profile: BrowserProfile) {
        viewModelScope.launch {
            repository.updateProfile(profile)
        }
    }

    fun deleteProfile(profileId: String) {
        if (profileId == "default_personal") return // Protect default profile
        viewModelScope.launch {
            repository.deleteProfile(profileId)
            if (_currentProfileId.value == profileId) {
                switchProfile("default_personal")
            }
        }
    }

    fun createNewTab(url: String = "", profileId: String = if (_isPrivateMode.value) "private_session" else _currentProfileId.value, isPrivate: Boolean = _isPrivateMode.value) {
        viewModelScope.launch {
            val tabId = UUID.randomUUID().toString()
            val newTab = BrowserTab(
                id = tabId,
                profileId = profileId,
                url = url,
                title = if (url.isBlank()) "New Tab" else url,
                isPrivate = isPrivate
            )
            repository.saveTab(newTab)
            selectTab(tabId)
            dismissSheet()
        }
    }

    fun selectTab(tabId: String) {
        _activeTabId.value = tabId
        val tab = currentTabs.value.find { it.id == tabId }
        val currentBlocked = ContentBlocker.getBlockCountForTab(tabId)
        _activeTabState.value = ActiveTabState(
            id = tabId,
            profileId = tab?.profileId ?: _currentProfileId.value,
            url = tab?.url ?: "",
            title = tab?.title ?: "New Tab",
            isPrivate = tab?.isPrivate ?: _isPrivateMode.value,
            isDesktopMode = tab?.isDesktopMode ?: false,
            blockedCount = currentBlocked
        )
        dismissSheet()
    }

    fun closeTab(tabId: String) {
        viewModelScope.launch {
            repository.deleteTab(tabId)
            ContentBlocker.resetTabBlockCount(tabId)
            val remaining = currentTabs.value.filter { it.id != tabId }
            if (remaining.isNotEmpty()) {
                if (_activeTabId.value == tabId) {
                    selectTab(remaining.first().id)
                }
            } else {
                createNewTab(isPrivate = _isPrivateMode.value)
            }
        }
    }

    fun closeAllTabs() {
        viewModelScope.launch {
            if (_isPrivateMode.value) {
                repository.clearPrivateTabs()
                privacyManager.cleanPrivateSessionData()
            } else {
                repository.getTabsForProfile(_currentProfileId.value).firstOrNull()?.forEach {
                    repository.deleteTab(it.id)
                }
            }
            createNewTab(isPrivate = _isPrivateMode.value)
            dismissSheet()
        }
    }

    fun navigateTo(input: String) {
        val parsedUrl = UrlUtils.parseInputToUrl(input, searchEngine.value)
        if (parsedUrl.isNotBlank()) {
            var tabId = _activeTabId.value
            if (tabId.isBlank()) {
                tabId = UUID.randomUUID().toString()
                _activeTabId.value = tabId
            }
            val curState = _activeTabState.value
            if (curState == null) {
                _activeTabState.value = ActiveTabState(
                    id = tabId,
                    profileId = if (_isPrivateMode.value) "private_session" else _currentProfileId.value,
                    url = parsedUrl,
                    title = parsedUrl,
                    isPrivate = _isPrivateMode.value,
                    isLoading = true,
                    progress = 10
                )
            } else {
                _activeTabState.update { it?.copy(url = parsedUrl, progress = 10, isLoading = true) }
            }
            viewModelScope.launch {
                _webViewActionEvent.emit(WebViewAction.LoadUrl(parsedUrl))
                val cur = currentTabs.value.find { it.id == tabId }
                if (cur != null) {
                    repository.saveTab(cur.copy(url = parsedUrl))
                } else {
                    repository.saveTab(
                        BrowserTab(
                            id = tabId,
                            profileId = if (_isPrivateMode.value) "private_session" else _currentProfileId.value,
                            url = parsedUrl,
                            title = parsedUrl,
                            isPrivate = _isPrivateMode.value
                        )
                    )
                }
            }
        }
    }

    fun reload() {
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.Reload) }
    }

    fun stopLoading() {
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.StopLoading) }
    }

    fun goBack() {
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.GoBack) }
    }

    fun goForward() {
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.GoForward) }
    }

    fun goHome() {
        val tabId = _activeTabId.value
        _activeTabState.update { it?.copy(url = "", title = "New Tab", progress = 0, isLoading = false) }
        viewModelScope.launch {
            _webViewActionEvent.emit(WebViewAction.LoadUrl("about:blank"))
            val cur = currentTabs.value.find { it.id == tabId }
            if (cur != null) {
                repository.saveTab(cur.copy(url = "", title = "New Tab"))
            }
        }
    }

    fun toggleDesktopMode() {
        val newDesktop = !(_activeTabState.value?.isDesktopMode ?: false)
        _activeTabState.update { it?.copy(isDesktopMode = newDesktop) }
        viewModelScope.launch {
            _webViewActionEvent.emit(WebViewAction.SetDesktopMode(newDesktop))
            val cur = currentTabs.value.find { it.id == _activeTabId.value }
            if (cur != null) {
                repository.saveTab(cur.copy(isDesktopMode = newDesktop))
            }
        }
    }

    fun startFindInPage() {
        _isFindInPageActive.value = true
        _findQuery.value = ""
    }

    fun closeFindInPage() {
        _isFindInPageActive.value = false
        _findQuery.value = ""
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.ClearFindMatches) }
    }

    fun setFindQuery(query: String) {
        _findQuery.value = query
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.FindAllAsync(query)) }
    }

    fun findNext(forward: Boolean) {
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.FindNext(forward)) }
    }

    fun onFindMatchResult(activeMatchOrdinal: Int, numberOfMatches: Int) {
        _activeTabState.update {
            it?.copy(
                searchMatchCurrent = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0,
                searchMatchCount = numberOfMatches
            )
        }
    }

    // Callbacks from WebView
    fun onPageStarted(url: String) {
        if (url.isBlank() || url == "about:blank") {
            _activeTabState.update { it?.copy(isLoading = false, progress = 0) }
            return
        }
        onUrlChanged(url)
        _activeTabState.update {
            it?.copy(
                isLoading = true,
                progress = it.progress.coerceIn(15, 90)
            )
        }
    }

    fun onPageFinished(url: String) {
        _activeTabState.update {
            it?.copy(
                isLoading = false,
                progress = 100
            )
        }
    }

    fun onPageLoadError() {
        _activeTabState.update {
            it?.copy(
                isLoading = false,
                progress = 100
            )
        }
    }

    fun onUrlChanged(url: String) {
        if (url.isBlank() || url == "about:blank") return
        val isSec = UrlUtils.isHttps(url)
        val tabId = _activeTabId.value
        val blocked = ContentBlocker.getBlockCountForTab(tabId)
        
        val curState = _activeTabState.value
        if (curState?.url == url && curState.isSecure == isSec && curState.blockedCount == blocked) {
            return
        }

        _activeTabState.update {
            it?.copy(url = url, isSecure = isSec, blockedCount = blocked)
        }
        viewModelScope.launch {
            val cur = currentTabs.value.find { it.id == tabId }
            if (cur != null && cur.url != url) {
                repository.saveTab(cur.copy(url = url, lastAccessedAt = System.currentTimeMillis()))
            }
        }
    }

    fun onTitleChanged(title: String) {
        if (title.isBlank() || title == "about:blank") return
        val tabId = _activeTabId.value
        val curState = _activeTabState.value
        if (curState?.title == title) return

        _activeTabState.update { it?.copy(title = title) }
        viewModelScope.launch {
            val cur = currentTabs.value.find { it.id == tabId }
            if (cur != null) {
                if (cur.title != title) {
                    repository.saveTab(cur.copy(title = title))
                }
                // Record history if not private
                val url = _activeTabState.value?.url ?: ""
                if (url.isNotBlank() && url != "about:blank") {
                    repository.recordHistory(
                        profileId = cur.profileId,
                        title = title,
                        url = url,
                        isPrivate = cur.isPrivate
                    )
                }
            }
        }
    }

    fun onProgressChanged(progress: Int) {
        val curState = _activeTabState.value ?: return
        // If on Home or about:blank, keep loading disabled
        if (curState.url.isBlank() || curState.url == "about:blank") {
            if (curState.isLoading || curState.progress != 0) {
                _activeTabState.update { it?.copy(isLoading = false, progress = 0) }
            }
            return
        }

        if (progress >= 100) {
            if (curState.isLoading || curState.progress != 100) {
                _activeTabState.update { it?.copy(isLoading = false, progress = 100) }
            }
        } else if (curState.isLoading) {
            // Only update progress increment while a real page load is active
            if (curState.progress != progress) {
                _activeTabState.update { it?.copy(progress = progress) }
            }
        }
    }

    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        val curState = _activeTabState.value ?: return
        if (curState.canGoBack == canGoBack && curState.canGoForward == canGoForward) {
            return
        }
        _activeTabState.update {
            it?.copy(canGoBack = canGoBack, canGoForward = canGoForward)
        }
    }

    fun onBlockerHit(tabId: String) {
        val count = ContentBlocker.getBlockCountForTab(tabId)
        if (_activeTabId.value == tabId) {
            _activeTabState.update { it?.copy(blockedCount = count) }
        }
    }

    // Bookmark Toggle
    fun toggleBookmarkCurrentUrl() {
        val tab = _activeTabState.value ?: return
        val url = tab.url
        if (url.isBlank() || url == "about:blank") return

        viewModelScope.launch {
            val isBm = repository.isBookmarked(tab.profileId, url)
            if (isBm) {
                val currentBm = bookmarks.value.find { it.url == url }
                if (currentBm != null) {
                    repository.deleteBookmark(currentBm)
                    Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                }
            } else {
                repository.addBookmark(tab.profileId, tab.title.ifBlank { url }, url)
                Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistoryForProfile(_currentProfileId.value)
            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteDownloadItem(id: Long) {
        viewModelScope.launch {
            repository.deleteDownloadItem(id)
        }
    }

    fun openDownloadedFile(item: DownloadItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(item.fileUri ?: item.url), item.mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Download handler from WebView
    fun handleDownloadRequest(url: String, userAgent: String, contentDisposition: String, mimetype: String, contentLength: Long) {
        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
        if (downloadProvider.value == DownloadProvider.EXTERNAL_APP) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val resolvedType = mimetype.ifBlank { "application/octet-stream" }
                    setDataAndType(Uri.parse(url), resolvedType)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, context.packageName)
                    putExtra(android.provider.Browser.EXTRA_HEADERS, Bundle().apply {
                        putString("User-Agent", userAgent)
                    })
                }
                val chooser = Intent.createChooser(intent, "Download with...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                viewModelScope.launch {
                    repository.recordDownload(
                        profileId = _currentProfileId.value,
                        url = url,
                        filename = filename,
                        mimeType = mimetype,
                        sizeBytes = contentLength
                    )
                }
                Toast.makeText(context, "Opening downloader for $filename...", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                // Fallback to built-in if no external downloader resolved
            }
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimetype)
                addRequestHeader("User-Agent", userAgent)
                setDescription("Downloading $filename")
                setTitle(filename)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            }
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            viewModelScope.launch {
                repository.recordDownload(
                    profileId = _currentProfileId.value,
                    url = url,
                    filename = filename,
                    mimeType = mimetype,
                    sizeBytes = contentLength
                )
            }
            Toast.makeText(context, "Downloading $filename...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Privacy Shield Whitelist Toggle
    fun toggleCurrentSiteAdBlockException() {
        val url = _activeTabState.value?.url ?: return
        val domain = UrlUtils.extractDomain(url)
        if (domain.isBlank()) return

        viewModelScope.launch {
            val isExempt = repository.isSiteAdBlockDisabled(domain)
            repository.toggleSiteAdBlockException(domain, !isExempt)
            reload()
        }
    }

    // Sheets management
    fun openSheet(sheet: ActiveSheet) {
        _activeSheet.value = sheet
    }

    fun dismissSheet() {
        _activeSheet.value = ActiveSheet.NONE
    }

    // Clear Data Action
    fun executeClearData(history: Boolean, cookies: Boolean, cache: Boolean, siteData: Boolean, downloads: Boolean) {
        viewModelScope.launch {
            privacyManager.clearBrowsingData(
                clearHistory = history,
                clearCookies = cookies,
                clearCache = cache,
                clearSiteData = siteData,
                clearDownloads = downloads
            )
            Toast.makeText(context, "Selected browsing data cleared", Toast.LENGTH_SHORT).show()
            dismissSheet()
        }
    }
}

sealed class WebViewAction {
    data class LoadUrl(val url: String) : WebViewAction()
    data object Reload : WebViewAction()
    data object StopLoading : WebViewAction()
    data object GoBack : WebViewAction()
    data object GoForward : WebViewAction()
    data class SetDesktopMode(val enabled: Boolean) : WebViewAction()
    data class FindAllAsync(val query: String) : WebViewAction()
    data class FindNext(val forward: Boolean) : WebViewAction()
    data object ClearFindMatches : WebViewAction()
}
