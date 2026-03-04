package com.picpose.bestphotographyapp.presentation.navigation

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.components.home.ViewAllPromptsScreen
import com.picpose.bestphotographyapp.presentation.screens.*
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModel
import com.picpose.bestphotographyapp.ui.prompts.PromptDetailV2Screen
import com.picpose.bestphotographyapp.ui.prompts.PromptsV2Screen
import com.picpose.bestphotographyapp.ui.packs.PackDetailsScreen
import com.picpose.bestphotographyapp.ui.packs.PacksListScreen
import com.picpose.bestphotographyapp.ui.rewards.RewardsScreenV3 as V3RewardsScreen

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NavGraph(navController: NavHostController, activity: Activity? = null) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn = authViewModel.isLoggedIn.collectAsState().value
    // ✅ Dynamic start destination
    val startDestination = if (isLoggedIn) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    // ✅ Global Back Handling — Go directly to Home
    val context = activity ?: return
    var lastBackPressTime by remember { mutableStateOf(0L) }

    BackHandler {
        val currentRoute = navController.currentBackStackEntry?.destination?.route

        if (currentRoute != Screen.Home.route && currentRoute != Screen.Login.route) {
            // Navigate directly to Home
            navController.popBackStack(Screen.Home.route, inclusive = false)
        } else {
            // Optional: exit on double press (to avoid accidental exits)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                activity.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(
                    context,
                    context.getString(R.string.press_back_again_to_exit),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ✅ Animated NavHost
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(350)) + slideInVertically(initialOffsetY = { it / 3 })
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it / 3 })
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { -it / 4 })
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { it / 4 })
        }
    ) {
        // 🏠 Home Screen
        composable(route = Screen.Home.route) {
            val homeVM: HomeViewModel = hiltViewModel()
            val authViewModel: AuthViewModel = hiltViewModel()
            val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

            HomeScreen(
                viewModel = homeVM,

                onNavigateToAllPrompts = {
                    navController.navigate(Screen.AllAIPrompts.route) { launchSingleTop = true }
                },

                onNavigateToFavorites = {
                    navController.navigate(Screen.AIPromptFavorites.route) { launchSingleTop = true }
                },

                onNavigateToCategory = { category ->
                    val safeCategory = Uri.encode(category.name)
                    navController.navigate("${Screen.AllAIPrompts.route}?category=$safeCategory") {
                        launchSingleTop = true
                    }
                },

                onNavigateToPostDetail = { post ->
                    val safeId = Uri.encode(post.id)
                    navController.navigate(Screen.PromptDetail.createRoute(safeId)) { launchSingleTop = true }
                },

                onNavigateToPromptDetail = { aiPrompt ->
                    val safeId = Uri.encode(aiPrompt.id)
                    navController.navigate(Screen.PromptDetail.createRoute(safeId)) { launchSingleTop = true }
                },

                onNavigateToGuidePostDetail = { guidePost ->
                    val safeId = Uri.encode(guidePost.id)
                    navController.navigate(Screen.GuidePostDetail.createRoute(safeId)) { launchSingleTop = true }
                },

                onNavigateToViewAll = { category ->
                    navController.navigate("viewAll/$category") { launchSingleTop = true }
                },

                onNavigateToEditProfile = {
                    // Only allow if logged in
                    if (isLoggedIn)
                        navController.navigate(Screen.EditProfile.route)
                    else
                        navController.navigate(Screen.Login.route)
                },
                onNavigateToExploreWithQuery = { query ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("home_search_query", query)
                    navController.navigate(Screen.Explore.route) { launchSingleTop = true }
                },

                // ⭐ REQUIRED NEW PARAMETER
                onRequestLogin = {
                    navController.navigate(Screen.Login.route) {
                        launchSingleTop = true
                    }
                }
            )
        }


        // 🔎 Explore Screen
        composable(route = Screen.Explore.route) {
            val incomingQuery = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>("home_search_query")

            LaunchedEffect(incomingQuery) {
                if (!incomingQuery.isNullOrBlank()) {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.remove<String>("home_search_query")
                }
            }

            ExploreScreen(
                initialSearchQuery = incomingQuery,
                onNavigateToPromptDetail = { aiPrompt ->
                    val safeId = Uri.encode(aiPrompt.id)
                    navController.navigate(Screen.PromptDetail.createRoute(safeId)) { launchSingleTop = true }
                },
                onNavigateToGuidePostDetail = { guidePost ->
                    val safeId = Uri.encode(guidePost.id)
                    navController.navigate(Screen.GuidePostDetail.createRoute(safeId)) { launchSingleTop = true }
                }
            )
        }

        // Other screens (Create, Rewards, Profile, Settings, etc.)
        composable(route = Screen.Create.route) { CreateScreen() }
        composable(route = Screen.Rewards.route) {
            V3RewardsScreen(
                onOpenPrompt = { promptId ->
                    navController.navigate(Screen.PromptDetail.createRoute(promptId)) { launchSingleTop = true }
                },
                onRequireLogin = {
                    navController.navigate(Screen.Login.route) { launchSingleTop = true }
                },
                onOpenPacks = {
                    navController.navigate(Screen.Packs.route) { launchSingleTop = true }
                }
            )
        }

        composable(route = Screen.Packs.route) {
            PacksListScreen(
                onBack = { navController.popBackStack() },
                onOpenPack = { packId ->
                    navController.navigate(Screen.PackDetail.createRoute(packId)) { launchSingleTop = true }
                }
            )
        }

        composable(
            route = Screen.PackDetail.route,
            arguments = listOf(navArgument("packId") { type = NavType.IntType })
        ) { backStackEntry ->
            val packId = backStackEntry.arguments?.getInt("packId") ?: 0
            if (packId <= 0) {
                navController.popBackStack()
            } else {
                PackDetailsScreen(
                    packId = packId,
                    onBack = { navController.popBackStack() },
                    onPromptClick = { promptId ->
                        navController.navigate(Screen.PromptDetail.createRoute(promptId)) { launchSingleTop = true }
                    },
                    onRequireLogin = {
                        navController.navigate(Screen.Login.route) { launchSingleTop = true }
                    }
                )
            }
        }

        composable(route = Screen.Profile.route) {
            ProfileScreen(
                navController = navController,
                onNavigateToEditProfile = { navController.navigate(Screen.EditProfile.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onNavigateToLogin = { navController.navigate(Screen.Login.route) { launchSingleTop = true } },
                onNavigateToAllPrompts = { navController.navigate(Screen.AllAIPrompts.route) { launchSingleTop = true } },
                onNavigateToFavorites = { navController.navigate(Screen.AIPromptFavorites.route) { launchSingleTop = true } },
                onLogout = {
                    authViewModel.logout {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        // ✏️ Edit Profile (smooth vertical animation)
        composable(
            route = Screen.EditProfile.route,
            enterTransition = { slideInVertically(initialOffsetY = { it / 2 }) + fadeIn() },
            exitTransition = { slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut() },
            popEnterTransition = { fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { -it / 4 }) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { it / 4 }) }
        ) {
            EditProfileScreen(
                onNavigateBack = { navController.popBackStack() },

                // ⭐ FIXED LOGIN REDIRECT
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.EditProfile.route) { inclusive = true }  // remove EditProfile from stack
                        launchSingleTop = true
                    }
                },

                onSaveSuccess = { navController.popBackStack() }
            )
        }


        // ⚙️ Settings Screen
        composable(
            route = Screen.Settings.route,
            enterTransition = { fadeIn(animationSpec = tween(350)) + slideInVertically(initialOffsetY = { it / 3 }) },
            exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it / 3 }) }
        ) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    authViewModel.logout {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onAccountDeleted = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // 🔐 Login Screen
        composable(route = Screen.Login.route) {
            LoginScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route) { launchSingleTop = true }
                },
                onNavigateToPrivacy = {
                    navController.navigate(Screen.Privacy.route) { launchSingleTop = true }
                },
                onNavigateToTerms = {
                    navController.navigate(Screen.Terms.route) { launchSingleTop = true }
                },
            )
        }

        composable(route = Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ResetPassword.route,
            arguments = listOf(navArgument("token") { type = NavType.StringType })
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token").orEmpty()
            ResetPasswordScreen(
                token = token,
                onBack = { navController.popBackStack() },
                onResetSuccessNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.VerifyEmail.route,
            arguments = listOf(navArgument("token") { type = NavType.StringType })
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token").orEmpty()
            VerifyEmailScreen(
                token = token,
                onBack = { navController.popBackStack() },
                onGoToProfile = {
                    navController.navigate(Screen.Profile.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // 📜 All AI Prompts
        composable(
            route = Screen.AllAIPrompts.route + "?category={category}",
            arguments = listOf(
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val initialCategory = backStackEntry.arguments
                ?.getString("category")
                ?.let { Uri.decode(it) }

            PromptsV2Screen(
                onBack = { navController.popBackStack() },
                onPromptClick = { promptId ->
                    navController.navigate(Screen.PromptDetail.createRoute(promptId)) { launchSingleTop = true }
                },
                initialCategory = initialCategory
            )
        }


        // 👇 NEW — View All Prompts (Trending / Featured / Popular)
        composable(
            route = "viewAll/{categoryType}",
            arguments = listOf(navArgument("categoryType") {
                type = NavType.StringType
                defaultValue = "Trending"
            })
        ) { backStackEntry ->
            val categoryType = backStackEntry.arguments?.getString("categoryType") ?: "Trending"
            val homeVM: HomeViewModel = hiltViewModel()

            ViewAllPromptsScreen(
                categoryType = categoryType,
                viewModel = homeVM,
                onBack = { navController.popBackStack() },
                onPromptClick = { promptId ->
                    navController.navigate(Screen.PromptDetail.createRoute(promptId)) { launchSingleTop = true }
                }
            )
        }



        // ❤️ Favorites
        composable(route = Screen.AIPromptFavorites.route) {
            val aiPromptVM: AIPromptViewModel = hiltViewModel()

            AIPromptFavoritesScreen(
                viewModel = aiPromptVM,
                onBack = { navController.popBackStack() },
                onPromptClick = { promptId ->
                    navController.navigate(Screen.PromptDetail.createRoute(promptId)) { launchSingleTop = true }
                },
                onNavigateToAllPrompts = {
                    navController.navigate(Screen.AllAIPrompts.route) { launchSingleTop = true }
                }
            )
        }

        // 🧠 Prompt Detail
        composable(
            route = Screen.PromptDetail.route,
            arguments = listOf(navArgument("promptId") { type = NavType.StringType })
        ) { backStackEntry ->
            val promptId = backStackEntry.arguments?.getString("promptId").orEmpty()

            if (promptId.isBlank()) {
                navController.popBackStack()
            } else {
                PromptDetailV2Screen(
                    promptId = promptId,
                    onBack = { navController.popBackStack() },
                    onRequireLogin = {
                        navController.navigate(Screen.Login.route) { launchSingleTop = true }
                    },
                    onOpenSubscribe = {
                        navController.navigate(Screen.Rewards.route) { launchSingleTop = true }
                    }
                )
            }
        }

        // 🏷 Tag Prompts
        composable(
            route = Screen.TagPrompts.route,
            arguments = listOf(navArgument(Screen.TagPrompts.ARG_TAG) { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedTag = backStackEntry.arguments?.getString(Screen.TagPrompts.ARG_TAG).orEmpty()
            val tag = Uri.decode(encodedTag)
            val aiPromptVM: AIPromptViewModel = hiltViewModel()

            TagPromptsScreen(
                tag = tag,
                viewModel = aiPromptVM,
                onBack = { navController.popBackStack() },
                onPromptClick = { promptId ->
                    navController.navigate(Screen.PromptDetail.createRoute(promptId)) { launchSingleTop = true }
                }
            )
        }

        // 📘 Guide Post Detail
        composable(
            route = Screen.GuidePostDetail.route,
            arguments = listOf(
                navArgument(Screen.GuidePostDetail.ARG_GUIDE_POST_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val guidePostId = backStackEntry.arguments?.getString(Screen.GuidePostDetail.ARG_GUIDE_POST_ID) ?: ""
            if (guidePostId.isBlank()) {
                navController.popBackStack()
            } else {
                GuideDetailScreen(
                    guidePostId = guidePostId,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // 📰 All Guide Posts
        composable(route = Screen.AllGuidePosts.route) {
            ExploreScreen()
        }

        // 🔐 Privacy Policy Screen
        composable(route = Screen.Privacy.route) {
            PrivacyPolicyScreen(onNavigateBack = { navController.popBackStack() })
        }

        // 📄 Terms & Conditions
        composable(route = Screen.Terms.route) {
            TermsScreen(onNavigateBack = { navController.popBackStack() })
        }

        // ℹ️ About
        composable(route = Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        //Help and Support
        composable(Screen.HelpAndSupportScreen.route) {
            HelpAndSupportScreen(onBack = { navController.popBackStack() })
        }


    }
}
