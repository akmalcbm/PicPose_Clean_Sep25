/**
 * ---
 * File: HelpAndSupportScreen.kt
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

package com.picpose.bestphotographyapp.presentation.about

import android.widget.Toast
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.HorizontalDivider
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar
import com.picpose.bestphotographyapp.data.remote.dto.SupportQueryRequest
import com.picpose.bestphotographyapp.data.remote.api.ApiService
import com.picpose.bestphotographyapp.core.network.RetrofitClient
import com.picpose.bestphotographyapp.presentation.settings.AppSettingsUiState
import com.picpose.bestphotographyapp.presentation.settings.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.auth.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpAndSupportScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val appSettingsState by appSettingsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 🔹 Load app settings once
    LaunchedEffect(Unit) { appSettingsViewModel.loadAppSettings() }

    // 🔸 Form state
    var name by remember { mutableStateOf(TextFieldValue(currentUser?.displayName ?: "")) }
    var email by remember { mutableStateOf(TextFieldValue(currentUser?.email ?: "")) }
    var phone by remember { mutableStateOf(TextFieldValue("")) }
    var message by remember { mutableStateOf(TextFieldValue("")) }
    var isSubmitting by remember { mutableStateOf(false) }

    // 🔸 Contact info from settings or fallback
    val fallbackSupportEmail = stringResource(R.string.help_support_email_fallback)
    val fallbackSupportPhone = stringResource(R.string.help_support_phone_fallback)
    val supportEmail = when (appSettingsState) {
        is AppSettingsUiState.Success -> (appSettingsState as AppSettingsUiState.Success).settings.contact.email
        else -> fallbackSupportEmail
    }
    val supportPhone = when (appSettingsState) {
        is AppSettingsUiState.Success -> (appSettingsState as AppSettingsUiState.Success).settings.contact.phone
        else -> fallbackSupportPhone
    }

    // ✅ Perfect edge-to-edge Scaffold setup
    Scaffold(
        topBar = {
            PicPoseTopAppBar(
                title = stringResource(R.string.help_title),
                onBack = onBack,
            )
        },
        contentWindowInsets = WindowInsets(0) // 🚫 prevent double padding
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(innerPadding)
                .padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                )
                .imePadding() // ✅ smooth keyboard handling
        ) {

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    stringResource(R.string.help_intro_description),
                    color = colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.help_name_label)) },
                    placeholder = { Text(stringResource(R.string.help_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.help_email_label)) },
                    placeholder = { Text(stringResource(R.string.help_email_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.help_phone_label)) },
                    placeholder = { Text(stringResource(R.string.help_phone_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.help_message_label)) },
                    placeholder = { Text(stringResource(R.string.help_message_hint)) },
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { /* Close keyboard */ }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 🔘 Submit Button
                Button(
                    onClick = {
                        val trimmedName = name.text.trim()
                        val trimmedEmail = email.text.trim()
                        val trimmedPhone = phone.text.trim()
                        val trimmedMessage = message.text.trim()
                        val validationError = when {
                            trimmedName.isEmpty() -> context.getString(R.string.help_error_name_required)
                            !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches() -> context.getString(R.string.help_error_invalid_email)
                            trimmedMessage.isEmpty() -> context.getString(R.string.help_error_message_required)
                            else -> null
                        }
                        if (validationError != null) {
                            Toast.makeText(context, validationError, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true
                        scope.launch {
                            try {
                                val apiService = RetrofitClient.createService(ApiService::class.java)
                                val request = SupportQueryRequest(
                                    name = trimmedName,
                                    email = trimmedEmail,
                                    phone = trimmedPhone,
                                    message = trimmedMessage,
                                    userId = currentUser?.id
                                )
                                val response = apiService.submitSupportQuery(
                                    request = request,
                                    apiKey = RetrofitClient.defaultApiKey
                                )

                                if (response.isSuccessful && response.body()?.success == true) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.help_success_message),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    message = TextFieldValue("")
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.help_failure_message),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.help_failure_message),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.help_submit_button), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(thickness = 1.dp, color = colorScheme.outline.copy(alpha = 0.3f))

                Spacer(modifier = Modifier.height(12.dp))

                Text(stringResource(R.string.help_contact_section_title), color = colorScheme.onSurfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = stringResource(R.string.help_contact_email_content_description),
                        tint = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(supportEmail, color = colorScheme.onSurface)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Phone,
                        contentDescription = stringResource(R.string.help_contact_phone_content_description),
                        tint = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(supportPhone, color = colorScheme.onSurface)
                }

                Text(
                    stringResource(R.string.help_response_time_note),
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    color = colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
