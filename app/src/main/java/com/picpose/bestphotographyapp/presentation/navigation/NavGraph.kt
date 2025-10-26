package com.picpose.bestphotographyapp.presentation.navigation

import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.picpose.bestphotographyapp.presentation.screens.*
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NavGraph(navController: NavHostController) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn = authViewModel.isLoggedIn.collectAsState().value
    val hasSkippedAuth = authViewModel.hasSkippedAuth.collectAsState().value

    // ✅ Choose start destination dynamically
    val startDestination = if (isLoggedIn || hasSkippedAuth) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn() + slideInVertically() },
        exitTransition = { fadeOut() + slideOutVertically() }
    ) {

        // 🏠 Home Screen
        composable(route = Screen.Home.route) {
            val homeVM: HomeViewModel = hiltViewModel()

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
                }
            )
        }

        // 🔎 Explore / Create / Rewards
        composable(route = Screen.Explore.route) {
            ExploreScreen(
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

        composable(route = Screen.Create.route) { CreateScreen() }
        composable(route = Screen.Rewards.route) { RewardsScreen() }

        // 👤 Profile Screen (FINAL)
        composable(route = Screen.Profile.route) {
            ProfileScreen(
                navController = navController, // ✅ pass it here
                onNavigateToEditProfile = {
                    navController.navigate(Screen.EditProfile.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) { launchSingleTop = true }
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }


        // ✏️ Edit Profile Screen (✅ with animation)
        composable(
            route = Screen.EditProfile.route,
            enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
            exitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() }
        ) {
            EditProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }

        // ⚙️ Settings Screen
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
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
                }
            )
        }

        // 📜 All AI Prompts
        composable(
            route = Screen.AllAIPrompts.route + "?category={category}",
            arguments = listOf(navArgument("category") {
                type = NavType.StringType
                defaultValue = "All"
            })
        ) { backStackEntry ->
            val initialCategory = backStackEntry.arguments?.getString("category") ?: "All"
            val aiPromptVM: AIPromptViewModel = hiltViewModel()

            AllAIPromptsScreen(
                viewModel = aiPromptVM,
                onBack = { navController.popBackStack() },
                onPromptClick = { promptId ->
                    navController.navigate(Screen.PromptDetail.createRoute(promptId)) { launchSingleTop = true }
                },
                initialCategory = initialCategory
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
            val aiPromptVM: AIPromptViewModel = hiltViewModel()

            if (promptId.isBlank()) {
                navController.popBackStack()
            } else {
                AIPromptDetailScreen(
                    promptId = promptId,
                    viewModel = aiPromptVM,
                    onBack = { navController.popBackStack() },
                    onPromptClick = { newPromptId ->
                        navController.navigate(Screen.PromptDetail.createRoute(newPromptId)) { launchSingleTop = true }
                    },
                    onTagClick = { tag ->
                        navController.navigate(Screen.TagPrompts.createRoute(tag)) { launchSingleTop = true }
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


        /*
        composable(route = Screen.Privacy.route) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }

        composable(route = Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        */

    }
}
