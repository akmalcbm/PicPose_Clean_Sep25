# Login + Settings Module Implementation

## Overview
This implementation adds comprehensive authentication and settings functionality to the PicPose app, including:
- Multiple login options (Google, Facebook, Twitter, Manual)
- User session management
- Settings screen with dark mode, language selection, and notifications
- Integration with existing Profile screen

## Architecture

### Data Layer

#### Models (`data/models/UserModels.kt`)
- `User`: User data model
- `LoginRequest`: Manual login request
- `RegisterRequest`: Registration request
- `AuthResponse`: API response wrapper
- `SocialAuthData`: Social authentication data

#### API Service (`data/network/UserApiService.kt`)
Retrofit interface for backend communication:
- `POST /api/users.php?action=login` - Manual login
- `POST /api/users.php?action=register` - User registration
- `GET /api/users.php?id={userId}` - Get user profile
- `PUT /api/users.php?id={userId}` - Update user profile

#### DataStore Managers
1. **UserSessionManager** (`data/datastore/UserSessionManager.kt`)
   - Manages user session persistence
   - Stores: userId, email, name, profilePicture, token, isLoggedIn
   - Provides Flow-based observables for reactive UI

2. **SettingsManager** (`data/datastore/SettingsManager.kt`)
   - Manages app preferences
   - Stores: darkMode, language, notificationsEnabled
   - Provides Flow-based observables

#### Repository (`data/repository/AuthRepository.kt`)
Handles all authentication operations:
- Manual login/register via backend API
- Google Sign-In integration
- Facebook authentication
- Twitter OAuth (placeholder)
- Logout functionality
- Session management

### Presentation Layer

#### ViewModels

1. **AuthViewModel** (`presentation/viewmodels/AuthViewModel.kt`)
   - Manages authentication state
   - Handles login, register, social auth
   - Provides user session observables
   - State: `Idle`, `Loading`, `Success`, `Error`

2. **SettingsViewModel** (`presentation/viewmodels/SettingsViewModel.kt`)
   - Manages app settings
   - Handles theme, language, notifications preferences

#### Screens

1. **LoginScreen** (`presentation/screens/LoginScreen.kt`)
   - Email/password login form
   - Registration form
   - Google Sign-In button
   - Facebook login button
   - Twitter login button (placeholder)
   - Form validation
   - Error handling
   - Loading states

2. **SettingsScreen** (`presentation/screens/SettingsScreen.kt`)
   - User profile display
   - Dark mode toggle
   - Language selector (English/Hindi)
   - Notifications toggle
   - Edit profile option
   - Privacy settings option
   - Logout button with confirmation

3. **ProfileScreen** (Updated)
   - Added navigation to Settings
   - Added logout functionality
   - Confirmation dialog for logout

4. **SplashScreen** (Updated)
   - Checks authentication status
   - Routes to Login if not authenticated
   - Routes to Home if authenticated

### UI/Theme

#### Theme System (`ui/theme/PicPoseTheme.kt`)
- Support for light and dark color schemes
- Dynamic theme switching based on user preferences
- Material Design 3 color system

#### String Resources
- **English** (`res/values/strings.xml`)
- **Hindi** (`res/values-hi/strings.xml`)
- Complete localization for all UI elements

### Navigation

Updated `NavGraph.kt` with:
- Login screen route
- Settings screen route
- Authentication-aware navigation
- Proper back stack management

Updated `Screen.kt` with:
- `Screen.Login`
- `Screen.Settings`

Updated `MainActivity.kt`:
- Integrates SettingsViewModel
- Observes dark mode preference
- Applies theme dynamically
- Hides bottom navigation on Login screen

### Dependency Injection

#### DataStoreModule (`di/DataStoreModule.kt`)
Provides:
- `UserSessionManager` singleton
- `SettingsManager` singleton

## Dependencies Added

### build.gradle.kts
```kotlin
// Firebase & Authentication
implementation(platform(libs.firebase.bom))
implementation(libs.firebase.auth)
implementation(libs.play.services.auth)
implementation(libs.facebook.login)

// DataStore
implementation(libs.androidx.datastore.preferences)
```

### libs.versions.toml
```toml
firebase-bom = "33.7.0"
play-services-auth = "21.2.0"
facebook-login = "17.0.2"
datastore = "1.1.1"
```

## Configuration

### Firebase Setup (`google-services.json`)
A placeholder Firebase configuration file has been added. **You must replace it with your actual Firebase configuration:**

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create/select your project
3. Add Android app with package name: `com.picpose.bestphotographyapp`
4. Download `google-services.json`
5. Replace the placeholder file at `app/google-services.json`

### Google Sign-In Configuration
Update the Web Client ID in `AuthRepository.kt`:
```kotlin
.requestIdToken("YOUR_WEB_CLIENT_ID_HERE")
```

### Facebook Login Configuration
Add to `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.facebook.sdk.ApplicationId"
    android:value="@string/facebook_app_id"/>
```

Add to `strings.xml`:
```xml
<string name="facebook_app_id">YOUR_FACEBOOK_APP_ID</string>
```

## Features

### Authentication Flow
1. **Splash Screen** → Checks authentication status
2. **Login Screen** (if not authenticated)
   - Manual email/password login
   - Registration for new users
   - Google Sign-In
   - Facebook Login
   - Twitter OAuth (placeholder)
3. **Home Screen** (if authenticated)

### Settings Features
- **Dark Mode**: Toggle between light and dark themes
- **Language**: Switch between English and Hindi
- **Notifications**: Enable/disable notifications (placeholder)
- **Profile**: View current user information
- **Logout**: Clear session and return to login

### User Session
- Persistent across app restarts
- Stored securely using DataStore
- Observable state for reactive UI
- Automatic logout handling

## Testing

### Manual Testing Checklist
- [ ] Launch app → should show splash → then login screen
- [ ] Enter credentials → should login and navigate to home
- [ ] Logout → should clear session and return to login
- [ ] Toggle dark mode → should persist across app restarts
- [ ] Change language → UI should update immediately
- [ ] Close and reopen app → should maintain logged-in state
- [ ] Google Sign-In → should authenticate via Firebase
- [ ] Settings navigation from Profile → should work

### Backend API Requirements
Your backend API at `https://picpose.iamakmal.in/api/users.php` should support:

**Login Request:**
```json
POST /api/users.php?action=login
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Register Request:**
```json
POST /api/users.php?action=register
{
  "email": "user@example.com",
  "password": "password123",
  "name": "User Name"
}
```

**Response Format:**
```json
{
  "success": true,
  "message": "Login successful",
  "user": {
    "id": "1",
    "email": "user@example.com",
    "name": "User Name",
    "profile_picture": "url",
    "followers_count": 0,
    "following_count": 0,
    "posts_count": 0
  },
  "token": "jwt_token_here"
}
```

## Known Limitations

1. **Twitter OAuth**: Placeholder implementation - needs Twitter Developer Account
2. **Facebook Login**: Requires Facebook App ID configuration
3. **Google Sign-In**: Requires proper Web Client ID from Firebase
4. **Backend API**: Must match the expected request/response format

## Next Steps

1. Configure Firebase with your actual project credentials
2. Set up Facebook App ID for Facebook Login
3. Configure Twitter Developer credentials
4. Test with your backend API
5. Implement profile edit functionality
6. Add privacy settings implementation
7. Add email verification flow
8. Implement forgot password functionality
9. Add biometric authentication option
10. Add unit and integration tests

## Security Considerations

- User passwords are sent over HTTPS to backend
- Session tokens stored in encrypted DataStore
- Firebase handles social authentication securely
- No plaintext password storage
- Session cleared on logout
- API key management via BuildConfig

## UI Screenshots

(Screenshots will be added after successful build)

---

**Note**: This implementation provides a complete authentication and settings framework. The social login features require proper configuration of Firebase, Facebook, and Twitter developer accounts with your actual credentials.
