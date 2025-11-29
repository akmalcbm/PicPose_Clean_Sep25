package com.picpose.bestphotographyapp.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
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
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel
import com.picpose.bestphotographyapp.ui.theme.PicPoseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ⭐ Correct way to hold the ViewModel reference in Activity
    private lateinit var authViewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ⭐ Correct Hilt ViewModel loading (NO @Inject!)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        // ⭐ Twitter cold-start redirect
        intent?.data?.let { handleTwitterUri(it) }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {

            val settingsViewModel: SettingsViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel()

            val themeMode by settingsViewModel.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()

            val requestedDarkTheme: Boolean? = when (themeMode.lowercase()) {
                "light" -> false
                "dark" -> true
                else -> null
            }

            val finalDarkTheme = requestedDarkTheme ?: systemDark

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
                            if (showBottomNav) BottomNavigationBar(navController)
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

    // ⭐ Facebook Login callback forwarding (required by FB SDK)
    @Deprecated("onActivityResult is deprecated but required by Facebook SDK")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        authViewModel.getFacebookCallbackManager()
            .onActivityResult(requestCode, resultCode, data)
    }

    // ⭐ Twitter redirect when activity is already open
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val uri = intent.data
        if (uri != null && uri.toString().startsWith("com.picpose://oauth/twitter_callback")) {
            authViewModel.handleTwitterRedirect(uri)
        }
    }

    // ⭐ Twitter redirect on cold start
    private fun handleTwitterUri(uri: Uri) {
        if (uri.toString().startsWith("com.picpose://oauth/twitter_callback")) {
            authViewModel.handleTwitterRedirect(uri)
        }
    }
}
