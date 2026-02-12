package com.picpose.bestphotographyapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.picpose.bestphotographyapp.core.locale.AppLocaleManager
import com.picpose.bestphotographyapp.fcm.PicPoseFirebaseMessagingService
import com.picpose.bestphotographyapp.presentation.navigation.AppRoot
import com.picpose.bestphotographyapp.presentation.navigation.Screen
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel
import com.picpose.bestphotographyapp.ui.theme.PicPoseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var authViewModel: AuthViewModel

    private val notificationDeepLinkState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        intent?.data?.let { handleTwitterUri(it) }
        notificationDeepLinkState.value = extractNotificationDeepLink(intent)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()

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
                    this@MainActivity.recreate()
                }
            }

            PicPoseTheme(darkTheme = finalDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(
                        activity = this@MainActivity,
                        deepLink = notificationDeepLinkState.value,
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
        val normalized = deepLink.trim()

        when {
            normalized.startsWith("app://prompts/") -> {
                val id = normalized.removePrefix("app://prompts/")
                navController.navigate(Screen.PromptDetail.createRoute(id)) {
                    launchSingleTop = true
                }
            }

            normalized.startsWith("app://guides/") -> {
                val id = normalized.removePrefix("app://guides/")
                navController.navigate(Screen.GuidePostDetail.createRoute(id)) {
                    launchSingleTop = true
                }
            }

            normalized.startsWith("app://category/") -> {
                val category = normalized.removePrefix("app://category/")
                navController.navigate("${Screen.AllAIPrompts.route}?category=$category") {
                    launchSingleTop = true
                }
            }

            else -> {
                navController.navigate(Screen.Home.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    private fun extractNotificationDeepLink(intent: Intent?): String? {
        if (intent == null) return null

        val fromExtra = intent.getStringExtra(PicPoseFirebaseMessagingService.EXTRA_DEEP_LINK)
        if (!fromExtra.isNullOrBlank()) {
            return fromExtra
        }

        val route = intent.getStringExtra("route")
        if (!route.isNullOrBlank()) {
            return route
        }

        val guideId = intent.getStringExtra("guide_id")
        if (!guideId.isNullOrBlank()) {
            return "app://guides/$guideId"
        }

        val promptId = intent.getStringExtra("prompt_id")
        if (!promptId.isNullOrBlank()) {
            return "app://prompts/$promptId"
        }

        return null
    }

    @Deprecated("onActivityResult is deprecated but required by Facebook SDK")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        authViewModel.getFacebookCallbackManager().onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        intent.data?.let { uri ->
            if (uri.toString().startsWith("com.picpose://oauth/twitter_callback")) {
                authViewModel.handleTwitterRedirect(uri)
                return
            }
        }

        notificationDeepLinkState.value = null
        notificationDeepLinkState.value = extractNotificationDeepLink(intent)
    }

    private fun handleTwitterUri(uri: Uri) {
        if (uri.toString().startsWith("com.picpose://oauth/twitter_callback")) {
            authViewModel.handleTwitterRedirect(uri)
        }
    }
}
