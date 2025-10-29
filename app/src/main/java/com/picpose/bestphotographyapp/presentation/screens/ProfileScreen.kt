package com.picpose.bestphotographyapp.presentation.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
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

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    //val appSettingsState by appSettingsViewModel.uiState.collectAsState()

    // Load app settings and user data on first composition
    LaunchedEffect(Unit) {
        appSettingsViewModel.loadAppSettings()
        if (isLoggedIn && currentUser == null) {
            authViewModel.fetchCurrentUser()
        }
    }

    /*
    // Extract settings from state
    val appSettings = when (appSettingsState) {
        is AppSettingsUiState.Success -> (appSettingsState as AppSettingsUiState.Success).settings
        is AppSettingsUiState.Error -> (appSettingsState as AppSettingsUiState.Error).cachedSettings
        else -> null
    }

    // Profile options organized in groups
    val profileManagementOptions = listOf(
        ProfileOption("Edit Profile", "Update your profile information", Icons.Filled.Edit),
    )
    */


    val appInfoOptions = listOf(
        ProfileOption("Privacy Policy", "Read our privacy policy", Icons.Filled.PrivacyTip),
        ProfileOption("Terms & Conditions", "Terms and conditions", Icons.Filled.Description),
        ProfileOption("About", "About the app", Icons.Filled.Info)
    )

    val supportOptions = listOf(
        ProfileOption(
            "Help & Support",
            "Get help and contact support",
            Icons.AutoMirrored.Filled.HelpOutline
        )
    )

    val backgroundColor = MaterialTheme.colorScheme.background

    // ✅ Wrapping everything in Scaffold for proper top inset handling
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                ),
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
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
                .background(backgroundColor)
                .padding(innerPadding)
                .padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp, start = 16.dp, end = 16.dp)
        ) {

            // 👤 Profile Header
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.95f),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 32.dp, horizontal = 16.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Profile Picture
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
                                                .border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                )
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
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.surface,
                                                CircleShape
                                            ),
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

                                Text(
                                    text = currentUser?.name ?: "Guest User",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = currentUser?.email ?: "Not logged in",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = currentUser?.bio
                                        ?: "Capturing moments, creating memories ✨",
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Quick Stats + Quick Actions
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
                        )
                )
            }

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
                        text = "Profile Management",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                    )

                    // Only include Edit Profile here — Settings moved to App Settings section
                    val profileOptions = listOf(
                        ProfileOption(
                            title = "Edit Profile",
                            description = "Update your profile information",
                            icon = Icons.Filled.Edit
                        )
                    )

                    profileOptions.forEach { option ->
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
                                    }
                                }
                            )
                        }
                    }
                }
            }


            // ⚙️ App Settings Section
            item {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "App Settings",
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


            item {
                Text(
                    text = "App Info",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp, end = 4.dp)
                )
            }

            items(appInfoOptions) { option ->
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
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

            item {
                Text(
                    text = "Support",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp, end = 4.dp)
                )
            }

            items(supportOptions) { option ->
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    ProfileOptionCard(
                        option = option,
                        onClick = {
                            if (option.title == "Help & Support") {
                                navController.navigate(Screen.HelpAndSupportScreen.route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
            }

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
                        onClick = { onNavigateToLogin() },
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

data class ProfileOption(val title: String, val description: String, val icon: ImageVector)
