package com.picpose.bestphotographyapp.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptDetailScreen(
    promptId: String,
    viewModel: AIPromptViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Find the prompt by ID
    val prompt = remember(promptId, uiState.allPrompts) {
        uiState.allPrompts.find { it.id == promptId }
    }

    // Load prompt details if not found in current list
    LaunchedEffect(promptId) {
        viewModel.loadPromptById(promptId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when {
            prompt == null && !uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Prompt not found",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
            }

            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                prompt?.let { promptData ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top App Bar
                        TopAppBar(
                            title = { Text("AI Prompt Details") },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                // Share button
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(promptData.fullPrompt))
                                        Toast.makeText(
                                            context,
                                            "Prompt copied for sharing!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                }

                                // Favorite button
                                IconButton(
                                    onClick = { viewModel.toggleFavorite(promptData) }
                                ) {
                                    Icon(
                                        if (promptData.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (promptData.isFavorite)
                                            MaterialTheme.colorScheme.error
                                        else
                                            LocalContentColor.current
                                    )
                                }
                            }
                        )

                        // Scrollable content
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            // Hero Image
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                ) {
                                    SubcomposeAsyncImage(
                                        model = promptData.imageUrl,
                                        contentDescription = promptData.title,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(
                                                RoundedCornerShape(
                                                    bottomStart = 16.dp,
                                                    bottomEnd = 16.dp
                                                )
                                            ),
                                        contentScale = ContentScale.Crop
                                    ) {
                                        when (painter.state) {
                                            is AsyncImagePainter.State.Loading -> {
                                                Box(
                                                    Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator()
                                                }
                                            }

                                            is AsyncImagePainter.State.Error -> {
                                                Icon(
                                                    Icons.Default.BrokenImage,
                                                    contentDescription = "Image load error",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(64.dp)
                                                )
                                            }

                                            else -> SubcomposeAsyncImageContent()
                                        }
                                    }

                                    // Gradient overlay (remembered)
                                    val gradient = remember {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.4f)
                                            ),
                                            startY = 100f
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(gradient)
                                    )
                                }
                            }

                            // Title + Stats Card
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Text(
                                            text = promptData.title,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            StatChip(
                                                icon = Icons.Default.FavoriteBorder,
                                                text = "${promptData.likes} likes",
                                                color = MaterialTheme.colorScheme.error
                                            )

                                            if (promptData.tags.isNotEmpty()) {
                                                StatChip(
                                                    icon = Icons.AutoMirrored.Filled.Label,
                                                    text = "${promptData.tags.size} tags",
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = promptData.shortPrompt,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 24.sp
                                        )
                                    }
                                }
                            }

                            // Full Prompt Section
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Full AI Prompt",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Surface(
                                            color = MaterialTheme.colorScheme.background,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Text(
                                                text = promptData.fullPrompt,
                                                modifier = Modifier.padding(16.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                lineHeight = 22.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Button(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(promptData.fullPrompt))
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("✨ Prompt copied to clipboard!")
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(vertical = 16.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Copy Full Prompt",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Tags Section
                            if (promptData.tags.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(20.dp)) {
                                            Text(
                                                text = "🏷️ Tags",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                promptData.tags.forEach { tag ->
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        shape = RoundedCornerShape(20.dp),
                                                        border = BorderStroke(
                                                            1.dp,
                                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                        )
                                                    ) {
                                                        Text(
                                                            text = "#$tag",
                                                            modifier = Modifier.padding(
                                                                horizontal = 12.dp,
                                                                vertical = 6.dp
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Simple FlowRow fallback (replace with Accompanist FlowRow if available)
@Composable
private fun FlowRow(
    horizontalArrangement: Arrangement.Horizontal,
    verticalArrangement: Arrangement.Vertical,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = verticalArrangement) {
        content()
    }
}