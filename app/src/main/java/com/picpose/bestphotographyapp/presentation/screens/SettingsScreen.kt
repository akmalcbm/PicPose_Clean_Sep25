package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.BuildConfig
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel

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
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            item {
                SettingsSectionCard(title = stringResource(R.string.account_settings)) {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = stringResource(R.string.logout),
                        subtitle = "Sign out from this device",
                        onClick = onLogout
                    )
                }
            }

            item {
                SettingsSectionCard(title = stringResource(R.string.section_appearance)) {
                    ToggleSettingsRow(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(R.string.dark_mode),
                        subtitle = stringResource(R.string.dark_mode_subtitle),
                        checked = themeMode == "dark",
                        onCheckedChange = { checked ->
                            settingsViewModel.setThemeMode(if (checked) "dark" else "light")
                        }
                    )

                    SectionDivider()

                    SettingsRow(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.language),
                        subtitle = languageLabel(language),
                        onClick = { showLanguageDialog = true },
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            item {
                SettingsSectionCard(title = stringResource(R.string.section_preferences)) {
                    ToggleSettingsRow(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.notifications),
                        subtitle = stringResource(R.string.notifications_subtitle),
                        checked = notificationsEnabled,
                        onCheckedChange = settingsViewModel::setNotificationsEnabled
                    )

                    SectionDivider()

                    SettingsRow(
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
                                style = MaterialTheme.typography.labelMedium,
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

            item {
                SettingsSectionCard(title = stringResource(R.string.app_info)) {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.version),
                        subtitle = BuildConfig.VERSION_NAME
                    )
                }
            }

            item {
                Text(
                    text = "PicPose ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }

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

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    ListItem(
        modifier = rowModifier.heightIn(min = 56.dp),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        },
        trailingContent = trailing
    )
}

@Composable
private fun ToggleSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Box(
                modifier = Modifier
                    .semantics { role = Role.Switch }
                    .clickable { onCheckedChange(!checked) }
                    .padding(start = 8.dp)
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
        }
    )
}

@Composable
private fun RadioButtonItem(text: String, selected: Boolean, onClick: () -> Unit) {
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
