package com.picpose.bestphotographyapp.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RewardsScreen() {
    val userPoints = 2450
    val rewards = listOf(
        Reward("Premium Filters Pack", "Unlock 20+ premium filters", 500, Icons.Filled.FilterVintage),
        Reward("Cloud Storage 100GB", "Extra cloud storage space", 800, Icons.Filled.CloudUpload),
        Reward("AI Photo Enhancement", "10 AI enhancement credits", 300, Icons.Filled.AutoAwesome),
        Reward("Premium Templates", "Access to premium templates", 600, Icons.Filled.WorkspacePremium),
        Reward("Photo Contest Entry", "Enter exclusive contests", 200, Icons.Filled.EmojiEvents),
        Reward("1-on-1 Photography Tips", "Personal photography session", 1500, Icons.Filled.School)
    )

    val achievements = listOf(
        Achievement("First Upload", "Upload your first photo", true, Icons.Filled.CloudUpload),
        Achievement("Social Butterfly", "Get 100 likes", true, Icons.Filled.Favorite),
        Achievement("Creative Streak", "Upload 10 photos", false, Icons.Filled.Stream),
        Achievement("Popular Creator", "Get 1000 followers", false, Icons.Filled.People)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Text(
                text = "Rewards",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Earn points and unlock amazing rewards",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // Points Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Stars,
                        contentDescription = "Points",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = userPoints.toString(),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Your Points",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Available Rewards
        item {
            Text(
                text = "Available Rewards",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(rewards) { reward ->
            RewardCard(
                reward = reward,
                userPoints = userPoints,
                onRedeem = { /* Handle redeem */ }
            )
        }

        // Achievements Section
        item {
            Text(
                text = "Achievements",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(achievements) { achievement ->
            AchievementCard(achievement = achievement)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardCard(
    reward: Reward,
    userPoints: Int,
    onRedeem: () -> Unit
) {
    val canRedeem = userPoints >= reward.pointsCost

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (canRedeem) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                reward.icon,
                contentDescription = reward.title,
                modifier = Modifier.size(40.dp),
                tint = if (canRedeem) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reward.title,
                    fontWeight = FontWeight.Medium,
                    color = if (canRedeem) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = reward.description,
                    fontSize = 12.sp,
                    color = if (canRedeem) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = "${reward.pointsCost} points",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = onRedeem,
                enabled = canRedeem,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Text(if (canRedeem) "Redeem" else "Locked")
            }
        }
    }
}

@Composable
fun AchievementCard(achievement: Achievement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isCompleted)
                MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                achievement.icon,
                contentDescription = achievement.title,
                modifier = Modifier.size(32.dp),
                tint = if (achievement.isCompleted)
                    MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    fontWeight = FontWeight.Medium,
                    color = if (achievement.isCompleted)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = achievement.description,
                    fontSize = 12.sp,
                    color = if (achievement.isCompleted)
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (achievement.isCompleted) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Completed",
                    tint = Color(0xFF4CAF50)
                )
            }
        }
    }
}

data class Reward(
    val title: String,
    val description: String,
    val pointsCost: Int,
    val icon: ImageVector
)

data class Achievement(
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val icon: ImageVector
)
