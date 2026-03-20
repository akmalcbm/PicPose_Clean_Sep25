/**
 * ---
 * File: ProfileScreen.kt
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

package com.picpose.bestphotographyapp.presentation.profile

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.picpose.bestphotographyapp.BuildConfig
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.common.PicPoseAppBar
import com.picpose.bestphotographyapp.presentation.home.components.QuickActionsCard
import com.picpose.bestphotographyapp.presentation.home.components.QuickStatsCard
import com.picpose.bestphotographyapp.navigation.Screen
import com.picpose.bestphotographyapp.components.common.ShimmerBox
import com.picpose.bestphotographyapp.presentation.settings.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.auth.AuthViewModel
import com.picpose.bestphotographyapp.presentation.auth.OperationState
import com.picpose.bestphotographyapp.presentation.home.StatsViewModel
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
    val emailVerificationState by authViewModel.emailVerificationRequestState.collectAsState()
    val context = LocalContext.current

    var showLogoutDialog by remember { mutableStateOf(false) }

    // ⭐ RANDOM fallback bios (UI only)
    val fallbackBios = remember(context) {
        context.resources.getStringArray(R.array.profile_fallback_bios).toList()
    }

    val chosenFallbackBio = remember { fallbackBios.random() }
    val appInfoOptions = listOf(
        ProfileOption(
            stringResource(R.string.privacy_policy_title),
            stringResource(R.string.read_privacy_policy),
            Icons.Filled.PrivacyTip
        ) to Screen.Privacy.route,
        ProfileOption(
            stringResource(R.string.terms_conditions_title),
            stringResource(R.string.terms_and_conditions),
            Icons.Filled.Description
        ) to Screen.Terms.route,
        ProfileOption(
            stringResource(R.string.about),
            stringResource(R.string.about_the_app),
            Icons.Filled.Info
        ) to Screen.About.route
    )

    Scaffold(
        topBar = {
            PicPoseAppBar(
                title = stringResource(R.string.profile),
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
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
                SectionHeader(stringResource(R.string.profile_management))
            }

            item {
                ProfileOptionCard(
                    option = ProfileOption(
                        title = stringResource(R.string.edit_profile),
                        description = stringResource(R.string.update_profile_information),
                        icon = Icons.Filled.Edit
                    ),
                    onClick = { onNavigateToEditProfile() }
                )
            }

            if (isLoggedIn) {
                item {
                    SectionHeader(stringResource(R.string.email_verification))
                }

                item {
                    val verified = currentUser?.isEmailVerified == true
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (verified) stringResource(R.string.email_verified_label) else stringResource(R.string.email_not_verified_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!verified) {
                                OutlinedButton(
                                    onClick = { authViewModel.requestEmailVerification() }
                                ) {
                                    Text(stringResource(R.string.resend_verification_link))
                                }
                            }
                            when (val state = emailVerificationState) {
                                is OperationState.Success -> {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                is OperationState.Error -> {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }

            // ------------------------
            // APP SETTINGS
            // ------------------------
            item {
                SectionHeader(stringResource(R.string.app_settings))
            }

            item {
                ProfileOptionCard(
                    option = ProfileOption(
                        title = stringResource(R.string.settings),
                        description = stringResource(R.string.customize_app_preferences),
                        icon = Icons.Filled.Settings
                    ),
                    onClick = onNavigateToSettings
                )
            }

            // ------------------------
            // APP INFO
            // ------------------------
            item { SectionHeader(stringResource(R.string.app_info)) }

            items(appInfoOptions) { entry ->
                val option = entry.first
                ProfileOptionCard(
                    option = option,
                    onClick = { navController.navigate(entry.second) }
                )
            }

            // ------------------------
            // SUPPORT
            // ------------------------
            item { SectionHeader(stringResource(R.string.support)) }

            item {
                ProfileOptionCard(
                    option = ProfileOption(
                        stringResource(R.string.help_support_title),
                        stringResource(R.string.get_help_and_contact_support),
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
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.logout))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.logout))
                    }
                } else {
                    Button(
                        onClick = onNavigateToLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = stringResource(R.string.login))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.login))
                    }
                }
            }

            item {
                Text(
                    text = "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
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
    currentUser: com.picpose.bestphotographyapp.data.remote.dto.User?,
    fallbackBio: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ----------------------------
            // PROFILE IMAGE WITH RING
            // ----------------------------
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {

                NeonGradientRing(
                    size = 180.dp,
                    borderWidth = 4.dp
                ) {
                    if (!currentUser?.displayProfilePicture.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = currentUser.displayProfilePicture,
                            contentDescription = stringResource(R.string.profile_picture),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(168.dp)
                                .clip(CircleShape)
                        ) {
                            if (painter.state is coil.compose.AsyncImagePainter.State.Loading) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .size(168.dp)
                                        .clip(CircleShape),
                                    shape = CircleShape
                                )
                            } else {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    } else {
                        DefaultProfileImage(
                            modifier = Modifier.size(168.dp)
                        )
                    }
                }

            }

            Spacer(Modifier.height(14.dp))

            // ----------------------------
            // NAME
            // ----------------------------
            Text(
                text = currentUser?.displayName ?: stringResource(R.string.guest_user),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // ----------------------------
            // EMAIL
            // ----------------------------
            Text(
                text = currentUser?.email ?: stringResource(R.string.not_logged_in),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.35f),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // ----------------------------
            // BIO
            // ----------------------------
            Text(
                text = currentUser?.bio?.takeIf { it.isNotBlank() } ?: fallbackBio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(0.88f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun ProfileOptionCard(option: ProfileOption, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                option.icon,
                null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    option.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logout)) },
        text = { Text(stringResource(R.string.logout_confirmation_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.logout)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

data class ProfileOption(val title: String, val description: String, val icon: ImageVector)
