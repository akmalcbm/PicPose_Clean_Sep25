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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.picpose.bestphotographyapp.data.service.ads.RewardedAdUiState

@Composable
fun EarnCard(
    isLoggedIn: Boolean,
    adState: RewardedAdUiState,
    adRewardedToday: Boolean,
    adDailyCount: Int?,
    adDailyCap: Int?,
    adRewardPoints: Int = 10,
    adRewardAvailable: Boolean = true,
    onWatchAd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulseScale by rememberInfiniteTransition(label = "watch_ad_pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (!adRewardedToday) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "watch_ad_scale",
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
                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Earn", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.rewards_watch_ad_value_statement, adRewardPoints),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                enabled = isLoggedIn && adRewardAvailable && !adState.isLoading && !adState.isShowing,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(pulseScale),
            ) {
                if (adState.isLoading && !adState.isReady) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.rewards_loading_reward_ad))
                } else {
                    Icon(Icons.Default.PlayCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.rewards_watch_ad_plus_amount, adRewardPoints))
                }
            }
            if (!adRewardAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.rewards_ad_limit_reached),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
