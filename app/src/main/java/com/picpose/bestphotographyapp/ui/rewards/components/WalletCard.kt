/**
 * ---
 * File: WalletCard.kt
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.R

@Composable
fun WalletCard(
    pointsBalance: Int,
    displayedPoints: Int,
    tokenBalances: Map<String, Int>,
    onQuickClaim: () -> Unit,
    onQuickWatchAd: () -> Unit,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wallet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "$displayedPoints credits",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (displayedPoints != pointsBalance) {
                Text(
                    "Balance syncing...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tokenBalances.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tokenBalances.forEach { (type, balance) ->
                        AssistChip(onClick = {}, label = { Text("${friendlyTokenLabel(type)}: $balance") })
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onQuickClaim,
                    enabled = isLoggedIn,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Claim")
                }
                OutlinedButton(
                    onClick = onQuickWatchAd,
                    enabled = isLoggedIn,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.rewards_watch_ad_short))
                }
            }
        }
    }
}

@Composable
private fun friendlyTokenLabel(raw: String): String {
    return when (raw.uppercase()) {
        "PROMPT_UNLOCK" -> stringResource(R.string.token_prompt_unlock)
        "IMAGE_GEN_PRIORITY" -> stringResource(R.string.token_image_gen_priority)
        else -> raw.replace('_', ' ').lowercase().split(' ')
            .joinToString(" ") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    }
}
