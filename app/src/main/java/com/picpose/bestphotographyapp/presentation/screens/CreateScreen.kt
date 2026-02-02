package com.picpose.bestphotographyapp.presentation.screens

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen() {
    val context = LocalContext.current
    val activity = context as? Activity

    // ✅ Enable edge-to-edge layout for Android 11+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        }
    }

    val creationCategories = listOf(
        CreationCategory(
            title = "Photography",
            items = listOf(
                CreateOption("Take Photo", "Capture a new photo", Icons.Filled.CameraAlt) {
                    Toast.makeText(context, "Camera feature coming soon!", Toast.LENGTH_SHORT).show()
                },
                CreateOption("Upload Photo", "Choose from gallery", Icons.Filled.PhotoLibrary) {
                    Toast.makeText(context, "Gallery picker coming soon!", Toast.LENGTH_SHORT).show()
                },
                CreateOption("Photo Editor", "Edit your photos", Icons.Filled.Edit) {
                    Toast.makeText(context, "Photo editor coming soon!", Toast.LENGTH_SHORT).show()
                },
                CreateOption("AI Enhancement", "Enhance with AI", Icons.Filled.AutoAwesome) {
                    Toast.makeText(context, "AI enhancement coming soon!", Toast.LENGTH_SHORT).show()
                }
            )
        ),
        CreationCategory(
            title = "Content Creation",
            items = listOf(
                CreateOption("Photo Collage", "Create a collage", Icons.Filled.Collections) {
                    Toast.makeText(context, "Collage maker coming soon!", Toast.LENGTH_SHORT).show()
                },
                CreateOption("Templates", "Use templates", Icons.AutoMirrored.Filled.ViewQuilt) {
                    Toast.makeText(context, "Templates coming soon!", Toast.LENGTH_SHORT).show()
                },
                CreateOption("Add Text", "Add text to photos", Icons.Filled.TextFields) {
                    Toast.makeText(context, "Text editor coming soon!", Toast.LENGTH_SHORT).show()
                },
                CreateOption("Add Filters", "Apply artistic filters", Icons.Filled.FilterVintage) {
                    Toast.makeText(context, "Filters coming soon!", Toast.LENGTH_SHORT).show()
                }
            )
        ),
        CreationCategory(
            title = "Guides & Tips",
            items = listOf(
                CreateOption("Create Guide", "Write a guide", Icons.AutoMirrored.Filled.Article) {
                    Toast.makeText(context, "Guide creation coming soon!", Toast.LENGTH_SHORT).show()
                },
                CreateOption("Share Tip", "Share a tip", Icons.Filled.Lightbulb) {
                    Toast.makeText(context, "Tip sharing coming soon!", Toast.LENGTH_SHORT).show()
                },
                CreateOption("AI Prompt", "Create AI prompts", Icons.Filled.Psychology) {
                    Toast.makeText(context, "AI prompt creation coming soon!", Toast.LENGTH_SHORT).show()
                }
            )
        )
    )

    // ✅ Scaffold to handle system bars and consistent layout
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                ),
                title = {
                    Text(
                        text = "Create",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding() + 24.dp
            )
        ) {
            // 🧠 Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Create Something New",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Choose what you want to make today",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 🧩 Categories
            items(creationCategories) { category ->
                CategorySection(category = category)
            }

            // ⭐ Coming Soon Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "More features coming soon!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "We’re working on bringing you new creation tools and experiences.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySection(category: CreationCategory) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            val columns = 2
            val cardHeight = 120.dp
            val verticalSpacing = 12.dp
            val rows = (category.items.size + columns - 1) / columns
            val totalHeight = (cardHeight * rows) + (verticalSpacing * (rows - 1))

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight + 8.dp)
                    .padding(end = 2.dp, bottom = 2.dp)
            ) {
                items(category.items) { option ->
                    CreateOptionCard(option = option)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateOptionCard(option: CreateOption) {
    Card(
        onClick = option.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = option.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = option.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

data class CreationCategory(
    val title: String,
    val items: List<CreateOption>
)

data class CreateOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit = {}
)