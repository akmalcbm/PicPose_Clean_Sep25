package com.picpose.bestphotographyapp.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Explore : Screen("explore")
    object Create : Screen("create")
    object Rewards : Screen("rewards")
    object Profile : Screen("profile")

    // AI Prompts screens
    object AllAIPrompts : Screen("all_ai_prompts")
    object AIPromptFavorites : Screen("ai_prompt_favorites")

    object PromptDetail : Screen("prompt_detail/{promptId}") {
        const val ARG_PROMPT_ID = "promptId"            // constant
        private const val BASE = "prompt_detail"
        fun createRoute(promptId: String) = "$BASE/$promptId"
    }
}
