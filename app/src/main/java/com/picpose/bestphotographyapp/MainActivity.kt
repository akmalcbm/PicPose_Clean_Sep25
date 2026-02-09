package com.picpose.bestphotographyapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.picpose.bestphotographyapp.presentation.navigation.AppRoot
import com.picpose.bestphotographyapp.presentation.navigation.Screen
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel
import com.picpose.bestphotographyapp.core.locale.AppLocaleManager
import com.picpose.bestphotographyapp.ui.theme.PicPoseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // ⭐ Correct way to hold the ViewModel reference in Activity
    private lateinit var authViewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val deepLink = intent.getStringExtra("deep_link")

        // ⭐ Correct Hilt ViewModel loading (NO @Inject!)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        // ⭐ Twitter cold-start redirect
        intent?.data?.let { handleTwitterUri(it) }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {

            val settingsViewModel: SettingsViewModel =
                viewModel()

            val themeMode by settingsViewModel.themeMode.collectAsState()
            val language by settingsViewModel.language.collectAsState()
            val systemDark = isSystemInDarkTheme()

            val requestedDarkTheme: Boolean? = when (themeMode.lowercase()) {
                "light" -> false
                "dark" -> true
                else -> null
            }

            val finalDarkTheme = requestedDarkTheme ?: systemDark

            var isApplyingLocale by remember { mutableStateOf(false) }
            LaunchedEffect(language) {
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                val target = AppLocaleManager.resolveLanguageTags(language)
                if (!isApplyingLocale && language.isNotBlank() && current != target) {
                    isApplyingLocale = true
                    AppLocaleManager.applyLanguage(language)
                    // Recreate so resources reload immediately.
                    this@MainActivity.recreate()
                }
            }

            PicPoseTheme(darkTheme = finalDarkTheme) {

                Surface(
                    modifier = Modifier.Companion.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(
                        activity = this@MainActivity,
                        deepLink = deepLink,
                        onHandleNotificationDeepLink = ::handleNotificationDeepLink
                    )
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
