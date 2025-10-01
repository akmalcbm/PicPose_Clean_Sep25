package com.picpose.bestphotographyapp.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Home : Screen("home")
    object Explore : Screen("explore")
    object Create : Screen("create")
    object Rewards : Screen("rewards")
    object Profile : Screen("profile")
    object Settings : Screen("settings")

    // AI Prompts screens
    object AllAIPrompts : Screen("all_ai_prompts")
    object AIPromptFavorites : Screen("ai_prompt_favorites")

    object PromptDetail : Screen("prompt_detail/{promptId}") {
        const val ARG_PROMPT_ID = "promptId"            // constant
        private const val BASE = "prompt_detail"
        fun createRoute(promptId: String) = "$BASE/$promptId"
    }

    // Guide Posts screens
    object AllGuidePosts : Screen("all_guide_posts")
    object GuidePostDetail : Screen("guide_post_detail/{guidePostId}") {
        const val ARG_GUIDE_POST_ID = "guidePostId"
        private const val BASE = "guide_post_detail"
        fun createRoute(guidePostId: String) = "$BASE/$guidePostId"
    }
}
