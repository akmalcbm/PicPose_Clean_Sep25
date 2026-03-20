/**
 * ---
 * File: CreateScreen.kt
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

package com.picpose.bestphotographyapp.presentation.create

import android.graphics.Color
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar
import com.picpose.bestphotographyapp.components.common.PicPoseTopBarActionButton
import com.picpose.bestphotographyapp.data.service.rembg.BgBackgroundMode
import com.picpose.bestphotographyapp.data.service.rembg.BgBackgroundOption
import com.picpose.bestphotographyapp.data.service.rembg.BgRemovalQualityMode
import com.picpose.bestphotographyapp.presentation.create.CreateUiEvent
import com.picpose.bestphotographyapp.presentation.create.CreateUiState
import com.picpose.bestphotographyapp.presentation.create.CreateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    viewModel: CreateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onImageSelected(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

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
                    galleryLauncher.launch("image/*")
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            PicPoseTopAppBar(
                title = stringResource(R.string.create_screen_title),
                actions = {
                    PicPoseTopBarActionButton(
                        icon = Icons.Default.AutoAwesome,
                        contentDescription = stringResource(R.string.remove_bg),
                        onClick = viewModel::onClickRemoveBg,
                    )
                },
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp
            )
        ) {
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
            }

            item {
                RemoveBgCard(
                    uiState = uiState,
                    onPickImage = { galleryLauncher.launch("image/*") },
                    onRemoveBg = viewModel::onClickRemoveBg
                )
            }

            items(creationCategories) { category ->
                CategorySection(category = category)
            }

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

    if (uiState.showDisclosureDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onDisclosureCancelled,
            title = { Text(stringResource(R.string.remove_bg_disclosure_title)) },
            text = { Text(stringResource(R.string.remove_bg_disclosure_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDisclosureAccepted) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDisclosureCancelled) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.showPreviewSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::onCancelBgRemoval
        ) {
            RemoveBgPreviewSheet(
                uiState = uiState,
                onToggleBeforeAfter = viewModel::onPreviewToggle,
                onSetBackgroundOption = viewModel::onSetBackgroundOption,
                onConfirmMode = viewModel::onConfirmBgRemoval,
                onRetry = viewModel::onRetry,
                onApply = viewModel::onApplyRemovedBg,
                onSavePng = viewModel::onSavePreviewAsPng,
                onCancel = viewModel::onCancelBgRemoval
            )
        }
    }
}

@Composable
private fun RemoveBgCard(
    uiState: CreateUiState,
    onPickImage: () -> Unit,
    onRemoveBg: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.remove_bg),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.remove_bg_card_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CheckerboardPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                imageUri = uiState.selectedImageUri
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pick_image))
                }

                Button(
                    onClick = onRemoveBg,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.selectedImageUri != null
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.remove_bg))
                }
            }
        }
    }
}

@Composable
private fun RemoveBgPreviewSheet(
    uiState: CreateUiState,
    onToggleBeforeAfter: (Boolean) -> Unit,
    onSetBackgroundOption: (BgBackgroundOption) -> Unit,
    onConfirmMode: (BgRemovalQualityMode) -> Unit,
    onRetry: () -> Unit,
    onApply: () -> Unit,
    onSavePng: () -> Unit,
    onCancel: () -> Unit
) {
    val beforeUri = uiState.selectedImageUri
    val afterUri = uiState.removeBgPreviewUri
    val currentUri = if (uiState.previewShowBefore) beforeUri else (afterUri ?: beforeUri)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.remove_bg_preview_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        CheckerboardPreview(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            imageUri = currentUri
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.previewShowBefore,
                onClick = { onToggleBeforeAfter(true) },
                label = { Text(stringResource(R.string.before)) }
            )
            FilterChip(
                selected = !uiState.previewShowBefore,
                onClick = { onToggleBeforeAfter(false) },
                label = { Text(stringResource(R.string.after)) }
            )
        }

        Text(
            text = stringResource(R.string.bg_options),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.backgroundOption.mode == BgBackgroundMode.TRANSPARENT,
                onClick = {
                    onSetBackgroundOption(BgBackgroundOption(mode = BgBackgroundMode.TRANSPARENT))
                },
                label = { Text(stringResource(R.string.transparent_bg)) }
            )
            FilterChip(
                selected = uiState.backgroundOption.mode == BgBackgroundMode.BLUR_ORIGINAL,
                onClick = {
                    onSetBackgroundOption(BgBackgroundOption(mode = BgBackgroundMode.BLUR_ORIGINAL))
                },
                label = { Text(stringResource(R.string.blur_bg)) }
            )
        }

        ColorPresetRow(
            selectedColor = uiState.backgroundOption.solidColor,
            onColorPicked = { colorInt ->
                onSetBackgroundOption(
                    BgBackgroundOption(
                        mode = BgBackgroundMode.SOLID_COLOR,
                        solidColor = colorInt
                    )
                )
            }
        )

        Text(
            text = stringResource(R.string.quality_mode),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.qualityMode == BgRemovalQualityMode.HIGH_QUALITY_ONLINE,
                onClick = { onConfirmMode(BgRemovalQualityMode.HIGH_QUALITY_ONLINE) },
                label = { Text(stringResource(R.string.high_quality_online)) }
            )
            FilterChip(
                selected = uiState.qualityMode == BgRemovalQualityMode.OFFLINE_BASIC,
                onClick = { onConfirmMode(BgRemovalQualityMode.OFFLINE_BASIC) },
                label = { Text(stringResource(R.string.offline_basic)) }
            )
        }

        if (uiState.isRemovingBg) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.remove_bg_processing))
            }
        }

        if (!uiState.removeBgError.isNullOrBlank()) {
            Text(
                text = uiState.removeBgError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry, enabled = !uiState.isRemovingBg, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.retry))
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onSavePng,
                enabled = !uiState.isRemovingBg && uiState.removeBgPreviewUri != null,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.save_png))
            }
            Button(
                onClick = onApply,
                enabled = !uiState.isRemovingBg && uiState.removeBgPreviewUri != null,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.apply))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ColorPresetRow(
    selectedColor: Int,
    onColorPicked: (Int) -> Unit
) {
    val colors = listOf(
        Color.WHITE,
        Color.BLACK,
        Color.parseColor("#E3F2FD"),
        Color.parseColor("#FFF3E0"),
        Color.parseColor("#E8F5E9")
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { colorInt ->
            val selected = colorInt == selectedColor
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ComposeColor(colorInt))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onColorPicked(colorInt) }
            )
        }
    }
}

@Composable
private fun CheckerboardPreview(
    modifier: Modifier,
    imageUri: Any?
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellSize = 24.dp.toPx()
            val light = ComposeColor(0xFFE9E9E9)
            val dark = ComposeColor(0xFFD6D6D6)
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = 0f
                var col = row % 2
                while (x < size.width) {
                    drawRect(
                        color = if (col % 2 == 0) light else dark,
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                    )
                    x += cellSize
                    col++
                }
                y += cellSize
                row++
            }
        }

        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(ComposeColor(0x44FFFFFF), ComposeColor(0x11000000))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.pick_image_to_start),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
