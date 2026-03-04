package com.picpose.bestphotographyapp.ui.prompts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.ads.RewardedAdManager
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptDetailV2Screen(
    promptId: String,
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    onOpenSubscribe: () -> Unit,
    viewModel: PromptDetailV2ViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity
    val rewardedAdManager = remember { RewardedAdManager() }
    val rewardedAdState by rewardedAdManager.uiState.collectAsState()

    LaunchedEffect(promptId) {
        viewModel.loadPrompt(promptId, forceRefresh = true)
    }

    LaunchedEffect(promptId) {
        rewardedAdManager.loadRewardedAd(context, AdsManager.KEY_REWARDED_AD)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prompt Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val prompt = uiState.prompt
        when {
            uiState.isLoading && prompt == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            prompt == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Prompt unavailable.")
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        AsyncImage(
                            model = prompt.imageUrl ?: prompt.imageUrl2,
                            contentDescription = prompt.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(24.dp),
                                ),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = prompt.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                prompt.category?.takeIf { it.isNotBlank() }?.let { category ->
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = prompt.shortPrompt.orEmpty(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (prompt.isLocked) {
                                        Icon(Icons.Default.Lock, contentDescription = null)
                                    } else {
                                        Icon(Icons.Default.Star, contentDescription = null)
                                    }
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = if (prompt.isLocked) "Premium prompt" else "Full prompt",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                if (prompt.isLocked) {
                                    LockedPromptPreview(prompt.teaserText.orEmpty())
                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (!isLoggedIn) {
                                        Text(
                                            text = "Login to unlock premium prompts with ads, credits, or tokens.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(onClick = onRequireLogin, modifier = Modifier.fillMaxWidth()) {
                                            Text("Login to Unlock")
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Button(
                                                onClick = {
                                                    val hostActivity = activity
                                                    if (hostActivity == null) {
                                                        viewModel.setMessage(context.getString(R.string.rewards_ad_requires_activity))
                                                    } else {
                                                        rewardedAdManager.showRewardedAd(
                                                            activity = hostActivity,
                                                            placementKey = AdsManager.KEY_REWARDED_AD,
                                                            onRewardEarned = { adRewardId ->
                                                                viewModel.unlockWithAd(
                                                                    promptId = prompt.id,
                                                                    adRewardId = adRewardId,
                                                                )
                                                            },
                                                            onUnavailable = { message ->
                                                                viewModel.setMessage(message)
                                                            },
                                                        )
                                                    }
                                                },
                                                enabled = !uiState.isUnlockingWithAd,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                                                Spacer(modifier = Modifier.size(8.dp))
                                                Text(
                                                    when {
                                                        uiState.isUnlockingWithAd -> "Unlocking..."
                                                        rewardedAdState.isLoading && !rewardedAdState.isReady -> context.getString(R.string.rewards_loading_reward_ad)
                                                        else -> "Watch Ad"
                                                    }
                                                )
                                            }
                                            Button(
                                                onClick = { viewModel.unlockWithPoints(prompt.id) },
                                                enabled = !uiState.isUnlockingWithPoints,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(
                                                    if (uiState.isUnlockingWithPoints) {
                                                        "Unlocking..."
                                                    } else {
                                                        "Unlock with ${prompt.premiumUnlockCostPoints} credits"
                                                    }
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.unlockWithToken(prompt.id) },
                                                enabled = !uiState.isUnlockingWithToken,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Icon(Icons.Default.WorkspacePremium, contentDescription = null)
                                                Spacer(modifier = Modifier.size(8.dp))
                                                Text(if (uiState.isUnlockingWithToken) "Checking token..." else "Use Unlock Token")
                                            }
                                            OutlinedButton(
                                                onClick = onOpenSubscribe,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text("Subscribe / Go Pro")
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = prompt.fullPrompt.orEmpty(),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { copyPrompt(context, prompt.fullPrompt.orEmpty()) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text("Copy Prompt")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedPromptPreview(teaser: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = teaser.ifBlank { "Unlock to view the full prompt." },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.blur(10.dp),
        )
    }
}

private fun copyPrompt(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("PicPose Prompt", text))
    Toast.makeText(context, "Prompt copied", Toast.LENGTH_SHORT).show()
}
