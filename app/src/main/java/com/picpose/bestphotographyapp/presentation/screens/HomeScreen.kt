package com.picpose.bestphotographyapp.presentation.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.ads.nativead.NativeAd
import com.picpose.bestphotographyapp.data.models.*
import com.picpose.bestphotographyapp.presentation.components.ads.*
import com.picpose.bestphotographyapp.presentation.components.home.*
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.StatsViewModel
import com.picpose.bestphotographyapp.core.utils.ShareUtils
import com.picpose.bestphotographyapp.presentation.ads.AdsLog
import com.picpose.bestphotographyapp.presentation.ads.AdsConfigState
import com.picpose.bestphotographyapp.presentation.ads.AdsManager
import com.picpose.bestphotographyapp.presentation.ads.LargeNativeAdCard
import com.picpose.bestphotographyapp.presentation.ads.NativeAdController
import com.picpose.bestphotographyapp.presentation.ads.NativeAdUiState
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch
import com.picpose.bestphotographyapp.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToAllPrompts: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onNavigateToPostDetail: (Post) -> Unit,
    onNavigateToPromptDetail: (AIPrompt) -> Unit,
    onNavigateToGuidePostDetail: (GuidePost) -> Unit,
    onNavigateToViewAll: (String) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onRequestLogin: () -> Unit        // ✅ NEW — navigation callback
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val localEngagementStates by viewModel.localEngagementStates.collectAsState()
    val statsViewModel: StatsViewModel = hiltViewModel()
    val coroutineScope = rememberCoroutineScope()

    val authViewModel: AuthViewModel = hiltViewModel()
    val currentUser by authViewModel.currentUser.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val hasSkippedAuth by authViewModel.hasSkippedAuth.collectAsState()

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val appOpenCount by settingsViewModel.appOpenCount.collectAsState()
    val permissionRequested by settingsViewModel.notificationPermissionRequested.collectAsState()
    val deniedAtOpen by settingsViewModel.notificationPermissionDeniedAtOpen.collectAsState()
    val lastPromptOpen by settingsViewModel.notificationPermissionLastPromptOpen.collectAsState()

    val isUserActive = isLoggedIn || hasSkippedAuth
    var hasCountedOpen by rememberSaveable { mutableStateOf(false) }
    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        settingsViewModel.setNotificationPermissionRequested(true)
        settingsViewModel.setNotificationPermissionLastPromptOpen(appOpenCount)
        if (isGranted) {
            settingsViewModel.setNotificationPermissionDeniedAtOpen(-1)
        } else {
            settingsViewModel.setNotificationPermissionDeniedAtOpen(appOpenCount)
        }
    }

    // Track app opens only when the user is on Home (logged in or skipped).
    LaunchedEffect(isUserActive) {
        if (isUserActive && !hasCountedOpen) {
            settingsViewModel.incrementAppOpenCount()
            hasCountedOpen = true
        }
    }

    // Ask permission only after Home is visible and user context is valid.
    LaunchedEffect(isUserActive, appOpenCount, permissionRequested, deniedAtOpen, lastPromptOpen) {
        if (!isUserActive) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) return@LaunchedEffect

        val retryGap = 4
        val canRetry = deniedAtOpen >= 0 && (appOpenCount - deniedAtOpen) >= retryGap
        val shouldPrompt = !permissionRequested || canRetry

        if (shouldPrompt && lastPromptOpen != appOpenCount) {
            // Slight delay to let users engage with Home content first.
            kotlinx.coroutines.delay(8_000)
            showPermissionDialog = true
        }
    }

    // Native Ad (single instance with explicit UI state)
    var nativeAdState by remember { mutableStateOf<NativeAdUiState>(NativeAdUiState.Disabled) }
    val adsConfigState by AdsManager.configState.collectAsState()
    val nativeAdController = remember { NativeAdController(placementKey = AdsManager.KEY_NATIVE_AD) }
    LaunchedEffect(adsConfigState) {
        when (adsConfigState) {
            is AdsConfigState.Loading -> {
                AdsLog.d(
                    AdsLog.TAG_UI,
                    "[AdsUI] screen=HomeScreen placement=${AdsManager.KEY_NATIVE_AD} action=wait reason=CONFIG_LOADING"
                )
            }

            is AdsConfigState.Error -> {
                nativeAdState = NativeAdUiState.Failed
                AdsLog.w(
                    AdsLog.TAG_UI,
                    "[AdsUI] screen=HomeScreen placement=${AdsManager.KEY_NATIVE_AD} action=skip reason=CONFIG_ERROR"
                )
            }

            is AdsConfigState.Ready -> {
                val canShowAds = AdsManager.canShowAds()
                AdsLog.i(
                    AdsLog.TAG_UI,
                    "[AdsUI] screen=HomeScreen placement=${AdsManager.KEY_NATIVE_AD} action=request canShowAds=$canShowAds"
                )
                if (!canShowAds) {
                    nativeAdState = NativeAdUiState.Disabled
                    AdsLog.i(
                        AdsLog.TAG_UI,
                        "[AdsUI] screen=HomeScreen placement=${AdsManager.KEY_NATIVE_AD} action=skip reason=global_gate"
                    )
                    return@LaunchedEffect
                }

                nativeAdState = NativeAdUiState.Loading
                nativeAdController.load(
                    context = context,
                    forceReload = false,
                    callbacks = object : NativeAdController.Callbacks {
                        override fun onLoaded(ad: NativeAd) {
                            AdsLog.i(
                                AdsLog.TAG_UI,
                                "[AdsUI] screen=HomeScreen placement=${AdsManager.KEY_NATIVE_AD} action=loaded"
                            )
                            nativeAdState = NativeAdUiState.Loaded(ad)
                        }

                        override fun onFailed(error: com.google.android.gms.ads.LoadAdError) {
                            AdsLog.w(
                                AdsLog.TAG_UI,
                                "[AdsUI] screen=HomeScreen placement=${AdsManager.KEY_NATIVE_AD} action=failed domain=${error.domain} code=${error.code} message=${error.message}"
                            )
                            nativeAdState = NativeAdUiState.Failed
                        }

                        override fun onUnavailable(reason: String) {
                            AdsLog.i(
                                AdsLog.TAG_UI,
                                "[AdsUI] screen=HomeScreen placement=${AdsManager.KEY_NATIVE_AD} action=unavailable reason=$reason"
                            )
                            nativeAdState = if (reason == "ADS_DISABLED") {
                                NativeAdUiState.Disabled
                            } else {
                                NativeAdUiState.Failed
                            }
                        }
                    }
                )
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            AdsLog.d(
                AdsLog.TAG_UI,
                "[AdsUI] screen=HomeScreen placement=${AdsManager.KEY_NATIVE_AD} action=dispose"
            )
            nativeAdController.clear()
            nativeAdState = NativeAdUiState.Disabled
        }
    }


    var isRefreshing by remember { mutableStateOf(false) }
    var currentTipIndex by remember { mutableIntStateOf(0) }

    // Initial shimmer loading logic
    val isCompletelyEmpty =
        uiState.aiPrompts.isEmpty() &&
                uiState.trendingPosts.isEmpty() &&
                uiState.recentPosts.isEmpty() &&
                uiState.categories.isEmpty() &&
                uiState.guidePosts.isEmpty()

    val showShimmer = uiState.isLoading && isCompletelyEmpty

    if (showPermissionDialog) {
        NotificationPermissionDialog(
            onAllow = {
                showPermissionDialog = false
                settingsViewModel.setNotificationPermissionLastPromptOpen(appOpenCount)
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
            onCancel = {
                showPermissionDialog = false
                settingsViewModel.setNotificationPermissionRequested(true)
                settingsViewModel.setNotificationPermissionDeniedAtOpen(appOpenCount)
                settingsViewModel.setNotificationPermissionLastPromptOpen(appOpenCount)
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            HomeTopBar(
                titleText = stringResource(R.string.app_name),
                initialSearch = "",
                userImage = currentUser?.displayProfilePicture,
                onQueryChanged = { query -> viewModel.onSearchChanged(query) },
                onSearchClick = { query -> viewModel.onSearchChanged(query) },

                // ⭐ Updated correct logic here
                onProfileClick = {
                    if (isLoggedIn) onNavigateToEditProfile()
                    else onRequestLogin()
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->

        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refresh()
                    isRefreshing = false
                }
            }
        ) {

            if (showShimmer) {
                ShimmerLoadingHomeScreen()
            } else {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = 24.dp
                    )
                ) {

                    // Welcome Header
                    item { AnimatedWelcomeHeader() }

                    // Daily Tip
                    if (uiState.dailyTips.isNotEmpty()) {
                        item {
                            val tip = uiState.dailyTips[currentTipIndex % uiState.dailyTips.size]
                            AnimatedDailyTipCard(
                                tip = tip.tip,
                                onNextTip = {
                                    currentTipIndex = (currentTipIndex + 1) % uiState.dailyTips.size
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // AI Prompts Section
                    if (uiState.aiPrompts.isNotEmpty()) {
                        item {
                            AIPromptSectionHeader(
                                viewModel = statsViewModel,
                                onNavigateToAllPrompts = onNavigateToAllPrompts,
                                onNavigateToFavorites = onNavigateToFavorites,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    // Trending & Featured & Popular
                    if (uiState.trendingPosts.isNotEmpty() || uiState.featuredPosts.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.community_highlights_title),
                                subtitle = stringResource(R.string.community_highlights_subtitle),
                                icon = Icons.Default.Star,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
                            )
                        }

                        item {
                            TrendingFeaturedAndPopularRow(
                                trendingPosts = uiState.trendingPosts,
                                featuredPosts = uiState.featuredPosts,
                                popularPosts = uiState.popularPosts,
                                onPostClick = onNavigateToPostDetail,
                                //onLikeClick = { viewModel.onPostLikeClicked(it.id) },
                                onShareClick = { post ->
                                    coroutineScope.launch {

                                        // 🔍 Try to resolve full AI Prompt first
                                        val promptMatch = uiState.aiPrompts
                                            .firstOrNull { it.id.trim() == post.id.trim() }

                                        if (promptMatch != null) {
                                            // ✅ FULL PROMPT SHARE (same as detail screen)
                                            ShareUtils.sharePrompt(
                                                context = context,
                                                promptText =
                                                    promptMatch.fullPrompt
                                                        ?: promptMatch.shortPrompt
                                                        ?: promptMatch.title,
                                                imageUrl = promptMatch.imageUrl ?: promptMatch.imageUrl2
                                            )
                                        } else {
                                            // 🔁 Fallback → normal post share
                                            ShareUtils.sharePrompt(
                                                context = context,
                                                promptText =
                                                    post.description
                                                        .takeIf { it.isNotBlank() }
                                                        ?: post.title,
                                                imageUrl = post.image
                                            )
                                        }
                                    }
                                },
                                onViewAllClick = { category ->
                                    onNavigateToViewAll(category)
                                }
                            )
                        }
                    }

                    // Banner Ad
                    /*if (AdsManager.canShowAds()) item {
                        AdmobBannerAd(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            placementKey = AdsManager.KEY_HOME_BANNER
                        )
                    }*/

                    when (val state = nativeAdState) {
                        is NativeAdUiState.Loading -> item {
                            LaunchedEffect(Unit) {
                                AdsLog.i(
                                    AdsLog.TAG_UI,
                                    "[AdsUI] screen=HomeScreen placement=${AdsManager.KEY_NATIVE_AD} action=render state=Loading"
                                )
                            }
                            LargeAdShimmerPlaceholder()
                        }

                        is NativeAdUiState.Loaded -> item {
                            LaunchedEffect(Unit) {
                                AdsLog.i(
                                    AdsLog.TAG_UI,
                                    "[AdsUI] screen=HomeScreen placement=${AdsManager.KEY_NATIVE_AD} action=render state=Loaded"
                                )
                            }
                            LargeNativeAdCard(
                                nativeAd = state.ad,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                        }

                        NativeAdUiState.Failed,
                        NativeAdUiState.Disabled -> Unit
                    }

                    // Categories
                    if (uiState.categories.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.categories_title),
                                subtitle = stringResource(R.string.categories_subtitle),
                                icon = Icons.Default.Category,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 6.dp)
                            )
                        }
                        item {
                            CategoriesRow(
                                categories = uiState.categories,
                                onCategoryClick = onNavigateToCategory,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Recent Posts
                    if (uiState.recentPosts.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.recent_posts_title),
                                subtitle = stringResource(R.string.recent_posts_subtitle),
                                icon = Icons.Default.Schedule,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
                            )
                        }

                        items(uiState.recentPosts.take(5)) { post ->
                            val local = localEngagementStates[post.id]

                            RecentPostItem(
                                post = post,
                                localEngagement = local,
                                onClick = {
                                    val match = uiState.aiPrompts.find { it.id.trim() == post.id.trim() }
                                    if (match != null)
                                        onNavigateToPromptDetail(match)
                                    else
                                        onNavigateToPostDetail(post)
                                },
                                onLikeClick = {
                                    viewModel.onPostLikeClicked(post.id)
                                },
                                onShareClick = {
                                    coroutineScope.launch {

                                        // 1️⃣ Try to resolve full AI Prompt first
                                        val promptMatch = uiState.aiPrompts
                                            .firstOrNull { it.id.trim() == post.id.trim() }

                                        if (promptMatch != null) {
                                            // ✅ SHARE FULL AI PROMPT (same as detail screen)
                                            ShareUtils.sharePrompt(
                                                context = context,
                                                promptText =
                                                    promptMatch.fullPrompt
                                                        ?: promptMatch.shortPrompt
                                                        ?: promptMatch.title,
                                                imageUrl = promptMatch.imageUrl ?: promptMatch.imageUrl2
                                            )
                                        } else {
                                            // 2️⃣ Fallback → normal Post sharing
                                            ShareUtils.sharePrompt(
                                                context = context,
                                                promptText =
                                                    post.description
                                                        .takeIf { it.isNotBlank() }
                                                        ?: post.title,
                                                imageUrl = post.image
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                    }

                    // Guides
                    if (uiState.guidePosts.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.photography_guides_title),
                                subtitle = stringResource(R.string.photography_guides_subtitle),
                                icon = Icons.Default.Book,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)
                            )
                        }

                        item {
                            GuidePostsRow(
                                guidePosts = uiState.guidePosts,
                                onGuidePostClick = onNavigateToGuidePostDetail,
                                onLikeClick = { viewModel.toggleGuidePostLike(it.id) },
                                onShareClick = { viewModel.shareGuidePost(context, it) }
                                //modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    //item { AdmobInterstitialTrigger() }
                }
            }
        }
    }
}



/* ───────────────────────────────
 *  Simple shimmer placeholder
 *  (replace with Modifier.placeholder if desired)
 *  ─────────────────────────────── */
@Composable
fun ShimmerLoadingHomeScreen() {
    val transition = rememberInfiniteTransition(label = "home_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaAnim"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        userScrollEnabled = false
    ) {
        // Simulate 3 main sections
        items(5) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray.copy(alpha = alpha))
                    .height(180.dp)
            ) {}
        }
    }
}

@Composable
private fun NotificationPermissionDialog(
    onAllow: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = stringResource(R.string.notification_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = buildAnnotatedString {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(stringResource(R.string.notification_message_header))
                    pop()

                    pushStyle(SpanStyle(fontStyle = FontStyle.Normal))
                    append(stringResource(R.string.notification_message_line1))
                    pop()

                    pushStyle(SpanStyle(fontStyle = FontStyle.Normal))
                    append(stringResource(R.string.notification_message_line2))
                    pop()

                    pushStyle(SpanStyle(fontStyle = FontStyle.Normal))
                    append(stringResource(R.string.notification_message_line3))

                    pushStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(stringResource(R.string.notification_note_title))
                    pop()

                    append(stringResource(R.string.notification_note_body_prefix)+" ")
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(stringResource(R.string.settings))
                    pop()
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.not_now))
            }
        }
    )
}
