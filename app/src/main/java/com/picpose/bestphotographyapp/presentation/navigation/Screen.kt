package com.picpose.bestphotographyapp.presentation.navigation

import android.net.Uri

sealed class Screen(val route: String) {

    // 🔹 Core App Screens
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Home : Screen("home")
    object Explore : Screen("explore")
    object Create : Screen("create")
    object Rewards : Screen("rewards")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object EditProfile : Screen("edit_profile")
    object Privacy : Screen("privacy_screen")
    object About : Screen("about_screen")

    // 🔹 AI Prompts Screens
    object AllAIPrompts : Screen("all_ai_prompts")        // ✅ Already defined — used for "Browse Prompts"
    object AIPromptFavorites : Screen("ai_prompt_favorites")

    object PromptDetail : Screen("prompt_detail/{promptId}") {
        private const val BASE = "prompt_detail"
        fun createRoute(promptId: String) = "$BASE/$promptId"
    }

    // 🔹 Prompts by Tag
    object TagPrompts : Screen("tag_prompts/{tag}") {
        const val ARG_TAG = "tag"
        private const val BASE = "tag_prompts"
        fun createRoute(tag: String): String = "$BASE/${Uri.encode(tag)}"
    }

    // 🔹 Guide Posts
    object AllGuidePosts : Screen("all_guide_posts")
    object GuidePostDetail : Screen("guide_post_detail/{guidePostId}") {
        const val ARG_GUIDE_POST_ID = "guidePostId"
        private const val BASE = "guide_post_detail"
        fun createRoute(guidePostId: String) = "$BASE/$guidePostId"
    }
}
