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
                onPromptClick = { promptId ->
                    navController.navigate(Screen.PromptDetail.createRoute(promptId))
                }
            )
        }

        composable(route = Screen.Explore.route) {
            ExploreScreen() // Move this file from viewmodels to screens folder
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

        // AI Prompts Screens - Using your existing files
        composable(route = Screen.AllAIPrompts.route) {
            val context = LocalContext.current
            val homeRepository = remember { HomeRepository(context) }
            val viewModel = remember { AIPromptViewModel(homeRepository) }

            AllAIPromptsScreen( // Your existing file
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(route = Screen.AIPromptFavorites.route) {
            val context = LocalContext.current
            val homeRepository = remember { HomeRepository(context) }
            val viewModel = remember { AIPromptViewModel(homeRepository) }

            AIPromptFavoritesScreen( // Your existing file
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
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

            PromptDetailScreen( // Your existing file
                promptId = promptId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(route = Screen.Home.route) {
            val context = LocalContext.current
            val homeRepository = remember { HomeRepository(context) }
            val homeViewModel = remember { HomeViewModel(homeRepository) }

            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToAllPrompts = {
                    navController.navigate(Screen.AllAIPrompts.route)
                },
                onNavigateToFavorites = {
                    navController.navigate(Screen.AIPromptFavorites.route)
                },
                onPromptClick = { promptId ->
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
