package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.browser.FingerprintPreset
import com.example.data.model.BrowserProfile
import com.example.privacy.WebViewProfileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerDialog(
    profiles: List<BrowserProfile>,
    currentProfileId: String,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: (displayName: String, iconName: String, colorHex: String, fingerprintPreset: String) -> Unit,
    onUpdateProfile: (BrowserProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onClearProfileData: (String) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var profileToEditFingerprint by remember { mutableStateOf<BrowserProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<BrowserProfile?>(null) }
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isolationSupported = remember { WebViewProfileManager.isSupported() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Browser Profiles & Identities",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isolationSupported) {
                            "Separate cookies, site storage, and device fingerprints"
                        } else {
                            "Separate history and bookmarks; WebView cookies are shared on this device"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontSize = 13.sp)
                }
            }

            if (!isolationSupported) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "This WebView version does not support isolated profiles. " +
                            "Update Android System WebView to get separate cookies and site storage per profile.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val isCurrent = profile.id == currentProfileId
                    val pColor = try {
                        Color(android.graphics.Color.parseColor(profile.colorHex))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }
                    val preset = try {
                        FingerprintPreset.valueOf(profile.fingerprintPreset)
                    } catch (e: Exception) {
                        FingerprintPreset.DEFAULT
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isCurrent) pColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isCurrent) 1.5.dp else 1.dp,
                            if (isCurrent) pColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectProfile(profile.id) }
                            .testTag("profile_item_${profile.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(pColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getProfileIcon(profile.iconName),
                                        contentDescription = null,
                                        tint = pColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = formatProfileName(profile.displayName),
                                            fontSize = if (profile.displayName.length > 20) 13.5.sp else 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = pColor.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = pColor,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = buildString {
                                            append(preset.displayName)
                                            if (!profile.adBlockEnabled) append(" · Ad block off")
                                            if (!profile.backgroundPlayEnabled) append(" · BG off")
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Identity edit button
                                IconButton(
                                    onClick = { profileToEditFingerprint = profile },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Edit profile, fingerprint, and privacy",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (profile.id != "default_personal") {
                                    IconButton(
                                        onClick = { profileToDelete = profile },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Delete Profile",
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

            Text(
                text = "Tap the fingerprint icon to edit the profile name, identity, privacy defaults, custom user-agent, or to clear its cookies.",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Create Profile Dialog
    if (showCreateDialog) {
        CreateProfileDialog(
            onConfirm = { name, icon, color, preset ->
                onCreateProfile(name, icon, color, preset)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // Edit Profile Identity / Fingerprint Dialog
    profileToEditFingerprint?.let { profile ->
        EditProfileFingerprintDialog(
            profile = profile,
            onConfirm = { updated ->
                onUpdateProfile(updated)
                profileToEditFingerprint = null
            },
            onClearData = {
                onClearProfileData(profile.id)
            },
            onDismiss = { profileToEditFingerprint = null }
        )
    }

    // Delete Confirmation Dialog
    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Profile?") },
            text = {
                Text(
                    if (isolationSupported) {
                        "Deleting '${profileToDelete?.displayName}' permanently erases its tabs, history, bookmarks, downloads, and its isolated cookies/site storage."
                    } else {
                        "Deleting '${profileToDelete?.displayName}' permanently erases its tabs, history, bookmarks, and downloads. This WebView version shares cookies between profiles."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        profileToDelete?.let { onDeleteProfile(it.id) }
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Data & Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EditProfileFingerprintDialog(
    profile: BrowserProfile,
    onConfirm: (BrowserProfile) -> Unit,
    onClearData: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var displayName by remember { mutableStateOf(profile.displayName) }
    var customUserAgent by remember { mutableStateOf(profile.customUserAgent) }
    var selectedPreset by remember {
        mutableStateOf(
            try {
                FingerprintPreset.valueOf(profile.fingerprintPreset)
            } catch (e: Exception) {
                FingerprintPreset.DEFAULT
            }
        )
    }
    var adBlockEnabled by remember { mutableStateOf(profile.adBlockEnabled) }
    var blockThirdPartyCookies by remember { mutableStateOf(profile.blockThirdPartyCookies) }
    var backgroundPlayEnabled by remember { mutableStateOf(profile.backgroundPlayEnabled) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val isolationSupported = remember { WebViewProfileManager.isSupported() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        if (it.length <= 32) {
                            displayName = it.replace("\\s+".toRegex(), " ")
                        }
                    },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customUserAgent,
                    onValueChange = { if (it.length <= 512) customUserAgent = it },
                    label = { Text("Custom User-Agent (optional)") },
                    placeholder = { Text("Leave empty to use the preset") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        Text(
                            "Only change this if a site needs a specific identity. A blank value uses the fingerprint preset.",
                            fontSize = 10.5.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Privacy defaults", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "These apply to this profile's tabs and can also be changed from Settings.",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ad blocking", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Block ads and trackers in this profile",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = adBlockEnabled,
                        onCheckedChange = { adBlockEnabled = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block third-party cookies", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Stop cross-site cookie tracking",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = blockThirdPartyCookies,
                        onCheckedChange = { blockThirdPartyCookies = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background playback", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Keep audio/video playing when this tab is hidden",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = backgroundPlayEnabled,
                        onCheckedChange = { backgroundPlayEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Browser fingerprint & identity", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose the device identity presented to websites for this profile.",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FingerprintPreset.entries.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedPreset = preset }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedPreset = preset },
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = preset.description,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (onClearData != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!isolationSupported) {
                        Text(
                            "Cookie isolation needs a newer Android System WebView. On this version, clearing cookies affects the shared WebView jar.",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    TextButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear cookies & site data for this profile")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        profile.copy(
                            displayName = displayName.trim().ifBlank { profile.displayName },
                            customUserAgent = customUserAgent.trim(),
                            fingerprintPreset = selectedPreset.name,
                            adBlockEnabled = adBlockEnabled,
                            blockThirdPartyCookies = blockThirdPartyCookies,
                            backgroundPlayEnabled = backgroundPlayEnabled
                        )
                    )
                },
                enabled = displayName.isNotBlank()
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showClearConfirm && onClearData != null) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear site data?") },
            text = {
                Text(
                    "This removes cookies and site storage for '${profile.displayName}'. " +
                        "History, bookmarks, and downloads are not deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CreateProfileDialog(
    onConfirm: (name: String, icon: String, colorHex: String, preset: String) -> Unit,
    onDismiss: () -> Unit
) {
    var profileName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("person") }
    var selectedColor by remember { mutableStateOf("#3B82F6") }
    var selectedPreset by remember { mutableStateOf(FingerprintPreset.DEFAULT) }

    val iconOptions = listOf(
        "person" to Icons.Default.Person,
        "work" to Icons.Default.Work,
        "school" to Icons.Default.School,
        "science" to Icons.Default.Science,
        "shopping" to Icons.Default.ShoppingCart,
        "globe" to Icons.Default.Public,
        "star" to Icons.Default.Star,
        "code" to Icons.Default.Code
    )

    val colorOptions = listOf(
        "#3B82F6", // Blue
        "#10B981", // Emerald
        "#8B5CF6", // Purple
        "#F59E0B", // Amber
        "#EC4899", // Pink
        "#06B6D4", // Cyan
        "#EF4444"  // Rose
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = {
                        if (it.length <= 32) {
                            profileName = it.replace("\\s+".toRegex(), " ")
                        }
                    },
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g., Dev, Travel, Banking") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        Text(
                            text = "${profileName.length}/32",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Profile Icon", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(iconOptions) { (name, icon) ->
                        val isSelected = selectedIcon == name
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { selectedIcon = name }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Theme Color", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(colorOptions) { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Browser Fingerprint & Identity", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FingerprintPreset.entries.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedPreset = preset }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedPreset = preset },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.displayName,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = preset.description,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (profileName.isNotBlank()) {
                        onConfirm(profileName.trim(), selectedIcon, selectedColor, selectedPreset.name)
                    }
                },
                enabled = profileName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Auto-formats profile display names to keep them clean and prevent excessive vertical wrapping.
 */
private fun formatProfileName(raw: String): String {
    val cleaned = raw.trim().replace("\\s+".toRegex(), " ")
    return if (cleaned.length > 24) {
        cleaned.take(22).trimEnd() + "…"
    } else {
        cleaned
    }
}

