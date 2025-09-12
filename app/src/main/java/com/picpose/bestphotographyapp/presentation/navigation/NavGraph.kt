package com.picpose.bestphotographyapp.presentation.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import com.picpose.bestphotographyapp.presentation.screens.AIPromptFavoritesScreen
import com.picpose.bestphotographyapp.presentation.screens.AllAIPromptsScreen
import com.picpose.bestphotographyapp.presentation.screens.CreateScreen
import com.picpose.bestphotographyapp.presentation.screens.ExploreScreen
import com.picpose.bestphotographyapp.presentation.screens.HomeScreen
import com.picpose.bestphotographyapp.presentation.screens.ProfileScreen
import com.picpose.bestphotographyapp.presentation.screens.PromptDetailScreen
import com.picpose.bestphotographyapp.presentation.screens.RewardsScreen
import com.picpose.bestphotographyapp.presentation.screens.SplashScreen
import com.picpose.bestphotographyapp.presentation.splash.SplashViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.AIPromptViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.HomeViewModel

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(route = Screen.Splash.route) {
            val viewModel: SplashViewModel = viewModel()
            SplashScreen(navController = navController, viewModel = viewModel)
        }

        // ✅ FIXED: Single HomeScreen composable with correct parameters
        composable(route = Screen.Home.route) {
            val context = LocalContext.current
            val homeRepository = remember { HomeRepository(context) }
            val homeViewModel: HomeViewModel = remember { HomeViewModel(homeRepository) }

            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToAllPrompts = {
                    navController.navigate(Screen.AllAIPrompts.route)
                },
                onNavigateToFavorites = {
                    navController.navigate(Screen.AIPromptFavorites.route)
                },
                // ✅ FIXED: Use correct parameter names
                onNavigateToCategory = { category ->
                    // TODO: Add category navigation when you have CategoryScreen
                    // navController.navigate(Screen.Category.createRoute(category.id))
                },
                onNavigateToPostDetail = { post ->
                    // TODO: Add post detail navigation when you have PostDetailScreen
                    // navController.navigate(Screen.PostDetail.createRoute(post.id))
                },
                onNavigateToPromptDetail = { aiPrompt ->
                    navController.navigate(Screen.PromptDetail.createRoute(aiPrompt.id))
                }
            )
        }

        composable(route = Screen.Explore.route) {
            ExploreScreen()
        }

        composable(route = Screen.Create.route) {
            CreateScreen()
        }

        composable(route = Screen.Rewards.route) {
            RewardsScreen()
        }

        composable(route = Screen.Profile.route) {
            ProfileScreen()
        }

        // AI Prompts Screens
        composable(route = Screen.AllAIPrompts.route) {
            val context = LocalContext.current
            val homeRepository = remember { HomeRepository(context) }
            val viewModel = remember { AIPromptViewModel(homeRepository) }

            AllAIPromptsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPromptClick = { promptId -> // ✅ Expecting String ID
                    navController.navigate(Screen.PromptDetail.createRoute(promptId))
                }
            )
        }

        composable(route = Screen.AIPromptFavorites.route) {
            val context = LocalContext.current
            val homeRepository = remember { HomeRepository(context) }
            val viewModel = remember { AIPromptViewModel(homeRepository) }

            AIPromptFavoritesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPromptClick = { promptId -> // ✅ Expecting String ID
                    navController.navigate(Screen.PromptDetail.createRoute(promptId))
                }
            )
        }

        composable(
            route = Screen.PromptDetail.route,
            arguments = listOf(
                navArgument("promptId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val promptId = backStackEntry.arguments?.getString("promptId") ?: ""
            val context = LocalContext.current
            val homeRepository = remember { HomeRepository(context) }
            val viewModel = remember { AIPromptViewModel(homeRepository) }

            PromptDetailScreen(
                promptId = promptId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
