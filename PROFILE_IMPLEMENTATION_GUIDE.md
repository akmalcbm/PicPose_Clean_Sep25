# Profile & App Settings Implementation Guide

## 🎯 Overview
Complete implementation of enhanced Profile, Edit Profile, Privacy Policy, Terms & Conditions, and About screens with proper data loading, state management, and Material 3 design.

---

## 📦 What Was Implemented

### 1. Data Model Updates
**File**: `app/src/main/java/com/picpose/bestphotographyapp/data/models/AppSettings.kt`
- ✅ Added `termsConditions` field to support Terms & Conditions from API

### 2. ViewModels

#### AppSettingsViewModel
**File**: `app/src/main/java/com/picpose/bestphotographyapp/presentation/viewmodels/AppSettingsViewModel.kt`

**New Features**:
- `AppSettingsUiState` sealed class for state management:
  ```kotlin
  sealed class AppSettingsUiState {
      object Idle
      object Loading
      data class Success(val settings: AppSettings)
      data class Error(val message: String)
  }
  ```
- Caching mechanism to prevent redundant API calls
- Helper methods:
  - `getPrivacyPolicyText()` - Returns privacy policy HTML
  - `getTermsText()` - Returns terms & conditions HTML
  - `getAboutText()` - Returns about app text

#### AuthViewModel
**File**: `app/src/main/java/com/picpose/bestphotographyapp/presentation/viewmodels/AuthViewModel.kt`

**New Method**:
- `fetchCurrentUser()` - Fetches fresh user data from API and updates session

### 3. New Screen Components

#### PrivacyPolicyScreen
**File**: `app/src/main/java/com/picpose/bestphotographyapp/presentation/screens/PrivacyPolicyScreen.kt`

**Features**:
- WebView-based rendering of HTML privacy policy
- Loading state with CircularProgressIndicator
- Error handling with retry button
- Responsive HTML styling
- Material 3 TopAppBar with back navigation

#### TermsScreen
**File**: `app/src/main/java/com/picpose/bestphotographyapp/presentation/screens/TermsScreen.kt`

**Features**:
- WebView-based rendering of HTML terms & conditions
- Loading state management
- Error handling with retry capability
- Consistent styling with Privacy Policy screen

### 4. Enhanced Screens

#### ProfileScreen
**File**: `app/src/main/java/com/picpose/bestphotographyapp/presentation/screens/ProfileScreen.kt`

**Improvements**:
- **Grouped Profile Options**:
  1. **Profile Management** (Edit Profile, Settings)
  2. **App Info** (Privacy Policy, Terms & Conditions, About)
  3. **Support** (Help & Support)
- Dynamic user data loading via `LaunchedEffect`
- Automatic data refresh when user is null
- Material 3 section headers with primary color
- Terms & Conditions navigation added

#### EditProfileScreen
**File**: `app/src/main/java/com/picpose/bestphotographyapp/presentation/screens/EditProfileScreen.kt`

**Improvements**:
- Loading state management with `isSaving` flag
- Loading indicators in both action bar and save button
- Toast confirmation on successful save
- Disabled state during save operation
- Removed unused account type fields
- Auto-populates fields when user data loads

#### HelpSupportDialog
**File**: Enhanced within `ProfileScreen.kt`

**Material 3 Improvements**:
- Rounded corners (24dp)
- Elevated card design (8dp)
- Icon-based contact info display:
  - Email icon with support email
  - Phone icon with support phone
- Success Snackbar confirmation
- Close button in header
- Pre-fills user info for logged-in users
- "Sending as" label showing current user

### 5. Navigation Updates

#### Screen.kt
**File**: `app/src/main/java/com/picpose/bestphotographyapp/presentation/navigation/Screen.kt`
- Added `Screen.Terms` route

#### NavGraph.kt
**File**: `app/src/main/java/com/picpose/bestphotographyapp/presentation/navigation/NavGraph.kt`
- Added Terms screen route
- Updated Privacy screen to use PrivacyPolicyScreen
- Maintained consistent navigation patterns

---

## 🔌 API Integration

The implementation works with your existing API endpoint:

### get_app_settings.php
**Expected Response Structure**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "app_name": "PicPose",
    "tagline": "Your tagline",
    "description": "App description",
    "privacy_policy_html": "<html>...</html>",
    "terms_conditions_html": "<html>...</html>",
    "about_app": "About text",
    "support_email": "support@example.com",
    "support_phone": "+1234567890",
    "admob": { ... }
  }
}
```

**Fields Used**:
- `privacy_policy` (or `privacy_policy_html`)
- `terms_conditions` (or `terms_conditions_html`)
- `about` or `description`
- `support_email`
- `support_phone`

---

## 🎨 Design Features

### Material 3 Design System
- ✅ Cards with rounded corners (12-24dp)
- ✅ Elevated surfaces (4-8dp)
- ✅ Primary color for section headers
- ✅ Consistent icon usage
- ✅ Proper spacing hierarchy

### User Experience
- ✅ Loading states with progress indicators
- ✅ Error states with retry buttons
- ✅ Success confirmations via Snackbar/Toast
- ✅ Smooth animations for list items
- ✅ Responsive layouts

### Typography
- **Section Headers**: 18sp, Bold, Primary Color
- **Option Titles**: Medium weight
- **Option Descriptions**: 12sp, 70% opacity
- **Body Text**: 14sp, standard weight

---

## 🚀 How to Use

### Navigating to New Screens

From ProfileScreen:
```kotlin
// Navigate to Privacy Policy
navController.navigate(Screen.Privacy.route)

// Navigate to Terms & Conditions
navController.navigate(Screen.Terms.route)

// Navigate to About
navController.navigate(Screen.About.route)
```

### Loading App Settings

In your composable:
```kotlin
val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()

LaunchedEffect(Unit) {
    appSettingsViewModel.loadAppSettings()
}

// Access data
val privacyPolicy = appSettingsViewModel.getPrivacyPolicyText()
val terms = appSettingsViewModel.getTermsText()
```

### Fetching Current User

```kotlin
val authViewModel: AuthViewModel = hiltViewModel()

LaunchedEffect(Unit) {
    if (currentUser == null) {
        authViewModel.fetchCurrentUser()
    }
}
```

---

## 📝 Testing Checklist

### Profile Screen
- [ ] User data loads correctly on first render
- [ ] Profile picture displays (or default icon)
- [ ] Three grouped sections render properly
- [ ] Each option navigates to correct screen
- [ ] Help & Support dialog opens correctly

### Edit Profile Screen
- [ ] Fields populate with current user data
- [ ] Image picker works for profile picture
- [ ] Save button shows loading state
- [ ] Success toast appears after save
- [ ] Navigation back to profile works
- [ ] Profile data updates immediately

### Privacy Policy Screen
- [ ] HTML content renders in WebView
- [ ] Loading indicator shows during fetch
- [ ] Error screen shows on failure
- [ ] Retry button works
- [ ] Back navigation functions

### Terms & Conditions Screen
- [ ] HTML content displays properly
- [ ] Loading state works
- [ ] Error handling functional
- [ ] Navigation works

### About Screen
- [ ] App name displays
- [ ] Description/About text shows
- [ ] Version number visible
- [ ] Developer info shows (if available)

### Help & Support Dialog
- [ ] Support email displays
- [ ] Support phone displays
- [ ] Message input works
- [ ] Submit button functions
- [ ] Success snackbar appears
- [ ] User info pre-fills for logged-in users

---

## 🔧 Troubleshooting

### Issue: Blank User Data in ProfileScreen
**Solution**: Check if `fetchCurrentUser()` is being called in LaunchedEffect

### Issue: Privacy Policy/Terms show "No content"
**Solution**: Verify API returns HTML in `privacy_policy` and `terms_conditions` fields

### Issue: WebView not rendering HTML properly
**Solution**: Check HTML format - should be valid HTML with proper tags

### Issue: Help dialog not submitting
**Solution**: Verify `submitSupportQuery` API endpoint is configured correctly

### Issue: Edit Profile not saving
**Solution**: 
1. Check `updateProfile` API endpoint
2. Verify user has valid session/token
3. Check network permissions

---

## 🏗️ Architecture

### MVVM Pattern
```
View (Composables) → ViewModel → Repository → API/DataStore
```

### State Management
```
AppSettingsUiState: Idle → Loading → Success/Error
AuthState: Idle → Loading → Success/Error
```

### Data Flow
```
API → Repository → ViewModel → StateFlow → Composable UI
```

---

## 📱 Screenshots Required

To verify implementation, please test and capture:
1. Profile Screen with grouped sections
2. Edit Profile Screen with loaded data
3. Privacy Policy Screen showing HTML content
4. Terms & Conditions Screen
5. About Screen
6. Help & Support Dialog with Material 3 design

---

## 🔄 Future Enhancements

Potential improvements:
- [ ] Add profile picture upload progress indicator
- [ ] Implement bio character counter
- [ ] Add support for markdown in About section
- [ ] Cache privacy/terms locally for offline access
- [ ] Add pull-to-refresh on Profile screen
- [ ] Implement profile analytics (views, followers)

---

## 📞 Support

If you encounter issues:
1. Check API responses in Logcat
2. Verify navigation routes are registered
3. Ensure Hilt modules are properly configured
4. Check user session is persisted correctly

---

**Last Updated**: 2025-10-27
**Version**: 1.0
**Status**: ✅ Implementation Complete
