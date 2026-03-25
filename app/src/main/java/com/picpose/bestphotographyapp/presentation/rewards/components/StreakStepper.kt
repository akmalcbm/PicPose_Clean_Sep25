/**
 * ---
 * File: StreakStepper.kt
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.R
import kotlin.math.absoluteValue

@Composable
fun StreakStepper(
    streakCount: Int,
    todayClaimed: Boolean,
    rewardsSchedule: List<Int>,
    isLoggedIn: Boolean,
    isClaiming: Boolean,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val schedule = if (rewardsSchedule.isNotEmpty()) rewardsSchedule.take(7) else listOf(10, 20, 30, 40, 50, 60, 100)
    val cycleLength = schedule.size.coerceAtLeast(1)
    val safeStreak = streakCount.coerceAtLeast(0)

    val completedInCycle = when {
        safeStreak <= 0 -> 0
        todayClaimed -> ((safeStreak - 1) % cycleLength) + 1
        else -> safeStreak % cycleLength
    }
    val claimDayInCycle = ((safeStreak % cycleLength) + 1)
    val nextDayInCycle = ((claimDayInCycle % cycleLength) + 1)

    val claimReward = schedule.getOrElse((claimDayInCycle - 1).coerceAtLeast(0)) { schedule.last() }
    val nextReward = schedule.getOrElse((nextDayInCycle - 1).coerceAtLeast(0)) { schedule.last() }
    val cycleProgress = (completedInCycle.toFloat() / cycleLength.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.rewards_streak_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (safeStreak == 0) {
                            stringResource(R.string.rewards_streak_start_today)
                        } else {
                            stringResource(R.string.rewards_streak_active, safeStreak)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.rewards_streak_day_label, claimDayInCycle),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { cycleProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (todayClaimed) {
                    stringResource(R.string.rewards_streak_claimed_next_reward, nextDayInCycle, nextReward)
                } else {
                    stringResource(R.string.rewards_streak_today_reward, claimDayInCycle, claimReward)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(7) { index ->
                    val day = index + 1
                    val state = when {
                        day <= completedInCycle -> StreakDayState.Completed
                        !todayClaimed && day == claimDayInCycle -> StreakDayState.Current
                        todayClaimed && day == nextDayInCycle -> StreakDayState.Next
                        else -> StreakDayState.Upcoming
                    }
                    StreakDayBadge(
                        day = day,
                        reward = schedule.getOrElse(index) { schedule.last() },
                        state = state,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            val milestoneText = when {
                todayClaimed -> stringResource(R.string.rewards_streak_consistency_message, nextReward)
                claimDayInCycle == cycleLength -> stringResource(R.string.rewards_streak_final_day_ready, claimReward)
                else -> stringResource(R.string.rewards_streak_claim_now_message)
            }
            Text(
                text = milestoneText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(visible = todayClaimed && safeStreak >= cycleLength) {
                Text(
                    text = stringResource(R.string.rewards_streak_weekly_complete),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClaim,
                enabled = isLoggedIn && !todayClaimed && !isClaiming,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isClaiming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.rewards_claiming))
                } else {
                    Text(
                        when {
                            todayClaimed -> stringResource(R.string.rewards_claimed_for_today)
                            !isLoggedIn -> stringResource(R.string.rewards_login_to_claim_reward)
                            else -> stringResource(R.string.rewards_claim_plus_credits, claimReward)
                        }
                    )
                }
            }
        }
    }
}

private enum class StreakDayState {
    Completed,
    Current,
    Next,
    Upcoming,
}

@Composable
private fun StreakDayBadge(
    day: Int,
    reward: Int,
    state: StreakDayState,
) {
    val colors = when (state) {
        StreakDayState.Completed -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primary,
        )
        StreakDayState.Current -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiary,
        )
        StreakDayState.Next -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondary,
        )
        StreakDayState.Upcoming -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline,
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.first,
        border = BorderStroke(
            width = if (state == StreakDayState.Current || state == StreakDayState.Next) 2.dp else 1.dp,
            color = colors.third.copy(alpha = if (state == StreakDayState.Upcoming) 0.35f else 0.85f),
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = when (state) {
                    StreakDayState.Completed -> Icons.Default.Check
                    StreakDayState.Current -> Icons.Default.LocalFireDepartment
                    StreakDayState.Next -> Icons.Default.Flag
                    StreakDayState.Upcoming -> Icons.Default.Flag
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.second,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.rewards_streak_day_label, day),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.second,
            )
            Text(
                text = "+${reward.absoluteValue}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.second.copy(alpha = 0.86f),
            )
        }
    }
}
