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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    viewModel: PackDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(packId) {
        viewModel.loadPack(packId)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            PicPoseTopAppBar(
                title = uiState.pack?.name ?: stringResource(R.string.pack_details_title),
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
                            name = uiState.pack?.name.orEmpty(),
                            description = uiState.pack?.description.orEmpty(),
                            itemCount = uiState.pack?.itemCount ?: 0,
                            pricePoints = uiState.pack?.pricePoints ?: 0,
                            ownsPack = uiState.pack?.ownsPack == true,
                            isUnlocking = uiState.isUnlocking,
                            onUnlock = {
                                if (isLoggedIn) viewModel.unlockPack(packId) else onRequireLogin()
                            },
                            onOpenPack = {
                                uiState.items.firstOrNull()?.id?.let(onPromptClick)
                            },
                        )
                    }

                    items(uiState.items, key = { it.id }) { prompt ->
                        val isOwned = uiState.pack?.ownsPack == true
                        PackPromptRow(
                            prompt = prompt,
                            isOwned = isOwned,
                            onPromptClick = onPromptClick,
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
    itemCount: Int,
    pricePoints: Int,
    ownsPack: Boolean,
    isUnlocking: Boolean,
    onUnlock: () -> Unit,
    onOpenPack: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("$itemCount ${stringResource(R.string.pack_prompts)} • $pricePoints ${stringResource(R.string.rewards_credits)}")
                    if (ownsPack) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.pack_owned)) })
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
    isOwned: Boolean,
    onPromptClick: (String) -> Unit,
) {
    val isLocked = !isOwned && prompt.isLocked
    Card(
        onClick = {
            if (!isLocked) {
                onPromptClick(prompt.id)
            }
        },
        enabled = !isLocked,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 108.dp, height = 86.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                )
                            )
                        )
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(prompt.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = prompt.teaserText ?: prompt.shortPrompt.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { if (!isLocked) onPromptClick(prompt.id) },
                    enabled = !isLocked,
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        when {
                            isOwned -> stringResource(R.string.prompt_unlocked)
                            isLocked -> stringResource(R.string.prompt_locked)
                            else -> stringResource(R.string.prompt_open)
                        }
                    )
                }
            }
        }
    }
}
