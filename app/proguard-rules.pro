# ========== GOOGLE SIGN-IN & AUTH ==========
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.security.** { *; }

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Google Identity Services
-keep class com.google.android.libraries.identity.googleid.** { *; }

# ========== RETROFIT & OKHTTP (CRITICAL) ==========
# Retrofit2
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Keep Retrofit interfaces
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep Retrofit annotations
-keep class retrofit2.http.* { *; }

# OkHttp3
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# OkHttp Interceptors
-keep class okhttp3.logging.HttpLoggingInterceptor { *; }
-keep class okhttp3.internal.** { *; }

# ========== GSON (For Retrofit) ==========
-keep class com.google.gson.** { *; }
-keep class sun.misc.** { *; }
-keep class com.google.gson.stream.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# GSON Model classes (keep your data classes)
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ========== MOSHI (Twitter) ==========
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter
-keep @com.squareup.moshi.JsonClass class * {
    *;
}

# ========== FIREBASE AUTH ==========
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firebase Messaging
-keep class com.google.firebase.messaging.** { *; }

# Play Services Auth
-keep class com.google.android.gms.auth.api.** { *; }
-keep class com.google.android.gms.auth.api.signin.** { *; }

# ========== FACEBOOK LOGIN ==========
-keep class com.facebook.** { *; }
-dontwarn com.facebook.**
-keepattributes Signature

# Facebook Login specific
-keep class com.facebook.login.** { *; }
-keep class com.facebook.AccessToken { *; }
-keep class com.facebook.GraphRequest { *; }
-keep class com.facebook.CallbackManager { *; }

# ========== REFLECTION USED BY LIBRARIES ==========
# Many auth libraries use reflection
-keepattributes Signature, InnerClasses, EnclosingMethod

# Keep annotation information
-keepattributes *Annotation*

# Keep generic types (important for Retrofit)
-keepattributes Signature

# ========== BROWSER CUSTOM TABS (Twitter OAuth) ==========
-keep class androidx.browser.** { *; }

# ========== KEEP YOUR API MODELS ==========
# Keep your data classes used in API calls
-keep class com.picpose.bestphotographyapp.model.** { *; }
-keepclassmembers class com.picpose.bestphotographyapp.model.** {
    *;
}

# Keep classes with @SerializedName (if using GSON)
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ========== SERIALIZATION ==========
# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-keep class kotlinx.serialization.** { *; }
-keep class * implements kotlinx.serialization.KSerializer

# ========== NETWORK SECURITY ==========
# Keep SSL/security classes
-keep class javax.net.ssl.** { *; }
-keep class java.security.** { *; }

# ========== ROOM DATABASE (if network cache) ==========
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *