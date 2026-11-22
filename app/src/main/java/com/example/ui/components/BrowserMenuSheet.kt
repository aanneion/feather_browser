package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    canReopenClosedTab: Boolean = false,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onReopenClosedTab: (() -> Unit)? = null,
    onOpenProfiles: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onStartFindInPage: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onSharePage: (() -> Unit)? = null,
    onShowQrCode: (() -> Unit)? = null,
    onPrintPage: (() -> Unit)? = null,
    onSaveOfflinePage: (() -> Unit)? = null,
    onAddToHomeScreen: (() -> Unit)? = null,
    pageTextZoom: Int = 100,
    onPageTextZoomChange: ((Int) -> Unit)? = null,
    onOpenPrivacyShield: () -> Unit,
    onOpenClearData: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitBrowser: () -> Unit,
    onDismiss: () -> Unit,
    onOpenReaderMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
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
                if (hasActiveUrl) {
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
            }

            if (hasActiveUrl && onPageTextZoomChange != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Text Size",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPageTextZoomChange((pageTextZoom - 10).coerceAtLeast(50)) },
                                enabled = pageTextZoom > 50,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("menu_zoom_decrease")
                            ) {
                                Text("A-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { onPageTextZoomChange(100) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("menu_zoom_reset")
                            ) {
                                Text(
                                    text = "$pageTextZoom%",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (pageTextZoom != 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            OutlinedButton(
                                onClick = { onPageTextZoomChange((pageTextZoom + 10).coerceAtMost(200)) },
                                enabled = pageTextZoom < 200,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("menu_zoom_increase")
                            ) {
                                Text("A+", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            if (canReopenClosedTab && onReopenClosedTab != null) {
                MenuItemRow(
                    icon = Icons.Default.Restore,
                    title = "Reopen Closed Tab",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        onDismiss()
                        onReopenClosedTab()
                    },
                    modifier = Modifier.testTag("menu_reopen_closed_tab")
                )
            }

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
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                if (onOpenReaderMode != null) {
                    MenuItemRow(
                        icon = Icons.Outlined.ChromeReaderMode,
                        title = "Reader Mode",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            onDismiss()
                            onOpenReaderMode()
                        }
                    )
                }

                MenuItemRow(
                    icon = Icons.Default.Search,
                    title = "Find in Page",
                    onClick = {
                        onDismiss()
                        onStartFindInPage()
                    }
                )

                if (onSharePage != null) {
                    MenuItemRow(
                        icon = Icons.Outlined.Share,
                        title = "Share Page",
                        onClick = {
                            onDismiss()
                            onSharePage()
                        },
                        modifier = Modifier.testTag("menu_share_page")
                    )
                }

                if (onShowQrCode != null) {
                    MenuItemRow(
                        icon = Icons.Outlined.QrCode2,
                        title = "QR Code for Page",
                        onClick = {
                            onDismiss()
                            onShowQrCode()
                        },
                        modifier = Modifier.testTag("menu_qr_code")
                    )
                }

                if (onPrintPage != null) {
                    MenuItemRow(
                        icon = Icons.Outlined.Print,
                        title = "Print / Save as PDF",
                        onClick = {
                            onDismiss()
                            onPrintPage()
                        },
                        modifier = Modifier.testTag("menu_print_pdf")
                    )
                }

                if (onSaveOfflinePage != null) {
                    MenuItemRow(
                        icon = Icons.Outlined.DownloadForOffline,
                        title = "Save Page Offline (.mht)",
                        onClick = {
                            onDismiss()
                            onSaveOfflinePage()
                        },
                        modifier = Modifier.testTag("menu_save_offline_mht")
                    )
                }

                if (onAddToHomeScreen != null) {
                    MenuItemRow(
                        icon = Icons.Outlined.AddToHomeScreen,
                        title = "Add to Home Screen",
                        onClick = {
                            onDismiss()
                            onAddToHomeScreen()
                        },
                        modifier = Modifier.testTag("menu_add_home_screen")
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        modifier = modifier
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
