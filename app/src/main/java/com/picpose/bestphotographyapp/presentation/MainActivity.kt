package com.picpose.bestphotographyapp.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.picpose.bestphotographyapp.ui.theme.PicPoseTheme
import com.picpose.bestphotographyapp.presentation.components.BottomNavigationBar
import com.picpose.bestphotographyapp.presentation.navigation.NavGraph
import com.picpose.bestphotographyapp.presentation.navigation.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PicPoseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // Show bottom navigation for all screens except splash
                    val showBottomNav = currentRoute != Screen.Splash.route

                    if (showBottomNav) {
                        Scaffold(
                            bottomBar = {
                                BottomNavigationBar(navController = navController)
                            }
                        ) { paddingValues ->
                            Box(modifier = Modifier.padding(paddingValues)) {
                                NavGraph(navController = navController)
                            }
                        }
                    } else {
                        // Show only the NavGraph for splash screen
                        NavGraph(navController = navController)
                    }
                }
            }
        }
    }
}
