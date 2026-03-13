/**
 * ---
 * File: ReferralCard.kt
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.R

@Composable
fun ReferralCard(
    isLoggedIn: Boolean,
    code: String?,
    statusLabel: String,
    hasAppliedCode: Boolean,
    canClaimReward: Boolean,
    isRewardClaimed: Boolean,
    referredCount: Int,
    pendingCount: Int,
    rewardedCount: Int,
    isApplyingCode: Boolean,
    onCopyCode: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpenApplyCode: () -> Unit,
    onClaimReward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val claimPulse by rememberInfiniteTransition(label = "claim_pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (canClaimReward) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "claim_scale",
    )

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
                Icon(Icons.Default.Redeem, contentDescription = null)
                Text("  Referrals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (isLoggedIn) (code ?: "Loading code...") else "Login required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { code?.let(onCopyCode) }, enabled = isLoggedIn && !code.isNullOrBlank()) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy code")
                    }
                    IconButton(onClick = { code?.let(onShare) }, enabled = isLoggedIn && !code.isNullOrBlank()) {
                        Icon(Icons.Default.Share, contentDescription = "Share code")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                statusLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("Referred $referredCount") })
                AssistChip(onClick = {}, label = { Text("Pending $pendingCount") })
                AssistChip(onClick = {}, label = { Text("Rewarded $rewardedCount") })
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onOpenApplyCode,
                    enabled = isLoggedIn && !isApplyingCode,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isApplyingCode) stringResource(R.string.referral_applying_code) else stringResource(R.string.referral_apply_code))
                }
                val claimEnabled = isLoggedIn && canClaimReward && !isRewardClaimed
                val claimLabel = when {
                    !isLoggedIn -> stringResource(R.string.referral_login_to_claim)
                    !hasAppliedCode -> stringResource(R.string.referral_apply_code_first)
                    isRewardClaimed -> stringResource(R.string.referral_reward_claimed)
                    canClaimReward -> stringResource(R.string.referral_claim_reward)
                    else -> stringResource(R.string.referral_complete_unlock_to_qualify)
                }

                FilledTonalButton(
                    onClick = onClaimReward,
                    enabled = claimEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .scale(claimPulse),
                ) {
                    if (isRewardClaimed) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null)
                    }
                    Text(claimLabel)
                }
            }
            AnimatedVisibility(visible = !isLoggedIn) {
                Text(
                    text = stringResource(R.string.referral_login_to_claim_rewards),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
