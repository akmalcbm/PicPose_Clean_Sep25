/**
 * ---
 * File: PacksRow.kt
 * Layer: Shared
 * Project: PicPose
 *
 * Purpose:
 * Contains reusable Compose UI building blocks shared across screens.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * UI (Compose) -> ViewModel -> Repository -> Local/Remote Data Source -> Room/API
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.ui.rewards.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.data.models.v2.PackSummaryDto

@Composable
fun PacksRow(
    packs: List<PackSummaryDto>,
    ownedCount: Int,
    isLoggedIn: Boolean,
    onOpenPacks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Default.CardGiftcard, contentDescription = null)
                Text("  Packs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Owned: $ownedCount • Active: ${packs.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (packs.isEmpty()) {
                Text("No packs available right now.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(packs.take(10), key = { it.id }) { pack ->
                        Card(
                            modifier = Modifier.width(210.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier.background(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.58f),
                                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                                        )
                                    )
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = pack.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (pack.ownsPack) {
                                            Box(
                                                modifier = Modifier.background(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    RoundedCornerShape(999.dp)
                                                ).padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Owned", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("${pack.pricePoints} credits", style = MaterialTheme.typography.bodyMedium)
                                    pack.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            desc,
                                            maxLines = 2,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenPacks,
                enabled = isLoggedIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Browse all packs")
            }
        }
    }
}
