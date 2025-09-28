package com.picpose.bestphotographyapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import com.picpose.bestphotographyapp.presentation.screens.*
import com.picpose.bestphotographyapp.presentation.splash.SplashViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModelFactory

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // Splash
        composable(route = Screen.Splash.route) {
            val splashVM: SplashViewModel = viewModel() // if you have factory, pass it
            SplashScreen(navController = navController, viewModel = splashVM)
        }

        // Home
        composable(route = Screen.Home.route) {
            val context = LocalContext.current
            val repo = remember { HomeRepository(context) }
            val homeVM: HomeViewModel = viewModel(factory = HomeViewModelFactory(repo))

            HomeScreen(
                viewModel = homeVM,
                onNavigateToAllPrompts = {
                    navController.navigate(Screen.AllAIPrompts.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToFavorites = {
                    navController.navigate(Screen.AIPromptFavorites.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToCategory = { /* TODO */ },
                onNavigateToPostDetail = { /* TODO */ },
                onNavigateToPromptDetail = { aiPrompt ->
                    val safeId = android.net.Uri.encode(aiPrompt.id)
                    navController.navigate(Screen.PromptDetail.createRoute(safeId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToGuidePostDetail = { guidePost ->
                    val safeId = android.net.Uri.encode(guidePost.id)
                    navController.navigate(Screen.GuidePostDetail.createRoute(safeId)) {
                        launchSingleTop = true
                    }
                }
            )
        }


        // Explore / Create / Rewards / Profile
        composable(route = Screen.Explore.route) { 
            ExploreScreen(
                onNavigateToPromptDetail = { aiPrompt ->
                    val safeId = android.net.Uri.encode(aiPrompt.id)
                    navController.navigate(Screen.PromptDetail.createRoute(safeId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToGuidePostDetail = { guidePost ->
                    val safeId = android.net.Uri.encode(guidePost.id)
                    navController.navigate(Screen.GuidePostDetail.createRoute(safeId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(route = Screen.Create.route) { CreateScreen() }
        composable(route = Screen.Rewards.route) { RewardsScreen() }
        composable(route = Screen.Profile.route) { ProfileScreen() }

        // All AI Prompts
        composable(route = Screen.AllAIPrompts.route) {
            val context = LocalContext.current
            val repo = remember { HomeRepository(context) }
            val vm: AIPromptViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AIPromptViewModel(repo) as T
                }
            })

            AllAIPromptsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onPromptClick = { promptId ->
                    // safe navigate with launchSingleTop
                    navController.navigate(Screen.PromptDetail.createRoute(promptId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // Favorites
        composable(route = Screen.AIPromptFavorites.route) {
            val context = LocalContext.current
            val repo = remember { HomeRepository(context) }
            val vm: AIPromptViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AIPromptViewModel(repo) as T
                }
            })

            AIPromptFavoritesScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onPromptClick = { promptId ->
                    navController.navigate(Screen.PromptDetail.createRoute(promptId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // Prompt Detail with argument
        composable(
            route = Screen.PromptDetail.route,
            arguments = listOf(navArgument("promptId") { type = NavType.StringType })
        ) { backStackEntry ->
            val promptId = backStackEntry.arguments?.getString("promptId").orEmpty()
            val context = LocalContext.current
            val repo = remember { HomeRepository(context) }
            val vm: AIPromptViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AIPromptViewModel(repo) as T
                }
            })

            // If promptId is empty, consider showing error or pop back
            if (promptId.isBlank()) {
                // fallback: popBackStack or show empty state — choose what fits app behavior
                navController.popBackStack()
            } else {
                PromptDetailScreen(promptId = promptId, viewModel = vm, onBack = { navController.popBackStack() })
            }
        }

        // Guide Post Detail with argument (placeholder for now)
        composable(
            route = Screen.GuidePostDetail.route,
            arguments = listOf(navArgument(Screen.GuidePostDetail.ARG_GUIDE_POST_ID) {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            val guidePostId = backStackEntry.arguments?.getString(Screen.GuidePostDetail.ARG_GUIDE_POST_ID) ?: ""
            
            // For now, just show a placeholder screen or navigate back
            // TODO: Implement GuidePostDetailScreen
            if (guidePostId.isBlank()) {
                navController.popBackStack()
            } else {
                // Placeholder implementation - you can create GuidePostDetailScreen later
                CreateScreen() // Using existing screen as placeholder
            }
        }

        // All Guide Posts screen (placeholder for now)
        composable(route = Screen.AllGuidePosts.route) {
            // TODO: Implement AllGuidePostsScreen
            // For now, use existing screen as placeholder
            ExploreScreen()
        }
    }
}
