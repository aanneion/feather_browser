package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.browser.ContextMenuData
import com.example.browser.ContextMenuType
import com.example.browser.UrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuSheet(
    data: ContextMenuData,
    onOpenInNewTab: (String) -> Unit,
    onOpenInBackground: (String) -> Unit,
    onOpenInPrivateTab: (String) -> Unit,
    onCopyLinkAddress: (String) -> Unit,
    onCopyLinkText: (String) -> Unit,
    onShareLink: (String) -> Unit,
    onOpenImageInNewTab: (String) -> Unit,
    onDownloadImage: (String) -> Unit,
    onCopyImageAddress: (String) -> Unit,
    onShareImage: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasLink = !data.url.isNullOrBlank()
    val hasImage = !data.imageUrl.isNullOrBlank()
    val displayUrl = data.url ?: data.imageUrl ?: ""
    val domain = if (displayUrl.isNotBlank()) UrlUtils.extractDomain(displayUrl) else ""
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("context_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(scrollState)
        ) {
            // Header card with title & destination URL preview
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                data.type == ContextMenuType.IMAGE -> Icons.Outlined.Image
                                data.type == ContextMenuType.IMAGE_LINK -> Icons.Outlined.AddPhotoAlternate
                                else -> Icons.Outlined.Link
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val headerTitle = when {
                            !data.title.isNullOrBlank() -> data.title
                            domain.isNotBlank() -> domain
                            else -> if (hasImage) "Image Details" else "Link Details"
                        }
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displayUrl,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Link Actions
            if (hasLink) {
                Text(
                    text = "Link Options",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                )

                ContextMenuActionItem(
                    icon = Icons.Outlined.OpenInNew,
                    title = "Open in new tab",
                    testTag = "context_menu_open_new_tab",
                    onClick = {
                        onDismiss()
                        onOpenInNewTab(data.url!!)
                    }
                )

                ContextMenuActionItem(
                    icon = Icons.Outlined.Tab,
                    title = "Open in background tab",
                    testTag = "context_menu_open_background_tab",
                    onClick = {
                        onDismiss()
                        onOpenInBackground(data.url!!)
                    }
                )

                ContextMenuActionItem(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Open in new private tab",
                    testTag = "context_menu_open_private_tab",
                    onClick = {
                        onDismiss()
                        onOpenInPrivateTab(data.url!!)
                    }
                )

                ContextMenuActionItem(
                    icon = Icons.Outlined.ContentCopy,
                    title = "Copy link address",
                    testTag = "context_menu_copy_link_address",
                    onClick = {
                        onDismiss()
                        onCopyLinkAddress(data.url!!)
                    }
                )

                if (!data.title.isNullOrBlank()) {
                    ContextMenuActionItem(
                        icon = Icons.Outlined.TextFields,
                        title = "Copy link text (\"${data.title.take(24)}${if (data.title.length > 24) "..." else ""}\")",
                        testTag = "context_menu_copy_link_text",
                        onClick = {
                            onDismiss()
                            onCopyLinkText(data.title)
                        }
                    )
                }

                ContextMenuActionItem(
                    icon = Icons.Outlined.Share,
                    title = "Share link",
                    testTag = "context_menu_share_link",
                    onClick = {
                        onDismiss()
                        onShareLink(data.url!!)
                    }
                )
            }

            // Image Actions
            if (hasImage) {
                if (hasLink) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                Text(
                    text = "Image Options",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                )

                ContextMenuActionItem(
                    icon = Icons.Outlined.Image,
                    title = "Open image in new tab",
                    testTag = "context_menu_open_image",
                    onClick = {
                        onDismiss()
                        onOpenImageInNewTab(data.imageUrl!!)
                    }
                )

                ContextMenuActionItem(
                    icon = Icons.Outlined.Download,
                    title = "Download image",
                    testTag = "context_menu_download_image",
                    onClick = {
                        onDismiss()
                        onDownloadImage(data.imageUrl!!)
                    }
                )

                ContextMenuActionItem(
                    icon = Icons.Outlined.Link,
                    title = "Copy image address",
                    testTag = "context_menu_copy_image_address",
                    onClick = {
                        onDismiss()
                        onCopyImageAddress(data.imageUrl!!)
                    }
                )

                ContextMenuActionItem(
                    icon = Icons.Outlined.Share,
                    title = "Share image link",
                    testTag = "context_menu_share_image",
                    onClick = {
                        onDismiss()
                        onShareImage(data.imageUrl!!)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ContextMenuActionItem(
    icon: ImageVector,
    title: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 13.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
