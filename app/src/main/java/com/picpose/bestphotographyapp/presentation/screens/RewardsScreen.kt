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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen() {
    val userPoints = 2450
    val rewards = listOf(
        Reward("Premium Filters Pack", "Unlock 20+ premium filters", 500, Icons.Filled.FilterVintage),
        Reward("Cloud Storage 100GB", "Extra cloud storage space", 800, Icons.Filled.CloudUpload),
        Reward("AI Photo Enhancement", "10 AI enhancement credits", 300, Icons.Filled.AutoAwesome),
        Reward("Premium Templates", "Access to premium templates", 600, Icons.Filled.WorkspacePremium),
        Reward("Photo Contest Entry", "Enter exclusive contests", 200, Icons.Filled.EmojiEvents),
        Reward("1-on-1 Photography Tips", "Personal session with expert", 1500, Icons.Filled.School)
    )

    val achievements = listOf(
        Achievement("First Upload", "Upload your first photo", true, Icons.Filled.CloudUpload),
        Achievement("Social Butterfly", "Get 100 likes", true, Icons.Filled.Favorite),
        Achievement("Creative Streak", "Upload 10 photos", false, Icons.Filled.Stream),
        Achievement("Popular Creator", "Get 1000 followers", false, Icons.Filled.People)
    )

    // ✅ Scaffold for insets-safe layout
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Rewards",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp
            )
        ) {
            // 🏆 Header Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Stars,
                        contentDescription = "Points",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$userPoints Points",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Earn points and unlock exclusive rewards",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 🎁 Available Rewards
            item {
                Text(
                    text = "Available Rewards",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(rewards) { reward ->
                RewardCard(
                    reward = reward,
                    userPoints = userPoints,
                    onRedeem = { /* TODO: Handle redeem logic */ }
                )
            }

            // 🥇 Achievements
            item {
                Text(
                    text = "Achievements",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            items(achievements) { achievement ->
                AchievementCard(achievement = achievement)
            }

            // ✨ Footer
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Keep earning points by participating in challenges and uploading your best shots!",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 2.dp, bottom = 2.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (canRedeem)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                tint = if (canRedeem)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reward.title,
                    fontWeight = FontWeight.Medium,
                    color = if (canRedeem)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = reward.description,
                    fontSize = 12.sp,
                    color = if (canRedeem)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = "${reward.pointsCost} Points",
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 2.dp, bottom = 2.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isCompleted)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    fontWeight = FontWeight.Medium,
                    color = if (achievement.isCompleted)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = achievement.description,
                    fontSize = 12.sp,
                    color = if (achievement.isCompleted)
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
