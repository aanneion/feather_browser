package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.browser.*
import com.example.data.model.BrowserProfile
import com.example.ui.theme.PrivateModePurple
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Ergonomic Unified Bottom Command Bar for mobile browsing.
 *
 * Consolidates navigation (Back, Forward), Address/Search Pill, Tabs Switcher,
 * and Menu into a single ultra-compact, floating dock (56dp height).
 *
 * Features prominent ambient drop shadow and crisp outline borders for pristine
 * Light Mode separation and elevation over bright web pages.
 */
@Composable
fun UnifiedBottomCommandBar(
    activeTab: ActiveTabState?,
    currentProfile: BrowserProfile?,
    isPrivateMode: Boolean,
    tabCount: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoHome: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenPrivacyShield: () -> Unit,
    onOpenProfiles: () -> Unit,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    onEditingChanged: ((Boolean) -> Unit)? = null,
    onOpenReaderMode: (() -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var inputText by remember(activeTab?.url) { mutableStateOf(activeTab?.url ?: "") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentView = LocalView.current
    val haptic = LocalHapticFeedback.current

    val profileColor = if (isPrivateMode) {
        PrivateModePurple
    } else {
        try {
            Color(android.graphics.Color.parseColor(currentProfile?.colorHex ?: "#3B82F6"))
        } catch (e: Exception) {
            MaterialTheme.colorScheme.primary
        }
    }

    // Intercept back button while editing search/address
    BackHandler(enabled = isEditing) {
        isEditing = false
        onEditingChanged?.invoke(false)
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    // Real-time search suggestions: Fetch query predictions from Google Search
    LaunchedEffect(inputText, isEditing) {
        val trimmed = inputText.trim()
        val currentUrl = activeTab?.url?.trim() ?: ""
        if (!isEditing || trimmed.isBlank() || trimmed == currentUrl) {
            suggestions = emptyList()
            isLoadingSuggestions = false
            return@LaunchedEffect
        }

        isLoadingSuggestions = true
        delay(150)
        val results = try {
            SearchSuggestionService.getGoogleSuggestions(trimmed, maxResults = 8)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        suggestions = results
        isLoadingSuggestions = false
    }

    fun submitNavigation(query: String) {
        val textToSubmit = query.trim()
        if (textToSubmit.isNotBlank()) {
            isEditing = false
            onEditingChanged?.invoke(false)
            suggestions = emptyList()
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            try {
                val imm = currentView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(currentView.windowToken, 0)
            } catch (e: Exception) { }
            onNavigate(textToSubmit)
        }
    }

    val hasValidUrl = !activeTab?.url.isNullOrBlank() && activeTab?.url != "about:blank"
    val isPageLoading = hasValidUrl && activeTab?.isLoading == true && (activeTab.progress in 1..99)
    val currentProgressRatio = ((activeTab?.progress ?: 0) / 100f).coerceIn(0.08f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = currentProgressRatio,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "urlLoadingProgress"
    )

    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val navBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

    // Use IME height when keyboard is shown so the bar floats immediately above the keyboard
    val effectiveBottomInset = maxOf(imeBottomDp, navBarBottomDp)

    // Calculate maximum available height for suggestions so it never collides with status bar or dock
    val suggestionsMaxHeight = (screenHeightDp - effectiveBottomInset - statusBarTopDp - 76.dp)
        .coerceIn(160.dp, 440.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = effectiveBottomInset)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Search Suggestions Overlay above the bottom dock
        val trimmedQuery = inputText.trim()
        val shouldShowSuggestions = isEditing && trimmedQuery.isNotBlank() &&
            (suggestions.isNotEmpty() || isLoadingSuggestions || trimmedQuery.isNotEmpty())

        AnimatedVisibility(
            visible = shouldShowSuggestions,
            enter = fadeIn(tween(150)) + expandVertically(tween(200)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(150))
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .heightIn(max = suggestionsMaxHeight)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color(0x33000000),
                        spotColor = Color(0x40000000)
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header: Google Suggestions branding indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Google Suggestions",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4285F4)
                            )
                        }
                        if (isLoadingSuggestions) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFF4285F4)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.5.dp
                    )

                    val looksLikeUrl = remember(trimmedQuery) {
                        (trimmedQuery.contains(".") && !trimmedQuery.contains(" ") &&
                            (trimmedQuery.startsWith("http://") || trimmedQuery.startsWith("https://") ||
                                trimmedQuery.matches(Regex("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(:[0-9]+)?(/.*)?$"))))
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = suggestionsMaxHeight - 34.dp)
                    ) {
                        // If user typed a direct URL, provide direct navigation option
                        if (looksLikeUrl) {
                            item(key = "dock_direct_url_item") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            submitNavigation(trimmedQuery)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = "Open URL",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = buildAnnotatedString {
                                            append("Open ")
                                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                            append(trimmedQuery)
                                            pop()
                                        },
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    thickness = 0.5.dp
                                )
                            }
                        }

                        // Direct search query item
                        item(key = "dock_direct_search_item") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        submitNavigation(trimmedQuery)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = buildAnnotatedString {
                                        append("Search ")
                                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                        append("\"$trimmedQuery\"")
                                        pop()
                                    },
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (suggestions.isNotEmpty()) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    thickness = 0.5.dp
                                )
                            }
                        }

                        // Real-time Google query predictions
                        val filteredSuggestions = suggestions.filter {
                            !it.equals(trimmedQuery, ignoreCase = true)
                        }

                        itemsIndexed(
                            items = filteredSuggestions,
                            key = { index, suggestion -> "$index-$suggestion" }
                        ) { index, item ->
                            val lowerTrimmed = trimmedQuery.lowercase()
                            val lowerSug = item.lowercase()
                            val annotatedText = remember(trimmedQuery, item) {
                                buildAnnotatedString {
                                    if (lowerSug.startsWith(lowerTrimmed)) {
                                        append(item.substring(0, lowerTrimmed.length))
                                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                        append(item.substring(lowerTrimmed.length))
                                        pop()
                                    } else {
                                        append(item)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        submitNavigation(item)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = annotatedText,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        inputText = item
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NorthWest,
                                        contentDescription = "Insert query into search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            if (index < filteredSuggestions.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Unified Dock Pill
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = if (isPrivateMode) {
                    PrivateModePurple.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color(0x33000000),
                    spotColor = Color(0x40000000)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Slim Progress Indicator along the top edge of the floating bar
                if (isPageLoading) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = profileColor,
                        trackColor = profileColor.copy(alpha = 0.12f)
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.5.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditing) {
                        // Full-Width Search Input Mode
                        IconButton(
                            onClick = {
                                isEditing = false
                                onEditingChanged?.invoke(false)
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Search Input Field
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Search,
                                        keyboardType = KeyboardType.Uri
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSearch = { submitNavigation(inputText) },
                                        onGo = { submitNavigation(inputText) }
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (inputText.isEmpty()) {
                                            Text(
                                                text = "Search or type URL",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontSize = 14.sp,
                                                maxLines = 1
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                        .testTag("unified_address_input")
                                )

                                if (inputText.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            inputText = ""
                                            suggestions = emptyList()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (inputText.isNotBlank()) {
                                    IconButton(
                                        onClick = { submitNavigation(inputText) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Go",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    } else {
                        // Normal Browsing Mode: Back + Forward + Home + Address Pill + Tabs + Menu
                        // Back Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onGoBack()
                            },
                            enabled = canGoBack,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("bottom_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Forward Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onGoForward()
                            },
                            enabled = canGoForward,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("bottom_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Home Button (Shortened URL bar enables always-accessible Home)
                        val isAtHome = activeTab?.url.isNullOrBlank() || activeTab?.url == "about:blank"
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onGoHome()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("bottom_home_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = if (isAtHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Center Address & Search Pill (Click to activate edit mode)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isEditing = true
                                    onEditingChanged?.invoke(true)
                                    inputText = if (activeTab?.url == "about:blank") "" else (activeTab?.url ?: "")
                                }
                                .testTag("bottom_address_pill")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Security Lock or Privacy Shield Indicator
                                if (hasValidUrl) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onOpenPrivacyShield() }
                                            .padding(2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (activeTab?.isSecure == true) Icons.Default.Lock else Icons.Default.LockOpen,
                                                contentDescription = if (activeTab?.isSecure == true) "Secure HTTPS" else "Insecure HTTP",
                                                tint = if (activeTab?.isSecure == true) Color(0xFF10B981) else Color(0xFFEF4444),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            if ((activeTab?.blockedCount ?: 0) > 0) {
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "${activeTab?.blockedCount}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                // Domain / URL / Title display
                                val displayText = remember(activeTab?.url, activeTab?.title) {
                                    val url = activeTab?.url ?: ""
                                    if (url.isBlank() || url == "about:blank") {
                                        "Search or type URL"
                                    } else {
                                        try {
                                            val uri = android.net.Uri.parse(url)
                                            uri.host ?: activeTab?.title?.takeIf { it.isNotBlank() } ?: url
                                        } catch (e: Exception) {
                                            activeTab?.title?.takeIf { it.isNotBlank() } ?: url
                                        }
                                    }
                                }

                                Text(
                                    text = displayText,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (hasValidUrl) FontWeight.Medium else FontWeight.Normal,
                                    color = if (hasValidUrl) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                // Quick Reload / Stop Icon inside the pill
                                if (hasValidUrl) {
                                    if (activeTab?.isLoading == true) {
                                        IconButton(
                                            onClick = onStop,
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Stop",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = onReload,
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Reload",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tab Switcher Button with Badge
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenTabs()
                                }
                                .testTag("bottom_tabs_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isPrivateMode) PrivateModePurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.5.dp,
                                    if (isPrivateMode) PrivateModePurple.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$tabCount",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPrivateMode) PrivateModePurple else MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (!isPrivateMode && currentProfile != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .align(Alignment.TopEnd)
                                                .offset(x = 2.dp, y = (-2).dp)
                                                .clip(CircleShape)
                                                .background(profileColor)
                                        )
                                    }
                                }
                            }
                        }

                        // Menu Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenMenu()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("bottom_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
