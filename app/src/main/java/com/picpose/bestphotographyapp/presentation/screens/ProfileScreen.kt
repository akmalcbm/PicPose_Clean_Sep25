package com.picpose.bestphotographyapp.presentation.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.data.models.SupportQueryRequest
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.presentation.components.home.QuickActionsCard
import com.picpose.bestphotographyapp.presentation.components.home.QuickStatsCard
import com.picpose.bestphotographyapp.presentation.navigation.Screen
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.StatsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    onNavigateToAllPrompts: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val appSettings = appSettingsViewModel.uiState.value

    // Load app settings and user data on first composition
    LaunchedEffect(Unit) {
        appSettingsViewModel.loadAppSettings()
        // Load user data if logged in but user info is empty
        if (isLoggedIn && currentUser == null) {
            authViewModel.fetchCurrentUser()
        }
    }

    // Profile options organized in groups
    val profileManagementOptions = listOf(
        ProfileOption("Edit Profile", "Update your profile information", Icons.Filled.Edit),
        ProfileOption("Settings", "App settings and preferences", Icons.Filled.Settings)
    )
    
    val appInfoOptions = listOf(
        ProfileOption("Privacy Policy", "Read our privacy policy", Icons.Filled.PrivacyTip),
        ProfileOption("Terms & Conditions", "Terms and conditions", Icons.Filled.Description),
        ProfileOption("About", "About the app", Icons.Filled.Info)
    )
    
    val supportOptions = listOf(
        ProfileOption("Help & Support", "Get help and contact support", Icons.AutoMirrored.Filled.HelpOutline)
    )

    // 🌈 Beautiful gradient background for header
    val gradientBackground = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surface
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // 👤 Profile Header (Centered)
        item {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.95f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(vertical = 32.dp, horizontal = 24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Profile Picture with Camera Icon
                            Box(
                                modifier = Modifier.size(120.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                if (!currentUser?.profilePicture.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(currentUser?.profilePicture)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Profile Picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                } else {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.Person,
                                                contentDescription = "Profile Picture",
                                                modifier = Modifier.size(50.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }

                                // 📸 Camera Overlay Icon
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Edit Photo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 👤 Name
                            Text(
                                text = currentUser?.name ?: "Guest User",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 📧 Email
                            Text(
                                text = currentUser?.email ?: "Not logged in",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 📝 Bio
                            Text(
                                text = currentUser?.bio ?: "Capturing moments, creating memories ✨",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // 🔹 Quick stats (Hilt-injected ViewModel)
        item {
            val statsViewModel: StatsViewModel = hiltViewModel()

            QuickStatsCard(
                viewModel = statsViewModel,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = IntOffset.VisibilityThreshold
                        )
                    ) // ✅ smooth animated appearance
            )
        }


        // 🔹 Quick actions
        item {
            QuickActionsCard(
                onNavigateToAllPrompts = onNavigateToAllPrompts,
                onNavigateToFavorites = onNavigateToFavorites,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }



        // 👤 Profile Management Section
        item {
            Column {
                Text(
                    text = "👤 Profile Management",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                )

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    ProfileOptionCard(
                        option = ProfileOption(
                            title = "Edit Profile",
                            description = "Update your profile information",
                            icon = Icons.Filled.Edit
                        ),
                        onClick = { onNavigateToEditProfile() }
                    )
                }
            }
        }

        // ⚙️ App Settings Section
        item {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "⚙️ App Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                )

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    ProfileOptionCard(
                        option = ProfileOption(
                            title = "Settings",
                            description = "Customize your app preferences",
                            icon = Icons.Filled.Settings
                        ),
                        onClick = { onNavigateToSettings() }
                    )
                }
            }
        }


        // 📄 App Info Section
        item {
            Text(
                text = "App Info",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp, start = 4.dp, end = 4.dp)
            )
        }

        items(appInfoOptions) { option ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ProfileOptionCard(
                    option = option,
                    onClick = {
                        when (option.title) {
                            "Privacy Policy" -> navController.navigate(Screen.Privacy.route)
                            "Terms & Conditions" -> navController.navigate(Screen.Terms.route)
                            "About" -> navController.navigate(Screen.About.route)
                        }
                    }
                )
            }
        }

        // 🆘 Support Section
        item {
            Text(
                text = "Support",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp, start = 4.dp, end = 4.dp)
            )
        }

        items(supportOptions) { option ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ProfileOptionCard(
                    option = option,
                    onClick = {
                        when (option.title) {
                            "Help & Support" -> showHelpDialog = true
                        }
                    }
                )
            }
        }

        // 🔒 Login / Logout
        item {
            if (isLoggedIn) {
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout")
                }
            } else {
                Button(
                    onClick = { onNavigateToLogin() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Login", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Login")
                }
            }
        }
    }

    // 🔔 Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 🆘 Help & Support Dialog
    if (showHelpDialog) {
        HelpSupportDialog(
            supportEmail = appSettings?.supportEmail ?: "support@picpose.com",
            supportPhone = appSettings?.supportPhone ?: "+1-234-567-8900",
            currentUser = currentUser,
            onDismiss = { showHelpDialog = false }
        )
    }
}


@Composable
fun StatCard(label: String, value: String) {
    Card(
        modifier = Modifier.width(90.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileOptionCard(option: ProfileOption, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(option.icon, contentDescription = option.title, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(option.title, fontWeight = FontWeight.Medium)
                Text(option.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Navigate", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportDialog(
    supportEmail: String,
    supportPhone: String,
    currentUser: com.picpose.bestphotographyapp.data.models.User?,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ✳️ Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Help & Support",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // 📞 Support Contact Info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SupportRow(
                                icon = Icons.Default.Email,
                                label = "Email",
                                value = supportEmail
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            SupportRow(
                                icon = Icons.Default.Phone,
                                label = "Phone",
                                value = supportPhone
                            )
                        }
                    }

                    // 📨 Message Input
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Send us a message",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text("Your Message") },
                            placeholder = {
                                Text(
                                    "Describe your issue or question...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            shape = RoundedCornerShape(16.dp),
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        if (currentUser != null) {
                            Text(
                                text = "Sending as: ${currentUser.name} (${currentUser.email})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    // 🔘 Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSubmitting
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (message.isNotBlank()) {
                                    isSubmitting = true
                                    scope.launch {
                                        try {
                                            val apiService = RetrofitClient.createService(ApiService::class.java)
                                            val request = SupportQueryRequest(
                                                userId = currentUser?.id,
                                                email = currentUser?.email ?: supportEmail,
                                                name = currentUser?.name ?: "Guest",
                                                message = message
                                            )
                                            val response = apiService.submitSupportQuery(request)

                                            if (response.isSuccessful && response.body()?.success == true) {
                                                snackbarHostState.showSnackbar("Message sent successfully!")
                                                kotlinx.coroutines.delay(1200)
                                                onDismiss()
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
                                } else {
                                    Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSubmitting && message.isNotBlank()
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Submit")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


data class ProfileOption(val title: String, val description: String, val icon: ImageVector)
