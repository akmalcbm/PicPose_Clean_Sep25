/**
 * ---
 * File: PromptOfDayCard.kt
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

package com.picpose.bestphotographyapp.presentation.rewards.components

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.picpose.bestphotographyapp.data.remote.dto.v2.V2PromptDto

@Composable
fun PromptOfDayCard(
    prompt: V2PromptDto?,
    mode: String?,
    cost: Int,
    onOpenPrompt: (String) -> Unit,
    onUnlockDiscount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize().padding(14.dp)) {
            Text("Prompt of the Day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (!prompt?.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = prompt?.imageUrl,
                        contentDescription = prompt?.title ?: "Prompt image",
                        modifier = Modifier.fillMaxWidth().height(190.dp),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        mode?.takeIf { it.isNotBlank() }?.let {
                            AssistChip(onClick = {}, label = { Text(it.uppercase()) })
                        }
                        if (cost > 0) {
                            AssistChip(onClick = {}, label = { Text("$cost credits") })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = prompt?.title ?: "Today's featured prompt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Crossfade(targetState = prompt?.id, label = "potd_cta") { promptId ->
                if (promptId.isNullOrBlank()) {
                    Text(
                        "Fetching today's card...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { onOpenPrompt(promptId) }, modifier = Modifier.weight(1f)) {
                            Text(if (mode.equals("FREE", true)) "Open Free Today" else "Open")
                        }
                        if (mode.equals("DISCOUNT", true) || mode.equals("FREE", true)) {
                            FilledTonalButton(
                                onClick = { onUnlockDiscount(promptId) },
                                modifier = Modifier.weight(1f),
                            ) {
                                val label = if (mode.equals("DISCOUNT", true)) {
                                    "Unlock for $cost"
                                } else {
                                    "Unlock Forever"
                                }
                                Text(label)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = prompt?.teaserText ?: prompt?.shortPrompt.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
