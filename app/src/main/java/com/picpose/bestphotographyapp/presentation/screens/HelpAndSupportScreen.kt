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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.picpose.bestphotographyapp.data.models.SupportQueryRequest
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.presentation.components.EdgeToEdgeScaffold
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

    // Load app settings once
    LaunchedEffect(Unit) { appSettingsViewModel.loadAppSettings() }

    // Form states
    var name by remember { mutableStateOf(TextFieldValue(currentUser?.name ?: "")) }
    var email by remember { mutableStateOf(TextFieldValue(currentUser?.email ?: "")) }
    var phone by remember { mutableStateOf(TextFieldValue("")) }
    var message by remember { mutableStateOf(TextFieldValue("")) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Contact info
    val supportEmail = when (appSettingsState) {
        is AppSettingsUiState.Success -> (appSettingsState as AppSettingsUiState.Success).settings.contact.email
        else -> "support@picpose.com"
    }
    val supportPhone = when (appSettingsState) {
        is AppSettingsUiState.Success -> (appSettingsState as AppSettingsUiState.Success).settings.contact.phone
        else -> "+1-234-567-8900"
    }

    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(innerPadding)
                .imePadding()               // ✅ prevents keyboard overlap
                .navigationBarsPadding()    // ✅ handles gesture/nav bars
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
                    "We’re here to help!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                Text(
                    "Please fill out the form below and our support team will get back to you shortly.",
                    color = colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Your Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (Optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    placeholder = { Text("Describe your issue or feedback...") },
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { /* close keyboard */ }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (name.text.isBlank() || email.text.isBlank() || message.text.isBlank()) {
                            Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
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
                                        response.body()?.message ?: "Message sent successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    message = TextFieldValue("")
                                } else {
                                    Toast.makeText(
                                        context,
                                        response.body()?.message ?: "Failed to send message",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        Text("Submit", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Divider(thickness = 1.dp, color = colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("You can also reach us at:", color = colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Email, contentDescription = "Email", tint = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(supportEmail, color = colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Phone, contentDescription = "Phone", tint = colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(supportPhone, color = colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Our support team typically replies within 24 hours.",
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    color = colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
