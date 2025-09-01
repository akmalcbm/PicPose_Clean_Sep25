package com.picpose.bestphotographyapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.picpose.bestphotographyapp.presentation.screens.CreateScreen
import com.picpose.bestphotographyapp.presentation.screens.ExploreScreen
import com.picpose.bestphotographyapp.presentation.screens.HomeScreen
import com.picpose.bestphotographyapp.presentation.screens.ProfileScreen
import com.picpose.bestphotographyapp.presentation.screens.RewardsScreen
import com.picpose.bestphotographyapp.presentation.screens.SplashScreen
import com.picpose.bestphotographyapp.presentation.splash.SplashViewModel

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
        composable(route = Screen.Explore.route) {
            ExploreScreen()
        }
        composable(route = Screen.Create.route) {
            CreateScreen()
        }
        composable(route = Screen.Home.route) {
            HomeScreen()
        }
        composable(route = Screen.Rewards.route) {
            RewardsScreen()
        }
        composable(route = Screen.Profile.route) {
            ProfileScreen()
        }
    }
}
