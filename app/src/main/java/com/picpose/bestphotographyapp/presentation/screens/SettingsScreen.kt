package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material3.TopAppBarDefaults
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
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel
import com.picpose.bestphotographyapp.R

private data class LanguageOption(
    val code: String,
    val label: String
)

private val languageOptions = listOf(
    LanguageOption("system", "System Default"),
    LanguageOption("en", "English"),
    LanguageOption("hi", "हिन्दी"),
    LanguageOption("zh-CN", "中文(简体)"),
    LanguageOption("es", "Español"),
    LanguageOption("ar", "العربية"),
    LanguageOption("pt-BR", "Português (Brasil)"),
    LanguageOption("id", "Bahasa Indonesia")
)

private fun languageLabel(languageCode: String): String =
    languageOptions.firstOrNull { it.code == languageCode }?.label ?: "English"

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
    val skipGeminiDialog by settingsViewModel.skipGeminiDialog.collectAsState(initial = false)

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showResetGeminiDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                //Use below code if you want same Bottom Nav Background Color
                /*colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),*/
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
            )

        },
        // 🔥 IMPORTANT — SAME AS ProfileScreen
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 32.dp // ✅ small & predictable
            )
        ) {

            // ---------------------
            // APPEARANCE
            // ---------------------
            item { SectionTitle(stringResource(R.string.section_appearance)) }

            item {
                SettingItem(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.dark_mode),
                    subtitle = stringResource(R.string.dark_mode_subtitle),
                    trailing = {
                        Switch(
                            checked = themeMode == "dark",
                            onCheckedChange = {
                                settingsViewModel.setThemeMode(
                                    if (it) "dark" else "light"
                                )
                            }
                        )
                    }
                )
            }

            item {
                SettingItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language),
                    subtitle = languageLabel(language),
                    onClick = { showLanguageDialog = true },
                    trailing = {
                        Icon(Icons.Default.ChevronRight, null)
                    }
                )
            }

            // ---------------------
            // PREFERENCES
            // ---------------------
            item { SectionTitle(stringResource(R.string.section_preferences)) }

            item {
                SettingItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.notifications),
                    subtitle = stringResource(R.string.notifications_subtitle),
                    trailing = {
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = {
                                settingsViewModel.setNotificationsEnabled(it)
                            }
                        )
                    }
                )
            }

            item {
                SettingItem(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.gemini_confirmation),
                    subtitle = if (skipGeminiDialog)
                        stringResource(R.string.gemini_confirmation_disabled)
                    else
                        stringResource(R.string.gemini_confirmation_enabled),
                    onClick = {
                        if (skipGeminiDialog) showResetGeminiDialog = true
                    },
                    trailing = {
                        Text(
                            text = if (skipGeminiDialog)
                                stringResource(R.string.reset)
                            else
                                stringResource(R.string.enabled),
                            color = if (skipGeminiDialog)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }

        // ---------------------
        // RESET GEMINI DIALOG
        // ---------------------
        if (showResetGeminiDialog) {
            AlertDialog(
                onDismissRequest = { showResetGeminiDialog = false },
                title = { Text(stringResource(R.string.reset_gemini_title)) },
                text = {
                    Text(stringResource(R.string.reset_gemini_message))
                },
                confirmButton = {
                    TextButton(onClick = {
                        settingsViewModel.resetGeminiDialog()
                        showResetGeminiDialog = false
                    }) {
                        Text(stringResource(R.string.reset))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetGeminiDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // ---------------------
        // LANGUAGE DIALOG
        // ---------------------
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.select_language)) },
                text = {
                    Column {
                        languageOptions.forEach { option ->
                            RadioButtonItem(
                                text = option.label,
                                selected = language == option.code,
                                onClick = {
                                    settingsViewModel.setLanguage(option.code)
                                    showLanguageDialog = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

// --------- Composables -----------

@Composable
fun SectionTitle(title: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = title.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
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
