/**
 * ---
 * File: BottomNavItem.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Contains reusable Compose UI building blocks shared across screens.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
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
    BottomNavItem(
        nameRes = R.string.rewards,
        route = Screen.Rewards.route,
        icon = Icons.Default.Star
    ),
    BottomNavItem(
        nameRes = R.string.profile,
        route = Screen.Profile.route,
        icon = Icons.Default.Person
        //Profile & Settings
        //icon = Icons.Default.PermDataSetting
    )

)
