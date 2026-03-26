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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.picpose.bestphotographyapp.BuildConfig
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.common.appInnerSurfaceColor
import com.picpose.bestphotographyapp.components.common.appSectionCardBorder
import com.picpose.bestphotographyapp.components.common.appSectionCardColors
import com.picpose.bestphotographyapp.components.common.appSectionCardElevation
import com.picpose.bestphotographyapp.components.common.appSectionCardShape
import com.picpose.bestphotographyapp.components.common.PicPoseAppBar
import com.picpose.bestphotographyapp.presentation.home.components.QuickActionsCard
import com.picpose.bestphotographyapp.presentation.home.components.QuickStatsCard
import com.picpose.bestphotographyapp.navigation.Screen
import com.picpose.bestphotographyapp.components.common.ShimmerBox
import com.picpose.bestphotographyapp.presentation.settings.AppSettingsViewModel
import com.picpose.bestphotographyapp.presentation.auth.AuthViewModel
import com.picpose.bestphotographyapp.presentation.auth.OperationState
import com.picpose.bestphotographyapp.presentation.home.StatsViewModel

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

    LaunchedEffect(isLoggedIn) {
        authViewModel.resetEmailVerificationRequestState()
        if (isLoggedIn) {
            authViewModel.refreshCurrentUserSilently()
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp, start = 16.dp, end = 16.dp)
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
                        fallbackBio = chosenFallbackBio,
                        isLoggedIn = isLoggedIn,
                        emailVerificationState = emailVerificationState,
                        onRequestVerification = authViewModel::requestEmailVerification
                    )
                }
            }

            // Quick Stats
            item {
                val statsViewModel: StatsViewModel = hiltViewModel()
                QuickStatsCard(
                    viewModel = statsViewModel,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                )
            }

            // Quick Actions
            item {
                QuickActionsCard(
                    onNavigateToAllPrompts = onNavigateToAllPrompts,
                    onNavigateToFavorites = onNavigateToFavorites,
                    modifier = Modifier.padding(horizontal = 4.dp)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = RoundedCornerShape(16.dp),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = RoundedCornerShape(16.dp)
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
    fallbackBio: String,
    isLoggedIn: Boolean,
    emailVerificationState: OperationState,
    onRequestVerification: () -> Unit
) {
    val hasBio = !currentUser?.bio.isNullOrBlank()
    val profileBio = currentUser?.bio?.takeIf { it.isNotBlank() } ?: fallbackBio
    val isEmailVerified = isLoggedIn && currentUser?.isEmailVerified == true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = appSectionCardShape,
        colors = appSectionCardColors(),
        border = appSectionCardBorder(),
        elevation = appSectionCardElevation(defaultElevation = 2.dp, pressedElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(vertical = 24.dp, horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(116.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentUser?.displayProfilePicture.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = currentUser.displayProfilePicture,
                            contentDescription = stringResource(R.string.profile_picture),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(108.dp)
                                .clip(CircleShape)
                        ) {
                            if (painter.state is coil.compose.AsyncImagePainter.State.Loading) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .size(108.dp)
                                        .clip(CircleShape),
                                    shape = CircleShape
                                )
                            } else {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    } else {
                        DefaultProfileImage(
                            modifier = Modifier.size(108.dp)
                        )
                    }
                }
            }

            Text(
                text = currentUser?.displayName ?: stringResource(R.string.guest_user),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth(0.9f)) {
                val badgeReservedWidth = if (isEmailVerified) 24.dp else 0.dp
                val emailMaxWidth = (maxWidth - badgeReservedWidth).coerceAtLeast(0.dp)

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = currentUser?.email ?: stringResource(R.string.not_logged_in),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = emailMaxWidth),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isEmailVerified) {
                        VerifiedEmailInlineBadge()
                    }
                }
            }

            if (isLoggedIn) {
                VerificationStatusSection(
                    isVerified = isEmailVerified,
                    emailVerificationState = emailVerificationState,
                    onRequestVerification = onRequestVerification
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = appInnerSurfaceColor(alpha = 0.5f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                )
            ) {
                Text(
                    text = profileBio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (hasBio) 0.9f else 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VerificationStatusSection(
    isVerified: Boolean,
    emailVerificationState: OperationState,
    onRequestVerification: () -> Unit
) {
    if (isVerified) {
        return
    }

    UnverifiedEmailCard(
        emailVerificationState = emailVerificationState,
        onRequestVerification = onRequestVerification
    )
}

@Composable
private fun VerifiedEmailInlineBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(18.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = stringResource(R.string.profile_email_verified_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun UnverifiedEmailCard(
    emailVerificationState: OperationState,
    onRequestVerification: () -> Unit
) {
    val isLoading = emailVerificationState is OperationState.Loading
    val stateMessageColor = when (emailVerificationState) {
        is OperationState.Success -> MaterialTheme.colorScheme.primary
        is OperationState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MarkEmailUnread,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.profile_email_unverified_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = stringResource(R.string.profile_email_unverified_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = onRequestVerification,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (emailVerificationState is OperationState.Success) {
                            stringResource(R.string.resend_verification_link)
                        } else {
                            stringResource(R.string.verify_email)
                        }
                    )
                }
            }

            when (emailVerificationState) {
                is OperationState.Success -> {
                    Text(
                        text = emailVerificationState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = stateMessageColor
                    )
                }
                is OperationState.Error -> {
                    Text(
                        text = emailVerificationState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = stateMessageColor
                    )
                }
                else -> Unit
            }
        }
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
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        )
    }
}

@Composable
fun ProfileOptionCard(option: ProfileOption, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = appSectionCardShape,
        colors = appSectionCardColors(),
        border = appSectionCardBorder(),
        elevation = appSectionCardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = appInnerSurfaceColor(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        option.icon,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    option.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
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
