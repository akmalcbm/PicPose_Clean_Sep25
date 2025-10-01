# Login + Settings Module - Architecture Diagram

## Overall Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Presentation Layer                    │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ LoginScreen  │  │ SettingsScreen│ │ProfileScreen │     │
│  └──────┬───────┘  └──────┬────────┘  └──────┬───────┘     │
│         │                  │                   │             │
│         └──────────────────┼───────────────────┘             │
│                           │                                 │
│         ┌─────────────────┴────────────────┐               │
│         │                                   │               │
│  ┌──────▼────────┐              ┌──────────▼────────┐      │
│  │ AuthViewModel │              │ SettingsViewModel │      │
│  └──────┬────────┘              └──────────┬────────┘      │
└─────────┼────────────────────────────────────┼──────────────┘
          │                                    │
┌─────────▼────────────────────────────────────▼──────────────┐
│                        Domain Layer                          │
│                                                              │
│  ┌────────────────┐              ┌────────────────┐        │
│  │ AuthRepository │              │SettingsManager │        │
│  └────────┬───────┘              └────────┬───────┘        │
│           │                                │                │
│           │                      ┌─────────▼────────┐      │
│           │                      │UserSessionManager│      │
│           │                      └──────────────────┘      │
└───────────┼──────────────────────────────────────────────────┘
            │
┌───────────▼──────────────────────────────────────────────────┐
│                         Data Layer                           │
│                                                              │
│  ┌─────────────┐   ┌──────────────┐   ┌────────────────┐   │
│  │ Firebase    │   │ UserApiService│   │   DataStore    │   │
│  │ Auth        │   │  (Retrofit)   │   │  (Preferences) │   │
│  └─────────────┘   └──────────────┘   └────────────────┘   │
│         │                  │                   │            │
└─────────┼──────────────────┼───────────────────┼────────────┘
          │                  │                   │
          ▼                  ▼                   ▼
    Google/FB/Twitter    Backend API        Local Storage
```

## Authentication Flow

```
┌──────────┐
│  Splash  │
│  Screen  │
└────┬─────┘
     │
     ▼
┌─────────────────┐
│Check Auth Status│
│(UserSession     │
│ DataStore)      │
└────┬─────┬──────┘
     │     │
  No │     │ Yes
     │     │
     ▼     ▼
┌─────────┐ ┌──────┐
│ Login   │ │ Home │
│ Screen  │ │Screen│
└────┬────┘ └──────┘
     │
     │ Login Success
     ▼
┌──────────────┐
│Save Session  │
│(DataStore)   │
└──────┬───────┘
       │
       ▼
   ┌──────┐
   │ Home │
   │Screen│
   └──────┘
```

## Login Screen Flow

```
┌─────────────────────────────────────────────────┐
│              Login Screen                       │
│                                                 │
│  ┌───────────────────────────────────────┐    │
│  │         Email/Password Fields          │    │
│  └─────────────────┬─────────────────────┘    │
│                    │                            │
│                    ▼                            │
│         ┌──────────────────────┐               │
│         │  Login/Register Btn  │               │
│         └──────────┬───────────┘               │
│                    │                            │
│         ┌──────────▼───────────┐               │
│         │   AuthViewModel      │               │
│         │   .login() or        │               │
│         │   .register()        │               │
│         └──────────┬───────────┘               │
│                    │                            │
│  ┌─────────────────┴─────────────────┐        │
│  │                                    │        │
│  ▼                                    ▼        │
│ Manual Auth                    Social Auth     │
│  │                                    │        │
│  ▼                                    ▼        │
│ Backend API                   Firebase Auth    │
│ (Retrofit)                    (Google/FB/X)    │
│  │                                    │        │
│  └────────────┬───────────────────────┘        │
│               │                                │
│               ▼                                │
│    ┌──────────────────────┐                   │
│    │  Save to DataStore   │                   │
│    └──────────┬───────────┘                   │
│               │                                │
│               ▼                                │
│      Navigate to Home                         │
└───────────────────────────────────────────────┘
```

## Settings Screen Flow

```
┌────────────────────────────────────────────────┐
│            Settings Screen                     │
│                                                │
│  ┌──────────────────────────────────────┐    │
│  │      User Profile Section             │    │
│  │  (Display current user info)          │    │
│  └──────────────────────────────────────┘    │
│                                                │
│  ┌──────────────────────────────────────┐    │
│  │      Dark Mode Toggle                 │    │
│  └─────────────┬────────────────────────┘    │
│                │                               │
│                ▼                               │
│  ┌─────────────────────────────┐             │
│  │   SettingsViewModel         │             │
│  │   .setDarkMode(enabled)     │             │
│  └─────────────┬─────────────────             │
│                │                               │
│                ▼                               │
│  ┌─────────────────────────────┐             │
│  │   SettingsManager            │             │
│  │   (DataStore)                │             │
│  └─────────────┬─────────────────             │
│                │                               │
│                ▼                               │
│      Save to DataStore                        │
│                │                               │
│                ▼                               │
│   ┌────────────────────────┐                  │
│   │   Update Theme in      │                  │
│   │   MainActivity         │                  │
│   └────────────────────────┘                  │
│                                                │
│  Similar flows for:                           │
│  - Language Selection                         │
│  - Notifications Toggle                       │
│  - Logout                                     │
└────────────────────────────────────────────────┘
```

## Data Flow

```
UI Layer (Compose)
    ↕ (State Flows)
ViewModel Layer
    ↕ (suspend functions / Flow)
Repository Layer
    ↕ (API calls / DataStore operations)
Data Sources
    ├─ Firebase Auth (Google/FB/Twitter)
    ├─ Backend API (Manual login/register)
    └─ DataStore (Session & Preferences)
```

## State Management

```
┌──────────────────────────────────────┐
│         AuthViewModel State          │
│                                      │
│  sealed class AuthState {            │
│    object Idle                       │
│    object Loading                    │
│    data class Success(user: User)    │
│    data class Error(message: String) │
│  }                                   │
│                                      │
│  StateFlow<AuthState>                │
│  StateFlow<Boolean> isLoggedIn      │
│  StateFlow<User?> currentUser       │
└──────────────────────────────────────┘

┌──────────────────────────────────────┐
│      SettingsViewModel State         │
│                                      │
│  StateFlow<Boolean> isDarkMode       │
│  StateFlow<String> language          │
│  StateFlow<Boolean> notificationsOn  │
└──────────────────────────────────────┘
```

## Dependency Injection (Hilt)

```
┌───────────────────────────────────────┐
│         Application Scope             │
│                                       │
│  @Singleton                           │
│  ├─ UserSessionManager                │
│  ├─ SettingsManager                   │
│  └─ AuthRepository                    │
└───────────────────────────────────────┘
            │
            ▼
┌───────────────────────────────────────┐
│         ViewModel Scope               │
│                                       │
│  @HiltViewModel                       │
│  ├─ AuthViewModel                     │
│  └─ SettingsViewModel                 │
└───────────────────────────────────────┘
            │
            ▼
┌───────────────────────────────────────┐
│         Composable Screens            │
│                                       │
│  @Composable                          │
│  ├─ LoginScreen                       │
│  ├─ SettingsScreen                    │
│  └─ ProfileScreen                     │
└───────────────────────────────────────┘
```

## File Structure

```
app/src/main/
├── java/com/picpose/bestphotographyapp/
│   ├── data/
│   │   ├── datastore/
│   │   │   ├── UserSessionManager.kt
│   │   │   └── SettingsManager.kt
│   │   ├── models/
│   │   │   └── UserModels.kt
│   │   ├── network/
│   │   │   └── UserApiService.kt
│   │   └── repository/
│   │       └── AuthRepository.kt
│   ├── presentation/
│   │   ├── viewmodels/
│   │   │   ├── AuthViewModel.kt
│   │   │   └── SettingsViewModel.kt
│   │   ├── screens/
│   │   │   ├── LoginScreen.kt
│   │   │   ├── SettingsScreen.kt
│   │   │   ├── ProfileScreen.kt (modified)
│   │   │   └── SplashScreen.kt (modified)
│   │   ├── navigation/
│   │   │   ├── Screen.kt (modified)
│   │   │   └── NavGraph.kt (modified)
│   │   └── MainActivity.kt (modified)
│   ├── ui/theme/
│   │   └── PicPoseTheme.kt (modified)
│   └── di/
│       └── DataStoreModule.kt
├── res/
│   ├── values/
│   │   └── strings.xml (modified)
│   └── values-hi/
│       └── strings.xml (new)
└── google-services.json (placeholder)
```

## Technology Stack

```
┌─────────────────────────────────────────┐
│          UI Framework                   │
│  • Jetpack Compose                      │
│  • Material Design 3                    │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│          Architecture                   │
│  • MVVM Pattern                         │
│  • Clean Architecture                   │
│  • Unidirectional Data Flow             │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│       Dependency Injection              │
│  • Hilt                                 │
│  • Dagger                               │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│          Data Persistence               │
│  • DataStore (Preferences)              │
│  • Encrypted SharedPreferences          │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│          Networking                     │
│  • Retrofit                             │
│  • OkHttp                               │
│  • Gson                                 │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│        Authentication                   │
│  • Firebase Auth                        │
│  • Google Sign-In                       │
│  • Facebook SDK                         │
│  • Custom Backend API                   │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│        Reactive Programming             │
│  • Kotlin Coroutines                    │
│  • Flow                                 │
│  • StateFlow                            │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│          Navigation                     │
│  • Navigation Compose                   │
│  • Type-safe routing                    │
└─────────────────────────────────────────┘
```

## Security Features

```
┌──────────────────────────────────────────┐
│         Security Measures                │
│                                          │
│  ✓ HTTPS for all API calls              │
│  ✓ Token-based authentication           │
│  ✓ Encrypted DataStore                  │
│  ✓ No plaintext password storage        │
│  ✓ Firebase Auth security               │
│  ✓ Session timeout handling             │
│  ✓ Secure credential transmission       │
└──────────────────────────────────────────┘
```

This architecture follows Android best practices and modern development patterns for scalability, maintainability, and testability.
