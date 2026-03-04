package com.picpose.bestphotographyapp.ui.rewards.components

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RewardsHeader(
    level: Int,
    xp: Int,
    nextLevelXp: Int,
    displayedPoints: Int,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = when {
        nextLevelXp <= 0 -> 0f
        xp <= 0 -> 0f
        xp >= nextLevelXp -> 1f
        else -> xp.toFloat() / nextLevelXp.toFloat()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Progress", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Level $level",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("$displayedPoints credits") },
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .height(10.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "$xp / $nextLevelXp XP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Crossfade(targetState = isLoggedIn, label = "header_login_state") { loggedIn ->
                    if (!loggedIn) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
                                Text("Login to claim rewards")
                            }
                        }
                    }
                }
            }
        }
    }
}
