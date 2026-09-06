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
import com.example.data.BrowserPreferences
import com.example.data.BookmarkExportHelper
import com.example.data.BrowserRepository
import com.example.data.model.*
import com.example.privacy.ContentBlocker
import com.example.privacy.PrivacyManager
import com.example.weather.WeatherRepository
import com.example.weather.WeatherUiState
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

    // Immersive Auto-Hiding Controls state (bars hide when scrolling down, reappear when scrolling up)
    private val _isBarsVisible = MutableStateFlow(true)
    val isBarsVisible: StateFlow<Boolean> = _isBarsVisible.asStateFlow()
    private var accumulatedScrollY = 0
    private var lastVisibilityToggleTime = 0L

    fun setBarsVisible(visible: Boolean) {
        if (_isBarsVisible.value != visible) {
            _isBarsVisible.value = visible
            lastVisibilityToggleTime = System.currentTimeMillis()
        }
        accumulatedScrollY = 0
    }

    fun onWebScroll(deltaY: Int, scrollY: Int) {
        val now = System.currentTimeMillis()

        // When near page top, always smoothly restore bars
        if (scrollY <= 35) {
            accumulatedScrollY = 0
            if (!_isBarsVisible.value) {
                _isBarsVisible.value = true
                lastVisibilityToggleTime = now
            }
            return
        }

        // Filter out microscopic finger tremors (< 3px)
        if (kotlin.math.abs(deltaY) < 3) return

        // Direction reversal: clean reset so sudden scroll direction changes respond immediately
        if ((deltaY > 0 && accumulatedScrollY < 0) || (deltaY < 0 && accumulatedScrollY > 0)) {
            accumulatedScrollY = 0
        }
        accumulatedScrollY += deltaY

        // Deliberate user scroll threshold to trigger hide or show
        if (accumulatedScrollY > 60) {
            // Scrolling down: hide bars with light debounce to prevent flapping
            if (_isBarsVisible.value && now - lastVisibilityToggleTime > 120) {
                _isBarsVisible.value = false
                lastVisibilityToggleTime = now
            }
            accumulatedScrollY = 0
        } else if (accumulatedScrollY < -25) {
            // Scrolling up: show bars promptly without artificial lag or dropped touch frames
            if (!_isBarsVisible.value) {
                _isBarsVisible.value = true
                lastVisibilityToggleTime = now
            }
            accumulatedScrollY = 0
        }
    }

    // Link / Image Long-Press Context Menu
    private val _contextMenuData = MutableStateFlow<ContextMenuData?>(null)
    val contextMenuData: StateFlow<ContextMenuData?> = _contextMenuData.asStateFlow()

    fun showContextMenu(data: ContextMenuData) {
        _contextMenuData.value = data
    }

    fun dismissContextMenu() {
        _contextMenuData.value = null
    }

    fun openLinkInNewTab(
        url: String,
        openInBackground: Boolean = false,
        isPrivate: Boolean = _isPrivateMode.value
    ) {
        viewModelScope.launch {
            val tabId = UUID.randomUUID().toString()
            val targetProfileId = if (isPrivate) "private_session" else _currentProfileId.value
            val newTab = BrowserTab(
                id = tabId,
                profileId = targetProfileId,
                url = url,
                title = url,
                isPrivate = isPrivate
            )
            repository.saveTab(newTab)
            if (!openInBackground) {
                if (isPrivate && !_isPrivateMode.value) {
                    _isPrivateMode.value = true
                } else if (!isPrivate && _isPrivateMode.value) {
                    _isPrivateMode.value = false
                }
                selectTab(tabId, autoDismiss = true)
            }
        }
    }

    // Find in Page state
    private val _isFindInPageActive = MutableStateFlow(false)
    val isFindInPageActive: StateFlow<Boolean> = _isFindInPageActive.asStateFlow()
    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()

    // Settings Preferences
    val preferences = BrowserPreferences(application)

    // Settings State
    val searchEngine = MutableStateFlow(preferences.getSearchEngine())
    val themeMode = MutableStateFlow(preferences.getThemeMode())
    val useMaterialYou = MutableStateFlow(preferences.getUseMaterialYou())
    val newTabStyle = MutableStateFlow(preferences.getNewTabStyle())
    val isAdBlockEnabled = MutableStateFlow(preferences.isAdBlockEnabled())
    val blockThirdPartyCookies = MutableStateFlow(preferences.isBlockThirdPartyCookies())
    val httpsMode = MutableStateFlow(preferences.getHttpsMode())
    val enableWebDarkMode = MutableStateFlow(preferences.isEnableWebDarkMode())
    val enableBackgroundPlay = MutableStateFlow(preferences.isEnableBackgroundPlay())
    val downloadProvider = MutableStateFlow(preferences.getDownloadProvider())
    val isWeatherOnNewTab = MutableStateFlow(preferences.isWeatherOnNewTab())
    val isWeatherFahrenheit = MutableStateFlow(preferences.isWeatherFahrenheit())
    val toolbarPosition = MutableStateFlow(preferences.getToolbarPosition())

    // Reader Mode State
    private val _readerArticle = MutableStateFlow<ReaderArticle?>(null)
    val readerArticle: StateFlow<ReaderArticle?> = _readerArticle.asStateFlow()

    private val _readerTheme = MutableStateFlow(ReaderTheme.SEPIA)
    val readerTheme: StateFlow<ReaderTheme> = _readerTheme.asStateFlow()

    private val _readerFontSize = MutableStateFlow(18)
    val readerFontSize: StateFlow<Int> = _readerFontSize.asStateFlow()

    private val _readerIsSerif = MutableStateFlow(true)
    val readerIsSerif: StateFlow<Boolean> = _readerIsSerif.asStateFlow()

    fun setToolbarPosition(position: ToolbarPosition) {
        toolbarPosition.value = position
        preferences.setToolbarPosition(position)
    }

    fun setReaderTheme(theme: ReaderTheme) { _readerTheme.value = theme }
    fun setReaderFontSize(size: Int) { _readerFontSize.value = size.coerceIn(12, 28) }
    fun setReaderIsSerif(isSerif: Boolean) { _readerIsSerif.value = isSerif }
    fun setReaderArticle(article: ReaderArticle?) { _readerArticle.value = article }

    fun openReaderMode() {
        val tabId = _activeTabId.value
        val currentTab = _activeTabState.value
        if (tabId.isBlank() || currentTab == null || currentTab.url.isBlank() || currentTab.url == "about:blank") return

        viewModelScope.launch {
            _webViewActionEvent.emit(WebViewAction.ExtractReaderContent(
                callback = { extracted ->
                    if (extracted != null && extracted.contentText.isNotBlank()) {
                        _readerArticle.value = extracted
                        openSheet(ActiveSheet.READER_MODE)
                    } else {
                        // Fallback article if extraction returns sparse text
                        val fallback = ReaderArticle(
                            title = currentTab.title,
                            domain = UrlUtils.extractDomain(currentTab.url),
                            contentText = "Unable to extract formatted article content for this page. The page may not contain long-form text or may be an interactive web application.",
                            wordCount = 25,
                            readingTimeMinutes = 1
                        )
                        _readerArticle.value = fallback
                        openSheet(ActiveSheet.READER_MODE)
                    }
                },
                targetTabId = tabId
            ))
        }
    }

    // Weather Repository & UI State Flow
    val weatherRepository = WeatherRepository(application)
    val weatherUiState: StateFlow<WeatherUiState> = weatherRepository.weatherState

    fun setWeatherOnNewTab(enabled: Boolean) {
        isWeatherOnNewTab.value = enabled
        preferences.setWeatherOnNewTab(enabled)
    }

    fun setWeatherFahrenheit(fahrenheit: Boolean) {
        isWeatherFahrenheit.value = fahrenheit
        preferences.setWeatherFahrenheit(fahrenheit)
    }

    fun refreshWeather(forceNetwork: Boolean = false) {
        viewModelScope.launch {
            weatherRepository.refreshWeather(forceNetwork)
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        searchEngine.value = engine
        preferences.setSearchEngine(engine)
    }

    fun setThemeMode(mode: AppThemeMode) {
        themeMode.value = mode
        preferences.setThemeMode(mode)
    }

    fun setUseMaterialYou(enabled: Boolean) {
        useMaterialYou.value = enabled
        preferences.setUseMaterialYou(enabled)
    }

    fun setNewTabStyle(style: NewTabStyle) {
        newTabStyle.value = style
        preferences.setNewTabStyle(style)
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        isAdBlockEnabled.value = enabled
        preferences.setAdBlockEnabled(enabled)
    }

    fun setBlockThirdPartyCookies(enabled: Boolean) {
        blockThirdPartyCookies.value = enabled
        preferences.setBlockThirdPartyCookies(enabled)
    }

    fun setHttpsMode(mode: HttpsMode) {
        httpsMode.value = mode
        preferences.setHttpsMode(mode)
    }

    fun setEnableWebDarkMode(enabled: Boolean) {
        enableWebDarkMode.value = enabled
        preferences.setEnableWebDarkMode(enabled)
    }

    fun setBackgroundPlay(enabled: Boolean) {
        enableBackgroundPlay.value = enabled
        preferences.setEnableBackgroundPlay(enabled)
    }

    fun setDownloadProvider(provider: DownloadProvider) {
        downloadProvider.value = provider
        preferences.setDownloadProvider(provider)
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
            if (isWeatherOnNewTab.value) {
                weatherRepository.refreshWeather(forceNetwork = false)
            }
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
                            selectTab(tabs.first().id, autoDismiss = false)
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

    fun switchToNextProfile() {
        val list = profiles.value
        if (list.size <= 1) return
        val currentIndex = list.indexOfFirst { it.id == _currentProfileId.value }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % list.size else 0
        switchProfile(list[nextIndex].id)
    }

    fun switchToPrevProfile() {
        val list = profiles.value
        if (list.size <= 1) return
        val currentIndex = list.indexOfFirst { it.id == _currentProfileId.value }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        switchProfile(list[prevIndex].id)
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

    fun createNewTab(
        url: String = "",
        profileId: String = if (_isPrivateMode.value) "private_session" else _currentProfileId.value,
        isPrivate: Boolean = _isPrivateMode.value,
        autoDismiss: Boolean = true
    ) {
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
            selectTab(tabId, autoDismiss = autoDismiss)
        }
    }

    fun selectTab(tabId: String, autoDismiss: Boolean = true) {
        setBarsVisible(true)
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
        if (autoDismiss) {
            dismissSheet()
        }
    }

    fun closeTab(tabId: String) {
        viewModelScope.launch {
            repository.deleteTab(tabId)
            ContentBlocker.resetTabBlockCount(tabId)
            try {
                if (com.example.media.MediaSessionManager.activeMediaTabId.value == tabId) {
                    com.example.media.MediaSessionManager.onMediaEnded(context, tabId)
                }
            } catch (e: Throwable) { }
            val remaining = currentTabs.value.filter { it.id != tabId }
            if (remaining.isNotEmpty()) {
                if (_activeTabId.value == tabId) {
                    selectTab(remaining.first().id, autoDismiss = false)
                }
            } else {
                createNewTab(isPrivate = _isPrivateMode.value, autoDismiss = false)
            }
        }
    }

    fun closeAllTabs() {
        viewModelScope.launch {
            try {
                com.example.media.MediaSessionManager.stopPlayback(context)
            } catch (e: Throwable) { }
            val profileId = if (_isPrivateMode.value) "private_session" else _currentProfileId.value
            val singleTab = repository.resetTabsToSingleTab(profileId, _isPrivateMode.value)
            if (_isPrivateMode.value) {
                privacyManager.cleanPrivateSessionData()
            }
            _activeTabId.value = singleTab.id
            _activeTabState.value = ActiveTabState(
                id = singleTab.id,
                profileId = singleTab.profileId,
                url = "",
                title = "New Tab",
                isPrivate = singleTab.isPrivate,
                isDesktopMode = false,
                blockedCount = 0
            )
        }
    }

    fun navigateTo(input: String) {
        setBarsVisible(true)
        val parsedUrl = UrlUtils.parseInputToUrl(input, searchEngine.value)
        if (parsedUrl.isNotBlank()) {
            var tabId = _activeTabId.value
            if (tabId.isBlank()) {
                tabId = UUID.randomUUID().toString()
                _activeTabId.value = tabId
            }
            val curState = _activeTabState.value
            if (curState == null || curState.id != tabId) {
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
                _webViewActionEvent.emit(WebViewAction.LoadUrl(parsedUrl, targetTabId = tabId))
                val cur = currentTabs.value.find { it.id == tabId }
                if (cur != null) {
                    repository.saveTab(cur.copy(url = parsedUrl, lastAccessedAt = System.currentTimeMillis()))
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
        setBarsVisible(true)
        val tabId = _activeTabId.value
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.Reload(targetTabId = tabId)) }
    }

    fun stopLoading() {
        val tabId = _activeTabId.value
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.StopLoading(targetTabId = tabId)) }
    }

    fun goBack() {
        setBarsVisible(true)
        val tabId = _activeTabId.value
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.GoBack(targetTabId = tabId)) }
    }

    fun goForward() {
        setBarsVisible(true)
        val tabId = _activeTabId.value
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.GoForward(targetTabId = tabId)) }
    }

    fun goHome() {
        setBarsVisible(true)
        val tabId = _activeTabId.value
        _activeTabState.update { it?.copy(url = "", title = "New Tab", progress = 0, isLoading = false) }
        viewModelScope.launch {
            _webViewActionEvent.emit(WebViewAction.LoadUrl("about:blank", targetTabId = tabId))
            val cur = currentTabs.value.find { it.id == tabId }
            if (cur != null) {
                repository.saveTab(cur.copy(url = "", title = "New Tab", lastAccessedAt = System.currentTimeMillis()))
            }
        }
    }

    fun toggleDesktopMode() {
        val tabId = _activeTabId.value
        val newDesktop = !(_activeTabState.value?.isDesktopMode ?: false)
        _activeTabState.update { it?.copy(isDesktopMode = newDesktop) }
        viewModelScope.launch {
            _webViewActionEvent.emit(WebViewAction.SetDesktopMode(newDesktop, targetTabId = tabId))
            val cur = currentTabs.value.find { it.id == tabId }
            if (cur != null) {
                repository.saveTab(cur.copy(isDesktopMode = newDesktop))
            }
        }
    }

    fun startFindInPage() {
        setBarsVisible(true)
        _isFindInPageActive.value = true
        _findQuery.value = ""
        _activeTabState.update {
            it?.copy(
                searchMatchCurrent = 0,
                searchMatchCount = 0
            )
        }
    }

    fun closeFindInPage() {
        val tabId = _activeTabId.value
        _isFindInPageActive.value = false
        _findQuery.value = ""
        _activeTabState.update {
            it?.copy(
                searchMatchCurrent = 0,
                searchMatchCount = 0
            )
        }
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.ClearFindMatches(targetTabId = tabId)) }
    }

    fun setFindQuery(query: String) {
        val tabId = _activeTabId.value
        _findQuery.value = query
        if (query.isBlank()) {
            _activeTabState.update {
                it?.copy(
                    searchMatchCurrent = 0,
                    searchMatchCount = 0
                )
            }
            viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.ClearFindMatches(targetTabId = tabId)) }
        } else {
            viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.FindAllAsync(query, targetTabId = tabId)) }
        }
    }

    fun findNext(forward: Boolean) {
        val tabId = _activeTabId.value
        viewModelScope.launch { _webViewActionEvent.emit(WebViewAction.FindNext(forward, targetTabId = tabId)) }
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
    fun onPageStarted(tabId: String, url: String) {
        if (tabId == _activeTabId.value) {
            setBarsVisible(true)
        }
        if (url.isBlank() || url == "about:blank") {
            if (tabId == _activeTabId.value) {
                _activeTabState.update { it?.copy(isLoading = false, progress = 0) }
            }
            return
        }
        onUrlChanged(tabId, url)
        if (tabId == _activeTabId.value) {
            _activeTabState.update {
                it?.copy(
                    isLoading = true,
                    progress = it.progress.coerceIn(15, 90)
                )
            }
        }
    }

    fun onPageFinished(tabId: String, url: String) {
        if (tabId == _activeTabId.value) {
            _activeTabState.update {
                it?.copy(
                    isLoading = false,
                    progress = 100
                )
            }
        }
    }

    fun onPageLoadError(tabId: String) {
        if (tabId == _activeTabId.value) {
            _activeTabState.update {
                it?.copy(
                    isLoading = false,
                    progress = 100
                )
            }
        }
    }

    fun onUrlChanged(tabId: String, url: String) {
        if (url.isBlank() || url == "about:blank") return
        val isSec = UrlUtils.isHttps(url)
        val blocked = ContentBlocker.getBlockCountForTab(tabId)
        
        if (tabId == _activeTabId.value) {
            val curState = _activeTabState.value
            if (curState?.url != url || curState.isSecure != isSec || curState.blockedCount != blocked) {
                _activeTabState.update {
                    it?.copy(url = url, isSecure = isSec, blockedCount = blocked)
                }
            }
        }
        viewModelScope.launch {
            val cur = currentTabs.value.find { it.id == tabId }
            if (cur != null && cur.url != url) {
                repository.saveTab(cur.copy(url = url, lastAccessedAt = System.currentTimeMillis()))
            }
        }
    }

    fun onTitleChanged(tabId: String, title: String) {
        if (title.isBlank() || title == "about:blank") return
        if (tabId == _activeTabId.value) {
            val curState = _activeTabState.value
            if (curState?.title != title) {
                _activeTabState.update { it?.copy(title = title) }
            }
        }
        viewModelScope.launch {
            val cur = currentTabs.value.find { it.id == tabId }
            if (cur != null) {
                if (cur.title != title) {
                    repository.saveTab(cur.copy(title = title))
                }
                // Record history if not private
                val url = if (tabId == _activeTabId.value) _activeTabState.value?.url ?: cur.url else cur.url
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

    fun onProgressChanged(tabId: String, progress: Int) {
        if (tabId != _activeTabId.value) return
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

    fun onNavigationStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        if (tabId != _activeTabId.value) return
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

    fun exportBookmarksHtml(profileName: String = "Feather"): String {
        return BookmarkExportHelper.exportToNetscapeHtml(bookmarks.value, profileName)
    }

    fun importBookmarks(rawContent: String, targetProfileId: String = _currentProfileId.value, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val parsed = BookmarkExportHelper.parseBookmarks(rawContent, targetProfileId)
            val count = repository.importBookmarks(parsed)
            onComplete(count)
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
        setBarsVisible(true)
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
    abstract val targetTabId: String?

    data class LoadUrl(val url: String, override val targetTabId: String? = null) : WebViewAction()
    data class Reload(override val targetTabId: String? = null) : WebViewAction()
    data class StopLoading(override val targetTabId: String? = null) : WebViewAction()
    data class GoBack(override val targetTabId: String? = null) : WebViewAction()
    data class GoForward(override val targetTabId: String? = null) : WebViewAction()
    data class SetDesktopMode(val enabled: Boolean, override val targetTabId: String? = null) : WebViewAction()
    data class FindAllAsync(val query: String, override val targetTabId: String? = null) : WebViewAction()
    data class FindNext(val forward: Boolean, override val targetTabId: String? = null) : WebViewAction()
    data class ClearFindMatches(override val targetTabId: String? = null) : WebViewAction()
    data class ExtractReaderContent(
        val callback: (ReaderArticle?) -> Unit,
        override val targetTabId: String? = null
    ) : WebViewAction()
}
