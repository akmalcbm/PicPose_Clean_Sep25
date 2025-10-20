# Quick Start - Using Enhanced AI Prompt Details Screen

## Minimal Integration Example

### 1. Add to Navigation Graph

```kotlin
// In NavGraph.kt, import the new components
import com.picpose.bestphotographyapp.ui.details.AiPromptDetailsScreen
import com.picpose.bestphotographyapp.ui.details.AiPromptDetailsViewModel
import com.picpose.bestphotographyapp.data.PromptRepository

// Add or update the route
composable(
    route = Screen.PromptDetail.route,
    arguments = listOf(navArgument("promptId") { type = NavType.StringType })
) { backStackEntry ->
    val promptId = backStackEntry.arguments?.getString("promptId").orEmpty()
    
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
            // Navigate to tag screen or handle as needed
            // navController.navigate(Screen.TagPrompts.createRoute(tag))
        }
    )
}
```

### 2. Initialize AdMob in Application Class

```kotlin
// In PicPoseApplication.kt or your Application class
class PicPoseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Google Mobile Ads SDK
        MobileAds.initialize(this) { initStatus ->
            Log.d("AdMob", "Initialized: ${initStatus.adapterStatusMap}")
        }
    }
}
```

### 3. Navigate to Details Screen

```kotlin
// From any screen, navigate to details
Button(
    onClick = {
        navController.navigate(Screen.PromptDetail.createRoute(promptId))
    }
) {
    Text("View Prompt Details")
}
```

## Complete Working Example

Here's a complete example showing how to integrate the screen:

```kotlin
// File: MainActivity.kt or wherever you set up navigation
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "prompt_list"
    ) {
        // List Screen
        composable("prompt_list") {
            PromptListScreen(
                onPromptClick = { promptId ->
                    navController.navigate(Screen.PromptDetail.createRoute(promptId))
                }
            )
        }
        
        // Enhanced Details Screen
        composable(
            route = "prompt_detail/{promptId}",
            arguments = listOf(navArgument("promptId") { type = NavType.StringType })
        ) { backStackEntry ->
            val promptId = backStackEntry.arguments?.getString("promptId") ?: return@composable
            
            // Create ViewModel
            val viewModel: AiPromptDetailsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return AiPromptDetailsViewModel(
                            repository = PromptRepository()
                        ) as T
                    }
                }
            )
            
            // Show Details Screen
            AiPromptDetailsScreen(
                promptId = promptId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPromptClick = { newPromptId ->
                    navController.navigate("prompt_detail/$newPromptId") {
                        launchSingleTop = true
                    }
                },
                onTagClick = { tag ->
                    // Handle tag click
                    Log.d("Navigation", "Tag clicked: $tag")
                }
            )
        }
    }
}
```

## Testing with Mock Data

The implementation comes with mock data in `PromptRepository.kt`. You can test immediately:

```kotlin
// 1. Run the app
// 2. Navigate to any prompt (IDs 1-10 are available)
// 3. Test features:
//    - View prompt details
//    - Click image to zoom
//    - Copy prompt to clipboard  
//    - Click similar prompts (watch for ad after 3 clicks)
//    - Click "Load More" to load additional similar prompts
//    - Click tags
```

## Customizing Mock Data

Edit `PromptRepository.kt` to add your own test data:

```kotlin
private val mockPrompts = listOf(
    AIPrompt(
        id = "custom-1",
        title = "Your Custom Prompt",
        shortPrompt = "A brief description",
        fullPrompt = "The complete prompt text here...",
        imageUrl = "https://your-image-url.com/image.jpg",
        category = "YourCategory",
        tags = listOf("tag1", "tag2", "tag3"),
        likes = 100,
        isPopular = true,
        isFavorite = false
    ),
    // Add more prompts...
)
```

## Configuration Options

### Ad Frequency

Change how often ads are shown:

```kotlin
// In loadAppSettings() in AiPromptDetailsViewModel
adManager.initialize(settings, clickFrequency = 2) // Show every 2 clicks
```

### Similar Prompts Page Size

Change the number of items loaded:

```kotlin
// In AiPromptDetailsViewModel.kt
private const val SIMILAR_PROMPTS_PAGE_SIZE = 10 // Change from 5 to 10
```

### Native Ad Customization

Customize the native ad appearance in `NativeAdComposable.kt`:

```kotlin
// Change media view height
val mediaView = MediaView(context).apply {
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        (240 * context.resources.displayMetrics.density).toInt() // Changed from 180
    )
}
```

## Troubleshooting

### "Prompt not found" error
- Check that the promptId exists in mock data (1-10)
- Verify navigation is passing the ID correctly

### Ads not showing
- Ads use test IDs by default and should work immediately
- Check internet connection
- Allow a few seconds for ad to load
- Check Logcat for "AdManager" tags

### Navigation not working
- Ensure Screen.PromptDetail.createRoute() is defined in Screen.kt
- Check navController is passed correctly
- Verify route strings match

### Build errors
- Run `./gradlew clean build`
- Sync Gradle files
- Check all dependencies are added

## Next Steps

1. **Replace Mock Data**: Connect to your real API/database
2. **Add Production Ad IDs**: Update AppSettings in your server
3. **Customize UI**: Adjust colors, spacing, fonts to match your design
4. **Add Analytics**: Track user interactions and ad impressions
5. **Test on Device**: Test on real Android devices, not just emulator

## Support

- See `AI_PROMPT_DETAILS_IMPLEMENTATION_GUIDE.md` for detailed documentation
- See `ARCHITECTURE_AI_PROMPT_DETAILS.md` for architecture details
- Check existing code in `presentation/screens/AIPromptDetailScreen.kt` for reference

## Example Test Scenario

```kotlin
// Test the complete flow:
fun testAiPromptDetailsFlow() {
    // 1. Launch app
    // 2. Navigate to prompt with ID "1"
    navController.navigate(Screen.PromptDetail.createRoute("1"))
    
    // 3. Verify prompt loads
    // 4. Scroll down to see similar prompts
    // 5. Click first similar prompt → navigates, no ad
    // 6. Click second similar prompt → navigates, no ad  
    // 7. Click third similar prompt → shows ad, then navigates
    // 8. Click fourth similar prompt → navigates, no ad
    // 9. Repeat to verify ad shows every 3 clicks
}
```

Happy coding! 🚀
