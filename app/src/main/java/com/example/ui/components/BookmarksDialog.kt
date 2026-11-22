package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.browser.UrlUtils
import com.example.data.model.Bookmark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    profileName: String,
    onNavigate: (String) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onUpdateBookmark: ((Bookmark) -> Unit)? = null,
    onExportBookmarks: () -> String = { "" },
    onImportBookmarks: (String) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("All") }
    var bookmarkToEdit by remember { mutableStateOf<Bookmark?>(null) }

    val availableFolders = remember(bookmarks) {
        val distinct = bookmarks
            .map { it.folderName.trim().ifBlank { "Mobile Bookmarks" } }
            .distinct()
        listOf("All") + distinct
    }

    // Reset selectedFolder if all items in that folder were deleted/moved
    LaunchedEffect(availableFolders) {
        if (selectedFolder != "All" && selectedFolder !in availableFolders) {
            selectedFolder = "All"
        }
    }

    val filteredBookmarks = remember(bookmarks, searchQuery, selectedFolder) {
        bookmarks.filter { bm ->
            val folderMatch = selectedFolder == "All" ||
                bm.folderName.trim().ifBlank { "Mobile Bookmarks" }.equals(selectedFolder, ignoreCase = true)
            val queryMatch = searchQuery.isBlank() ||
                bm.title.contains(searchQuery, ignoreCase = true) ||
                bm.url.contains(searchQuery, ignoreCase = true) ||
                bm.folderName.contains(searchQuery, ignoreCase = true)
            folderMatch && queryMatch
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            try {
                val content = onExportBookmarks()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Exported ${bookmarks.size} bookmarks successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                if (content.isNotBlank()) {
                    onImportBookmarks(content)
                } else {
                    Toast.makeText(context, "Selected file is empty", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
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
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Browser",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bookmarks",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$profileName profile (${bookmarks.size} saved)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                importLauncher.launch(arrayOf("text/html", "application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier.testTag("import_bookmarks_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = "Import Bookmarks",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                val safeProfile = profileName.lowercase().replace(Regex("[^a-z0-9_]"), "_")
                                exportLauncher.launch("feather_bookmarks_${safeProfile}.html")
                            },
                            modifier = Modifier.testTag("export_bookmarks_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileUpload,
                                contentDescription = "Export Bookmarks",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Search Box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search bookmarks or folders...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("search_bookmarks_input")
                    )

                    // Folder Filter Chips (visible when multiple folders or custom folders exist)
                    if (availableFolders.size > 2 || (availableFolders.size == 2 && !availableFolders.contains("Mobile Bookmarks"))) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableFolders, key = { it }) { folder ->
                                val isSelected = selectedFolder.equals(folder, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedFolder = folder },
                                    shape = RoundedCornerShape(10.dp),
                                    leadingIcon = if (folder != "All") {
                                        {
                                            Icon(
                                                imageVector = Icons.Outlined.Folder,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    } else null,
                                    label = {
                                        Text(
                                            text = folder,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
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
        if (filteredBookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isBlank() && selectedFolder == "All") {
                            "No bookmarks in this profile yet"
                        } else {
                            "No matching bookmarks"
                        },
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredBookmarks, key = { it.id }) { bm ->
                    val folderLabel = bm.folderName.trim().ifBlank { "Mobile Bookmarks" }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onNavigate(bm.url) }
                            .testTag("bookmark_item_${bm.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bm.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!folderLabel.equals("Mobile Bookmarks", ignoreCase = true)) {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = folderLabel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = bm.url,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (onUpdateBookmark != null) {
                                IconButton(
                                    onClick = { bookmarkToEdit = bm },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("edit_bookmark_${bm.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit bookmark",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeleteBookmark(bm) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete bookmark",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    bookmarkToEdit?.let { target ->
        EditBookmarkDialog(
            bookmark = target,
            existingFolders = availableFolders.filter { it != "All" },
            onSave = { updated ->
                onUpdateBookmark?.invoke(updated)
                bookmarkToEdit = null
            },
            onDismiss = { bookmarkToEdit = null }
        )
    }
}

@Composable
private fun EditBookmarkDialog(
    bookmark: Bookmark,
    existingFolders: List<String>,
    onSave: (Bookmark) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(bookmark.id) { mutableStateOf(bookmark.title) }
    var url by remember(bookmark.id) { mutableStateOf(bookmark.url) }
    var folder by remember(bookmark.id) {
        mutableStateOf(bookmark.folderName.ifBlank { "Mobile Bookmarks" })
    }

    val suggestedFolders = remember(existingFolders) {
        (listOf("Mobile Bookmarks", "Reading List", "Work", "Dev & Docs") + existingFolders)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Bookmark", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Folder") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(suggestedFolders, key = { it }) { presetFolder ->
                        SuggestionChip(
                            onClick = { folder = presetFolder },
                            label = { Text(presetFolder, fontSize = 11.5.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedUrl = UrlUtils.parseInputToUrl(url)
                    if (normalizedUrl.isNotBlank()) {
                        onSave(
                            bookmark.copy(
                                title = title.trim().ifBlank { normalizedUrl },
                                url = normalizedUrl,
                                folderName = folder.trim().ifBlank { "Mobile Bookmarks" }
                            )
                        )
                    }
                },
                enabled = url.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Backward-compatible alias
@Composable
fun BookmarksDialog(
    bookmarks: List<Bookmark>,
    profileName: String,
    onNavigate: (String) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BookmarksScreen(
        bookmarks = bookmarks,
        profileName = profileName,
        onNavigate = onNavigate,
        onDeleteBookmark = onDeleteBookmark,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
