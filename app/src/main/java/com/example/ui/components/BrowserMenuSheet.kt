package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    isDesktopMode: Boolean,
    isBookmarked: Boolean,
    isPrivateMode: Boolean,
    hasActiveUrl: Boolean,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onOpenProfiles: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onStartFindInPage: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onOpenPrivacyShield: () -> Unit,
    onOpenClearData: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitBrowser: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Quick Top Action Row (New Tab, New Private Tab, Bookmark, Desktop Site)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickMenuActionButton(
                    icon = Icons.Default.Add,
                    label = "New Tab",
                    onClick = {
                        onDismiss()
                        onNewTab()
                    }
                )
                QuickMenuActionButton(
                    icon = Icons.Default.VpnKey,
                    label = if (isPrivateMode) "Leave Private" else "Private Tab",
                    tint = if (isPrivateMode) Color(0xFF9333EA) else MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        onDismiss()
                        onNewPrivateTab()
                    }
                )
                if (hasActiveUrl) {
                    QuickMenuActionButton(
                        icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        label = if (isBookmarked) "Saved" else "Bookmark",
                        tint = if (isBookmarked) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            onDismiss()
                            onToggleBookmark()
                        }
                    )
                }
                QuickMenuActionButton(
                    icon = Icons.Default.DesktopWindows,
                    label = if (isDesktopMode) "Mobile Site" else "Desktop Site",
                    tint = if (isDesktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        onDismiss()
                        onToggleDesktopMode()
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // List Items
            MenuItemRow(
                icon = Icons.Default.AccountCircle,
                title = "Switch Profile",
                onClick = {
                    onDismiss()
                    onOpenProfiles()
                }
            )

            MenuItemRow(
                icon = Icons.Default.Shield,
                title = "Privacy Shield",
                tint = MaterialTheme.colorScheme.primary,
                onClick = {
                    onDismiss()
                    onOpenPrivacyShield()
                }
            )

            MenuItemRow(
                icon = Icons.Default.Bookmarks,
                title = "Bookmarks",
                onClick = {
                    onDismiss()
                    onOpenBookmarks()
                }
            )

            MenuItemRow(
                icon = Icons.Default.History,
                title = "History",
                onClick = {
                    onDismiss()
                    onOpenHistory()
                }
            )

            MenuItemRow(
                icon = Icons.Default.Download,
                title = "Downloads",
                onClick = {
                    onDismiss()
                    onOpenDownloads()
                }
            )

            if (hasActiveUrl) {
                MenuItemRow(
                    icon = Icons.Default.Search,
                    title = "Find in Page",
                    onClick = {
                        onDismiss()
                        onStartFindInPage()
                    }
                )
            }

            MenuItemRow(
                icon = Icons.Default.DeleteOutline,
                title = "Clear Browsing Data",
                onClick = {
                    onDismiss()
                    onOpenClearData()
                }
            )

            MenuItemRow(
                icon = Icons.Default.Settings,
                title = "Settings",
                onClick = {
                    onDismiss()
                    onOpenSettings()
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            MenuItemRow(
                icon = Icons.Default.ExitToApp,
                title = "Exit Browser",
                tint = MaterialTheme.colorScheme.error,
                onClick = {
                    onDismiss()
                    onExitBrowser()
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun QuickMenuActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .clickable { onClick() }
            .padding(2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun MenuItemRow(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
