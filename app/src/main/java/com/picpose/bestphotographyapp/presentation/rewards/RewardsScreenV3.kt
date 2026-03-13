/**
 * ---
 * File: RewardsScreenV3.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Contains a Compose screen or dialog used in newer feature modules.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * UI (Compose) -> ViewModel -> Repository -> Local/Remote Data Source -> Room/API
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.rewards

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.picpose.bestphotographyapp.BuildConfig
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.service.ads.RewardedAdManager
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar
import com.picpose.bestphotographyapp.components.ads.AdsManager
import com.picpose.bestphotographyapp.presentation.rewards.components.EarnCard
import com.picpose.bestphotographyapp.presentation.rewards.components.PacksRow
import com.picpose.bestphotographyapp.presentation.rewards.components.PromptOfDayCard
import com.picpose.bestphotographyapp.presentation.rewards.components.ReferralCard
import com.picpose.bestphotographyapp.presentation.rewards.components.RewardsHeader
import com.picpose.bestphotographyapp.presentation.rewards.components.StreakStepper
import com.picpose.bestphotographyapp.presentation.rewards.components.WalletCard
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreenV3(
    onOpenPrompt: (String) -> Unit,
    onRequireLogin: () -> Unit,
    onOpenPacks: () -> Unit,
    viewModel: RewardsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val rewardedAdManager = remember { RewardedAdManager() }
    val rewardedAdState by rewardedAdManager.uiState.collectAsState()
    val displayedPoints by animateIntAsState(uiState.pointsBalance, label = "points_counter")

    var showApplySheet by rememberSaveable { mutableStateOf(false) }
    var applyCode by rememberSaveable { mutableStateOf("") }
    var showConfetti by remember { mutableStateOf(false) }
    var showCoinBurst by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(isLoggedIn) {
        viewModel.loadRewards(forceRefresh = true)
    }

    LaunchedEffect(Unit) {
        rewardedAdManager.loadRewardedAd(context, AdsManager.KEY_REWARDED_AD)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RewardsUiEvent.ClaimSuccess -> {
                    showConfetti = true
                    snackbarHostState.showSnackbar("+${event.pointsAdded} credits added")
                    delay(1200)
                    showConfetti = false
                }
                is RewardsUiEvent.AdRewardSuccess -> {
                    showCoinBurst = true
                    snackbarHostState.showSnackbar("+${event.pointsAdded} ad credits added")
                    delay(900)
                    showCoinBurst = false
                }
                is RewardsUiEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.setStatusMessage(null)
        }
    }

    if (showApplySheet) {
        ModalBottomSheet(onDismissRequest = { showApplySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.referral_apply_code), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = applyCode,
                    onValueChange = { applyCode = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.referral_code_label)) },
                    supportingText = {
                        Text(stringResource(R.string.referral_qualify_helper))
                    },
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        showApplySheet = false
                        viewModel.applyReferralCode(applyCode.trim())
                        applyCode = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = applyCode.isNotBlank() && !uiState.isApplyingCode,
                ) {
                    Text(if (uiState.isApplyingCode) stringResource(R.string.referral_applying_code) else stringResource(R.string.referral_apply_code))
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { showApplySheet = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PicPoseTopAppBar(
                title = stringResource(R.string.rewards),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Crossfade(targetState = uiState.isLoading, label = "rewards_loading_state") { loading ->
                if (loading) {
                    RewardsLoadingSkeleton()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            RewardsHeader(
                                level = uiState.level,
                                xp = uiState.xp,
                                nextLevelXp = uiState.nextLevelXp,
                                displayedPoints = displayedPoints,
                                isLoggedIn = isLoggedIn,
                                onLoginClick = onRequireLogin,
                            )
                        }
                        item {
                            WalletCard(
                                pointsBalance = uiState.pointsBalance,
                                displayedPoints = displayedPoints,
                                tokenBalances = uiState.tokenBalances,
                                onQuickClaim = {
                                    if (isLoggedIn) viewModel.claimDailyLogin() else onRequireLogin()
                                },
                                onQuickWatchAd = {
                                    val hostActivity = activity
                                    when {
                                        !isLoggedIn -> onRequireLogin()
                                        hostActivity == null -> viewModel.setStatusMessage(context.getString(R.string.rewards_ad_requires_activity))
                                        else -> rewardedAdManager.showRewardedAd(
                                            activity = hostActivity,
                                            placementKey = AdsManager.KEY_REWARDED_AD,
                                            onRewardEarned = { adRewardId ->
                                                viewModel.rewardAdPoints(adRewardId)
                                            },
                                            onUnavailable = { msg -> viewModel.setStatusMessage(msg) },
                                        )
                                    }
                                },
                                isLoggedIn = isLoggedIn,
                            )
                        }
                        if (!isLoggedIn) {
                            item {
                                LoginRequiredCard(onRequireLogin = onRequireLogin)
                            }
                        }
                        item {
                            StreakStepper(
                                streakCount = uiState.streakCount,
                                todayClaimed = uiState.todayClaimed,
                                rewardsSchedule = uiState.rewardsSchedule,
                                isLoggedIn = isLoggedIn,
                                isClaiming = uiState.isClaimingReward,
                                onClaim = {
                                    if (isLoggedIn) viewModel.claimDailyLogin() else onRequireLogin()
                                },
                            )
                        }
                        item {
                            EarnCard(
                                isLoggedIn = isLoggedIn,
                                adState = rewardedAdState,
                                adRewardedToday = uiState.adRewardedToday,
                                adDailyCount = uiState.adDailyCount,
                                adDailyCap = uiState.adDailyCap,
                                onWatchAd = {
                                    val hostActivity = activity
                                    when {
                                        !isLoggedIn -> onRequireLogin()
                                        hostActivity == null -> viewModel.setStatusMessage(context.getString(R.string.rewards_ad_requires_activity))
                                        else -> rewardedAdManager.showRewardedAd(
                                            activity = hostActivity,
                                            placementKey = AdsManager.KEY_REWARDED_AD,
                                            onRewardEarned = { adRewardId ->
                                                viewModel.rewardAdPoints(adRewardId)
                                            },
                                            onUnavailable = { msg -> viewModel.setStatusMessage(msg) },
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
                                onUnlockDiscount = onOpenPrompt,
                            )
                        }
                        item {
                            ReferralCard(
                                isLoggedIn = isLoggedIn,
                                code = uiState.referralCode,
                                statusLabel = uiState.referralStatusLabel,
                                hasAppliedCode = uiState.hasAppliedReferralCode,
                                canClaimReward = uiState.canClaimReferralReward,
                                isRewardClaimed = uiState.isReferralRewardClaimed,
                                referredCount = uiState.referralReferredCount,
                                pendingCount = uiState.referralPendingCount,
                                rewardedCount = uiState.referralRewardedCount,
                                isApplyingCode = uiState.isApplyingCode,
                                onCopyCode = { code ->
                                    clipboardManager.setText(AnnotatedString(code))
                                    viewModel.setStatusMessage(context.getString(R.string.referral_code_copied))
                                },
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
                                onOpenApplyCode = {
                                    if (isLoggedIn) showApplySheet = true else onRequireLogin()
                                },
                                onClaimReward = {
                                    if (isLoggedIn) viewModel.claimReferralReward() else onRequireLogin()
                                },
                            )
                        }
                        item {
                            PacksRow(
                                packs = uiState.packs,
                                ownedCount = uiState.ownedPackCount,
                                isLoggedIn = isLoggedIn,
                                onOpenPacks = {
                                    if (isLoggedIn) onOpenPacks() else onRequireLogin()
                                },
                            )
                        }
                    }
                }
            }
        }

        CelebrationOverlay(
            visible = showConfetti,
            rawRes = R.raw.confetti,
            fallbackIcon = Icons.Default.AutoAwesome,
            tint = MaterialTheme.colorScheme.tertiary,
        )
        CelebrationOverlay(
            visible = showCoinBurst,
            rawRes = R.raw.coin_burst,
            fallbackIcon = Icons.Default.MonetizationOn,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LoginRequiredCard(onRequireLogin: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                Text("  ${stringResource(R.string.rewards_login_prompt)}", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onRequireLogin, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.login))
            }
        }
    }
}

@Composable
private fun RewardsLoadingSkeleton() {
    val alpha by rememberInfiniteTransition(label = "rewards_skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "skeleton_alpha",
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(6) { _ ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
private fun CelebrationOverlay(
    visible: Boolean,
    rawRes: Int,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
) {
    AnimatedVisibility(visible = visible, modifier = Modifier.fillMaxSize()) {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
        val progress by animateLottieCompositionAsState(
            composition = composition,
            isPlaying = visible,
            iterations = 1,
            speed = 1.15f,
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )
            } else {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .padding(top = 36.dp)
                        .size(78.dp),
                )
            }
        }
    }
}
