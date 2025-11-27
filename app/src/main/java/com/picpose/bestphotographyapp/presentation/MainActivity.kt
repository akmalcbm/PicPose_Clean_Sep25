package com.picpose.bestphotographyapp.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.picpose.bestphotographyapp.presentation.components.BottomNavigationBar
import com.picpose.bestphotographyapp.presentation.navigation.NavGraph
import com.picpose.bestphotographyapp.presentation.navigation.Screen
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel
import com.picpose.bestphotographyapp.ui.theme.PicPoseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {

            val settingsViewModel: SettingsViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel()

            val themeMode by settingsViewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()

            // Convert String → Boolean?
            val requestedDarkTheme: Boolean? = when (themeMode.lowercase()) {
                "light" -> false
                "dark" -> true
                else -> null  // system default
            }

            // Convert nullable → final Boolean
            val finalDarkTheme: Boolean = requestedDarkTheme ?: systemDark

            PicPoseTheme(darkTheme = finalDarkTheme) {

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    val showBottomNav =
                        currentRoute != Screen.Splash.route &&
                                currentRoute != Screen.Login.route

                    Scaffold(
                        contentWindowInsets = WindowInsets(0),
                        bottomBar = {
                            if (showBottomNav) {
                                BottomNavigationBar(navController = navController)
                            }
                        }
                    ) { paddingValues ->

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            NavGraph(
                                navController = navController,
                                activity = this@MainActivity
                            )
                        }
                    }
                }
            }
        }
    }
}

