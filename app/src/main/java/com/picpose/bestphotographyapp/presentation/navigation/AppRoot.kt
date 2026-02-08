package com.picpose.bestphotographyapp.presentation.navigation

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.picpose.bestphotographyapp.presentation.components.BottomNavigationBar

private val authRoutes = setOf(Screen.Login.route)
private val mainBottomBarRoutes = setOf(Screen.Home.route, Screen.Create.route, Screen.Explore.route, Screen.Rewards.route, Screen.Profile.route)

@Composable
fun AppRoot(
    activity: Activity,
    deepLink: String?,
    onHandleNotificationDeepLink: (NavHostController, String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isAuthRoute = currentRoute in authRoutes
    val showBottomBar = currentRoute in mainBottomBarRoutes

    LaunchedEffect(deepLink) {
        deepLink?.let { link ->
            onHandleNotificationDeepLink(navController, link)
        }
    }

    if (isAuthRoute) {
        AuthScaffold {
            NavGraph(navController = navController, activity = activity)
        }
    } else {
        MainScaffold(
            showBottomBar = showBottomBar,
            navController = navController
        ) {
            NavGraph(navController = navController, activity = activity)
        }
    }
}

@Composable
private fun AuthScaffold(content: @Composable () -> Unit) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        InsetOwnedContent(innerPadding = innerPadding, content = content)
    }
}

@Composable
fun MainScaffold(
    showBottomBar: Boolean,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val contentInsets = if (showBottomBar) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.safeDrawing
    }

    Scaffold(
        contentWindowInsets = contentInsets,
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(navController = navController)
            }
        }
    ) { innerPadding ->
        InsetOwnedContent(innerPadding = innerPadding, content = content)
    }
}

@Composable
private fun InsetOwnedContent(
    innerPadding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
    ) {
        content()
    }
}
