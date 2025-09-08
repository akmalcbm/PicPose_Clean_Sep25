package com.picpose.bestphotographyapp.presentation.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.picpose.bestphotographyapp.data.models.AIPrompt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPromptCardOnlyHome(
    prompt: AIPrompt,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onFavoriteClick: ((AIPrompt) -> Unit)? = null,
    isCompact: Boolean = false,
    showFavoriteIcon: Boolean = true,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }

    // Animation for expand/collapse arrow
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300)
    )

    Card(
        modifier = modifier
            .width(if (isCompact) 280.dp else 320.dp) // Fixed width instead of fillMaxWidth
            .animateContentSize(
                animationSpec = tween(300)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 12.dp else 8.dp
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFFF1F5F9)
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
        ) {
            // Image Section with overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 160.dp else 200.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                AsyncImage(
                    model = prompt.imageUrl,
                    contentDescription = prompt.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f)
                                ),
                                startY = 0f,
                                endY = 600f
                            )
                        )
                )

                // Top badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Category badge
                    Surface(
                        color = Color(0xFF6366F1).copy(alpha = 0.95f),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            text = prompt.category,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Action icons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Favorite icon
                        if (showFavoriteIcon && onFavoriteClick != null) {
                            IconButton(
                                onClick = { onFavoriteClick(prompt) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        Color.White.copy(alpha = 0.9f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    if (prompt.isFavorite) Icons.Default.Favorite
                                    else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (prompt.isFavorite)
                                        Color(0xFFE91E63)
                                    else
                                        Color(0xFF64748B)
                                )
                            }
                        }

                        // Copy icon
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(prompt.fullPrompt))
                                Toast.makeText(
                                    context,
                                    "Prompt copied to clipboard!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onCopy()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Color.White.copy(alpha = 0.9f),
                                    CircleShape
                                )
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

                // AI Badge (bottom left)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.95f),
                    shape = RoundedCornerShape(12.dp)
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

                // Popular badge (if applicable)
                if (prompt.isPopular) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.95f),
                        shape = RoundedCornerShape(12.dp)
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

            // Content Section
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Title
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    maxLines = if (isCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Short prompt
                Text(
                    text = prompt.shortPrompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF64748B),
                    maxLines = if (isExpanded) Int.MAX_VALUE else (if (isCompact) 2 else 3),
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )

                // Expandable Full Prompt
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(
                        animationSpec = tween(300)
                    ) + fadeIn(),
                    exit = shrinkVertically(
                        animationSpec = tween(300)
                    ) + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))

                        Divider(
                            color = Color(0xFFF1F5F9),
                            thickness = 1.dp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Full Prompt:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF374151)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = prompt.fullPrompt,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF374151),
                                lineHeight = 22.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Copy full prompt button
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(prompt.fullPrompt))
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

                // Bottom action row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Likes
                    Row(
                        verticalAlignment = Alignment.CenterVertically
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

                    // Expand/Collapse button
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
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
}
