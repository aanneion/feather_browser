package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.browser.*
import com.example.ui.components.*

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val activeTabState by viewModel.activeTabState.collectAsStateWithLifecycle()
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val isPrivateMode by viewModel.isPrivateMode.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val currentTabs by viewModel.currentTabs.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isCurrentUrlBookmarked.collectAsStateWithLifecycle()
    val activeSheet by viewModel.activeSheet.collectAsStateWithLifecycle()
    val isFindInPageActive by viewModel.isFindInPageActive.collectAsStateWithLifecycle()
    val findQuery by viewModel.findQuery.collectAsStateWithLifecycle()

    // Settings
    val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val useMaterialYou by viewModel.useMaterialYou.collectAsStateWithLifecycle()
    val newTabStyle by viewModel.newTabStyle.collectAsStateWithLifecycle()
    val isAdBlockEnabled by viewModel.isAdBlockEnabled.collectAsStateWithLifecycle()
    val blockThirdPartyCookies by viewModel.blockThirdPartyCookies.collectAsStateWithLifecycle()
    val httpsMode by viewModel.httpsMode.collectAsStateWithLifecycle()
    val enableWebDarkMode by viewModel.enableWebDarkMode.collectAsStateWithLifecycle()
    val enableBackgroundPlay by viewModel.enableBackgroundPlay.collectAsStateWithLifecycle()
    val downloadProvider by viewModel.downloadProvider.collectAsStateWithLifecycle()
    val adBlockExceptions by viewModel.adBlockExceptions.collectAsStateWithLifecycle()
    val quickShortcuts by viewModel.quickShortcuts.collectAsStateWithLifecycle()

    var showMenuSheet by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    val whitelistedDomains = remember(adBlockExceptions) {
        adBlockExceptions.filter { it.isAdBlockDisabled }.map { it.domain }.toSet()
    }

    val currentUrl = activeTabState?.url ?: ""
    val isHome = currentUrl.isBlank() || currentUrl == "about:blank"
    val isSiteWhitelisted = remember(currentUrl, whitelistedDomains) {
        val domain = UrlUtils.extractDomain(currentUrl)
        whitelistedDomains.contains(domain) || whitelistedDomains.contains(domain.removePrefix("www."))
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val systemInDark = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        AppThemeMode.SYSTEM -> systemInDark
    }

    when (activeSheet) {
        ActiveSheet.SETTINGS -> {
            BackHandler(enabled = true) {
                viewModel.dismissSheet()
            }
            SettingsScreen(
                searchEngine = searchEngine,
                onSearchEngineChange = { viewModel.searchEngine.value = it },
                themeMode = themeMode,
                onThemeModeChange = { viewModel.setThemeMode(it) },
                useMaterialYou = useMaterialYou,
                onToggleMaterialYou = { viewModel.setUseMaterialYou(it) },
                newTabStyle = newTabStyle,
                onNewTabStyleChange = { viewModel.setNewTabStyle(it) },
                isAdBlockEnabled = isAdBlockEnabled,
                onToggleAdBlock = { viewModel.isAdBlockEnabled.value = it },
                blockThirdPartyCookies = blockThirdPartyCookies,
                onToggleBlockThirdPartyCookies = { viewModel.blockThirdPartyCookies.value = it },
                httpsMode = httpsMode,
                onHttpsModeChange = { viewModel.httpsMode.value = it },
                enableWebDarkMode = enableWebDarkMode,
                onToggleWebDarkMode = { viewModel.enableWebDarkMode.value = it },
                enableBackgroundPlay = enableBackgroundPlay,
                onToggleBackgroundPlay = { viewModel.setBackgroundPlay(it) },
                downloadProvider = downloadProvider,
                onDownloadProviderChange = { viewModel.setDownloadProvider(it) },
                onOpenClearData = { showClearDataDialog = true },
                onDismiss = { viewModel.dismissSheet() },
                modifier = modifier
            )
        }
        ActiveSheet.BOOKMARKS -> {
            BackHandler(enabled = true) {
                viewModel.dismissSheet()
            }
            BookmarksScreen(
                bookmarks = bookmarks,
                profileName = currentProfile?.displayName ?: "Personal",
                onNavigate = {
                    viewModel.navigateTo(it)
                    viewModel.dismissSheet()
                },
                onDeleteBookmark = { viewModel.deleteBookmark(it) },
                onDismiss = { viewModel.dismissSheet() },
                modifier = modifier
            )
        }
        ActiveSheet.HISTORY -> {
            BackHandler(enabled = true) {
                viewModel.dismissSheet()
            }
            HistoryScreen(
                history = history,
                profileName = currentProfile?.displayName ?: "Personal",
                onNavigate = {
                    viewModel.navigateTo(it)
                    viewModel.dismissSheet()
                },
                onDeleteHistoryItem = { viewModel.deleteHistoryItem(it) },
                onClearAllHistory = { viewModel.clearAllHistory() },
                onDismiss = { viewModel.dismissSheet() },
                modifier = modifier
            )
        }
        ActiveSheet.DOWNLOADS -> {
            BackHandler(enabled = true) {
                viewModel.dismissSheet()
            }
            DownloadsScreen(
                downloads = downloads,
                onOpenFile = { viewModel.openDownloadedFile(it) },
                onDeleteDownload = { viewModel.deleteDownloadItem(it) },
                onDismiss = { viewModel.dismissSheet() },
                modifier = modifier
            )
        }
        ActiveSheet.TABS -> {
            BackHandler(enabled = true) {
                viewModel.dismissSheet()
            }
            TabsManagerScreen(
                tabs = currentTabs,
                activeTabId = activeTabState?.id ?: "",
                currentProfile = currentProfile,
                isPrivateMode = isPrivateMode,
                onSelectTab = { viewModel.selectTab(it) },
                onCloseTab = { viewModel.closeTab(it) },
                onNewTab = { viewModel.createNewTab() },
                onCloseAllTabs = { viewModel.closeAllTabs() },
                onTogglePrivateMode = { viewModel.togglePrivateMode() },
                onDismiss = { viewModel.dismissSheet() },
                modifier = modifier
            )
        }
        else -> {
            // Hardware back press handling for main browser
            BackHandler(enabled = true) {
                if (isFindInPageActive) {
                    viewModel.closeFindInPage()
                } else if (activeTabState?.canGoBack == true) {
                    viewModel.goBack()
                } else if (!isHome) {
                    viewModel.goHome()
                }
            }

            Scaffold(
                topBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 5.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            // Top URL Bar
                            AddressBar(
                                activeTab = activeTabState,
                                currentProfile = currentProfile,
                                isPrivateMode = isPrivateMode,
                                tabCount = currentTabs.size,
                                isBookmarked = isBookmarked,
                                onNavigate = {
                                    focusManager.clearFocus(force = true)
                                    viewModel.navigateTo(it)
                                },
                                onReload = { viewModel.reload() },
                                onStop = { viewModel.stopLoading() },
                                onToggleBookmark = { viewModel.toggleBookmarkCurrentUrl() },
                                onOpenTabs = { viewModel.openSheet(ActiveSheet.TABS) },
                                onOpenProfiles = { viewModel.openSheet(ActiveSheet.PROFILES) },
                                onOpenPrivacyShield = { viewModel.openSheet(ActiveSheet.PRIVACY_SHIELD) },
                                onOpenMenu = { showMenuSheet = true }
                            )

                            if (isFindInPageActive) {
                                FindInPageBar(
                                    query = findQuery,
                                    matchCurrent = activeTabState?.searchMatchCurrent ?: 0,
                                    matchTotal = activeTabState?.searchMatchCount ?: 0,
                                    onQueryChange = { viewModel.setFindQuery(it) },
                                    onFindNext = { forward -> viewModel.findNext(forward) },
                                    onClose = { viewModel.closeFindInPage() }
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    BrowserBottomBar(
                        canGoBack = activeTabState?.canGoBack == true,
                        canGoForward = activeTabState?.canGoForward == true,
                        tabCount = currentTabs.size,
                        onGoBack = { viewModel.goBack() },
                        onGoForward = { viewModel.goForward() },
                        onGoHome = { viewModel.goHome() },
                        onOpenTabs = { viewModel.openSheet(ActiveSheet.TABS) },
                        onOpenMenu = { showMenuSheet = true }
                    )
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                modifier = modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (isHome) {
                        NewTabPage(
                            currentProfile = currentProfile,
                            isPrivateMode = isPrivateMode,
                            searchEngine = searchEngine,
                            bookmarks = bookmarks,
                            shortcuts = quickShortcuts,
                            newTabStyle = newTabStyle,
                            onNavigate = { viewModel.navigateTo(it) },
                            onAddShortcut = { title, url -> viewModel.addQuickShortcut(title, url) },
                            onEditShortcut = { id, title, url -> viewModel.editQuickShortcut(id, title, url) },
                            onRemoveShortcut = { id -> viewModel.removeQuickShortcut(id) },
                            onOpenProfiles = { viewModel.openSheet(ActiveSheet.PROFILES) },
                            onOpenPrivacyShield = { viewModel.openSheet(ActiveSheet.PRIVACY_SHIELD) }
                        )
                    } else {
                        activeTabState?.let { tab ->
                            key(tab.id) {
                                WebViewContainer(
                                    tabId = tab.id,
                                    initialUrl = tab.url,
                                    isDesktopMode = tab.isDesktopMode,
                                    isAdBlockEnabled = isAdBlockEnabled,
                                    whitelistedDomains = whitelistedDomains,
                                    blockThirdPartyCookies = blockThirdPartyCookies,
                                    enableWebDarkMode = enableWebDarkMode,
                                    enableBackgroundPlay = enableBackgroundPlay,
                                    isDarkTheme = isDarkTheme,
                                    currentProfile = currentProfile,
                                    viewModel = viewModel,
                                    actions = viewModel.webViewActionEvent
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Sheets
            if (activeSheet == ActiveSheet.PROFILES) {
                ProfileManagerDialog(
                    profiles = profiles,
                    currentProfileId = viewModel.currentProfileId.collectAsStateWithLifecycle().value,
                    onSelectProfile = { viewModel.switchProfile(it) },
                    onCreateProfile = { name, icon, color, preset -> viewModel.createNewProfile(name, icon, color, preset) },
                    onUpdateProfile = { viewModel.updateProfile(it) },
                    onDeleteProfile = { viewModel.deleteProfile(it) },
                    onDismiss = { viewModel.dismissSheet() }
                )
            } else if (activeSheet == ActiveSheet.PRIVACY_SHIELD) {
                PrivacyShieldDialog(
                    activeTab = activeTabState,
                    isGlobalBlockerEnabled = isAdBlockEnabled,
                    isSiteWhitelisted = isSiteWhitelisted,
                    onToggleGlobalBlocker = { viewModel.isAdBlockEnabled.value = it },
                    onToggleSiteException = { viewModel.toggleCurrentSiteAdBlockException() },
                    onDismiss = { viewModel.dismissSheet() }
                )
            }
        }
    }

    // Overlays / Sheets
    if (showMenuSheet) {
        BrowserMenuSheet(
            isDesktopMode = activeTabState?.isDesktopMode == true,
            isBookmarked = isBookmarked,
            isPrivateMode = isPrivateMode,
            hasActiveUrl = !isHome,
            onNewTab = { viewModel.createNewTab() },
            onNewPrivateTab = { viewModel.togglePrivateMode() },
            onOpenProfiles = { viewModel.openSheet(ActiveSheet.PROFILES) },
            onToggleBookmark = { viewModel.toggleBookmarkCurrentUrl() },
            onOpenBookmarks = { viewModel.openSheet(ActiveSheet.BOOKMARKS) },
            onOpenHistory = { viewModel.openSheet(ActiveSheet.HISTORY) },
            onOpenDownloads = { viewModel.openSheet(ActiveSheet.DOWNLOADS) },
            onStartFindInPage = { viewModel.startFindInPage() },
            onToggleDesktopMode = { viewModel.toggleDesktopMode() },
            onOpenPrivacyShield = { viewModel.openSheet(ActiveSheet.PRIVACY_SHIELD) },
            onOpenClearData = { showClearDataDialog = true },
            onOpenSettings = { viewModel.openSheet(ActiveSheet.SETTINGS) },
            onExitBrowser = {
                val activity = context as? android.app.Activity
                activity?.finishAndRemoveTask()
                activity?.finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
            },
            onDismiss = { showMenuSheet = false }
        )
    }

    if (showClearDataDialog) {
        ClearBrowsingDataDialog(
            onConfirm = { history, cookies, cache, siteData, downloads ->
                viewModel.executeClearData(history, cookies, cache, siteData, downloads)
                showClearDataDialog = false
            },
            onDismiss = { showClearDataDialog = false }
        )
    }
}
