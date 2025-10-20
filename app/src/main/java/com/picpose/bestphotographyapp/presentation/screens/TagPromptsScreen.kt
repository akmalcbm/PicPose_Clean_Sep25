package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.picpose.bestphotographyapp.presentation.components.AIPromptCard
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPromptsScreen(
    tag: String,
    onBack: () -> Unit,
    onPromptClick: (String) -> Unit,
    viewModel: AIPromptViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Ensure prompts are loaded
    LaunchedEffect(Unit) {
        if (uiState.allPrompts.isEmpty()) {
            viewModel.loadAllPrompts()
        }
    }

    // Normalize tag (remove leading # for matching)
    val normalizedTag = remember(tag) { tag.removePrefix("#") }

    // Build filtered list
    val taggedPrompts = remember(uiState.allPrompts, normalizedTag) {
        uiState.allPrompts.filter { prompt ->
            prompt.tags?.any { t ->
                t.equals(normalizedTag, ignoreCase = true) ||
                        t.equals(tag, ignoreCase = true) // in case tags already include '#'
            } == true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "#$normalizedTag • ${taggedPrompts.size} prompts", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.allPrompts.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            taggedPrompts.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No prompts found for #$normalizedTag")
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(taggedPrompts) { prompt ->
                        AIPromptCard(
                            prompt = prompt,
                            onClick = { onPromptClick(prompt.id) },
                            onCopy = {
                                // optional snackbar after copy if your card exposes a copy callback
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Prompt copied to clipboard")
                                }
                            },
                            //onFavoriteToggle = { /* optionally wire favorite toggle here */ }
                        )
                    }
                }
            }
        }
    }
}