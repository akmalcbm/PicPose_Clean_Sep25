/**
 * ---
 * File: EarnCard.kt
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

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.components.common.appSectionCardBorder
import com.picpose.bestphotographyapp.components.common.appSectionCardColors
import com.picpose.bestphotographyapp.components.common.appSectionCardElevation
import com.picpose.bestphotographyapp.components.common.appSectionCardShape
import com.picpose.bestphotographyapp.data.service.ads.RewardedAdUiState

@Composable
fun EarnCard(
    isLoggedIn: Boolean,
    adState: RewardedAdUiState,
    adRewardedToday: Boolean,
    adDailyCount: Int?,
    adDailyCap: Int?,
    streakCount: Int,
    todayClaimed: Boolean,
    rewardsSchedule: List<Int>,
    adRewardPoints: Int = 10,
    adRewardAvailable: Boolean = true,
    onWatchAd: () -> Unit,
    onRetryAdLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val schedule = if (rewardsSchedule.isNotEmpty()) rewardsSchedule.take(7) else listOf(10, 20, 30, 40, 50, 60, 100)
    val cycleLength = schedule.size.coerceAtLeast(1)
    val claimDayInCycle = ((streakCount.coerceAtLeast(0) % cycleLength) + 1)
    val streakRewardToday = schedule.getOrElse((claimDayInCycle - 1).coerceAtLeast(0)) { schedule.last() }
    val watchAdEnabled = isLoggedIn && adRewardAvailable && adState.isReady && !adState.isLoading && !adState.isShowing

    val pulseScale by rememberInfiniteTransition(label = "watch_ad_pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (!adRewardedToday && watchAdEnabled) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "watch_ad_scale",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = appSectionCardShape,
        colors = appSectionCardColors(),
        border = appSectionCardBorder(),
        elevation = appSectionCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column {
                    Text(
                        stringResource(R.string.pack_earn_credits_action),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.rewards_earn_card_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            EarnOpportunityRow(
                icon = Icons.Default.VideoLibrary,
                title = stringResource(R.string.rewards_earn_row_rewarded_ad),
                subtitle = stringResource(R.string.rewards_watch_ad_value_statement, adRewardPoints),
                value = stringResource(R.string.rewards_plus_credits_amount, adRewardPoints),
            )
            Spacer(modifier = Modifier.height(8.dp))
            EarnOpportunityRow(
                icon = Icons.Default.LocalFireDepartment,
                title = stringResource(R.string.rewards_streak_title),
                subtitle = if (todayClaimed) {
                    stringResource(R.string.rewards_streak_already_claimed_next_tomorrow)
                } else {
                    stringResource(R.string.rewards_streak_claim_below_hint, streakRewardToday)
                },
                value = if (todayClaimed) {
                    stringResource(R.string.rewards_claimed_short)
                } else {
                    stringResource(R.string.rewards_plus_credits_amount, streakRewardToday)
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            EarnOpportunityRow(
                icon = Icons.Default.Groups,
                title = stringResource(R.string.rewards_referral_rewards_title),
                subtitle = stringResource(R.string.rewards_referral_rewards_subtitle),
                value = stringResource(R.string.rewards_bonus),
            )

            if (adDailyCount != null && adDailyCap != null && adDailyCap > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(stringResource(R.string.rewards_ad_daily_progress, adDailyCount, adDailyCap))
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (adDailyCount.toFloat() / adDailyCap.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onWatchAd,
                enabled = watchAdEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .scale(pulseScale),
            ) {
                if (adState.isLoading && !adState.isReady) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.rewards_loading_reward_ad))
                } else {
                    Icon(Icons.Default.PlayCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.rewards_watch_ad_plus_amount, adRewardPoints))
                }
            }
            when {
                !isLoggedIn -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.rewards_login_prompt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !adRewardAvailable -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.rewards_ad_limit_reached),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                adState.lastError != null && !adState.isLoading && !adState.isReady -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.rewards_ad_temporarily_unavailable),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onRetryAdLoad,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.rewards_retry_ad_load))
                    }
                }
            }
        }
    }
}

@Composable
private fun EarnOpportunityRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(7.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
