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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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
import com.picpose.bestphotographyapp.presentation.search.SearchMatchers
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
    onNavigateToExploreWithQuery: (String) -> Unit,
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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val normalizedSearchQuery = remember(searchQuery) { SearchMatchers.normalizeQuery(searchQuery) }
    val hasActiveSearch = normalizedSearchQuery.isNotBlank()
    val quickPromptResults = remember(uiState.aiPrompts, normalizedSearchQuery) {
        uiState.aiPrompts.filter { SearchMatchers.matchesAIPrompt(it, normalizedSearchQuery) }.take(3)
    }
    val quickGuideResults = remember(uiState.guidePosts, normalizedSearchQuery) {
        uiState.guidePosts.filter { SearchMatchers.matchesGuidePost(it, normalizedSearchQuery) }.take(3)
    }
    val quickRecentPostResults = remember(uiState.recentPosts, normalizedSearchQuery) {
        uiState.recentPosts.filter { SearchMatchers.matchesRecentPost(it, normalizedSearchQuery) }.take(3)
    }
    val quickCategoryResults = remember(uiState.categories, normalizedSearchQuery) {
        uiState.categories.filter { SearchMatchers.matchesCategory(it, normalizedSearchQuery) }.take(6)
    }
    val hasQuickSearchResults = quickPromptResults.isNotEmpty() ||
        quickGuideResults.isNotEmpty() ||
        quickRecentPostResults.isNotEmpty() ||
        quickCategoryResults.isNotEmpty()

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
            kotlinx.coroutines.delay(5_000) //Notification Dialog Delay Timer
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


    var currentTipIndex by remember { mutableIntStateOf(0) }
    var showNetworkBannerAfterDelay by remember { mutableStateOf(false) }
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }

    val anyCriticalLoading = uiState.isAnyCriticalLoading
    val anyCriticalError = uiState.hasAnyCriticalError
    val shouldShowLoadingBanner = showNetworkBannerAfterDelay && anyCriticalLoading
    val shouldShowErrorBanner = anyCriticalError
    val shouldShowBanner = shouldShowLoadingBanner || shouldShowErrorBanner

    LaunchedEffect(anyCriticalLoading) {
        if (anyCriticalLoading) {
            kotlinx.coroutines.delay(800)
            if (uiState.isAnyCriticalLoading) {
                showNetworkBannerAfterDelay = true
            }
        } else {
            showNetworkBannerAfterDelay = false
        }
    }

    LaunchedEffect(anyCriticalError) {
        if (anyCriticalError) {
            bannerDismissed = false
        }
    }

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
                showSearchAction = false,
                userImage = currentUser?.displayProfilePicture,
                onQueryChanged = {},
                onSearchClick = {},

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
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                viewModel.refreshHome()
            }
        ) {
            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = 24.dp
                    )
            ) {
                    if (shouldShowBanner && (!bannerDismissed || anyCriticalLoading)) {
                        item {
                            HomeNetworkStatusBanner(
                                isLoading = anyCriticalLoading,
                                hasError = anyCriticalError,
                                onDismiss = { if (!anyCriticalLoading) bannerDismissed = true },
                                onRetry = { viewModel.refreshHome() },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                    item {
                        HomeQuickSearchBar(
                            query = searchQuery,
                            onQueryChange = viewModel::onSearchChanged,
                            onSearchAction = { onNavigateToExploreWithQuery(normalizedSearchQuery) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (hasActiveSearch) {
                        if (hasQuickSearchResults) {
                            item {
                                HomeQuickSearchResultsCard(
                                    prompts = quickPromptResults,
                                    guides = quickGuideResults,
                                    posts = quickRecentPostResults,
                                    categories = quickCategoryResults,
                                    query = normalizedSearchQuery,
                                    onPromptClick = onNavigateToPromptDetail,
                                    onGuideClick = onNavigateToGuidePostDetail,
                                    onPostClick = { post ->
                                        val match = uiState.aiPrompts.firstOrNull { it.id.trim() == post.id.trim() }
                                        if (match != null) onNavigateToPromptDetail(match) else onNavigateToPostDetail(post)
                                    },
                                    onCategoryClick = onNavigateToCategory,
                                    onSeeAllClick = { onNavigateToExploreWithQuery(normalizedSearchQuery) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        } else {
                            item {
                                HomeSearchEmptyState(
                                    onSeeAllClick = { onNavigateToExploreWithQuery(normalizedSearchQuery) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }

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
                    item {
                        SectionHeader(
                            title = stringResource(R.string.community_highlights_title),
                            subtitle = stringResource(R.string.community_highlights_subtitle),
                            icon = Icons.Default.Star,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
                        )
                    }

                    when {
                        (uiState.isTrendingLoading || uiState.isFeaturedLoading || uiState.isPopularLoading) &&
                            uiState.trendingPosts.isEmpty() &&
                            uiState.featuredPosts.isEmpty() &&
                            uiState.popularPosts.isEmpty() -> {
                            item { CommunityHighlightsLoadingRow() }
                        }

                        uiState.trendingPosts.isNotEmpty() || uiState.featuredPosts.isNotEmpty() || uiState.popularPosts.isNotEmpty() -> {
                            item {
                                TrendingFeaturedAndPopularRow(
                                    trendingPosts = uiState.trendingPosts,
                                    featuredPosts = uiState.featuredPosts,
                                    popularPosts = uiState.popularPosts,
                                    onPostClick = onNavigateToPostDetail,
                                    onShareClick = { post ->
                                        coroutineScope.launch {
                                            val promptMatch = uiState.aiPrompts
                                                .firstOrNull { it.id.trim() == post.id.trim() }

                                            if (promptMatch != null) {
                                                ShareUtils.sharePrompt(
                                                    context = context,
                                                    promptText =
                                                        promptMatch.fullPrompt
                                                            ?: promptMatch.shortPrompt
                                                            ?: promptMatch.title,
                                                    imageUrl = promptMatch.imageUrl ?: promptMatch.imageUrl2
                                                )
                                            } else {
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

                        !uiState.trendingError.isNullOrBlank() || !uiState.featuredError.isNullOrBlank() || !uiState.popularError.isNullOrBlank() -> {
                            item {
                                SectionErrorCard(
                                    message = stringResource(R.string.section_load_error_message, stringResource(R.string.community_highlights_title)),
                                    onRetry = { viewModel.loadTrendingFeaturedAndPopularPosts() }
                                )
                            }
                        }

                        uiState.trendingLoadedOnce || uiState.featuredLoadedOnce || uiState.popularLoadedOnce -> {
                            item {
                                SectionEmptyCard(
                                    title = stringResource(R.string.no_community_highlights_yet),
                                    body = stringResource(R.string.check_back_later_for_new_content)
                                )
                            }
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
                    item {
                        SectionHeader(
                            title = stringResource(R.string.categories_title),
                            subtitle = stringResource(R.string.categories_subtitle),
                            icon = Icons.Default.Category,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 6.dp)
                        )
                    }
                    when {
                        uiState.isCategoriesLoading && uiState.categories.isEmpty() -> item {
                            CategoriesLoadingRow()
                        }
                        uiState.categories.isNotEmpty() -> item {
                            CategoriesRow(
                                categories = uiState.categories,
                                onCategoryClick = onNavigateToCategory,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        !uiState.categoriesError.isNullOrBlank() -> item {
                            SectionErrorCard(
                                message = stringResource(R.string.section_load_error_message, stringResource(R.string.categories_title)),
                                onRetry = { viewModel.refreshCategories() }
                            )
                        }
                        uiState.categoriesLoadedOnce -> item {
                            SectionEmptyCard(
                                title = stringResource(R.string.no_categories_available_yet),
                                body = stringResource(R.string.check_back_later_for_new_content)
                            )
                        }
                    }

                    // Recent Posts
                    item {
                        SectionHeader(
                            title = stringResource(R.string.recent_posts_title),
                            subtitle = stringResource(R.string.recent_posts_subtitle),
                            icon = Icons.Default.Schedule,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
                        )
                    }
                    when {
                        uiState.isRecentPostsLoading && uiState.recentPosts.isEmpty() -> item {
                            RecentPostsLoadingList()
                        }
                        uiState.recentPosts.isNotEmpty() -> {
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
                                            val promptMatch = uiState.aiPrompts
                                                .firstOrNull { it.id.trim() == post.id.trim() }

                                            if (promptMatch != null) {
                                                ShareUtils.sharePrompt(
                                                    context = context,
                                                    promptText =
                                                        promptMatch.fullPrompt
                                                            ?: promptMatch.shortPrompt
                                                            ?: promptMatch.title,
                                                    imageUrl = promptMatch.imageUrl ?: promptMatch.imageUrl2
                                                )
                                            } else {
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
                        !uiState.recentPostsError.isNullOrBlank() -> item {
                            SectionErrorCard(
                                message = stringResource(R.string.section_load_error_message, stringResource(R.string.recent_posts_title)),
                                onRetry = { viewModel.refreshRecentPosts() }
                            )
                        }
                        uiState.recentPostsLoadedOnce -> item {
                            SectionEmptyCard(
                                title = stringResource(R.string.no_recent_posts_available_yet),
                                body = stringResource(R.string.check_back_later_for_new_content)
                            )
                        }
                    }

                    // Guides
                    item {
                        SectionHeader(
                            title = stringResource(R.string.photography_guides_title),
                            subtitle = stringResource(R.string.photography_guides_subtitle),
                            icon = Icons.Default.Book,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)
                        )
                    }

                    when {
                        uiState.isGuideLoading && uiState.guidePosts.isEmpty() -> item {
                            GuidesLoadingRow()
                        }

                        uiState.guideError != null -> item {
                            GuideErrorCard(
                                error = uiState.guideError.orEmpty(),
                                onRetry = { viewModel.refreshGuides() }
                            )
                        }

                        uiState.guidePosts.isNotEmpty() -> item {
                            GuidePostsRow(
                                guidePosts = uiState.guidePosts,
                                onGuidePostClick = onNavigateToGuidePostDetail
                            )
                        }

                        uiState.guideLoadedOnce -> item {
                            GuideEmptyCard()
                        }
                    }

                    //item { AdmobInterstitialTrigger() }
                }
        }
    }
}

@Composable
private fun HomeQuickSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search)
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear)
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = stringResource(R.string.search_prompts_guides_placeholder),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
                onSearchAction()
            }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    )
}

@Composable
private fun HomeQuickSearchResultsCard(
    prompts: List<AIPrompt>,
    guides: List<GuidePost>,
    posts: List<Post>,
    categories: List<Category>,
    query: String,
    onPromptClick: (AIPrompt) -> Unit,
    onGuideClick: (GuidePost) -> Unit,
    onPostClick: (Post) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.home_search_results_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "\"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            prompts.forEach { prompt ->
                HomeSearchResultRow(
                    title = prompt.title,
                    subtitle = prompt.category?.takeIf { it.isNotBlank() },
                    imageUrl = prompt.imageUrl ?: prompt.imageUrl2,
                    onClick = { onPromptClick(prompt) }
                )
                HorizontalDivider()
            }

            guides.forEach { guide ->
                HomeSearchResultRow(
                    title = guide.title,
                    subtitle = stringResource(R.string.photography_guides_title),
                    imageUrl = guide.image.ifBlank { guide.imageUrl },
                    onClick = { onGuideClick(guide) }
                )
                HorizontalDivider()
            }

            posts.forEach { post ->
                HomeSearchResultRow(
                    title = post.title,
                    subtitle = stringResource(R.string.recent_posts_title),
                    imageUrl = post.image.ifBlank { post.image2 },
                    onClick = { onPostClick(post) }
                )
                HorizontalDivider()
            }

            if (categories.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.categories_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        AssistChip(
                            onClick = { onCategoryClick(category) },
                            label = { Text(category.name) }
                        )
                    }
                }
            }

            TextButton(
                onClick = onSeeAllClick,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.home_search_view_all))
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun HomeSearchResultRow(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SearchResultThumbnail(
            imageUrl = imageUrl,
            contentDescription = title
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchResultThumbnail(
    imageUrl: String?,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HomeSearchEmptyState(
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.home_search_no_results_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.home_search_no_results_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onSeeAllClick,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.home_search_view_all))
            }
        }
    }
}

@Composable
private fun HomeNetworkStatusBanner(
    isLoading: Boolean,
    hasError: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (hasError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            }

            Text(
                text = when {
                    hasError -> stringResource(R.string.home_offline_limited_content)
                    isLoading -> stringResource(R.string.home_loading_content_banner)
                    else -> stringResource(R.string.home_checking_connection_banner)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )

            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }

            if (!isLoading) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dismiss)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun SectionEmptyCard(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommunityHighlightsLoadingRow() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(3) {
            Card(
                modifier = Modifier
                    .width(230.dp)
                    .height(170.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
private fun CategoriesLoadingRow() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(8) {
            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .width(86.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {}
        }
    }
}

@Composable
private fun RecentPostsLoadingList() {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        repeat(3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(98.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
private fun GuidesLoadingRow() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(3) {
            Card(
                modifier = Modifier
                    .width(220.dp)
                    .height(140.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
private fun GuideErrorCard(
    error: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (error.isBlank()) {
                    stringResource(R.string.section_load_error_message, stringResource(R.string.photography_guides_title))
                } else {
                    stringResource(R.string.section_load_error_message, stringResource(R.string.photography_guides_title))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun GuideEmptyCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.no_guides_available_yet),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.check_back_later_for_new_content),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
