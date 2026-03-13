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

package com.picpose.bestphotographyapp.ui.rewards.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

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
    val currentDay = min(7, max(1, if (todayClaimed) streakCount else streakCount + 1))
    val completedUntil = if (todayClaimed) streakCount else streakCount - 1
    val nextReward = schedule.getOrNull((currentDay - 1).coerceIn(0, schedule.lastIndex)) ?: 0
    val daySevenReward = schedule.getOrNull(6) ?: schedule.lastOrNull() ?: 0

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
                Icon(Icons.Default.LocalFireDepartment, contentDescription = null)
                Text(
                    "  Daily Streak",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(7) { index ->
                    val day = index + 1
                    val completed = day <= completedUntil
                    val isCurrent = day == currentDay
                    StreakDayBadge(
                        day = day,
                        reward = schedule.getOrElse(index) { schedule.last() },
                        completed = completed,
                        isCurrent = isCurrent,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            val milestoneText = if (todayClaimed && streakCount >= 7) {
                "Streak complete! Mega reward unlocked."
            } else if (todayClaimed) {
                "Come back tomorrow for Day ${min(7, currentDay + 1)} bonus +${schedule.getOrElse(min(6, currentDay)) { schedule.last() }}"
            } else if (currentDay == 7) {
                "Day 7 mega +$daySevenReward is ready."
            } else {
                "Day $currentDay bonus +$nextReward is ready."
            }
            Text(
                milestoneText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(visible = todayClaimed && streakCount >= 7) {
                Text(
                    "🏆 Weekly streak champion",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
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
                    Text("  Claiming...")
                } else {
                    Text(if (todayClaimed) "Come back tomorrow" else "Claim today's reward")
                }
            }
        }
    }
}

@Composable
private fun StreakDayBadge(
    day: Int,
    reward: Int,
    completed: Boolean,
    isCurrent: Boolean,
) {
    val pulseScale by rememberInfiniteTransition(label = "current_day_pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isCurrent) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "current_day_scale",
    )

    val containerColor = when {
        completed -> MaterialTheme.colorScheme.primaryContainer
        isCurrent -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(pulseScale)
            .background(containerColor, RoundedCornerShape(16.dp))
            .border(width = if (isCurrent) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (completed) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Completed day",
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(3.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Day $day", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text("+$reward", style = MaterialTheme.typography.labelLarge)
    }
}
