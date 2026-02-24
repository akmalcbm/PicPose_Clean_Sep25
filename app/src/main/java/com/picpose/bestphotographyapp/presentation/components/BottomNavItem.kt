package com.picpose.bestphotographyapp.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.picpose.bestphotographyapp.R

data class BottomNavItem(
    val nameRes: Int,
    val route: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        nameRes = R.string.explore_title,
        route = Screen.Explore.route,
        icon = Icons.Default.Explore
    ),
    /*BottomNavItem(
        nameRes = R.string.create,
        route = Screen.Create.route,
        icon = Icons.Default.Add
    ),*/
    BottomNavItem(
        nameRes = R.string.home,
        route = Screen.Home.route,
        icon = Icons.Default.Home
    ),
    /*BottomNavItem(
        nameRes = R.string.rewards,
        route = Screen.Rewards.route,
        icon = Icons.Default.Star
    ),*/
    BottomNavItem(
        nameRes = R.string.profile,
        route = Screen.Profile.route,
        icon = Icons.Default.Person
        //Profile & Settings
        //icon = Icons.Default.PermDataSetting
    )

)
