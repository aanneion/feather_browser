package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.browser.rememberFavicon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.browser.QuickShortcutItem
import com.example.browser.SearchEngine
import com.example.data.model.Bookmark
import com.example.data.model.BrowserProfile
import com.example.privacy.ContentBlocker
import com.example.weather.WeatherUiState
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewTabPage(
    currentProfile: BrowserProfile?,
    isPrivateMode: Boolean,
    searchEngine: SearchEngine,
    bookmarks: List<Bookmark>,
    shortcuts: List<QuickShortcutItem> = emptyList(),
    newTabStyle: com.example.browser.NewTabStyle = com.example.browser.NewTabStyle.PRODUCTIVITY,
    weatherState: WeatherUiState = WeatherUiState.Loading,
    isWeatherEnabled: Boolean = true,
    isWeatherFahrenheit: Boolean = false,
    onRefreshWeather: () -> Unit = {},
    onNavigate: (String) -> Unit,
    onAddShortcut: (String, String) -> Unit = { _, _ -> },
    onEditShortcut: (String, String, String) -> Unit = { _, _, _ -> },
    onRemoveShortcut: (String) -> Unit = {},
    onOpenProfiles: () -> Unit,
    onOpenPrivacyShield: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    var showAddDialog by remember { mutableStateOf(false) }
    var shortcutToEdit by remember { mutableStateOf<QuickShortcutItem?>(null) }
    var shortcutToConfirmDelete by remember { mutableStateOf<QuickShortcutItem?>(null) }

    val profileColor = if (isPrivateMode) Color(0xFF9333EA) else {
        try {
            Color(android.graphics.Color.parseColor(currentProfile?.colorHex ?: "#3B82F6"))
        } catch (e: Exception) {
            MaterialTheme.colorScheme.primary
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus(force = true)
                })
            }
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = if (newTabStyle == com.example.browser.NewTabStyle.MINIMALIST) 36.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (newTabStyle == com.example.browser.NewTabStyle.PRODUCTIVITY) {
            Spacer(modifier = Modifier.height(12.dp))

            // Profile Badge Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = profileColor.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, profileColor.copy(alpha = 0.3f)),
                modifier = Modifier
                    .clickable { onOpenProfiles() }
                    .testTag("new_tab_profile_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPrivateMode) Icons.Default.VpnKey else getProfileIcon(currentProfile?.iconName),
                        contentDescription = null,
                        tint = profileColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPrivateMode) "Private Session" else "${currentProfile?.displayName ?: "Personal"} Profile",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = profileColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Switch profile",
                        tint = profileColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Spacer(modifier = Modifier.height(30.dp))
        }

        // Feather App Icon Hero Logo
        Box(
            modifier = Modifier
                .size(if (newTabStyle == com.example.browser.NewTabStyle.MINIMALIST) 72.dp else 64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            profileColor.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_feather_logo),
                contentDescription = "Feather Logo",
                tint = Color.Unspecified,
                modifier = Modifier.size(if (newTabStyle == com.example.browser.NewTabStyle.MINIMALIST) 58.dp else 52.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isPrivateMode) "Feather Private" else "Feather Browser",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = if (isPrivateMode) "Isolated session • Zero history saved" else if (newTabStyle == com.example.browser.NewTabStyle.MINIMALIST) "Clean • Lightweight • Fast" else "Lightweight • Isolated Profiles • Tracker-Free",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Center Quick Search / URL Bar Pill
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onNavigate("") }
                .testTag("new_tab_center_search")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Search or type URL",
                    fontSize = 14.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Live Local Weather Widget (Zero permissions required, IP-based)
        if (isWeatherEnabled && newTabStyle == com.example.browser.NewTabStyle.PRODUCTIVITY) {
            Spacer(modifier = Modifier.height(14.dp))
            WeatherCard(
                state = weatherState,
                isFahrenheit = isWeatherFahrenheit,
                onRefresh = onRefreshWeather,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(if (newTabStyle == com.example.browser.NewTabStyle.MINIMALIST) 24.dp else 22.dp))

        // Shortcuts Header with "+ Add" action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "QUICK SHORTCUTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            TextButton(
                onClick = { showAddDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Shortcut",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Display shortcuts + Add Shortcut Card
        val itemsList = shortcuts.take(11)
        val chunkedShortcuts = itemsList.chunked(4)

        Column(modifier = Modifier.fillMaxWidth()) {
            chunkedShortcuts.forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowItems.forEach { shortcut ->
                        val shortcutColor = remember(shortcut.url) { getShortcutColor(shortcut.url) }
                        val shortcutInitial = remember(shortcut.title, shortcut.url) { getShortcutInitial(shortcut.title, shortcut.url) }
                        val faviconBitmap by rememberFavicon(shortcut.url)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .combinedClickable(
                                    onClick = { onNavigate(shortcut.url) },
                                    onLongClick = { shortcutToEdit = shortcut }
                                )
                                .padding(vertical = 4.dp)
                                .testTag("shortcut_${shortcut.title}")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (faviconBitmap != null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else shortcutColor.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (faviconBitmap != null) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f) else shortcutColor.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val bitmap = faviconBitmap
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap,
                                            contentDescription = shortcut.title,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = shortcutInitial,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = shortcutColor
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = shortcut.title,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Fill remaining slots in row if less than 4
                    val remaining = 4 - rowItems.size
                    for (i in 0 until remaining) {
                        if (i == 0 && itemsList.size < 12) {
                            // Render Add shortcut button inside slot
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showAddDialog = true }
                                    .padding(vertical = 4.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Add",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (newTabStyle == com.example.browser.NewTabStyle.PRODUCTIVITY) {
            Spacer(modifier = Modifier.height(24.dp))

            // Privacy Shield Summary Card
            val totalBlocked by ContentBlocker.totalBlockedCount.collectAsState()

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPrivacyShield() }
                    .testTag("privacy_shield_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Privacy Shield Active",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$totalBlocked Blocked",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeatureCheckItem(icon = Icons.Default.Check, text = "Tracker Blocker", modifier = Modifier.weight(1f))
                        FeatureCheckItem(icon = Icons.Default.Check, text = "Isolated Data", modifier = Modifier.weight(1f))
                        FeatureCheckItem(icon = Icons.Default.Check, text = "Zero Telemetry", modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Recent Bookmarks if any
        if (bookmarks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SAVED BOOKMARKS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(bookmarks.take(6)) { bm ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { onNavigate(bm.url) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = bm.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }

    // Add Shortcut Dialog
    if (showAddDialog) {
        AddEditShortcutDialog(
            initialTitle = "",
            initialUrl = "",
            onConfirm = { title, url ->
                onAddShortcut(title, url)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Edit Shortcut Dialog
    shortcutToEdit?.let { item ->
        AddEditShortcutDialog(
            initialTitle = item.title,
            initialUrl = item.url,
            isEditing = true,
            onConfirm = { title, url ->
                onEditShortcut(item.id, title, url)
                shortcutToEdit = null
            },
            onDelete = {
                onRemoveShortcut(item.id)
                shortcutToEdit = null
            },
            onDismiss = { shortcutToEdit = null }
        )
    }
}

@Composable
fun AddEditShortcutDialog(
    initialTitle: String,
    initialUrl: String,
    isEditing: Boolean = false,
    onConfirm: (String, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Edit Quick Shortcut" else "Add Quick Shortcut")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (e.g. google.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        onConfirm(title.ifBlank { url }, url)
                    }
                },
                enabled = url.isNotBlank()
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            Row {
                if (isEditing && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

private val shortcutColors = listOf(
    Color(0xFF4285F4),
    Color(0xFF10B981),
    Color(0xFF8B5CF6),
    Color(0xFFFF4500),
    Color(0xFF06B6D4),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFF6366F1)
)

private fun getShortcutColor(url: String): Color {
    val index = abs(url.hashCode()) % shortcutColors.size
    return shortcutColors[index]
}

private fun getShortcutInitial(title: String, url: String): String {
    val cleaned = title.trim()
    return if (cleaned.isNotEmpty()) {
        cleaned.take(2).uppercase()
    } else {
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.").take(2).uppercase()
    }
}

@Composable
fun FeatureCheckItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
