# API Response Mapping Guide

## 📡 get_app_settings API

### Expected API Response Structure

Based on your `get_app_settings.php` implementation:

```json
{
  "success": true,
  "data": {
    "id": 1,
    "admin_name": "Admin Name",
    "app_name": "PicPose",
    "tagline": "Best Photography App",
    "description": "App description text",
    "google_play_url": "https://play.google.com/...",
    "admob": {
      "app_id": "ca-app-pub-...",
      "banner1_id": "ca-app-pub-.../banner1",
      "banner2_id": "ca-app-pub-.../banner2",
      "interstitial1_id": "ca-app-pub-.../interstitial1",
      "interstitial2_id": "ca-app-pub-.../interstitial2",
      "native1_id": "ca-app-pub-.../native1",
      "native2_id": "ca-app-pub-.../native2",
      "native3_id": "ca-app-pub-.../native3",
      "rewarded1_id": "ca-app-pub-.../rewarded1"
    },
    "policies": {
      "privacy_policy_html": "<h1>Privacy Policy</h1><p>Your privacy policy content...</p>",
      "terms_conditions_html": "<h1>Terms & Conditions</h1><p>Your terms content...</p>",
      "privacy_policy_text": "Plain text version...",
      "terms_conditions_text": "Plain text version..."
    }
  },
  "meta": {
    "generated_at": "2025-10-27T07:11:44+00:00"
  }
}
```

### Android Data Model Mapping

The Kotlin `AppSettings` data class expects these fields:

```kotlin
data class AppSettings(
    // AdMob settings
    @SerializedName("app_id") val appId: String = "",
    @SerializedName("banner1_id") val banner1Id: String = "",
    @SerializedName("banner2_id") val banner2Id: String = "",
    // ... other admob fields
    
    // App information
    @SerializedName("app_name") val appName: String = "PicPose",
    @SerializedName("app_version") val appVersion: String = "1.0.0",
    @SerializedName("tagline") val tagline: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("about") val about: String = "",
    @SerializedName("developer") val developer: String = "",
    
    // Privacy & Policy
    @SerializedName("privacy_policy") val privacyPolicy: String = "",
    @SerializedName("terms_conditions") val termsConditions: String = "",
    
    // Support information
    @SerializedName("support_email") val supportEmail: String = "",
    @SerializedName("support_phone") val supportPhone: String = ""
)
```

## 🔄 Required PHP Response Updates

To ensure compatibility, your PHP API should return fields matching the Android model:

### Option 1: Flatten the response
```php
$payload = [
    'id' => (int)$row['id'],
    'admin_name' => $row['admin_name'] ?? '',
    'app_name' => $row['app_name'] ?? '',
    'tagline' => $row['tagline'] ?? '',
    'description' => $row['description'] ?? '',
    'app_version' => '1.0.0', // Add this field
    'developer' => $row['admin_name'] ?? '', // Or separate developer field
    'about' => $row['description'] ?? '', // Or separate about field
    
    // AdMob - flatten
    'app_id' => $row['admob_app_id'] ?? '',
    'banner1_id' => $row['admob_banner1_id'] ?? '',
    'banner2_id' => $row['admob_banner2_id'] ?? '',
    'interstitial1_id' => $row['admob_interstitial1_id'] ?? '',
    'interstitial2_id' => $row['admob_interstitial2_id'] ?? '',
    'native1_id' => $row['admob_native1_id'] ?? '',
    'native2_id' => $row['admob_native2_id'] ?? '',
    'native3_id' => $row['admob_native3_id'] ?? '',
    'rewarded1_id' => $row['admob_rewarded1_id'] ?? '',
    
    // Privacy & Terms - use HTML version
    'privacy_policy' => $row['privacy_policy'] ?? '',
    'terms_conditions' => $row['terms_conditions'] ?? '',
    
    // Support (add these to your database if not present)
    'support_email' => 'support@picpose.com',
    'support_phone' => '+1-234-567-8900',
];
```

### Option 2: Update Retrofit parsing
If you prefer to keep your nested structure, update the Retrofit API service to handle the nested response:

```kotlin
// Create intermediate DTOs
data class AppSettingsApiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: AppSettingsData?
)

data class AppSettingsData(
    @SerializedName("id") val id: Int,
    @SerializedName("app_name") val appName: String,
    @SerializedName("tagline") val tagline: String,
    @SerializedName("description") val description: String,
    @SerializedName("admob") val admob: AdmobSettings,
    @SerializedName("policies") val policies: PoliciesSettings
)

data class PoliciesSettings(
    @SerializedName("privacy_policy_html") val privacyPolicyHtml: String,
    @SerializedName("terms_conditions_html") val termsConditionsHtml: String
)
```

## 📝 Database Schema Requirements

Your `app_settings` table should have these columns:

```sql
CREATE TABLE app_settings (
    id INT PRIMARY KEY AUTO_INCREMENT,
    admin_name VARCHAR(255),
    app_name VARCHAR(255) NOT NULL DEFAULT 'PicPose',
    tagline TEXT,
    description TEXT,
    
    -- AdMob IDs
    admob_app_id VARCHAR(255),
    admob_banner1_id VARCHAR(255),
    admob_banner2_id VARCHAR(255),
    admob_interstitial1_id VARCHAR(255),
    admob_interstitial2_id VARCHAR(255),
    admob_native1_id VARCHAR(255),
    admob_native2_id VARCHAR(255),
    admob_native3_id VARCHAR(255),
    admob_rewarded1_id VARCHAR(255),
    
    -- HTML Content
    privacy_policy TEXT,           -- HTML content
    terms_conditions TEXT,         -- HTML content
    
    -- Optional fields for Android app
    app_version VARCHAR(50) DEFAULT '1.0.0',
    developer VARCHAR(255),
    about TEXT,
    support_email VARCHAR(255),
    support_phone VARCHAR(50),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 🧪 Testing the API

### cURL Test Command

```bash
curl -X GET "https://picpose.iamakmal.in/api/get_app_settings.php" \
  -H "X-API-Key: 7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json"
```

### Expected Android Parsing

The Android app uses Retrofit with Gson converter:

```kotlin
// In HomeRepository.kt
suspend fun getAppSettings(): Flow<Result<AppSettings>> = flow {
    val apiResult = safeApiCall {
        callWithRetries {
            apiService.getAppSettings()
        }
    }
    
    apiResult.fold(
        onSuccess = { response ->
            if (response.success && response.data != null) {
                emit(Result.success(response.data))
            } else {
                emit(Result.failure(Exception("Empty app settings response")))
            }
        },
        onFailure = { error ->
            emit(Result.failure(error))
        }
    )
}
```

## ⚠️ Important Notes

1. **HTML Content**: The `privacy_policy` and `terms_conditions` fields should contain valid HTML
2. **Support Contact**: Add `support_email` and `support_phone` fields to your database and API response
3. **About Field**: Use either `description` or add a separate `about` field for the About screen
4. **Version Field**: Add `app_version` field to track app version from server

## 🔍 Troubleshooting

### Issue: Privacy Policy shows "No content"
Check:
- [ ] Database has content in `privacy_policy` column
- [ ] PHP API returns non-empty `privacy_policy` field
- [ ] Content is valid HTML (starts with `<html>` or `<h1>` etc.)

### Issue: Terms & Conditions not loading
Check:
- [ ] Database has `terms_conditions` column
- [ ] PHP SELECT query includes `terms_conditions`
- [ ] API response includes `terms_conditions` field

### Issue: Support email/phone not showing
Check:
- [ ] Database has these columns (or use default values)
- [ ] PHP API includes these in response
- [ ] Values are not NULL or empty strings

## 📊 Sample Data

Insert sample data into your database:

```sql
INSERT INTO app_settings (
    app_name,
    tagline,
    description,
    privacy_policy,
    terms_conditions,
    support_email,
    support_phone
) VALUES (
    'PicPose',
    'Capture, Create, Inspire',
    'The ultimate photography companion app',
    '<h1>Privacy Policy</h1><p>We respect your privacy...</p>',
    '<h1>Terms & Conditions</h1><p>By using this app...</p>',
    'support@picpose.com',
    '+1-800-PICPOSE'
);
```

---

**Last Updated**: 2025-10-27
**Compatible With**: PicPose Android App v1.0
