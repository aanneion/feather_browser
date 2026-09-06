package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.browser.ReaderArticle
import com.example.browser.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderModeSheet(
    article: ReaderArticle?,
    theme: ReaderTheme,
    fontSize: Int,
    isSerif: Boolean,
    onThemeChange: (ReaderTheme) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onSerifChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showAppearanceControls by remember { mutableStateOf(false) }

    val bgColor = remember(theme) {
        try {
            Color(android.graphics.Color.parseColor(theme.bgHex))
        } catch (e: Exception) {
            Color.White
        }
    }

    val textColor = remember(theme) {
        try {
            Color(android.graphics.Color.parseColor(theme.textHex))
        } catch (e: Exception) {
            Color.Black
        }
    }

    val mutedTextColor = textColor.copy(alpha = 0.65f)

    Scaffold(
        topBar = {
            Surface(
                color = bgColor,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("reader_back_button")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Reader Mode",
                                tint = textColor
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "READER MODE",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = mutedTextColor
                            )
                            Text(
                                text = article?.domain?.ifBlank { "Article" } ?: "Article",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                                maxLines = 1
                            )
                        }

                        // Appearance toggle (Themes, Font size, Serif)
                        IconButton(
                            onClick = { showAppearanceControls = !showAppearanceControls },
                            modifier = Modifier.testTag("reader_appearance_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "Typography & Appearance",
                                tint = if (showAppearanceControls) MaterialTheme.colorScheme.primary else textColor
                            )
                        }

                        // Copy article content
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Article Text", "${article?.title}\n\n${article?.contentText}")
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Article copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("reader_copy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy Text",
                                tint = textColor
                            )
                        }
                    }

                    // Collapsible Appearance Controls Tray
                    if (showAppearanceControls) {
                        Surface(
                            color = textColor.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Theme Selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ReaderTheme.values().forEach { t ->
                                        val isSelected = t == theme
                                        val tBg = try { Color(android.graphics.Color.parseColor(t.bgHex)) } catch (e: Exception) { Color.White }
                                        val tText = try { Color(android.graphics.Color.parseColor(t.textHex)) } catch (e: Exception) { Color.Black }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = tBg,
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 4.dp)
                                                .height(40.dp)
                                                .clickable { onThemeChange(t) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "Aa",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tText
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Font Size & Serif Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Font Size Stepper
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(textColor.copy(alpha = 0.08f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onFontSizeChange(fontSize - 2) },
                                            enabled = fontSize > 14,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("A-", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)
                                        }
                                        Text(
                                            text = "${fontSize}sp",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textColor,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        IconButton(
                                            onClick = { onFontSizeChange(fontSize + 2) },
                                            enabled = fontSize < 26,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("A+", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textColor)
                                        }
                                    }

                                    // Font Family Toggle (Serif vs Sans)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(textColor.copy(alpha = 0.08f))
                                            .padding(4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSerif) textColor.copy(alpha = 0.15f) else Color.Transparent,
                                            modifier = Modifier.clickable { onSerifChange(true) }
                                        ) {
                                            Text(
                                                text = "Serif",
                                                fontFamily = FontFamily.Serif,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (!isSerif) textColor.copy(alpha = 0.15f) else Color.Transparent,
                                            modifier = Modifier.clickable { onSerifChange(false) }
                                        ) {
                                            Text(
                                                text = "Sans",
                                                fontFamily = FontFamily.Default,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                }
            }
        },
        containerColor = bgColor,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            if (article == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = textColor)
                }
            } else {
                // Article Title
                Text(
                    text = article.title,
                    fontSize = (fontSize + 6).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (isSerif) FontFamily.Serif else FontFamily.Default,
                    lineHeight = (fontSize + 12).sp,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Byline & Read-time metadata chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (article.byline.isNotBlank()) {
                        Text(
                            text = article.byline,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = mutedTextColor
                        )
                        Text(text = "•", fontSize = 13.sp, color = mutedTextColor)
                    }
                    Text(
                        text = "⏱️ ${article.readingTimeMinutes} min read (${article.wordCount} words)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = mutedTextColor
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.12f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(20.dp))

                // Article Body
                val paragraphs = remember(article.contentText) {
                    article.contentText.split(Regex("\n\n+"))
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }

                paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.6f).sp,
                        fontFamily = if (isSerif) FontFamily.Serif else FontFamily.Default,
                        color = textColor,
                        modifier = Modifier.padding(bottom = 18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
