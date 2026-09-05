package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBar(
    activeTab: ActiveTabState?,
    currentProfile: BrowserProfile?,
    isPrivateMode: Boolean,
    tabCount: Int,
    isBookmarked: Boolean,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenPrivacyShield: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var inputText by remember(activeTab?.url) { mutableStateOf(activeTab?.url ?: "") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Real-time search suggestions: Fetch query predictions from Google Search as the user types
    LaunchedEffect(inputText, isEditing) {
        val trimmed = inputText.trim()
        val currentUrl = activeTab?.url?.trim() ?: ""
        if (!isEditing || trimmed.isBlank() || trimmed == currentUrl) {
            suggestions = emptyList()
            isLoadingSuggestions = false
            return@LaunchedEffect
        }

        isLoadingSuggestions = true
        delay(150) // Debounce rapid keystrokes
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

    // Dismiss keyboard and address bar focus on back button/gesture
    BackHandler(enabled = isEditing) {
        isEditing = false
        suggestions = emptyList()
        focusManager.clearFocus(force = true)
        inputText = activeTab?.url ?: ""
    }

    LaunchedEffect(activeTab?.url) {
        if (!isEditing) {
            inputText = activeTab?.url ?: ""
        }
    }

    val profileColor = if (isPrivateMode) Color(0xFF9333EA) else {
        try {
            Color(android.graphics.Color.parseColor(currentProfile?.colorHex ?: "#3B82F6"))
        } catch (e: Exception) {
            MaterialTheme.colorScheme.primary
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentView = LocalView.current

    val submitNavigation: (String) -> Unit = { queryOrUrl ->
        val textToSubmit = queryOrUrl.trim()
        if (textToSubmit.isNotBlank()) {
            isEditing = false
            suggestions = emptyList()
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            try {
                val imm = currentView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(currentView.windowToken, 0)
                imm?.hideSoftInputFromWindow(currentView.applicationWindowToken, 0)
                imm?.hideSoftInputFromWindow(currentView.rootView.windowToken, 0)
                (currentView.context as? android.app.Activity)?.let { act ->
                    act.currentFocus?.clearFocus()
                    act.window?.decorView?.let { decor ->
                        imm?.hideSoftInputFromWindow(decor.windowToken, 0)
                    }
                }
            } catch (e: Exception) { }
            onNavigate(textToSubmit)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Avatar / Private Badge Icon (isolated click box, unfocusable via keyboard)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(profileColor.copy(alpha = 0.2f))
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 18.dp)
                        ) { onOpenProfiles() }
                        .testTag("profile_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPrivateMode) Icons.Default.VpnKey else getProfileIcon(currentProfile?.iconName),
                        contentDescription = "Switch Profile",
                        tint = profileColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Address / Search Bar Input Field with depth
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Security Indicator or Privacy Shield Badge
                        if (!isEditing && (activeTab?.url?.isNotBlank() == true)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .focusProperties { canFocus = false }
                                    .clickable { onOpenPrivacyShield() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("privacy_shield_indicator"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (activeTab.isSecure) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (activeTab.isSecure) "Secure HTTPS" else "Insecure HTTP",
                                        tint = if (activeTab.isSecure) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    if (activeTab.blockedCount > 0) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "Shield Active",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "${activeTab.blockedCount}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 1.dp)
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
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

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
                                onGo = { submitNavigation(inputText) },
                                onDone = { submitNavigation(inputText) },
                                onSend = { submitNavigation(inputText) }
                            ),
                            decorationBox = { innerTextField ->
                                if (inputText.isEmpty() && !isEditing) {
                                    Text(
                                        text = "Search or enter URL",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) {
                                        if (keyEvent.type == KeyEventType.KeyUp || keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) {
                                            submitNavigation(inputText)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .onFocusChanged { focusState ->
                                    val newlyFocused = focusState.isFocused
                                    if (newlyFocused != isEditing) {
                                        isEditing = newlyFocused
                                        if (newlyFocused && inputText.isBlank() && activeTab?.url?.isNotBlank() == true) {
                                            inputText = activeTab.url
                                        }
                                        if (!newlyFocused) {
                                            suggestions = emptyList()
                                        }
                                    }
                                }
                                .testTag("address_input")
                        )

                        // Clear Button and Navigate/Go Action
                        val hasValidUrl = !activeTab?.url.isNullOrBlank() && activeTab?.url != "about:blank"
                        if (isEditing) {
                            if (inputText.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        inputText = ""
                                        suggestions = emptyList()
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .focusProperties { canFocus = false }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (inputText.trim().isNotEmpty()) {
                                IconButton(
                                    onClick = { submitNavigation(inputText) },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .focusProperties { canFocus = false }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Go",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else if (hasValidUrl && activeTab?.isLoading == true) {
                            IconButton(
                                onClick = onStop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .focusProperties { canFocus = false }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Stop Loading",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else if (hasValidUrl) {
                            IconButton(
                                onClick = onReload,
                                modifier = Modifier
                                    .size(28.dp)
                                    .focusProperties { canFocus = false }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload Page",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                val hasValidUrl = !activeTab?.url.isNullOrBlank() && activeTab?.url != "about:blank"
                if (!isEditing) {
                    Spacer(modifier = Modifier.width(4.dp))

                    // Quick Reload or Bookmark on Top Bar
                    if (hasValidUrl) {
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier
                                .size(36.dp)
                                .focusProperties { canFocus = false }
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Bookmarked" else "Bookmark this page",
                                tint = if (isBookmarked) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Smooth Linear Progress Bar on Address Bar
            val hasValidUrl = !activeTab?.url.isNullOrBlank() && activeTab?.url != "about:blank"
            val isPageLoading = hasValidUrl && activeTab?.isLoading == true && (activeTab.progress in 1..99)
            val currentProgressRatio = ((activeTab?.progress ?: 0) / 100f).coerceIn(0.08f, 1f)
            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = currentProgressRatio,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 180,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                ),
                label = "urlLoadingProgress"
            )

            // Fixed-height container (3.dp) prevents vertical layout jumping/jittering during page loads
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            ) {
                if (isPageLoading) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = profileColor,
                        trackColor = profileColor.copy(alpha = 0.12f)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp
            )

            // Real-Time Google Search Suggestions Dropdown
            val trimmedQuery = inputText.trim()
            val shouldShowSuggestions = isEditing && trimmedQuery.isNotBlank() &&
                (suggestions.isNotEmpty() || isLoadingSuggestions)

            AnimatedVisibility(
                visible = shouldShowSuggestions,
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) +
                        expandVertically(animationSpec = androidx.compose.animation.core.tween(200)),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(100)) +
                       shrinkVertically(animationSpec = androidx.compose.animation.core.tween(150))
            ) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("search_suggestions_container")
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
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
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
                                .heightIn(max = 280.dp)
                        ) {
                            // If user typed a direct URL, provide direct navigation option
                            if (looksLikeUrl) {
                                item(key = "direct_url_item") {
                                    SuggestionRow(
                                        icon = Icons.Default.Public,
                                        iconTint = MaterialTheme.colorScheme.primary,
                                        text = buildAnnotatedString {
                                            append("Open ")
                                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                            append(trimmedQuery)
                                            pop()
                                        },
                                        trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                                        onTrailingClick = null,
                                        onClick = { submitNavigation(trimmedQuery) },
                                        modifier = Modifier.testTag("suggestion_direct_url")
                                    )
                                }
                            }

                            // Direct search query item
                            item(key = "direct_search_item") {
                                SuggestionRow(
                                    icon = Icons.Default.Search,
                                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    text = buildAnnotatedString {
                                        append("Search ")
                                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                        append("\"$trimmedQuery\"")
                                        pop()
                                    },
                                    trailingIcon = Icons.Default.NorthWest,
                                    onTrailingClick = {
                                        inputText = trimmedQuery
                                    },
                                    onClick = { submitNavigation(trimmedQuery) },
                                    modifier = Modifier.testTag("suggestion_query_direct")
                                )
                            }

                            // Real-time Google query predictions
                            val filteredSuggestions = suggestions.filter {
                                !it.equals(trimmedQuery, ignoreCase = true)
                            }

                            itemsIndexed(
                                items = filteredSuggestions,
                                key = { index, suggestion -> "$index-$suggestion" }
                            ) { index, suggestion ->
                                val annotatedSuggestion = remember(trimmedQuery, suggestion) {
                                    buildAnnotatedString {
                                        val lowerTrimmed = trimmedQuery.lowercase()
                                        val lowerSug = suggestion.lowercase()
                                        if (lowerSug.startsWith(lowerTrimmed)) {
                                            append(suggestion.substring(0, lowerTrimmed.length))
                                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                            append(suggestion.substring(lowerTrimmed.length))
                                            pop()
                                        } else {
                                            append(suggestion)
                                        }
                                    }
                                }

                                SuggestionRow(
                                    icon = Icons.Default.TrendingUp,
                                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    text = annotatedSuggestion,
                                    trailingIcon = Icons.Default.NorthWest,
                                    onTrailingClick = {
                                        inputText = suggestion
                                    },
                                    onClick = { submitNavigation(suggestion) },
                                    modifier = Modifier.testTag("search_suggestion_$index")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    icon: ImageVector,
    iconTint: Color,
    text: AnnotatedString,
    trailingIcon: ImageVector?,
    onTrailingClick: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailingIcon != null && onTrailingClick != null) {
            IconButton(
                onClick = onTrailingClick,
                modifier = Modifier
                    .size(36.dp)
                    .focusProperties { canFocus = false }
                    .testTag("suggestion_insert_action")
            ) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = "Insert into search bar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp)
                )
            }
        } else if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun getProfileIcon(iconName: String?): ImageVector {
    return when (iconName?.lowercase()) {
        "work", "business" -> Icons.Default.Work
        "school", "university" -> Icons.Default.School
        "science", "testing" -> Icons.Default.Science
        "shopping", "cart" -> Icons.Default.ShoppingCart
        "public", "globe" -> Icons.Default.Public
        "star", "favorite" -> Icons.Default.Star
        "code", "dev" -> Icons.Default.Code
        else -> Icons.Default.Person
    }
}
