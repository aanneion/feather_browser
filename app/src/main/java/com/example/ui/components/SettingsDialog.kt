package com.example.ui.components

import android.os.Build
import android.webkit.WebView
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.webkit.WebViewFeature
import com.example.browser.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    searchEngine: SearchEngine,
    onSearchEngineChange: (SearchEngine) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    useMaterialYou: Boolean,
    onToggleMaterialYou: (Boolean) -> Unit,
    newTabStyle: NewTabStyle,
    onNewTabStyleChange: (NewTabStyle) -> Unit,
    isWeatherEnabled: Boolean = true,
    onToggleWeather: (Boolean) -> Unit = {},
    isWeatherFahrenheit: Boolean = false,
    onToggleWeatherFahrenheit: (Boolean) -> Unit = {},
    isAdBlockEnabled: Boolean,
    onToggleAdBlock: (Boolean) -> Unit,
    blockThirdPartyCookies: Boolean,
    onToggleBlockThirdPartyCookies: (Boolean) -> Unit,
    httpsMode: HttpsMode,
    onHttpsModeChange: (HttpsMode) -> Unit,
    enableWebDarkMode: Boolean,
    onToggleWebDarkMode: (Boolean) -> Unit,
    enableBackgroundPlay: Boolean,
    onToggleBackgroundPlay: (Boolean) -> Unit,
    downloadProvider: DownloadProvider,
    onDownloadProviderChange: (DownloadProvider) -> Unit,
    onOpenClearData: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val webViewInfo = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pkg = WebView.getCurrentWebViewPackage()
                "${pkg?.packageName ?: "Android System WebView"} (${pkg?.versionName ?: "Standard"})"
            } else {
                "Android System WebView"
            }
        } catch (e: Exception) {
            "Android System WebView"
        }
    }

    val supportsForceDark = remember {
        WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
    }

    val supportsMaterialYou = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Browser",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Section: APPEARANCE & THEME
            item(key = "appearance_section") {
                GlassySettingsCard(
                    title = "APPEARANCE & THEME",
                    icon = Icons.Default.Palette
                ) {
                    Text(
                        text = "Theme Mode",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Choose between Light, Dark, or AMOLED true pitch black",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(AppThemeMode.SYSTEM, AppThemeMode.LIGHT).forEach { mode ->
                                val isSelected = themeMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onThemeModeChange(mode) },
                                    shape = RoundedCornerShape(10.dp),
                                    label = {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (mode == AppThemeMode.SYSTEM) "System Default" else "Light Theme",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(AppThemeMode.DARK, AppThemeMode.AMOLED).forEach { mode ->
                                val isSelected = themeMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onThemeModeChange(mode) },
                                    shape = RoundedCornerShape(10.dp),
                                    label = {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (mode == AppThemeMode.DARK) "Dark Theme" else "AMOLED Black",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (supportsMaterialYou) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Material You Dynamic Colors",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Extracts accent tones from system wallpaper",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useMaterialYou,
                                onCheckedChange = onToggleMaterialYou
                            )
                        }
                    }

                    if (supportsForceDark) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Darken Web Content",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Applies dark styling to supported websites",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = enableWebDarkMode,
                                onCheckedChange = onToggleWebDarkMode
                            )
                        }
                    }
                }
            }

            // Section: NEW TAB PAGE
            item(key = "new_tab_section") {
                GlassySettingsCard(
                    title = "NEW TAB PAGE",
                    icon = Icons.Default.Tab
                ) {
                    Text(
                        text = "Layout Style",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customize the look and density of the home tab",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NewTabStyle.entries.forEach { style ->
                            FilterChip(
                                selected = newTabStyle == style,
                                onClick = { onNewTabStyleChange(style) },
                                label = { Text(style.displayName, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    // Weather on New Tab toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Weather Widget",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Shows real-time weather on Home screen via approximate IP (zero location permissions needed)",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isWeatherEnabled,
                            onCheckedChange = onToggleWeather
                        )
                    }

                    if (isWeatherEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Temperature Unit",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isWeatherFahrenheit) "Displaying in Fahrenheit (°F)" else "Displaying in Celsius (°C)",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilterChip(
                                selected = isWeatherFahrenheit,
                                onClick = { onToggleWeatherFahrenheit(!isWeatherFahrenheit) },
                                label = { Text(if (isWeatherFahrenheit) "°F" else "°C", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            }

            // Section: SEARCH ENGINE
            item(key = "search_section") {
                GlassySettingsCard(
                    title = "SEARCH & GENERAL",
                    icon = Icons.Default.Search
                ) {
                    Text(
                        text = "Default Search Engine",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SearchEngine.entries.forEach { engine ->
                        val isSelected = searchEngine == engine
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSearchEngineChange(engine) }
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSearchEngineChange(engine) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = engine.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Section: MEDIA & BACKGROUND PLAYBACK
            item(key = "media_section") {
                GlassySettingsCard(
                    title = "MEDIA & BACKGROUND PLAYBACK",
                    icon = Icons.Default.PlayCircle
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background Play (YouTube & Media)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Keep audio and video playing when switching tabs or minimizing the browser",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enableBackgroundPlay,
                            onCheckedChange = onToggleBackgroundPlay
                        )
                    }
                }
            }

            // Section: DOWNLOADS & EXTERNAL TOOLS
            item(key = "downloads_section") {
                GlassySettingsCard(
                    title = "DOWNLOADS & EXTERNAL TOOLS",
                    icon = Icons.Default.Download
                ) {
                    Text(
                        text = "Download Provider",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Choose whether to download files using the built-in manager or open your installed external download manager (1DM, ADM, IDM, Aria2, etc.)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    DownloadProvider.entries.forEach { provider ->
                        val isSelected = downloadProvider == provider
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDownloadProviderChange(provider) }
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onDownloadProviderChange(provider) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = provider.displayName,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = provider.description,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section: PRIVACY & SECURITY
            item(key = "privacy_section") {
                GlassySettingsCard(
                    title = "PRIVACY & SECURITY",
                    icon = Icons.Default.Shield
                ) {
                    // Ad & Tracker Blocker
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ad & Tracker Blocker",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Native rule engine intercepts ads, tracking beacons & telemetry",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAdBlockEnabled,
                            onCheckedChange = onToggleAdBlock
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    // 3rd-Party Cookies
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Block 3rd-Party Cookies",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Prevents cross-site tracking and profiling",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = blockThirdPartyCookies,
                            onCheckedChange = onToggleBlockThirdPartyCookies
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    // HTTPS Enforcement
                    Text(
                        text = "HTTPS Enforcement",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            HttpsMode.PREFER_HTTPS to "Prefer HTTPS",
                            HttpsMode.HTTPS_ONLY to "HTTPS Only",
                            HttpsMode.NORMAL to "Standard"
                        ).forEach { (mode, label) ->
                            val isSelected = httpsMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { onHttpsModeChange(mode) },
                                shape = RoundedCornerShape(10.dp),
                                label = {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    // Clear data button
                    OutlinedButton(
                        onClick = onOpenClearData,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Browsing Data & Cache", fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Section: ABOUT & ENGINE
            item(key = "about_section") {
                GlassySettingsCard(
                    title = "ABOUT & ENGINE",
                    icon = Icons.Default.Info
                ) {
                    Text(
                        text = "Feather Browser",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Version ${com.example.BuildConfig.VERSION_NAME} • Jetpack Compose M3 • Multi-Profile Identities & Adblock",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Engine Info:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = webViewInfo,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Premium Glassmorphic Card Container with frosted styling and subtle light-reflecting gradient borders.
 */
@Composable
private fun GlassySettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        val outlineVariant = MaterialTheme.colorScheme.outlineVariant
        val borderBrush = remember(outlineVariant) {
            Brush.verticalGradient(
                colors = listOf(
                    outlineVariant.copy(alpha = 0.45f),
                    outlineVariant.copy(alpha = 0.12f)
                )
            )
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            border = BorderStroke(
                width = 1.dp,
                brush = borderBrush
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun ClearBrowsingDataDialog(
    onConfirm: (clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean, clearSiteData: Boolean, clearDownloads: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var clearSiteData by remember { mutableStateOf(false) }
    var clearDownloads by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Browsing Data", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Select the data categories you would like to purge:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { clearHistory = !clearHistory }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = clearHistory, onCheckedChange = { clearHistory = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browsing History", fontSize = 14.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { clearCookies = !clearCookies }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = clearCookies, onCheckedChange = { clearCookies = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cookies & Site Data", fontSize = 14.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { clearCache = !clearCache }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = clearCache, onCheckedChange = { clearCache = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cached Images & Files", fontSize = 14.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { clearDownloads = !clearDownloads }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = clearDownloads, onCheckedChange = { clearDownloads = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download History Records", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(clearHistory, clearCookies, clearCache, clearSiteData, clearDownloads) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
