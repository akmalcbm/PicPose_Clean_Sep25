/**
 * ---
 * File: PromptsV2Screen.kt
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

package com.picpose.bestphotographyapp.ui.prompts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.picpose.bestphotographyapp.data.models.v2.V2PromptDto

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptsV2Screen(
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit,
    initialCategory: String? = null,
    viewModel: PromptsV2ViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialCategory) {
        viewModel.loadPrompts(category = initialCategory, forceRefresh = true)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prompts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                label = { Text("Search prompts") },
                singleLine = true,
                trailingIcon = {
                    TextButton(
                        onClick = {
                            viewModel.loadPrompts(forceRefresh = true)
                        }
                    ) {
                        Text("Go")
                    }
                },
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PromptChipFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = { Text(filter.name) },
                    )
                }
            }

            if (uiState.isLoading && uiState.prompts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(uiState.prompts, key = { it.id }) { prompt ->
                        PromptV2Card(
                            prompt = prompt,
                            onOpen = { onPromptClick(prompt.id) },
                            onWatchAd = { onPromptClick(prompt.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptV2Card(
    prompt: V2PromptDto,
    onOpen: () -> Unit,
    onWatchAd: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = prompt.imageUrl ?: prompt.imageUrl2,
                    contentDescription = prompt.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop,
                )
                if (prompt.isLocked) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Premium",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = prompt.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (prompt.isFeatured) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Featured") },
                            leadingIcon = {
                                Icon(Icons.Default.Star, contentDescription = null)
                            },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (prompt.isLocked) {
                        prompt.teaserText.orEmpty()
                    } else {
                        prompt.shortPrompt.orEmpty()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    prompt.category?.takeIf { it.isNotBlank() }?.let { category ->
                        AssistChip(onClick = {}, label = { Text(category) })
                    }
                    if (prompt.premiumUnlockCostPoints > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${prompt.premiumUnlockCostPoints} credits") },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (prompt.isLocked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                            Text("Unlock")
                        }
                        Button(onClick = onWatchAd, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Watch Ad")
                        }
                    }
                } else {
                    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Prompt")
                    }
                }
            }
        }
    }
}
