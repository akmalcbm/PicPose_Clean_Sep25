/**
 * ---
 * File: PackDetailsScreen.kt
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

package com.picpose.bestphotographyapp.presentation.packs

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackDetailsScreen(
    packId: Int,
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit,
    onRequireLogin: () -> Unit,
    onOpenRewards: () -> Unit,
    viewModel: PackDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var lockedPromptTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val pack = uiState.pack
    val ownsPack = pack?.ownsPack == true

    LaunchedEffect(packId) {
        viewModel.loadPack(packId)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(ownsPack) {
        if (ownsPack) {
            lockedPromptTitle = null
            viewModel.clearUnlockDialogFeedback()
        }
    }

    if (lockedPromptTitle != null && pack != null) {
        ModalBottomSheet(
            onDismissRequest = {
                lockedPromptTitle = null
                viewModel.clearUnlockDialogFeedback()
            },
        ) {
            LockedPromptAccessSheet(
                promptTitle = lockedPromptTitle.orEmpty(),
                packName = pack.name,
                packPricePoints = pack.pricePoints,
                pointsBalance = uiState.pointsBalance,
                isUnlocking = uiState.isUnlocking,
                isLoggedIn = isLoggedIn,
                inlineError = uiState.unlockDialogError,
                isInsufficientCredits = uiState.unlockDialogInsufficientCredits,
                onUnlock = {
                    if (isLoggedIn) {
                        viewModel.unlockPack(
                            packId = packId,
                            source = PackUnlockSource.LockedPromptSheet,
                        )
                    } else {
                        lockedPromptTitle = null
                        viewModel.clearUnlockDialogFeedback()
                        onRequireLogin()
                    }
                },
                onEarnCredits = {
                    lockedPromptTitle = null
                    viewModel.clearUnlockDialogFeedback()
                    onOpenRewards()
                },
                onDismiss = {
                    lockedPromptTitle = null
                    viewModel.clearUnlockDialogFeedback()
                },
            )
        }
    }

    Scaffold(
        topBar = {
            PicPoseTopAppBar(
                title = pack?.name ?: stringResource(R.string.pack_details_title),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { innerPadding ->
        Crossfade(targetState = uiState.isLoading && uiState.pack == null, label = "pack_details_loading") { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        PackHeaderCard(
                            name = pack?.name.orEmpty(),
                            description = pack?.description.orEmpty(),
                            thumbnailUrl = pack?.thumbnailUrl,
                            itemCount = pack?.itemCount ?: 0,
                            pricePoints = pack?.pricePoints ?: 0,
                            ownsPack = ownsPack,
                            isUnlocking = uiState.isUnlocking,
                            onUnlock = {
                                if (isLoggedIn) {
                                    viewModel.unlockPack(
                                        packId = packId,
                                        source = PackUnlockSource.HeaderCard,
                                    )
                                } else {
                                    onRequireLogin()
                                }
                            },
                            onOpenPack = {
                                uiState.items.firstOrNull()?.id?.let(onPromptClick)
                            },
                        )
                    }

                    items(uiState.items, key = { it.id }) { prompt ->
                        PackPromptRow(
                            prompt = prompt,
                            isPackOwned = ownsPack,
                            onPromptClick = onPromptClick,
                            onLockedClick = {
                                viewModel.clearUnlockDialogFeedback()
                                lockedPromptTitle = prompt.title
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackHeaderCard(
    name: String,
    description: String,
    thumbnailUrl: String?,
    itemCount: Int,
    pricePoints: Int,
    ownsPack: Boolean,
    isUnlocking: Boolean,
    onUnlock: () -> Unit,
    onOpenPack: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                    )
                                )
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text("$itemCount ${stringResource(R.string.pack_prompts)}") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                )
                            },
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("$pricePoints ${stringResource(R.string.rewards_credits)}") },
                        )
                        if (ownsPack) {
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.pack_owned)) },
                            )
                        }
                    }
                }
            }
            if (description.isNotBlank()) {
                Text(
                    description,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = if (ownsPack) onOpenPack else onUnlock,
                enabled = !isUnlocking,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    when {
                        ownsPack -> stringResource(R.string.pack_open_pack)
                        isUnlocking -> stringResource(R.string.pack_unlocking)
                        else -> stringResource(R.string.pack_unlock_for_credits, pricePoints)
                    }
                )
            }
        }
    }
}

@Composable
private fun PackPromptRow(
    prompt: V2PromptDto,
    isPackOwned: Boolean,
    onPromptClick: (String) -> Unit,
    onLockedClick: () -> Unit,
) {
    val isLocked = !isPackOwned
    val teaser = prompt.teaserText ?: prompt.shortPrompt.orEmpty()
    val subtitle = if (teaser.isNotBlank()) {
        teaser
    } else if (isLocked) {
        stringResource(R.string.pack_row_locked_hint)
    } else {
        stringResource(R.string.pack_row_unlocked_hint)
    }
    Card(
        onClick = {
            if (isLocked) onLockedClick() else onPromptClick(prompt.id)
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 90.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                if (!prompt.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = prompt.imageUrl,
                        contentDescription = prompt.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                )
                            )
                        )
                )
                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)),
                    ) {
                        PremiumLockBadge(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FilledTonalButton(
                    onClick = {
                        if (isLocked) onLockedClick() else onPromptClick(prompt.id)
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        if (isLocked) {
                            stringResource(R.string.pack_unlock_pack_action)
                        } else {
                            stringResource(R.string.prompt_open)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumLockBadge(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.96f),
        ),
        shape = RoundedCornerShape(999.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = stringResource(R.string.premium),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LockedPromptAccessSheet(
    promptTitle: String,
    packName: String,
    packPricePoints: Int,
    pointsBalance: Int?,
    isUnlocking: Boolean,
    isLoggedIn: Boolean,
    inlineError: String?,
    isInsufficientCredits: Boolean,
    onUnlock: () -> Unit,
    onEarnCredits: () -> Unit,
    onDismiss: () -> Unit,
) {
    val requiredCredits = packPricePoints.coerceAtLeast(0)
    val hasEnoughCredits = pointsBalance?.let { it >= requiredCredits } ?: true
    val hasKnownBalance = pointsBalance != null
    val creditsDeficit = pointsBalance?.let { (requiredCredits - it).coerceAtLeast(0) } ?: 0
    val shouldShowInsufficientState = isLoggedIn && hasKnownBalance && !hasEnoughCredits
    val shouldDisableUnlock = isLoggedIn && (shouldShowInsufficientState || isInsufficientCredits)
    val errorText = inlineError?.takeIf { it.isNotBlank() } ?: if (isInsufficientCredits) {
        stringResource(R.string.pack_unlock_insufficient_inline)
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .animateContentSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.pack_unlock_prompt_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(
                R.string.pack_unlock_prompt_message,
                promptTitle,
                packName,
                requiredCredits,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.pack_unlock_required_credits, requiredCredits),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        pointsBalance?.let { balance ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.pack_unlock_current_balance, balance),
                style = MaterialTheme.typography.labelLarge,
                color = if (balance >= requiredCredits) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Medium,
            )
            if (balance < requiredCredits) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.pack_unlock_deficit, creditsDeficit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (!errorText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onUnlock,
            enabled = !isUnlocking && (!isLoggedIn || !shouldDisableUnlock),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (isUnlocking) {
                    stringResource(R.string.pack_unlocking)
                } else if (isLoggedIn && shouldDisableUnlock) {
                    stringResource(R.string.pack_unlock_not_enough_action)
                } else if (isLoggedIn) {
                    stringResource(R.string.pack_unlock_for_credits, requiredCredits)
                } else {
                    stringResource(R.string.prompt_unlock_login_required)
                }
            )
        }
        if (isLoggedIn && shouldDisableUnlock) {
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onEarnCredits,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.pack_earn_credits_action))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.not_now))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
