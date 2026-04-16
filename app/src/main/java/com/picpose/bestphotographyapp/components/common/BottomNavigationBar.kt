/**
 * ---
 * File: BottomNavigationBar.kt
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

package com.picpose.bestphotographyapp.components.common

import android.os.SystemClock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hierarchy
import com.picpose.bestphotographyapp.navigation.BottomTabReselectManager
import com.picpose.bestphotographyapp.navigation.bottomNavItems

private const val SELECTED_TAB_DOUBLE_TAP_TIMEOUT_MS = 300L

private data class SelectedTabTap(
    val route: String,
    val timestamp: Long
)

@Composable
fun BottomNavigationBar(
    navController: NavController,
    bottomTabReselectManager: BottomTabReselectManager
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var lastSelectedTabTap by remember { mutableStateOf<SelectedTabTap?>(null) }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentDestination.isInHierarchy(item.route)

            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = stringResource(item.nameRes)) },
                label = { Text(stringResource(item.nameRes)) },
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        val now = SystemClock.elapsedRealtime()
                        val isDoubleTap = lastSelectedTabTap?.let { previousTap ->
                            previousTap.route == item.route &&
                                now - previousTap.timestamp <= SELECTED_TAB_DOUBLE_TAP_TIMEOUT_MS
                        } == true

                        lastSelectedTabTap = if (isDoubleTap) {
                            null
                        } else {
                            SelectedTabTap(route = item.route, timestamp = now)
                        }

                        if (isDoubleTap) {
                            bottomTabReselectManager.emitScrollToTop(item.route)
                        }
                    } else {
                        lastSelectedTabTap = null
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

private fun NavDestination?.isInHierarchy(route: String): Boolean {
    return this?.hierarchy?.any { destination -> destination.route == route } == true
}
