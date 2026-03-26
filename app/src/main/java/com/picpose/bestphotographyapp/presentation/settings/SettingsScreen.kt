/**
 * ---
 * File: SettingsScreen.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Lists the app navigation routes and helper builders used by Navigation Compose.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * UI (Compose) -> ViewModel -> Repository -> Local/Remote Data Source -> Room/API
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.common.appSectionCardBorder
import com.picpose.bestphotographyapp.components.common.appSectionCardColors
import com.picpose.bestphotographyapp.components.common.appSectionCardElevation
import com.picpose.bestphotographyapp.components.common.appSectionCardShape
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar
import com.picpose.bestphotographyapp.data.local.datastore.ThemeMode
import com.picpose.bestphotographyapp.presentation.auth.AuthViewModel
import com.picpose.bestphotographyapp.presentation.settings.SettingsViewModel
import kotlinx.coroutines.launch

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
    onAccountDeleted: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val language by settingsViewModel.language.collectAsState()
    val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsState()
    val skipGeminiDialog by settingsViewModel.skipGeminiDialog.collectAsState(initial = false)
    val notificationsToggleInProgress by settingsViewModel.notificationsToggleInProgress.collectAsState()
    val showSystemNotificationSettingsDialog by settingsViewModel.showSystemNotificationSettingsDialog.collectAsState()
    val showGeminiConfirmDialog by settingsViewModel.showGeminiConfirmDialog.collectAsState()
    val pendingGeminiAction by settingsViewModel.pendingGeminiAction.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val isDeletingAccount by authViewModel.isDeletingAccount.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteConfirmInput by remember(showDeleteAccountDialog) { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is SettingsViewModel.SettingsEvent.ShowMessage -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(event.messageRes))
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            PicPoseTopAppBar(
                title = stringResource(R.string.settings),
                onBack = onNavigateBack,
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            item {
                SettingsSectionCard(title = stringResource(R.string.account_settings)) {
                    AnimatedVisibility(
                        visible = isLoggedIn,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            SettingsRow(
                                icon = Icons.AutoMirrored.Filled.Logout,
                                title = stringResource(R.string.logout),
                                subtitle = stringResource(R.string.logout_subtitle_device),
                                onClick = onLogout
                            )

                            SectionDivider()

                            SettingsRow(
                                icon = Icons.Default.DeleteForever,
                                title = stringResource(R.string.delete_account_title),
                                subtitle = stringResource(R.string.delete_account_subtitle),
                                onClick = { showDeleteAccountDialog = true }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !isLoggedIn,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.please_login_to_edit_profile),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = stringResource(R.string.section_appearance)) {
                    SettingsRow(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(R.string.settings_theme_title),
                        subtitle = stringResource(R.string.settings_theme_description)
                    )

                    SectionDivider()

                    ThemeModeOptionRow(
                        text = stringResource(R.string.settings_theme_system),
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { settingsViewModel.setThemeMode(ThemeMode.SYSTEM) }
                    )
                    ThemeModeOptionRow(
                        text = stringResource(R.string.settings_theme_light),
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { settingsViewModel.setThemeMode(ThemeMode.LIGHT) }
                    )
                    ThemeModeOptionRow(
                        text = stringResource(R.string.settings_theme_dark),
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { settingsViewModel.setThemeMode(ThemeMode.DARK) }
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
                        enabled = !notificationsToggleInProgress,
                        onCheckedChange = settingsViewModel::onNotificationsToggleRequested
                    )

                    SectionDivider()

                    ToggleSettingsRow(
                        icon = Icons.Default.AutoAwesome,
                        title = stringResource(R.string.gemini_confirmation),
                        subtitle = if (skipGeminiDialog)
                            stringResource(R.string.gemini_confirmation_disabled)
                        else
                            stringResource(R.string.gemini_confirmation_enabled),
                        checked = !skipGeminiDialog,
                        onCheckedChange = settingsViewModel::onGeminiConfirmationToggleRequested
                    )
                }
            }

        }

        if (showSystemNotificationSettingsDialog) {
            AlertDialog(
                onDismissRequest = settingsViewModel::dismissSystemNotificationDialog,
                title = { Text(stringResource(R.string.notifications_system_disabled_title)) },
                text = {
                    Text(stringResource(R.string.notifications_system_disabled_message))
                },
                confirmButton = {
                    TextButton(onClick = {
                        settingsViewModel.dismissSystemNotificationDialog()
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = settingsViewModel::dismissSystemNotificationDialog) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showGeminiConfirmDialog && pendingGeminiAction != null) {
            val action = pendingGeminiAction
            AlertDialog(
                onDismissRequest = settingsViewModel::dismissGeminiActionDialog,
                title = {
                    Text(
                        if (action == SettingsViewModel.GeminiConfirmationAction.ENABLE) {
                            stringResource(R.string.settings_gemini_enable_title)
                        } else {
                            stringResource(R.string.settings_gemini_disable_title)
                        }
                    )
                },
                text = {
                    Text(
                        if (action == SettingsViewModel.GeminiConfirmationAction.ENABLE) {
                            stringResource(R.string.settings_gemini_enable_message)
                        } else {
                            stringResource(R.string.settings_gemini_disable_message)
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = settingsViewModel::confirmGeminiAction) {
                        Text(
                            if (action == SettingsViewModel.GeminiConfirmationAction.ENABLE) {
                                stringResource(R.string.action_enable)
                            } else {
                                stringResource(R.string.action_disable)
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = settingsViewModel::dismissGeminiActionDialog) {
                        Text(stringResource(R.string.action_cancel))
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

        if (showDeleteAccountDialog) {
            val deleteKeyword = stringResource(R.string.delete_account_delete_keyword)
            val isDeletePhraseValid = deleteConfirmInput.trim() == deleteKeyword
            AlertDialog(
                onDismissRequest = {
                    if (!isDeletingAccount) showDeleteAccountDialog = false
                },
                title = { Text(stringResource(R.string.delete_account_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.delete_account_confirm_message))
                        Text(
                            text = stringResource(R.string.delete_account_type_delete_instruction),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = deleteConfirmInput,
                            onValueChange = { deleteConfirmInput = it },
                            singleLine = true,
                            enabled = !isDeletingAccount,
                            label = { Text(stringResource(R.string.delete_account_type_delete_label)) },
                            placeholder = { Text(deleteKeyword) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isDeletingAccount && isDeletePhraseValid,
                        onClick = {
                            authViewModel.deleteAccount { result ->
                                if (result.isSuccess) {
                                    showDeleteAccountDialog = false
                                    deleteConfirmInput = ""
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.delete_account_success)
                                        )
                                    }
                                    onAccountDeleted()
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            result.exceptionOrNull()?.message
                                                ?: context.getString(R.string.delete_account_failed)
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        Text(
                            if (isDeletingAccount) {
                                stringResource(R.string.delete_account_in_progress)
                            } else {
                                stringResource(R.string.delete_account_action)
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isDeletingAccount,
                        onClick = {
                            showDeleteAccountDialog = false
                            deleteConfirmInput = ""
                        }
                    ) {
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
            shape = appSectionCardShape,
            colors = appSectionCardColors(),
            elevation = appSectionCardElevation(defaultElevation = 2.dp),
            border = appSectionCardBorder(alpha = 0.1f)
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
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = {
            Box(
                modifier = Modifier
                    .semantics { role = Role.Switch }
                    .then(
                        if (enabled) {
                            Modifier.clickable { onCheckedChange(!checked) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 8.dp)
            ) {
                Switch(
                    checked = checked,
                    enabled = enabled,
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

@Composable
private fun ThemeModeOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
