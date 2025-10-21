package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AIPromptSectionHeader(
    promptCount: Int,
    favoriteCount: Int,
    onNavigateToAllPrompts: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF667EEA),
                            Color(0xFF764BA2)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🤖 AI Prompts",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Discover creative AI prompts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "✨ New",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Each stat gets equal weight so labels have enough room and won't wrap awkwardly.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatItem(
                        icon = Icons.Default.AutoAwesome,
                        count = promptCount,
                        label = "Available",
                        modifier = Modifier.weight(1f)
                    )

                    StatItem(
                        icon = Icons.Default.Favorite,
                        count = favoriteCount,
                        label = "Favorites",
                        modifier = Modifier.weight(1f)
                    )

                    StatItem(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        count = 42,
                        label = "Trending",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToFavorites,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFFF6B9D)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Favorites",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = onNavigateToAllPrompts,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF667EEA)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(
                            text = "View All",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    count: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    // Use a centered Row so icon + stacked texts are centered within the weighted space
    Column(
        modifier = modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon - First Line
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Color.White.copy(alpha = 0.95f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Count - Second Line
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Label - Third Line
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }

}