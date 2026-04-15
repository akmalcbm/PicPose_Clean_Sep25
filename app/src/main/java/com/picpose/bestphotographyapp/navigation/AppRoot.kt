/**
 * ---
 * File: AppRoot.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Creates the root Compose shell and connects activity-level inputs to the app navigation tree.
 *
 * Interactions:
 * Connects screen callbacks and route arguments so feature flows remain decoupled from concrete destinations.
 *
 * Data Flow:
 * UI (Compose) -> ViewModel -> Repository -> Local/Remote Data Source -> Room/API
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.navigation

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.picpose.bestphotographyapp.components.common.PicPoseWindowInsets
import com.picpose.bestphotographyapp.core.analytics.AnalyticsLogger
import com.picpose.bestphotographyapp.core.crash.CrashReporter
import com.picpose.bestphotographyapp.components.common.BottomNavigationBar

private val authRoutes = setOf(
    Screen.Login.route,
    Screen.ForgotPassword.route,
    Screen.ResetPassword.route,
    Screen.VerifyEmail.route
)
private val mainBottomBarRoutes = setOf(Screen.Home.route, Screen.Create.route, Screen.Explore.route, Screen.Rewards.route, Screen.Profile.route)

@Composable
fun AppRoot(
    activity: Activity,
    deepLink: String?,
    analyticsLogger: AnalyticsLogger,
    crashReporter: CrashReporter,
    onHandleNotificationDeepLink: (NavHostController, String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isAuthRoute = currentRoute in authRoutes
    val showBottomBar = currentRoute in mainBottomBarRoutes
    var lastScreenName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deepLink) {
        deepLink?.let { link ->
            onHandleNotificationDeepLink(navController, link)
        }
    }

    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            val screenName = destination.route.toScreenName()
            if (screenName != null && screenName != lastScreenName) {
                lastScreenName = screenName
                analyticsLogger.logScreenView(screenName)
                crashReporter.setLastScreen(screenName)
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
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

private fun String?.toScreenName(): String? {
    return when {
        this == null -> null
        startsWith(Screen.Home.route) -> "home"
        startsWith(Screen.Explore.route) -> "explore"
        startsWith("prompt_detail") -> "prompt_detail"
        startsWith("guide_post_detail") -> "guide_detail"
        startsWith(Screen.Profile.route) -> "profile"
        startsWith(Screen.Settings.route) -> "settings"
        startsWith(Screen.Login.route) -> "login"
        startsWith(Screen.Create.route) -> "create"
        startsWith(Screen.Rewards.route) -> "rewards"
        else -> null
    }
}

@Composable
private fun AuthScaffold(content: @Composable () -> Unit) {
    val contentInsets = PicPoseWindowInsets.screenContent()
    Scaffold(
        contentWindowInsets = contentInsets
    ) { innerPadding ->
        InsetOwnedContent(innerPadding = innerPadding, content = content)
    }
}

@Composable
fun MainScaffold(
    showBottomBar: Boolean,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val contentInsets = PicPoseWindowInsets.screenContent()
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
