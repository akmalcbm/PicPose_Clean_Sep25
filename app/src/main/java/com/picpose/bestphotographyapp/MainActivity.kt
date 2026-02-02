package com.picpose.bestphotographyapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.picpose.bestphotographyapp.presentation.components.BottomNavigationBar
import com.picpose.bestphotographyapp.presentation.navigation.NavGraph
import com.picpose.bestphotographyapp.presentation.navigation.Screen
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel
import com.picpose.bestphotographyapp.ui.theme.PicPoseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ⭐ Correct way to hold the ViewModel reference in Activity
    private lateinit var authViewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deepLink = intent.getStringExtra("deep_link")

        // ⭐ Correct Hilt ViewModel loading (NO @Inject!)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        // ⭐ Twitter cold-start redirect
        intent?.data?.let { handleTwitterUri(it) }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {

            RequestNotificationPermission()

            val settingsViewModel: SettingsViewModel =
                viewModel()

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
                    modifier = Modifier.Companion.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val navController = rememberNavController()

                    // ✅ HANDLE NOTIFICATION DEEP LINK HERE
                    LaunchedEffect(Unit) {
                        deepLink?.let { link ->
                            handleNotificationDeepLink(navController, link)
                        }
                    }

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
                            modifier = Modifier.Companion
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

    private fun handleNotificationDeepLink(
        navController: NavHostController,
        deepLink: String
    ) {
        when {
            deepLink.startsWith("app://prompts/") -> {
                val id = deepLink.removePrefix("app://prompts/")
                navController.navigate(Screen.PromptDetail.createRoute(id)) {
                    launchSingleTop = true
                }
            }

            deepLink.startsWith("app://category/") -> {
                val category = deepLink.removePrefix("app://category/")
                navController.navigate("${Screen.AllAIPrompts.route}?category=$category") {
                    launchSingleTop = true
                }
            }

            else -> {
                // fallback → Home
                navController.navigate(Screen.Home.route) {
                    launchSingleTop = true
                }
            }
        }
    }


    @Composable
    fun RequestNotificationPermission() {
        val context = LocalContext.current
        val activity = context as Activity

        var showRationale by remember { mutableStateOf(false) }
        var permissionRequested by remember { mutableStateOf(false) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            showRationale = false
            permissionRequested = true
            if (isGranted) {
                // Permission granted
            } else {
                // Permission denied
            }
        }

        LaunchedEffect(Unit) {
            // Wait for 1.5 minutes (90 seconds) before showing permission
            delay(65000L) // 65 seconds = 1:05 minutes

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                val shouldShowRationale =
                    activity.shouldShowRequestPermissionRationale(
                        Manifest.permission.POST_NOTIFICATIONS
                    )

                // Only show if permission not already granted and not already requested
                if (!hasPermission && !permissionRequested) {
                    // You can check some condition here before showing
                    // For example, only show if user has engaged with the app
                    showRationale = true
                }
            }
        }

        if (showRationale) {
            NotificationPermissionDialog(
                onAllow = {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onCancel = {
                    showRationale = false
                    permissionRequested = true
                }
            )
        }
    }

    @Composable
    fun NotificationPermissionDialog(
        onAllow: () -> Unit,
        onCancel: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = {
                Text(
                    text = "Stay Updated 📢",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = buildAnnotatedString {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append("Enable notifications to receive:\n\n")
                        pop()

                        pushStyle(SpanStyle(fontStyle = FontStyle.Normal))
                        append("⚆  Daily AI Prompts & Creative Ideas\n")
                        pop()

                        pushStyle(SpanStyle(fontStyle = FontStyle.Normal))
                        append("⚆  Daily Photography Guide & Tips\n")
                        pop()

                        pushStyle(SpanStyle(fontStyle = FontStyle.Normal))
                        append("⚆  Important App Updates, etc\n\n")

                        pushStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append("Note:\n")
                        pop()

                        append("You can turn notifications off anytime from ")
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append("Settings.")
                        pop()
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = onAllow) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel) {
                    Text("Not Now")
                }
            }
        )
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