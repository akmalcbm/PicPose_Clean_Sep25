# Architecture Diagram - Enhanced AI Prompt Details Screen

```
┌─────────────────────────────────────────────────────────────────────┐
│                         User Interaction Layer                       │
│                      (AiPromptDetailsScreen.kt)                      │
│                                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │  Image View  │  │ Prompt Card  │  │  Native Ad   │              │
│  │  (Zoomable)  │  │  Details     │  │   Section    │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────┐        │
│  │          Similar Prompts (Vertical List)                │        │
│  │  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐       │        │
│  │  │Prompt 1│  │Prompt 2│  │Prompt 3│  │  ...   │       │        │
│  │  └────────┘  └────────┘  └────────┘  └────────┘       │        │
│  │                    [Load More Button]                   │        │
│  └─────────────────────────────────────────────────────────┘        │
│                                                                       │
└───────────────────────┬───────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     ViewModel Layer                                  │
│                (AiPromptDetailsViewModel.kt)                         │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────┐        │
│  │                    UI State Flow                         │        │
│  │  • isLoading: Boolean                                    │        │
│  │  • currentPrompt: AIPrompt?                              │        │
│  │  • similarPrompts: List<AIPrompt>                        │        │
│  │  • error: String?                                        │        │
│  │  • appSettings: AppSettings?                             │        │
│  │  • showAdLoader: Boolean                                 │        │
│  └─────────────────────────────────────────────────────────┘        │
│                                                                       │
│  Functions:                                                          │
│  • loadPromptById(promptId: String)                                  │
│  • loadSimilarPrompts(category: String, excludeId: String)           │
│  • onSimilarPromptClicked() → increment ad counter                   │
│  • shouldShowInterstitial() → check if ad should be shown            │
│  • loadMoreSimilarPrompts() → pagination                             │
│                                                                       │
└───────────────┬───────────────────────────┬───────────────────────────┘
                │                           │
                ▼                           ▼
┌──────────────────────────┐   ┌──────────────────────────┐
│    Data Layer            │   │    Ad Management         │
│  (PromptRepository.kt)   │   │    (AdManager.kt)        │
│                          │   │                          │
│  • getPromptById()       │   │  • initialize()          │
│  • getSimilarPrompts()   │   │  • preloadAds()          │
│  • getAllPrompts()       │   │  • incrementClickCount() │
│  • getAppSettings()      │   │  • shouldShowInter...()  │
│                          │   │  • showInterstitialAn... │
│  Returns: Flow<Result<T>>│   │                          │
└───────────┬──────────────┘   └────────────┬─────────────┘
            │                               │
            │                               │
            ▼                               ▼
┌────────────────────────┐      ┌────────────────────────┐
│   Network/Database     │      │   AdMob SDK            │
│                        │      │                        │
│  • API Service         │      │  • InterstitialAd      │
│  • Room Database       │      │  • NativeAd            │
│  • Shared Preferences  │      │  • AdLoader            │
└────────────────────────┘      └────────────────────────┘


═══════════════════════════════════════════════════════════════════════
                            User Flow Diagram
═══════════════════════════════════════════════════════════════════════

User Opens Prompt Details
           │
           ▼
┌──────────────────────┐
│  Load Prompt Data    │
│  Load Similar Items  │
│  Load Native Ad      │
│  Preload Interstitial│
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  Display Content     │
│  • Image             │
│  • Details           │
│  • Native Ad         │
│  • Similar Prompts   │
└──────────┬───────────┘
           │
           │  User clicks Similar Prompt
           ▼
    ┌─────────────┐
    │ Increment   │
    │ Click Count │
    └──────┬──────┘
           │
           ▼
    ┌─────────────────┐
    │ Should Show Ad? │ ──No──┐
    └──────┬──────────┘       │
           │ Yes              │
           ▼                  │
    ┌─────────────────┐       │
    │  Show Loading   │       │
    │  Show Ad        │       │
    │  Wait Dismiss   │       │
    └──────┬──────────┘       │
           │                  │
           ▼                  ▼
    ┌──────────────────────────┐
    │  Navigate to New Prompt  │
    │  • Animate Transition    │
    │  • Reset Scroll to Top   │
    │  • Load New Data         │
    └──────────────────────────┘


═══════════════════════════════════════════════════════════════════════
                        Ad Integration Flow
═══════════════════════════════════════════════════════════════════════

App Launch
    │
    ▼
┌──────────────────────┐
│ Initialize AdManager │
│ • Load AppSettings   │
│ • Set Ad Unit IDs    │
│ • Set Frequency (3)  │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  Preload Ads         │
│  • Interstitial 1    │
│  • Interstitial 2    │
└──────────┬───────────┘
           │
           │  User clicks Similar Prompt #1
           ▼
    Click Count = 1 ──No Ad──▶ Navigate
           │
           │  User clicks Similar Prompt #2
           ▼
    Click Count = 2 ──No Ad──▶ Navigate
           │
           │  User clicks Similar Prompt #3
           ▼
    Click Count = 3 ──Show Ad──▶ Wait ──▶ Navigate
           │                      │
           │                      ▼
           │              ┌──────────────┐
           │              │ Reload Next  │
           │              │ Interstitial │
           │              └──────────────┘
           │
           │  User clicks Similar Prompt #4
           ▼
    Click Count = 4 ──No Ad──▶ Navigate
           │
           │  User clicks Similar Prompt #5
           ▼
    Click Count = 5 ──No Ad──▶ Navigate
           │
           │  User clicks Similar Prompt #6
           ▼
    Click Count = 6 ──Show Ad──▶ Wait ──▶ Navigate
                                  │
                                  ▼
                          (Pattern Repeats)


═══════════════════════════════════════════════════════════════════════
                        Native Ad Display
═══════════════════════════════════════════════════════════════════════

┌─────────────────────────────────────────────────┐
│            Native Ad Card Layout                │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │         Media View (Image/Video)          │ │
│  │                                           │ │
│  └───────────────────────────────────────────┘ │
│                                                 │
│  Headline Text (Bold, 16sp)                     │
│                                                 │
│  Body Text (Regular, 14sp)                      │
│  Multiple lines of description text...          │
│                                                 │
│  ┌─────────────────────┐                       │
│  │  Call-to-Action     │                       │
│  │     Button          │                       │
│  └─────────────────────┘                       │
│                                                 │
└─────────────────────────────────────────────────┘

Positioned between:
- Full Prompt Card (above)
- Tags Section (below)
- Similar Prompts (further below)


═══════════════════════════════════════════════════════════════════════
                        Component Hierarchy
═══════════════════════════════════════════════════════════════════════

Scaffold
 └─ TopAppBar
     └─ Back Button
     └─ Share Button  
     └─ Favorite Button
 └─ SnackbarHost
 └─ Box (Main Content)
     └─ AnimatedContent (Crossfade Transition)
         └─ LazyColumn
             ├─ Header Image (Clickable → FullScreenImageDialog)
             ├─ Prompt Details Card
             │   ├─ Title
             │   ├─ Stats (Likes, Tags)
             │   └─ Short Prompt
             ├─ Full Prompt Card
             │   ├─ Full Prompt Text
             │   └─ Copy Button
             ├─ Native Ad Card
             │   └─ NativeAdSection (Composable)
             ├─ Tags Card (if tags exist)
             │   └─ FlowRow of Tag Chips
             └─ Similar Prompts Section
                 ├─ Section Title
                 ├─ Vertical List of Prompt Cards
                 │   └─ SimilarPromptCardVertical (onClick)
                 └─ Load More Button (if has more)
     └─ Ad Loading Overlay (if showing ad)
         └─ CircularProgressIndicator


═══════════════════════════════════════════════════════════════════════
                        State Management
═══════════════════════════════════════════════════════════════════════

PromptUiState {
    isLoading: Boolean           ──▶ Show loading indicator
    currentPrompt: AIPrompt?     ──▶ Display prompt content
    similarPrompts: List         ──▶ Display similar items
    error: String?               ──▶ Show error snackbar
    isLoadingMore: Boolean       ──▶ Show loading in list
    appSettings: AppSettings?    ──▶ Configure ad unit IDs
    showAdLoader: Boolean        ──▶ Show ad loading overlay
}

State Updates:
    • loadPromptById() → isLoading=true → fetch → currentPrompt=data
    • loadSimilarPrompts() → fetch → similarPrompts=data
    • onSimilarPromptClicked() → increment counter
    • showInterstitial() → showAdLoader=true → show ad → showAdLoader=false


═══════════════════════════════════════════════════════════════════════
                        File Dependencies
═══════════════════════════════════════════════════════════════════════

AiPromptDetailsScreen.kt
 ├─ Depends on: AiPromptDetailsViewModel
 ├─ Depends on: NativeAdSection
 ├─ Depends on: FullScreenImageDialog
 ├─ Depends on: AdManager
 └─ Depends on: AIPrompt (data model)

AiPromptDetailsViewModel.kt
 ├─ Depends on: PromptRepository
 ├─ Depends on: AdManager
 ├─ Depends on: AIPrompt (data model)
 └─ Depends on: AppSettings (data model)

PromptRepository.kt
 ├─ Depends on: AIPrompt (data model)
 └─ Depends on: AppSettings (data model)

AdManager.kt
 ├─ Depends on: AppSettings (data model)
 └─ Depends on: Google AdMob SDK

NativeAdComposable.kt
 └─ Depends on: Google AdMob SDK

FullScreenImageDialog.kt
 └─ Depends on: Coil image library


═══════════════════════════════════════════════════════════════════════
```
