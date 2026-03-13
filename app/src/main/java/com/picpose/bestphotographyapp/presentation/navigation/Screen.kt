/**
 * ---
 * File: Screen.kt
 * Layer: Presentation (Navigation)
 * Project: PicPose
 *
 * Purpose:
 * Central route registry for Navigation Compose. Each sealed object represents
 * one destination, and parameterized destinations expose helper builders to
 * construct safe route strings.
 *
 * Interactions:
 * `NavGraph` declares destinations from these routes, while activities and
 * screens use the helper methods when navigating.
 *
 * Data Flow:
 * UI event -> navigation callback -> `Screen` route builder -> `NavGraph` destination
 *
 * Maintainer Notes:
 * - Keep route names stable to avoid breaking deep links or saved back stack entries.
 * - Encode user-generated values before appending them to route paths.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.navigation

import android.net.Uri

sealed class Screen(val route: String) {

    // Core shell and account-management destinations.
    object Splash : Screen("splash")
    object Login : Screen("login")
    object ForgotPassword : Screen("forgot_password")
    object ResetPassword : Screen("reset_password/{token}") {
        private const val BASE = "reset_password"
        fun createRoute(token: String) = "$BASE/$token"
    }
    object VerifyEmail : Screen("verify_email/{token}") {
        private const val BASE = "verify_email"
        fun createRoute(token: String) = "$BASE/$token"
    }
    object Home : Screen("home")
    object Explore : Screen("explore")
    object Create : Screen("create")
    object Rewards : Screen("rewards")
    object Packs : Screen("packs")
    object PackDetail : Screen("pack_detail/{packId}") {
        private const val BASE = "pack_detail"
        fun createRoute(packId: Int) = "$BASE/$packId"
    }
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object EditProfile : Screen("edit_profile")
    object Privacy : Screen("privacy_screen")
    object Terms : Screen("terms_screen")
    object About : Screen("about_screen")
    object HelpAndSupportScreen : Screen("help_and_support")

    // Prompt discovery and detail destinations.
    object AllAIPrompts : Screen("all_ai_prompts")
    object AIPromptFavorites : Screen("ai_prompt_favorites")

    object PromptDetail : Screen("prompt_detail/{promptId}") {
        private const val BASE = "prompt_detail"
        fun createRoute(promptId: String) = "$BASE/$promptId"
    }

    // Tag-based listing. Tags are encoded because they may contain spaces or symbols.
    object TagPrompts : Screen("tag_prompts/{tag}") {
        const val ARG_TAG = "tag"
        private const val BASE = "tag_prompts"
        fun createRoute(tag: String): String = "$BASE/${Uri.encode(tag)}"
    }

    // Guide/article style content destinations.
    object AllGuidePosts : Screen("all_guide_posts")
    object GuidePostDetail : Screen("guide_post_detail/{guidePostId}") {
        const val ARG_GUIDE_POST_ID = "guidePostId"
        private const val BASE = "guide_post_detail"
        fun createRoute(guidePostId: String) = "$BASE/$guidePostId"
    }
}
