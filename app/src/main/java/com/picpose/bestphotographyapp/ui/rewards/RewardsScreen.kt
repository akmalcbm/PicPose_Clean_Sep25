package com.picpose.bestphotographyapp.ui.rewards

import android.content.Intent
import android.app.Activity
import com.picpose.bestphotographyapp.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.picpose.bestphotographyapp.data.ads.RewardedAdManager
import com.picpose.bestphotographyapp.data.models.v2.PackSummaryDto
import com.picpose.bestphotographyapp.data.models.v2.V2PromptDto
import com.picpose.bestphotographyapp.presentation.ads.AdsManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RewardsScreen(
    onOpenPrompt: (String) -> Unit,
    onRequireLogin: () -> Unit,
    onOpenPacks: () -> Unit,
    viewModel: RewardsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity
    val rewardedAdManager = remember { RewardedAdManager() }
    val rewardedAdState by rewardedAdManager.uiState.collectAsState()
    var showApplyCodeDialog by rememberSaveable { mutableStateOf(false) }
    var applyCodeValue by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(isLoggedIn) {
        viewModel.loadRewards(forceRefresh = true)
    }

    LaunchedEffect(Unit) {
        rewardedAdManager.loadRewardedAd(context, AdsManager.KEY_REWARDED_AD)
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.setStatusMessage(null)
        }
    }

    if (showApplyCodeDialog) {
        AlertDialog(
            onDismissRequest = { showApplyCodeDialog = false },
            title = { Text("Apply referral code") },
            text = {
                OutlinedTextField(
                    value = applyCodeValue,
                    onValueChange = { applyCodeValue = it.uppercase() },
                    label = { Text("Referral code") },
                    singleLine = true,
                    supportingText = {
                        Text("Rewards unlock after qualifying action (e.g., first premium unlock or first generation).")
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApplyCodeDialog = false
                        viewModel.applyReferralCode(applyCodeValue)
                        applyCodeValue = ""
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyCodeDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!isLoggedIn) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Login to earn credits and unlock premium prompts.", fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = onRequireLogin, modifier = Modifier.fillMaxWidth()) {
                                    Text("Login")
                                }
                            }
                        }
                    }
                }

                item {
                    BalanceCard(
                        pointsBalance = uiState.pointsBalance,
                        tokenBalances = uiState.tokenBalances,
                    )
                }

                item {
                    StreakCard(
                        streakCount = uiState.streakCount,
                        rewardsSchedule = uiState.rewardsSchedule,
                        todayClaimed = uiState.todayClaimed,
                        isLoggedIn = isLoggedIn,
                        onClaim = {
                            if (isLoggedIn) viewModel.claimDailyLogin() else onRequireLogin()
                        },
                    )
                }

                item {
                    EarnCard(
                        isLoggedIn = isLoggedIn,
                        adState = rewardedAdState,
                        onWatchAd = {
                            val hostActivity = activity
                            if (!isLoggedIn) {
                                onRequireLogin()
                            } else if (hostActivity == null) {
                                viewModel.setStatusMessage("Rewarded ads require an activity context.")
                            } else {
                                rewardedAdManager.showRewardedAd(
                                    activity = hostActivity,
                                    placementKey = AdsManager.KEY_REWARDED_AD,
                                    onRewardEarned = { adRewardId ->
                                        viewModel.rewardAdPoints(adRewardId)
                                    },
                                    onUnavailable = { message ->
                                        viewModel.setStatusMessage(message)
                                    },
                                )
                            }
                        },
                    )
                }

                item {
                    PromptOfDayCard(
                        prompt = uiState.promptOfTheDay?.post ?: uiState.publicPromptOfTheDay,
                        mode = uiState.promptOfDayMode,
                        cost = uiState.promptOfDayCost,
                        onOpenPrompt = onOpenPrompt,
                    )
                }

                item {
                    ReferralCard(
                        isLoggedIn = isLoggedIn,
                        code = uiState.referralCode,
                        status = uiState.referralStatus,
                        referredCount = uiState.referralReferredCount,
                        rewardedCount = uiState.referralRewardedCount,
                        pendingCount = uiState.referralPendingCount,
                        onShare = { code ->
                            val shareMessage = buildString {
                                append("Install PicPose: AI Prompts & Posing Guide\n")
                                append(BuildConfig.REFERRAL_PLAY_URL)
                                append("\nUse my referral code: ")
                                append(code)
                                append("\nAfter installing, open Rewards -> Apply Code.")
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share referral code"))
                        },
                        onApply = {
                            if (isLoggedIn) showApplyCodeDialog = true else onRequireLogin()
                        },
                        onClaimReward = {
                            if (isLoggedIn) viewModel.claimReferralReward() else onRequireLogin()
                        },
                    )
                }

                item {
                    PacksCard(
                        isLoggedIn = isLoggedIn,
                        ownedCount = uiState.ownedPackCount,
                        packs = uiState.packs,
                        onOpenPacks = {
                            if (isLoggedIn) onOpenPacks() else onRequireLogin()
                        },
                    )
                }

                item {
                    ProgressCard(
                        level = uiState.level,
                        xp = uiState.xp,
                        nextLevelXp = uiState.nextLevelXp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(
    pointsBalance: Int,
    tokenBalances: Map<String, Int>,
) {
    Card {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Wallet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("$pointsBalance credits", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tokenBalances.forEach { (type, balance) ->
                    AssistChip(onClick = {}, label = { Text("$type: $balance") })
                }
            }
        }
    }
}

@Composable
private fun StreakCard(
    streakCount: Int,
    rewardsSchedule: List<Int>,
    todayClaimed: Boolean,
    isLoggedIn: Boolean,
    onClaim: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Daily streak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Current streak: $streakCount")
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rewardsSchedule.forEachIndexed { index, reward ->
                    AssistChip(onClick = {}, label = { Text("Day ${index + 1}: $reward") })
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            FilledTonalButton(
                onClick = onClaim,
                enabled = isLoggedIn && !todayClaimed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (todayClaimed) "Claimed today" else "Claim today's reward")
            }
        }
    }
}

@Composable
private fun EarnCard(
    isLoggedIn: Boolean,
    adState: com.picpose.bestphotographyapp.data.ads.RewardedAdUiState,
    onWatchAd: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Earn points", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Watch a rewarded ad to earn server-verified credits.")
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onWatchAd, modifier = Modifier.fillMaxWidth(), enabled = isLoggedIn) {
                Text(
                    when {
                        adState.isLoading && !adState.isReady -> "Loading Ad..."
                        else -> "Watch Ad (+credits)"
                    }
                )
            }
        }
    }
}

@Composable
private fun PromptOfDayCard(
    prompt: V2PromptDto?,
    mode: String?,
    cost: Int,
    onOpenPrompt: (String) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Prompt of the Day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(prompt?.title ?: "Today's featured prompt is loading.")
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = prompt?.teaserText ?: prompt?.shortPrompt.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mode?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                if (cost > 0) AssistChip(onClick = {}, label = { Text("$cost credits") })
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { prompt?.id?.let(onOpenPrompt) },
                enabled = prompt != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open prompt")
            }
        }
    }
}

@Composable
private fun ReferralCard(
    isLoggedIn: Boolean,
    code: String?,
    status: String?,
    referredCount: Int,
    rewardedCount: Int,
    pendingCount: Int,
    onShare: (String) -> Unit,
    onApply: () -> Unit,
    onClaimReward: () -> Unit,
) {
    val normalizedStatus = status?.uppercase()
    val statusText = when (normalizedStatus) {
        "PENDING" -> "Reward will unlock after your first premium unlock"
        "QUALIFIED" -> "Referral qualified. Claim your reward now."
        "REWARDED" -> "Reward credited to your wallet"
        else -> if (isLoggedIn) "Apply a referral code to earn bonus credits after your first premium unlock." else "Login to generate and claim referral rewards."
    }

    Card {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Referrals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(if (isLoggedIn) "My referral code: ${code ?: "Loading..."}" else "Login to generate and claim referral rewards.")
            Spacer(modifier = Modifier.height(8.dp))
            Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Rewards unlock after qualifying action (e.g., first premium unlock or first generation).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("Referred: $referredCount") })
                AssistChip(onClick = {}, label = { Text("Pending: $pendingCount") })
                AssistChip(onClick = {}, label = { Text("Rewarded: $rewardedCount") })
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { if (code != null) onShare(code) },
                    enabled = isLoggedIn && code != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share")
                }
                Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                    Text("Apply code")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onClaimReward,
                enabled = isLoggedIn && normalizedStatus == "QUALIFIED",
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (normalizedStatus) {
                        "REWARDED" -> "Reward claimed"
                        "QUALIFIED" -> "Claim reward"
                        else -> "Claim after qualifying"
                    }
                )
            }
        }
    }
}

@Composable
private fun PacksCard(
    isLoggedIn: Boolean,
    ownedCount: Int,
    packs: List<PackSummaryDto>,
    onOpenPacks: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Packs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("Active packs: ${packs.size} • Owned: $ownedCount")
            Spacer(modifier = Modifier.height(8.dp))
            packs.take(3).forEach { pack ->
                Text("• ${pack.name} (${pack.pricePoints} credits)")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onOpenPacks, modifier = Modifier.fillMaxWidth(), enabled = isLoggedIn) {
                Text("Browse packs")
            }
        }
    }
}

@Composable
private fun ProgressCard(
    level: Int,
    xp: Int,
    nextLevelXp: Int,
) {
    val progress = when {
        nextLevelXp <= 0 -> 0f
        xp <= 0 -> 0f
        xp >= nextLevelXp -> 1f
        else -> xp.toFloat() / nextLevelXp.toFloat()
    }
    Card {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Level $level")
            Spacer(modifier = Modifier.height(4.dp))
            Text("$xp / $nextLevelXp XP")
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}
