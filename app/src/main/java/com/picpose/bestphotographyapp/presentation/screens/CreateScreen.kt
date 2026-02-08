package com.picpose.bestphotographyapp.presentation.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picpose.bestphotographyapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen() {
    val context = LocalContext.current
    val creationCategories = listOf(
        CreationCategory(
            title = context.getString(R.string.create_category_photography),
            items = listOf(
                CreateOption(
                    context.getString(R.string.create_option_take_photo),
                    context.getString(R.string.create_option_take_photo_desc),
                    Icons.Filled.CameraAlt
                ) {
                    Toast.makeText(context, context.getString(R.string.camera_feature_coming_soon), Toast.LENGTH_SHORT).show()
                },
                CreateOption(
                    context.getString(R.string.create_option_upload_photo),
                    context.getString(R.string.create_option_upload_photo_desc),
                    Icons.Filled.PhotoLibrary
                ) {
                    Toast.makeText(context, context.getString(R.string.gallery_picker_coming_soon), Toast.LENGTH_SHORT).show()
                },
                CreateOption(
                    context.getString(R.string.create_option_photo_editor),
                    context.getString(R.string.create_option_photo_editor_desc),
                    Icons.Filled.Edit
                ) {
                    Toast.makeText(context, context.getString(R.string.photo_editor_coming_soon), Toast.LENGTH_SHORT).show()
                },
                CreateOption(
                    context.getString(R.string.create_option_ai_enhancement),
                    context.getString(R.string.create_option_ai_enhancement_desc),
                    Icons.Filled.AutoAwesome
                ) {
                    Toast.makeText(context, context.getString(R.string.ai_enhancement_coming_soon), Toast.LENGTH_SHORT).show()
                }
            )
        ),
        CreationCategory(
            title = context.getString(R.string.create_category_content_creation),
            items = listOf(
                CreateOption(
                    context.getString(R.string.create_option_photo_collage),
                    context.getString(R.string.create_option_photo_collage_desc),
                    Icons.Filled.Collections
                ) {
                    Toast.makeText(context, context.getString(R.string.collage_maker_coming_soon), Toast.LENGTH_SHORT).show()
                },
                CreateOption(
                    context.getString(R.string.create_option_templates),
                    context.getString(R.string.create_option_templates_desc),
                    Icons.AutoMirrored.Filled.ViewQuilt
                ) {
                    Toast.makeText(context, context.getString(R.string.templates_coming_soon), Toast.LENGTH_SHORT).show()
                },
                CreateOption(
                    context.getString(R.string.create_option_add_text),
                    context.getString(R.string.create_option_add_text_desc),
                    Icons.Filled.TextFields
                ) {
                    Toast.makeText(context, context.getString(R.string.text_editor_coming_soon), Toast.LENGTH_SHORT).show()
                },
                CreateOption(
                    context.getString(R.string.create_option_add_filters),
                    context.getString(R.string.create_option_add_filters_desc),
                    Icons.Filled.FilterVintage
                ) {
                    Toast.makeText(context, context.getString(R.string.filters_coming_soon), Toast.LENGTH_SHORT).show()
                }
            )
        ),
        CreationCategory(
            title = context.getString(R.string.create_category_guides_tips),
            items = listOf(
                CreateOption(
                    context.getString(R.string.create_option_create_guide),
                    context.getString(R.string.create_option_create_guide_desc),
                    Icons.AutoMirrored.Filled.Article
                ) {
                    Toast.makeText(context, context.getString(R.string.guide_creation_coming_soon), Toast.LENGTH_SHORT).show()
                },
                CreateOption(
                    context.getString(R.string.create_option_share_tip),
                    context.getString(R.string.create_option_share_tip_desc),
                    Icons.Filled.Lightbulb
                ) {
                    Toast.makeText(context, context.getString(R.string.tip_sharing_coming_soon), Toast.LENGTH_SHORT).show()
                },
                CreateOption(
                    context.getString(R.string.create_option_ai_prompt),
                    context.getString(R.string.create_option_ai_prompt_desc),
                    Icons.Filled.Psychology
                ) {
                    Toast.makeText(context, context.getString(R.string.ai_prompt_creation_coming_soon), Toast.LENGTH_SHORT).show()
                }
            )
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.create_screen_title),
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
                bottom = 24.dp
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
                            text = stringResource(R.string.create_header_title),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.create_header_subtitle),
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
                            text = stringResource(R.string.more_features_coming_soon),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.more_features_coming_soon_desc),
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
