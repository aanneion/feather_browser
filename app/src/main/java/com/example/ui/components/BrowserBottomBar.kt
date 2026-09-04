package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FeatherDimensions
import com.example.ui.theme.FeatherShapes
import com.example.ui.theme.PrivateModePurple

@Composable
fun BrowserBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoHome: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    isPrivateMode: Boolean = false
) {
    val activeAccentColor = if (isPrivateMode) PrivateModePurple else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Modern Dock Pill
        Surface(
            shape = FeatherShapes.FloatingDock,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isPrivateMode) {
                    PrivateModePurple.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(FeatherDimensions.BottomBarHeight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onGoBack,
                    enabled = canGoBack,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("bottom_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Forward Button
                IconButton(
                    onClick = onGoForward,
                    enabled = canGoForward,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("bottom_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Home Button
                IconButton(
                    onClick = onGoHome,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("bottom_home_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(23.dp)
                    )
                }

                // Tabs Switcher Pill
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenTabs() }
                        .padding(4.dp)
                        .testTag("bottom_tabs_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPrivateMode) PrivateModePurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isPrivateMode) PrivateModePurple.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
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
                        }
                    }
                }

                // Menu 3-dots Button
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("bottom_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
