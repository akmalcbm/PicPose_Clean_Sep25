package com.picpose.bestphotographyapp.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Explore : Screen("explore")
    object Create : Screen("create")
    object Home : Screen("home")
    object Rewards : Screen("rewards")
    object Profile : Screen("profile")
}
