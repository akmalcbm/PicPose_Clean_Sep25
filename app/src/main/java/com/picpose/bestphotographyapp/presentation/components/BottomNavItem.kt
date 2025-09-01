package com.picpose.bestphotographyapp.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val name: String,
    val route: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        name = "Explore",
        route = Screen.Explore.route,
        icon = Icons.Default.Explore
    ),
    BottomNavItem(
        name = "Create",
        route = Screen.Create.route,
        icon = Icons.Default.Add
    ),
    BottomNavItem(
        name = "Home",
        route = Screen.Home.route,
        icon = Icons.Default.Home
    ),
    BottomNavItem(
        name = "Rewards",
        route = Screen.Rewards.route,
        icon = Icons.Default.Star
    ),
    BottomNavItem(
        name = "Profile",
        route = Screen.Profile.route,
        icon = Icons.Default.Person
    )
)
