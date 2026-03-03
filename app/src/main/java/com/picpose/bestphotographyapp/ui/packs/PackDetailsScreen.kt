package com.picpose.bestphotographyapp.ui.packs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.data.models.v2.V2PromptDto

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
            TopAppBar(
                title = { Text(uiState.pack?.name ?: "Pack details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.isLoading && uiState.pack == null) {
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
                    Card {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = uiState.pack?.name.orEmpty(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiState.pack?.description.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("${uiState.pack?.itemCount ?: 0} prompts • ${uiState.pack?.pricePoints ?: 0} credits")
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (isLoggedIn) {
                                        viewModel.unlockPack(packId)
                                    } else {
                                        onRequireLogin()
                                    }
                                },
                                enabled = !uiState.isUnlocking && uiState.pack?.ownsPack != true,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    when {
                                        uiState.pack?.ownsPack == true -> "Owned"
                                        uiState.isUnlocking -> "Unlocking..."
                                        else -> "Unlock pack"
                                    }
                                )
                            }
                        }
                    }
                }

                items(uiState.items, key = { it.id }) { prompt ->
                    PackPromptRow(prompt = prompt, onPromptClick = onPromptClick)
                }
            }
        }
    }
}

@Composable
private fun PackPromptRow(
    prompt: V2PromptDto,
    onPromptClick: (String) -> Unit,
) {
    Card(onClick = { onPromptClick(prompt.id) }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(prompt.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = prompt.teaserText ?: prompt.shortPrompt.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(if (prompt.isLocked) "Locked" else "Unlocked")
        }
    }
}
