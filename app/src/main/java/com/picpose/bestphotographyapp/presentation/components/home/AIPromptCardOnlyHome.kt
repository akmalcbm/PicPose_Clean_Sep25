package com.picpose.bestphotographyapp.presentation.components.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPromptCardOnlyHome(
    prompt: AIPrompt,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onFavoriteClick: ((AIPrompt) -> Unit)? = null,
    isCompact: Boolean = false,
    showFavoriteIcon: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: AIPromptViewModel? = null // ✅ add this

) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isExpanded by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }

    // 🎨 Enhanced haptic feedback
    val hapticFeedback = LocalHapticFeedback.current

    // Animation for expand/collapse arrow
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing), // ✨ Better easing
        label = "rotation"
    )

    // 🎨 Enhanced press animation
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "cardScale"
    )

    // Safe string helpers to avoid passing null to Text
    val safeTitle = prompt.title ?: ""
    val safeShortPrompt = prompt.shortPrompt ?: ""
    val safeFullPrompt = prompt.fullPrompt ?: ""
    val safeCategory = prompt.category ?: ""

    // ✅ Box with enhanced shadow breathing room
    Box(
        modifier = modifier
            .width(if (isCompact) 280.dp else 320.dp)
            .wrapContentHeight()
            .padding(6.dp) // ✨ Increased shadow breathing room
            .scale(cardScale) // ✨ Added scale animation
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null, // ✨ Custom indication
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    isPressed = true
                    onClick()
                },
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = if (isPressed) 20.dp else 12.dp, // ✨ Enhanced shadow
            tonalElevation = 2.dp, // ✨ Added tonal elevation
            border = BorderStroke(
                width = if (isPressed) 1.dp else 0.5.dp, // ✨ Dynamic border
                color = if (isPressed) Color(0xFF6366F1).copy(alpha = 0.3f)
                else Color(0xFFE5E7EB)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFAFAFA),
                                Color.White
                            )
                        )
                    )
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ) // ✨ Better spring animation
                    )
            ) {
                // ✅ Image Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompact) 160.dp else 200.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(prompt.imageUrl)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(400) // ✨ Slightly longer crossfade
                            .build(),
                        contentDescription = safeTitle.ifBlank { "AI Generated Image" },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(Color.Gray.copy(alpha = 0.2f)),
                        error = ColorPainter(Color.Red.copy(alpha = 0.1f))
                    )

                    // ✅ Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.4f) // ✨ Slightly stronger overlay
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            )
                    )

                    // ✅ Top badges row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Category badge with enhanced styling
                        Surface(
                            color = Color(0xFF6366F1).copy(alpha = 0.95f), // ✨ Slightly more opaque
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 4.dp // ✨ Added subtle shadow
                        ) {
                            Text(
                                text = safeCategory,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // ✨ Enhanced action icons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Favorite icon
                            if (showFavoriteIcon && onFavoriteClick != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.95f), // ✨ More opaque
                                    shadowElevation = 2.dp // ✨ Added shadow
                                ) {
                                    IconButton(
                                        onClick = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onFavoriteClick(prompt)
                                            // ✅ Increment favorite count in background (only if newly favorited)
                                            if (!prompt.isBookmarked) {
                                                prompt.id.toIntOrNull()?.let { id ->
                                                    viewModel?.incrementFavoriteCount(id)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (prompt.isBookmarked) Icons.Filled.Favorite
                                            else Icons.Filled.FavoriteBorder,
                                            contentDescription = "Toggle Favorite",
                                            tint = if (prompt.isBookmarked)
                                                Color(0xFFE91E63) else Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Copy icon
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.95f), // ✨ More opaque
                                shadowElevation = 2.dp // ✨ Added shadow
                            ) {
                                IconButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        clipboardManager.setText(AnnotatedString(safeFullPrompt))
                                        Toast.makeText(
                                            context,
                                            "Prompt copied to clipboard!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onCopy()
                                        // ✅ Increment copy count in background
                                        prompt.id.toIntOrNull()?.let { id ->
                                            viewModel?.incrementCopyCount(id)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(18.dp),
                                        tint = Color(0xFF10B981)
                                    )
                                }
                            }
                        }
                    }

                    // ✨ Enhanced AI Badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.95f),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 3.dp // ✨ Added shadow
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🤖 AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // ✨ Enhanced Popular badge
                    if (prompt.isPopular) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.95f),
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 3.dp // ✨ Added shadow
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Whatshot,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Popular",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // ✅ Content Section
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Title
                    Text(
                        text = safeTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        maxLines = if (isCompact) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Short prompt
                    Text(
                        text = safeShortPrompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B),
                        maxLines = if (isExpanded) Int.MAX_VALUE else (if (isCompact) 2 else 3),
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )

                    // ✅ Expandable Full Prompt
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn(),
                        exit = shrinkVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color(0xFFF1F5F9)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Full Prompt:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF374151)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // ✨ Enhanced full prompt surface
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = Color(0xFFE2E8F0)
                                ) // ✨ Added subtle border
                            ) {
                                SelectionContainer { // ✨ Made text selectable
                                    Text(
                                        text = safeFullPrompt,
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF374151),
                                        lineHeight = 22.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // ✨ Enhanced copy button
                            OutlinedButton(
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    clipboardManager.setText(AnnotatedString(safeFullPrompt))
                                    Toast.makeText(
                                        context,
                                        "Full prompt copied!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onCopy()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF6366F1)
                                ),
                                border = BorderStroke(
                                    width = 1.5.dp, // ✨ Slightly thicker border
                                    color = Color(0xFF6366F1).copy(alpha = 0.3f)
                                )
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Copy Full Prompt",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ✅ Bottom action row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Likes
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { // ✨ Made clickable
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onFavoriteClick?.invoke(prompt)
                            }
                        ) {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${prompt.likes} likes",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // ✨ Enhanced expand/collapse button
                        TextButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                isExpanded = !isExpanded
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF6366F1)
                            )
                        ) {
                            Text(
                                text = if (isExpanded) "Show Less" else "Show More",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(rotationState),
                                tint = Color(0xFF6366F1)
                            )
                        }
                    }
                }
            }
        }

        // ✅ Enhanced pressed state reset
        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(200) // ✨ Slightly longer delay for better UX
                isPressed = false
            }
        }
    }
}
