package com.picpose.bestphotographyapp.presentation.screens

import android.widget.Toast
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
import com.picpose.bestphotographyapp.data.models.SupportQueryRequest
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsUiState
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
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
    val supportEmail = when (appSettingsState) {
        is AppSettingsUiState.Success -> (appSettingsState as AppSettingsUiState.Success).settings.contact.email
        else -> "support@picpose.com"
    }
    val supportPhone = when (appSettingsState) {
        is AppSettingsUiState.Success -> (appSettingsState as AppSettingsUiState.Success).settings.contact.phone
        else -> "+1-234-567-8900"
    }

    // ✅ Perfect edge-to-edge Scaffold setup
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_support_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = colorScheme.onSurface
                ),
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
                    stringResource(R.string.help_intro_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )

                Text(
                    stringResource(R.string.help_intro_description),
                    color = colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.help_your_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.help_your_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.help_phone_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.help_message)) },
                    placeholder = { Text(stringResource(R.string.help_message_placeholder)) },
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
                        if (name.text.isBlank() || email.text.isBlank() || message.text.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.help_required_fields), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true
                        scope.launch {
                            try {
                                val apiService = RetrofitClient.createService(ApiService::class.java)
                                val request = SupportQueryRequest(
                                    name = name.text.trim(),
                                    email = email.text.trim(),
                                    phone = phone.text.trim(),
                                    message = message.text.trim(),
                                    userId = currentUser?.id
                                )
                                val response = apiService.submitSupportQuery(
                                    request = request,
                                    apiKey = RetrofitClient.defaultApiKey
                                )

                                if (response.isSuccessful && response.body()?.success == true) {
                                    Toast.makeText(
                                        context,
                                        response.body()?.message ?: context.getString(R.string.message_sent_successfully),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    message = TextFieldValue("")
                                } else {
                                    Toast.makeText(
                                        context,
                                        response.body()?.message ?: context.getString(R.string.failed_to_send_message),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error_with_message, e.message ?: ""),
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
                        Text(stringResource(R.string.submit), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider(thickness = 1.dp, color = colorScheme.outline.copy(alpha = 0.3f))

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.help_also_reach_us), color = colorScheme.onSurfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Email, contentDescription = stringResource(R.string.email), tint = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(supportEmail, color = colorScheme.onSurface)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Phone, contentDescription = stringResource(R.string.phone), tint = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(supportPhone, color = colorScheme.onSurface)
                }

                Text(
                    stringResource(R.string.help_support_reply_time),
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    color = colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
