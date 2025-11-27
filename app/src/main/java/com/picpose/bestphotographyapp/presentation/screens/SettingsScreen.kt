package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val language by settingsViewModel.language.collectAsState()
    val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }

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

            // ---------------------
            // APPEARANCE SECTION
            // ---------------------
            item { SectionTitle("Appearance") }

            // 🌙 Dark Mode Switch (ON = dark, OFF = light)
            item {
                val isDarkChecked = themeMode == "dark"

                SettingItem(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Mode",
                    subtitle = "Enable or disable dark theme",
                    trailing = {
                        Switch(
                            checked = isDarkChecked,
                            onCheckedChange = { checked ->
                                if (checked) settingsViewModel.setThemeMode("dark")
                                else settingsViewModel.setThemeMode("light")
                            }
                        )
                    }
                )
            }

            // 🌐 Language Selector
            item {
                SettingItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language),
                    subtitle = if (language == "hi") "Hindi" else "English",
                    onClick = { showLanguageDialog = true },
                    trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                )
            }

            // ---------------------
            // PREFERENCES SECTION
            // ---------------------
            item { SectionTitle("Preferences") }

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
        }

        // LANGUAGE DIALOG
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text("Select Language") },
                text = {
                    Column {
                        RadioButtonItem(
                            text = "English",
                            selected = language == "en",
                            onClick = {
                                settingsViewModel.setLanguage("en")
                                showLanguageDialog = false
                            }
                        )
                        RadioButtonItem(
                            text = "Hindi",
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
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// --------- Composables -----------

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

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
