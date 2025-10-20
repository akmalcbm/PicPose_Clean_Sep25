# Enhanced AI Prompt Details Screen - Implementation Guide

## Overview

This implementation provides a complete, production-ready AI Prompt Details Screen with integrated AdMob advertising, smooth animations, and improved UX.

## Files Created

### 1. AdManager (`data/AdManager.kt`)
Singleton class for managing interstitial ads with the following features:
- Alternates between two interstitial ad units
- Tracks click count and shows ads every N clicks (default: 3)
- Preloads ads for better UX
- Suspendable functions for Compose integration
- Fallback to Google test ad IDs

### 2. NativeAdComposable (`ui/ads/NativeAdComposable.kt`)
Composable for displaying native ads:
- Displays native ads in Compose using AndroidView
- Graceful fallback if ad fails to load
- Proper lifecycle management
- Customizable layout

### 3. PromptRepository (`data/PromptRepository.kt`)
Data access layer for AI prompts:
- Mock implementation with sample data (can be replaced with real API calls)
- Methods for fetching prompts, similar prompts, and app settings
- Flow-based for reactive updates

### 4. AiPromptDetailsViewModel (`ui/details/AiPromptDetailsViewModel.kt`)
ViewModel for managing state and business logic:
- Loads prompt details and similar prompts
- Manages ad click counter
- Coordinates with AdManager
- Provides UI state via StateFlow
- Handles pagination for similar prompts

### 5. FullScreenImageDialog (`ui/details/FullScreenImageDialog.kt`)
Zoomable full-screen image viewer:
- Pinch to zoom (1x to 5x)
- Pan to move when zoomed
- Tap to dismiss (or reset zoom)
- Loading and error states

### 6. AiPromptDetailsScreen (`ui/details/AiPromptDetailsScreen.kt`)
Main enhanced details screen with:
- Animated content transitions (crossfade)
- Native ad between content and similar prompts
- Vertical scrollable list of similar prompts with "Load More"
- Interstitial ads on similar prompt clicks
- Scroll reset to top on navigation
- Proper error handling and loading states

## Dependencies Required

Add these to your `app/build.gradle.kts` if not already present:

```kotlin
dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    
    // Image loading
    implementation(libs.coil.compose)
    
    // AdMob
    implementation(libs.play.services.ads)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
```

## Integration Steps

### Option 1: Replace Existing Screen (Recommended)

Update `NavGraph.kt` to use the new implementation:

```kotlin
// Import the new screen
import com.picpose.bestphotographyapp.ui.details.AiPromptDetailsScreen
import com.picpose.bestphotographyapp.ui.details.AiPromptDetailsViewModel
import com.picpose.bestphotographyapp.data.PromptRepository

// In NavGraph composable, update the PromptDetail route:
composable(
    route = Screen.PromptDetail.route,
    arguments = listOf(navArgument("promptId") { type = NavType.StringType })
) { backStackEntry ->
    val promptId = backStackEntry.arguments?.getString("promptId").orEmpty()
    
    // Create ViewModel with repository
    val viewModel: AiPromptDetailsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AiPromptDetailsViewModel(
                    repository = PromptRepository()
                ) as T
            }
        }
    )
    
    AiPromptDetailsScreen(
        promptId = promptId,
        viewModel = viewModel,
        onBack = { navController.popBackStack() },
        onPromptClick = { newPromptId ->
            navController.navigate(Screen.PromptDetail.createRoute(newPromptId)) {
                launchSingleTop = true
            }
        },
        onTagClick = { tag ->
            navController.navigate(Screen.TagPrompts.createRoute(tag)) {
                launchSingleTop = true
            }
        }
    )
}
```

### Option 2: Side-by-Side Testing

Keep both implementations and create a new route:

```kotlin
// In Screen.kt
object NewPromptDetail : Screen("new_prompt_detail/{promptId}") {
    private const val BASE = "new_prompt_detail"
    fun createRoute(promptId: String) = "$BASE/$promptId"
}

// In NavGraph.kt - add new composable
composable(
    route = Screen.NewPromptDetail.route,
    arguments = listOf(navArgument("promptId") { type = NavType.StringType })
) { backStackEntry ->
    // ... same as Option 1
}
```

## Using Real Data Instead of Mock

Replace the `PromptRepository` implementation with your existing `HomeRepository`:

```kotlin
// In AiPromptDetailsViewModel initialization
val viewModel: AiPromptDetailsViewModel = viewModel(
    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            val context = LocalContext.current
            val homeRepo = HomeRepository(context)
            @Suppress("UNCHECKED_CAST")
            return AiPromptDetailsViewModel(
                repository = homeRepo // Use HomeRepository instead of PromptRepository
            ) as T
        }
    }
)
```

Then update `AiPromptDetailsViewModel` to use `HomeRepository`:

```kotlin
class AiPromptDetailsViewModel(
    private val repository: HomeRepository, // Changed type
    private val adManager: AdManager = AdManager.getInstance()
) : ViewModel() {
    // Update loadPromptById to use repository.getPromptById()
    // Update loadSimilarPrompts to use repository.getSimilarPrompts() or similar
    // Update loadAppSettings to use repository.getAppSettings()
}
```

## AdMob Configuration

### 1. Initialize AdManager in Application class

```kotlin
// In your Application class (e.g., PicPoseApplication.kt)
class PicPoseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdMob
        MobileAds.initialize(this)
        
        // Initialize AdManager with settings
        lifecycleScope.launch {
            val repository = HomeRepository(this@PicPoseApplication)
            repository.getAppSettings().collect { result ->
                result.onSuccess { settings ->
                    AdManager.getInstance().initialize(settings, clickFrequency = 3)
                }
            }
        }
    }
}
```

### 2. Update Ad Unit IDs

Replace test ad IDs with your production ad IDs in your server's `AppSettings` response:

```json
{
  "success": true,
  "data": {
    "app_id": "ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY",
    "interstitial1_id": "ca-app-pub-XXXXXXXXXXXXXXXX/1111111111",
    "interstitial2_id": "ca-app-pub-XXXXXXXXXXXXXXXX/2222222222",
    "native1_id": "ca-app-pub-XXXXXXXXXXXXXXXX/3333333333",
    "native2_id": "ca-app-pub-XXXXXXXXXXXXXXXX/4444444444",
    "native3_id": "ca-app-pub-XXXXXXXXXXXXXXXX/5555555555"
  }
}
```

### 3. Test Ads During Development

The implementation automatically falls back to Google's test ad IDs when server IDs are empty:
- Interstitial: `ca-app-pub-3940256099942544/1033173712`
- Native: `ca-app-pub-3940256099942544/2247696110`

## Customization

### Change Ad Frequency

Update the click frequency in AdManager initialization:

```kotlin
adManager.initialize(settings, clickFrequency = 2) // Show ad every 2 clicks
```

### Customize Native Ad Layout

Edit `NativeAdComposable.kt` `createNativeAdView()` function to change the layout.

### Adjust Animations

Modify animation parameters in `AiPromptDetailsScreen.kt`:

```kotlin
AnimatedContent(
    targetState = currentPromptId,
    transitionSpec = {
        // Customize animation duration and type
        fadeIn(animationSpec = tween(700)) with fadeOut(animationSpec = tween(400))
    }
)
```

### Change Similar Prompts Page Size

Update the constant in `AiPromptDetailsViewModel.kt`:

```kotlin
private const val SIMILAR_PROMPTS_PAGE_SIZE = 10 // Default is 5
```

## Features Checklist

✅ Interstitial ads on similar prompt clicks (configurable frequency)
✅ Native ad between content and similar prompts  
✅ Vertical scrollable list with "Load More" for similar prompts
✅ Smooth crossfade animation on prompt reload
✅ Automatic scroll reset to top on navigation
✅ Full-screen zoomable image viewer
✅ Integration with AppSettings for ad IDs
✅ Fallback to test ad IDs when server IDs missing
✅ Proper error handling and loading states
✅ Lifecycle-aware ad management
✅ Haptic feedback on user actions
✅ Copy prompt to clipboard
✅ Tag navigation support
✅ Favorite toggle support (ready for implementation)

## Testing

1. **Test Ad Loading**: Verify ads load using test IDs
2. **Test Ad Frequency**: Click similar prompts and verify ad shows every 3 clicks
3. **Test Navigation**: Verify smooth transitions between prompts
4. **Test Scroll Reset**: Verify scroll position resets when navigating to new prompt
5. **Test Error States**: Verify graceful handling when prompt not found
6. **Test Image Zoom**: Verify pinch-to-zoom works in full-screen dialog
7. **Test Load More**: Verify pagination loads additional similar prompts

## Troubleshooting

### Ads not showing
- Check internet connection
- Verify ad unit IDs are correct
- Check AdMob account is active
- Test with Google's test ad IDs first

### Compilation errors
- Ensure all dependencies are added to build.gradle
- Sync Gradle files
- Clean and rebuild project

### Navigation not working
- Verify Screen routes are correctly configured
- Check NavGraph has the correct composable
- Ensure promptId is being passed correctly

## Future Enhancements

- [ ] Add banner ads at bottom of screen
- [ ] Implement rewarded ads for premium content
- [ ] Add analytics tracking for ad impressions
- [ ] Implement ad mediation for better fill rates
- [ ] Add A/B testing for ad frequency
- [ ] Cache similar prompts locally
- [ ] Add share functionality
- [ ] Implement deep linking for prompts

## Support

For issues or questions, please refer to:
- AdMob documentation: https://developers.google.com/admob/android/quick-start
- Jetpack Compose documentation: https://developer.android.com/jetpack/compose
- This repository's issue tracker

---

**Note**: This implementation uses mock data in `PromptRepository`. Replace with your actual API/database calls for production use.
