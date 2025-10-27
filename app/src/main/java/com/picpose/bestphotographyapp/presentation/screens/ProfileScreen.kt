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

    // Load app settings on first composition
    LaunchedEffect(Unit) {
        appSettingsViewModel.loadAppSettings()
    }

    val profileOptions = listOf(
        ProfileOption("Edit Profile", "Update your profile information", Icons.Filled.Edit),
        ProfileOption("Settings", "App settings and preferences", Icons.Filled.Settings),
        ProfileOption("Privacy Policy", "Read our privacy policy", Icons.Filled.PrivacyTip),
        ProfileOption("Help & Support", "Get help and contact support", Icons.AutoMirrored.Filled.HelpOutline),
        ProfileOption("About", "About the app and its creators", Icons.Filled.Info)
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



        // ⚙️ Profile Options Section
        item {
            Text(
                text = "Profile Options",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }

        // ✅ Profile Options List
        items(profileOptions) { option ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ProfileOptionCard(
                    option = option,
                    onClick = {
                        when (option.title) {
                            "Edit Profile" -> onNavigateToEditProfile()
                            "Settings" -> onNavigateToSettings()
                            "Privacy Policy" -> navController.navigate(Screen.Privacy.route)
                            "About" -> navController.navigate(Screen.About.route)
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Help & Support",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider()

                // Support Email
                Column {
                    Text(
                        text = "Support Email",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = supportEmail,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Support Phone
                Column {
                    Text(
                        text = "Support Phone",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = supportPhone,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider()

                // Message Input
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Your Message") },
                    placeholder = { Text("Describe your issue or question...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 6
                )

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
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
                                            Toast.makeText(
                                                context,
                                                "Your query has been submitted successfully!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                response.body()?.message ?: "Failed to submit query",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Error: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting && message.isNotBlank()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
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

data class ProfileOption(val title: String, val description: String, val icon: ImageVector)
