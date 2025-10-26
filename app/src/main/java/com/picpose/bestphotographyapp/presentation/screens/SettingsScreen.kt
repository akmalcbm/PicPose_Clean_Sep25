package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.data.models.UserRole
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
    val language by settingsViewModel.language.collectAsState()
    val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            /*
            // 👤 USER PROFILE CARD
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile image or icon
                        if (!currentUser?.profilePicture.isNullOrBlank()) {
                            AsyncImage(
                                model = currentUser?.profilePicture,
                                contentDescription = "Profile Picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Profile Icon",
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.name ?: "Guest User",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentUser?.email ?: "Not logged in",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 🪙 Account Type Badge
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AccountBadge(currentUser?.accountType)
                                RoleBadge(currentUser?.role)
                            }
                        }
                    }
                }
            }*/

            // ⚙️ Appearance Section
            item {
                SectionTitle("Appearance")
            }

            // 🌙 Dark Mode
            item {
                SettingItem(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.dark_mode),
                    subtitle = stringResource(R.string.enable_dark_mode),
                    trailing = {
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { settingsViewModel.setDarkMode(it) }
                        )
                    }
                )
            }

            // 🌐 Language
            item {
                SettingItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language),
                    subtitle = if (language == "hi") stringResource(R.string.hindi) else stringResource(
                        R.string.english
                    ),
                    onClick = { showLanguageDialog = true },
                    trailing = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                )
            }

            // 🔔 Preferences Section
            item {
                SectionTitle("Preferences")
            }

            // Notifications
            item {
                SettingItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.notifications),
                    subtitle = stringResource(R.string.enable_notifications),
                    trailing = {
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { settingsViewModel.setNotificationsEnabled(it) }
                        )
                    }
                )
            }

            /*
            // 👤 Account Section
            item {
                SectionTitle("Account")
            }

            // Edit Profile
            item {
                SettingItem(
                    icon = Icons.Default.Edit,
                    title = stringResource(R.string.edit_profile),
                    subtitle = "Update your profile information",
                    onClick = { *//* TODO: navigate to EditProfileScreen *//* },
                    trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                )
            }

            // Privacy Settings
            item {
                SettingItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy",
                    subtitle = "Manage your privacy settings",
                    onClick = { *//* TODO: navigate to PrivacyScreen *//* },
                    trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                )
            }

            // Logout Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.logout))
                }
            }*/
        }

        // 🌐 Language Selection Dialog
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.select_language)) },
                text = {
                    Column {
                        RadioButtonItem(
                            text = stringResource(R.string.english),
                            selected = language == "en",
                            onClick = {
                                settingsViewModel.setLanguage("en")
                                showLanguageDialog = false
                            }
                        )
                        RadioButtonItem(
                            text = stringResource(R.string.hindi),
                            selected = language == "hi",
                            onClick = {
                                settingsViewModel.setLanguage("hi")
                                showLanguageDialog = false
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        /*
        // 🚪 Logout Dialog
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text(stringResource(R.string.logout)) },
                text = { Text("Are you sure you want to logout?") },
                confirmButton = {
                    TextButton(onClick = {
                        authViewModel.logout()
                        showLogoutDialog = false
                        onLogout()
                    }) {
                        Text(stringResource(R.string.logout))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }*/
    }
}

// 🪙 Account Type Badge
@Composable
fun AccountBadge(accountType: AccountType?) {
    val bgColor = when (accountType) {
        AccountType.PREMIUM -> MaterialTheme.colorScheme.tertiaryContainer
        AccountType.AD_FREE -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val label = when (accountType) {
        AccountType.PREMIUM -> "Premium"
        AccountType.AD_FREE -> "Ad-Free"
        else -> "Free"
    }
    BadgeBox(label, bgColor)
}

// 🧰 Role Badge
@Composable
fun RoleBadge(role: UserRole?) {
    val bgColor = when (role) {
        UserRole.PROFESSIONAL -> MaterialTheme.colorScheme.primaryContainer
        UserRole.ADMIN -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val label = when (role) {
        UserRole.PROFESSIONAL -> "Professional"
        UserRole.ADMIN -> "Admin"
        else -> "User"
    }
    BadgeBox(label, bgColor)
}

@Composable
private fun BadgeBox(label: String, bgColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// Title for each section
@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

// Reusable setting item
@Composable
fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.let {
                Spacer(modifier = Modifier.width(8.dp))
                it()
            }
        }
    }
}

@Composable
fun RadioButtonItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}
