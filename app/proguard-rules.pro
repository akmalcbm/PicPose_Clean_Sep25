# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Facebook Infer annotations (R8 warning suppression)
-dontwarn com.facebook.infer.annotation.Nullsafe
-dontwarn com.facebook.infer.annotation.Nullsafe$Mode

# ========== KOTLIN ==========
-keepattributes *Annotation*, InnerClasses
-dontnote kotlin.**
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Kotlin Serialization
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ========== COMPOSE ==========
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.** { *; }

# ========== HILT ==========
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclassmembers @dagger.hilt.android.AndroidEntryPoint class * {
    @dagger.hilt.android.AndroidEntryPoint <init>();
}
-keep class * extends dagger.hilt.processor.internal.androidentrypoint.InjectorEntryPointGenerator { *; }

# ========== ROOM ==========
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers @androidx.room.Dao class * {
    *** *(...);
}
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
}

# ========== RETROFIT & OKHTTP ==========
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class sun.misc.** { *; }
-keep class com.google.gson.stream.** { *; }
-keep class retrofit2.** { *; }
-dontnote retrofit2.**
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers class * {
    @retrofit2.http.* *;
}

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# OkHttp logging interceptor
-keep class okhttp3.logging.HttpLoggingInterceptor { *; }

# ========== MOSHI ==========
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * {
    *;
}
-keep class **JsonAdapter { *; }

# ========== FIREBASE ==========
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keepattributes *Annotation*

# Firebase Messaging
-keep class com.google.firebase.messaging.** { *; }

# ========== GOOGLE SERVICES ==========
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.ads.** { *; }

# ========== FACEBOOK ==========
-keep class com.facebook.** { *; }
-dontwarn com.facebook.**
-keepattributes *Annotation*
-keep class * extends com.facebook.FacebookException { *; }

# Facebook Login
-keep class com.facebook.login.** { *; }

# Facebook Audience Network (AdMob mediation)
-keep class com.facebook.ads.** { *; }
-dontwarn com.facebook.ads.**
-keepattributes Signature

# ========== COIL ==========
-keep class coil.** { *; }
-keep class coil3.** { *; }
-keep class com.google.accompanist.coil.** { *; }

# ========== LOTTIE ==========
-keep class com.airbnb.lottie.** { *; }

# ========== IMAGE CROPPER ==========
-keep class com.canhub.cropper.** { *; }

# ========== DATASTORE ==========
-keep class androidx.datastore.** { *; }

# ========== NAVIGATION ==========
-keep class androidx.navigation.** { *; }

# ========== WINDOW SIZE ==========
-keep class androidx.compose.material3.adaptive.** { *; }

# ========== BUILD CONFIG ==========
# Keep BuildConfig fields (especially API_KEY)
-keepclassmembers class **.BuildConfig {
    public static *;
}

# ========== GENERAL ANDROID ==========
# Keep ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep annotations
-keepattributes *Annotation*, EnclosingMethod, Signature

# Keep resource classes
-keep class **.R
-keep class **.R$* {
    <fields>;
}

# Keep data classes
-keepclassmembers class * {
    public <init>(...);
}

# ========== TWITTER/OKHTTP INTEGRATION ==========
-keep class okhttp3.OkHttpClient { *; }
-keep class okhttp3.Callback { *; }
-keep class okhttp3.Response { *; }

# ========== CUSTOM APPLICATION CLASS ==========
# If you have a custom Application class
-keep public class * extends android.app.Application

# ========== R8 OPTIMIZATIONS ==========
# These options help prevent crashes from aggressive optimization
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep generic types
-keepattributes Signature