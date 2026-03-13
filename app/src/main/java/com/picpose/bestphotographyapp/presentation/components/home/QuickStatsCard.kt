/**
 * ---
 * File: QuickStatsCard.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Contains reusable Compose UI building blocks shared across screens.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.viewmodels.StatsViewModel

@Composable
fun QuickStatsCard(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchStats()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource( R.string.quick_stats_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoading && uiState.stats == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            } else {
                val stats = uiState.stats
                if (stats != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // First row: Prompts and Likes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                icon = Icons.Default.AutoAwesome,
                                value = stats.total_prompts.toString(),
                                label = stringResource(R.string.ai_prompts),
                                color = Color(0xFFF44336)
                            )
                            StatItem(
                                icon = Icons.Default.Favorite,
                                value = stats.total_likes.toString(),
                                label = stringResource(R.string.likes),
                                color = Color(0xFFE91E63)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Second row: Favorites and Copies
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                icon = Icons.Default.CollectionsBookmark,
                                value = stats.total_favorites.toString(),
                                label = stringResource(R.string.favorites),
                                color = Color(0xFF673AB7)
                            )
                            StatItem(
                                icon = Icons.Default.ContentCopy,
                                value = stats.total_copies.toString(),
                                label = stringResource(R.string.copies),
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.last_updated_just_now),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                } else {
                    Text(
                        text = uiState.error ?: stringResource(R.string.no_data_available),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
