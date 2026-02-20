package com.picpose.bestphotographyapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.picpose.bestphotographyapp.core.analytics.AnalyticsLogger
import com.picpose.bestphotographyapp.core.crash.CrashReporter
import com.picpose.bestphotographyapp.core.locale.AppLocaleManager
import com.picpose.bestphotographyapp.fcm.PicPoseFirebaseMessagingService
import com.picpose.bestphotographyapp.presentation.navigation.AppRoot
import com.picpose.bestphotographyapp.presentation.navigation.Screen
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import com.picpose.bestphotographyapp.presentation.viewmodels.SettingsViewModel
import com.picpose.bestphotographyapp.data.datastore.ThemeMode
import com.picpose.bestphotographyapp.ui.theme.PicPoseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var analyticsLogger: AnalyticsLogger

    @Inject
    lateinit var crashReporter: CrashReporter

    private lateinit var authViewModel: AuthViewModel

    private val notificationDeepLinkState = mutableStateOf<String?>(null)
    private var lastConsumedSignature: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        analyticsLogger.logAppOpen()

        intent?.data?.let { handleTwitterUri(it) }
        consumeNotificationIntent(intent, source = "onCreate")

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()

            val themeMode by settingsViewModel.themeMode.collectAsState()
            val language by settingsViewModel.language.collectAsState()
            val finalDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

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
                        analyticsLogger = analyticsLogger,
                        crashReporter = crashReporter,
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
            normalized == "app://home" || normalized == "app://home/" -> {
                navController.navigate(Screen.Home.route) {
                    launchSingleTop = true
                }
            }

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

        intent.dataString?.takeIf { it.startsWith("app://") }?.let {
            return it
        }

        val deeplinkAlias = intent.getStringExtra(PicPoseFirebaseMessagingService.EXTRA_DEEPLINK_ALIAS)
        if (!deeplinkAlias.isNullOrBlank()) {
            return deeplinkAlias
        }

        val fromExtra = intent.getStringExtra(PicPoseFirebaseMessagingService.EXTRA_DEEP_LINK)
        if (!fromExtra.isNullOrBlank()) {
            return fromExtra
        }

        val route = intent.getStringExtra("route")
        if (!route.isNullOrBlank()) {
            return route
        }

        val extraTargetType = intent.getStringExtra(PicPoseFirebaseMessagingService.EXTRA_TARGET_TYPE)
            ?: intent.getStringExtra("target_type")
        val extraTargetId = intent.getStringExtra(PicPoseFirebaseMessagingService.EXTRA_TARGET_ID)
            ?: intent.getStringExtra("target_id")
            ?: intent.getStringExtra("id")

        when (extraTargetType?.lowercase()) {
            "prompt" -> if (!extraTargetId.isNullOrBlank()) return "app://prompts/$extraTargetId"
            "guide" -> if (!extraTargetId.isNullOrBlank()) return "app://guides/$extraTargetId"
            "category" -> if (!extraTargetId.isNullOrBlank()) return "app://category/$extraTargetId"
            "home" -> return "app://home"
        }

        val guideId = intent.getStringExtra("guide_id")
        if (!guideId.isNullOrBlank()) {
            return "app://guides/$guideId"
        }

        val promptId = intent.getStringExtra("prompt_id")
        if (!promptId.isNullOrBlank()) {
            return "app://prompts/$promptId"
        }

        val hasNotificationId = !intent.getStringExtra(PicPoseFirebaseMessagingService.EXTRA_NOTIFICATION_ID).isNullOrBlank()
        if (hasNotificationId) {
            return "app://home"
        }

        return null
    }

    private fun consumeNotificationIntent(intent: Intent?, source: String) {
        if (intent == null) return

        val deepLink = extractNotificationDeepLink(intent)
        val targetType = intent.getStringExtra(PicPoseFirebaseMessagingService.EXTRA_TARGET_TYPE)
        val targetId = intent.getStringExtra(PicPoseFirebaseMessagingService.EXTRA_TARGET_ID)
        val signature = listOf(
            intent.getStringExtra(PicPoseFirebaseMessagingService.EXTRA_NOTIFICATION_ID).orEmpty(),
            deepLink.orEmpty(),
            targetType.orEmpty(),
            targetId.orEmpty(),
            intent.dataString.orEmpty()
        ).joinToString("|")

        Log.i(
            "NotifTap",
            "source=$source data=${intent.dataString} deepLink=$deepLink targetType=$targetType targetId=$targetId extras=${intent.extras?.keySet()}"
        )

        if (!deepLink.isNullOrBlank() && signature != lastConsumedSignature) {
            lastConsumedSignature = signature
            analyticsLogger.logNotificationOpen(deepLinkTypeFromUrl(deepLink))
            notificationDeepLinkState.value = null
            notificationDeepLinkState.value = deepLink
        }

        intent.removeExtra(PicPoseFirebaseMessagingService.EXTRA_DEEP_LINK)
        intent.removeExtra(PicPoseFirebaseMessagingService.EXTRA_DEEPLINK_ALIAS)
        intent.removeExtra(PicPoseFirebaseMessagingService.EXTRA_NOTIFICATION_ID)
        intent.removeExtra(PicPoseFirebaseMessagingService.EXTRA_TARGET_TYPE)
        intent.removeExtra(PicPoseFirebaseMessagingService.EXTRA_TARGET_ID)
        intent.removeExtra("route")
        intent.removeExtra("guide_id")
        intent.removeExtra("prompt_id")
        intent.removeExtra("target_type")
        intent.removeExtra("target_id")
        intent.removeExtra("deeplink")
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

        consumeNotificationIntent(intent, source = "onNewIntent")
    }

    private fun handleTwitterUri(uri: Uri) {
        if (uri.toString().startsWith("com.picpose://oauth/twitter_callback")) {
            authViewModel.handleTwitterRedirect(uri)
        }
    }

    private fun deepLinkTypeFromUrl(deepLink: String): String {
        return when {
            deepLink.startsWith("app://prompts/") -> "prompt"
            deepLink.startsWith("app://guides/") -> "guide"
            deepLink.startsWith("app://category/") -> "category"
            deepLink.startsWith("app://home") -> "home"
            else -> "unknown"
        }
    }
}
