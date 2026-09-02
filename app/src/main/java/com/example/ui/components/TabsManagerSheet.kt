package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.browser.UrlUtils
import com.example.data.model.BrowserProfile
import com.example.data.model.BrowserTab

@Composable
fun TabsManagerScreen(
    tabs: List<BrowserTab>,
    activeTabId: String,
    currentProfile: BrowserProfile?,
    isPrivateMode: Boolean,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseAllTabs: () -> Unit,
    onTogglePrivateMode: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profileColor = if (isPrivateMode) Color(0xFF9333EA) else {
        try {
            Color(android.graphics.Color.parseColor(currentProfile?.colorHex ?: "#3B82F6"))
        } catch (e: Exception) {
            MaterialTheme.colorScheme.primary
        }
    }

    val listState = rememberLazyListState()

    // Scroll to active tab on open
    LaunchedEffect(activeTabId) {
        val activeIndex = tabs.indexOfFirst { it.id == activeTabId }
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
    ) {
        // Upper tap-to-dismiss scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.32f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        // Downside tabs container
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }

                // Top Controls Bar (Profile pill, Private mode toggle, Close All)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile / Tabs count badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = profileColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, profileColor.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPrivateMode) Icons.Default.VpnKey else getProfileIcon(currentProfile?.iconName),
                                contentDescription = null,
                                tint = profileColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPrivateMode) "Private (${tabs.size})" else "${currentProfile?.displayName ?: "Personal"} (${tabs.size})",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = profileColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Private Mode Switcher
                    IconButton(
                        onClick = onTogglePrivateMode,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("toggle_private_tabs_button")
                    ) {
                        Icon(
                            imageVector = if (isPrivateMode) Icons.Filled.VpnKey else Icons.Outlined.VpnKey,
                            contentDescription = "Toggle Private Mode",
                            tint = if (isPrivateMode) Color(0xFF9333EA) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Close All Tabs
                    if (tabs.isNotEmpty()) {
                        TextButton(
                            onClick = onCloseAllTabs,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("close_all_tabs_button")
                        ) {
                            Text(
                                text = "Close All",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Horizontal Carousel of Tabs
                if (tabs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.LayersClear,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No open tabs",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyRow(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        items(tabs, key = { it.id }) { tab ->
                            val isActive = tab.id == activeTabId
                            DownsideTabCard(
                                tab = tab,
                                isActive = isActive,
                                profileColor = profileColor,
                                onClick = { onSelectTab(tab.id) },
                                onClose = { onCloseTab(tab.id) }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.75.dp
                )

                // Downside Bottom Bar (Close X, Collapse Chevron Down, New Tab +)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Close Tab Switcher (X)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("dismiss_tabs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tab Manager",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Center: Collapse Chevron Down (V)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(onClick = onDismiss)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Return to Webpage",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Right: New Tab (+)
                    Surface(
                        shape = CircleShape,
                        color = profileColor,
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(4.dp, CircleShape)
                            .clickable(onClick = onNewTab)
                            .testTag("new_tab_sheet_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Open New Tab",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownsideTabCard(
    tab: BrowserTab,
    isActive: Boolean,
    profileColor: Color,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val isHome = tab.url.isBlank() || tab.url == "about:blank"
    val domain = if (isHome) "Home" else UrlUtils.extractDomain(tab.url)
    val isYouTube = tab.url.contains("youtube.com", ignoreCase = true) || tab.url.contains("youtu.be", ignoreCase = true)

    val cardBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val borderColor = if (isActive) profileColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val borderWidth = if (isActive) 2.5.dp else 1.dp

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardBgColor,
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
        tonalElevation = if (isActive) 8.dp else 2.dp,
        shadowElevation = if (isActive) 10.dp else 3.dp,
        modifier = Modifier
            .width(225.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .testTag("tab_card_${tab.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Header: Favicon/Badge + Tab Title + Close Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // Site Icon Badge
                Surface(
                    shape = CircleShape,
                    color = when {
                        isYouTube -> Color(0xFFFF0000).copy(alpha = 0.15f)
                        isHome -> profileColor.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            isYouTube -> {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFFFF0000),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            isHome -> {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = profileColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            tab.isPrivate -> {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = Color(0xFF9333EA),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            else -> {
                                Text(
                                    text = domain.firstOrNull()?.uppercase() ?: "W",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Title
                Text(
                    text = when {
                        isHome -> "Home"
                        tab.title.isNotBlank() -> tab.title
                        else -> domain
                    },
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Close tab button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("close_tab_${tab.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Tab",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body: High quality preview surface
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isHome) {
                    // Mini Home Page representation
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = profileColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = profileColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Mini Search Bar pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(22.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Mini Shortcuts Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    }
                } else {
                    // Mini Webpage representation
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // Top mini site banner
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (isYouTube) Color(0xFFFF0000).copy(alpha = 0.2f) else profileColor.copy(alpha = 0.2f))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = domain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isYouTube) {
                            // Video player preview card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(85.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayCircleFilled,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        } else {
                            // General webpage hero placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(75.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Webpage text skeleton lines
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                        )
                    }
                }
            }
        }
    }
}

// Backward-compatible alias
@Composable
fun TabsManagerSheet(
    tabs: List<BrowserTab>,
    activeTabId: String,
    currentProfile: BrowserProfile?,
    isPrivateMode: Boolean,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseAllTabs: () -> Unit,
    onTogglePrivateMode: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    TabsManagerScreen(
        tabs = tabs,
        activeTabId = activeTabId,
        currentProfile = currentProfile,
        isPrivateMode = isPrivateMode,
        onSelectTab = onSelectTab,
        onCloseTab = onCloseTab,
        onNewTab = onNewTab,
        onCloseAllTabs = onCloseAllTabs,
        onTogglePrivateMode = onTogglePrivateMode,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
