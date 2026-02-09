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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.ads.AdsLog
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import com.picpose.bestphotographyapp.presentation.components.ads.AdmobRewardedAd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen() {
    val canShowAds = AdsManager.canShowAds()
    LaunchedEffect(Unit) {
        AdsLog.i(
            AdsLog.TAG_UI,
            "[AdsUI] screen=RewardsScreen placement=${AdsManager.KEY_REWARDED_AD} action=compose canShowAds=$canShowAds"
        )
    }
    LaunchedEffect(canShowAds) {
        AdsLog.i(
            AdsLog.TAG_UI,
            if (canShowAds) {
                "[AdsUI] screen=RewardsScreen placement=${AdsManager.KEY_REWARDED_AD} action=render"
            } else {
                "[AdsUI] screen=RewardsScreen placement=${AdsManager.KEY_REWARDED_AD} action=skip reason=global_gate"
            }
        )
    }

    val userPoints = 2450
    val rewards = listOf(
        Reward(stringResource(R.string.reward_premium_filters_pack), stringResource(R.string.reward_premium_filters_pack_desc), 500, Icons.Filled.FilterVintage),
        Reward(stringResource(R.string.reward_cloud_storage_100gb), stringResource(R.string.reward_cloud_storage_100gb_desc), 800, Icons.Filled.CloudUpload),
        Reward(stringResource(R.string.reward_ai_photo_enhancement), stringResource(R.string.reward_ai_photo_enhancement_desc), 300, Icons.Filled.AutoAwesome),
        Reward(stringResource(R.string.reward_premium_templates), stringResource(R.string.reward_premium_templates_desc), 600, Icons.Filled.WorkspacePremium),
        Reward(stringResource(R.string.reward_photo_contest_entry), stringResource(R.string.reward_photo_contest_entry_desc), 200, Icons.Filled.EmojiEvents),
        Reward(stringResource(R.string.reward_one_on_one_photography_tips), stringResource(R.string.reward_one_on_one_photography_tips_desc), 1500, Icons.Filled.School)
    )

    val achievements = listOf(
        Achievement(stringResource(R.string.achievement_first_upload), stringResource(R.string.achievement_first_upload_desc), true, Icons.Filled.CloudUpload),
        Achievement(stringResource(R.string.achievement_social_butterfly), stringResource(R.string.achievement_social_butterfly_desc), true, Icons.Filled.Favorite),
        Achievement(stringResource(R.string.achievement_creative_streak), stringResource(R.string.achievement_creative_streak_desc), false, Icons.Filled.Stream),
        Achievement(stringResource(R.string.achievement_popular_creator), stringResource(R.string.achievement_popular_creator_desc), false, Icons.Filled.People)
    )

    // ✅ Scaffold for insets-safe layout
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.rewards),
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
                        contentDescription = stringResource(R.string.points),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.points_count, userPoints),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.earn_points_and_unlock_rewards),
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
                    text = stringResource(R.string.available_rewards),
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
                    text = stringResource(R.string.achievements),
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
                    text = stringResource(R.string.rewards_footer_message),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }

            if (canShowAds) {
                item {
                    AdmobRewardedAd(
                        placementKey = AdsManager.KEY_REWARDED_AD,
                        onRewardEarned = { /* reward payout hook */ },
                        onAdDismissed = { /* UI callback hook */ }
                    )
                }
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
                    text = stringResource(R.string.points_count, reward.pointsCost),
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
                Text(if (canRedeem) stringResource(R.string.redeem) else stringResource(R.string.locked))
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
                    contentDescription = stringResource(R.string.completed),
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
