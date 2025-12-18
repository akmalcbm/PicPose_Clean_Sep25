package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.picpose.bestphotographyapp.presentation.components.home.QuickActionsCard
import com.picpose.bestphotographyapp.presentation.components.home.QuickStatsCard
import com.picpose.bestphotographyapp.presentation.navigation.Screen
import com.picpose.bestphotographyapp.presentation.viewmodels.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.StatsViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

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
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }

    // ⭐ RANDOM fallback bios (UI only)
    val fallbackBios = remember {
        listOf(
            "Capturing moments, creating memories ✨",
            "Chasing light & freezing time 📸",
            "Turning everyday life into art 🎨",
            "Life looks better through a lens ✨",
            "Framing emotions in every shot 💫"
        )
    }

    val chosenFallbackBio = remember { fallbackBios.random() }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                ),
                title = { Text("Profile", fontWeight = FontWeight.Bold) }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp, start = 16.dp, end = 16.dp)
        ) {

            // -------------------------------
            // PROFILE HEADER CARD
            // -------------------------------
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {

                    ProfileHeaderCard(
                        currentUser = currentUser,
                        fallbackBio = chosenFallbackBio
                    )
                }
            }

            // Quick Stats
            item {
                val statsViewModel: StatsViewModel = hiltViewModel()
                QuickStatsCard(
                    viewModel = statsViewModel,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                )
            }

            // Quick Actions
            item {
                QuickActionsCard(
                    onNavigateToAllPrompts = onNavigateToAllPrompts,
                    onNavigateToFavorites = onNavigateToFavorites,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // ------------------------
            // PROFILE MANAGEMENT
            // ------------------------
            item {
                SectionHeader("Profile Management")
            }

            item {
                ProfileOptionCard(
                    option = ProfileOption(
                        title = "Edit Profile",
                        description = "Update your profile information",
                        icon = Icons.Filled.Edit
                    ),
                    onClick = { onNavigateToEditProfile() }
                )
            }

            // ------------------------
            // APP SETTINGS
            // ------------------------
            item {
                SectionHeader("App Settings")
            }

            item {
                ProfileOptionCard(
                    option = ProfileOption(
                        title = "Settings",
                        description = "Customize your app preferences",
                        icon = Icons.Filled.Settings
                    ),
                    onClick = onNavigateToSettings
                )
            }

            // ------------------------
            // APP INFO
            // ------------------------
            item { SectionHeader("App Info") }

            items(
                listOf(
                    ProfileOption("Privacy Policy", "Read our privacy policy", Icons.Filled.PrivacyTip),
                    ProfileOption("Terms & Conditions", "Terms and conditions", Icons.Filled.Description),
                    ProfileOption("About", "About the app", Icons.Filled.Info)
                )
            ) { option ->
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

            // ------------------------
            // SUPPORT
            // ------------------------
            item { SectionHeader("Support") }

            item {
                ProfileOptionCard(
                    option = ProfileOption(
                        "Help & Support",
                        "Get help and contact support",
                        Icons.AutoMirrored.Filled.HelpOutline
                    ),
                    onClick = { navController.navigate(Screen.HelpAndSupportScreen.route) }
                )
            }

            // ------------------------
            // LOGOUT / LOGIN BUTTON
            // ------------------------
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
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout")
                    }
                } else {
                    Button(
                        onClick = onNavigateToLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Login")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Login")
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        LogoutDialog(onDismiss = { showLogoutDialog = false }, onConfirm = onLogout)
    }
}

// -----------------------------------------------------
// PROFILE HEADER COMPOSABLE (Enhanced & Polished)
// -----------------------------------------------------
@Composable
private fun ProfileHeaderCard(
    currentUser: com.picpose.bestphotographyapp.data.models.User?,
    fallbackBio: String
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(10.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ----------------------------
            // PROFILE IMAGE WITH RING
            // ----------------------------
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {

                NeonGradientRing(
                    size = 150.dp,
                    borderWidth = 3.dp
                ) {
                    if (!currentUser?.displayProfilePicture.isNullOrBlank()) {
                        AsyncImage(
                            model = currentUser.displayProfilePicture,
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        DefaultProfileImage(
                            modifier = Modifier.size(140.dp)
                        )
                    }
                }

            }

            Spacer(Modifier.height(14.dp))

            // ----------------------------
            // NAME
            // ----------------------------
            Text(
                text = currentUser?.displayName ?: "Guest User",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            // ----------------------------
            // EMAIL
            // ----------------------------
            Text(
                text = currentUser?.email ?: "Not logged in",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            // ----------------------------
            // BIO
            // ----------------------------
            Text(
                text = currentUser?.bio?.takeIf { it.isNotBlank() } ?: fallbackBio,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(0.90f)
            )
        }
    }
}

@Composable
fun NeonGradientRing(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    borderWidth: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val gradientColors = listOf(
        Color(0xFFFF5F6D),  // Pink
        Color(0xFFFFC371),  // Orange
        Color(0xFF42E695),  // Green
        Color(0xFF3BB2B8),  // Teal
        Color(0xFF4776E6),  // Blue
        Color(0xFF8E54E9)   // Purple
    )

    val brush = Brush.sweepGradient(gradientColors)

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 25.dp,
                shape = CircleShape,
                ambientColor = Color(0xFF8E54E9).copy(alpha = 0.7f),
                spotColor = Color(0xFFFF5F6D).copy(alpha = 0.7f)
            )
            .background(
                brush = brush,
                shape = CircleShape
            )
            .padding(borderWidth),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}


@Composable
private fun DefaultProfileImage(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        color = MaterialTheme.colorScheme.primary,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}


@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

@Composable
fun ProfileOptionCard(option: ProfileOption, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(option.icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(option.title, fontWeight = FontWeight.Medium)
                Text(
                    option.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}

@Composable
private fun LogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logout") },
        text = { Text("Are you sure you want to logout?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Logout") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

data class ProfileOption(val title: String, val description: String, val icon: ImageVector)
