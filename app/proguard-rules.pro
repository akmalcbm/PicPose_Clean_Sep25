# General Android rules
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable

# Keep important Android classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final *** NULL;
}

# Keep annotations
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep Kotlin metadata
-keepclassmembers class **.R$* {
    public static <fields>;
}
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontnote kotlin.**
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep Hilt/Dagger
-keep class com.google.dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.HiltWrapper_ActivityRetainedComponentManager_ActivityRetainedComponentBuilderEntryPoint { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponentManagerHolder { *; }
-keep class * extends dagger.hilt.internal.aggregatedroot.codegen.Root { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-keep class * extends dagger.hilt.internal.processedrootsentinel.codegen.ProcessedRootSentinel { *; }
-dontwarn dagger.hilt.internal.aggregatedroot.codegen.**

# Keep Room database
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class * extends androidx.room.migration.AutoMigrationSpec

# Keep Retrofit
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions, InnerClasses, EnclosingMethod
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep Gson/Retrofit converters
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.squareup.retrofit2.converter.gson.** { *; }
-keepattributes Signature

# Keep Moshi
-keep class com.squareup.moshi.** { *; }
-keepnames class kotlin.Any { *; }
-keep class com.squareup.moshi.JsonQualifier { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
}

# Keep Navigation
-keep class androidx.navigation.** { *; }
-keep class * extends androidx.navigation.NavType
-keep class * implements androidx.navigation.NavArgs

# Keep Compose
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep Coil
-keep class coil.** { *; }
-keep class coil3.** { *; }
-keep class okio.** { *; }

# Keep Lottie
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# Keep Firebase/Auth
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class com.google.firebase.provider.FirebaseInitProvider
-keepnames class com.fasterxml.jackson.** { *; }
-keepnames class javax.inject.**

# Keep Facebook
-keep class com.facebook.** { *; }
-dontwarn com.facebook.**
-keepattributes Signature
-keep class * extends com.facebook.FacebookException {
    <init>(...);
}
-keepnames class com.facebook.internal.NativeProtocol

# Keep DataStore
-keep class androidx.datastore.** { *; }
-keep class * implements androidx.datastore.core.DataStore

# Keep serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.* <methods>;
}

# Keep your application class and entry points
-keep class com.picpose.bestphotographyapp.** { *; }
-keep class com.picpose.bestphotographyapp.BuildConfig { *; }
-keep class * extends android.app.Application
-keep class * extends androidx.lifecycle.ViewModel

# Keep BuildConfig fields
-keepclassmembers class **.BuildConfig {
    public static ** *;
}

# Keep API key and other important fields
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
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

# Keep resource classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep onClick methods
-keepclassmembers class * {
    public void *(android.view.View);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JavaScript interfaces (if using WebView)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Firebase Messaging service
-keep class * extends com.google.firebase.messaging.FirebaseMessagingService
-keep class com.picpose.bestphotographyapp.fcm.** { *; }

# Keep FileProvider
-keep class androidx.core.content.FileProvider

# Keep AdMob (explicit)
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Remove logging in release (optional but recommended)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# For OkHttp logging interceptor
-assumenosideeffects class okhttp3.logging.HttpLoggingInterceptor$Logger {
    public void log(...);
}

# AdMob specific rules
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.ads.**

# Facebook Ads specific rules
-keep class com.facebook.ads.** { *; }
-dontwarn com.facebook.ads.**

# Keep constructors for activities, services, etc.
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# For lambda expressions
-keepclassmembers class * {
    private static synthetic lambda$*(...);
}

# Keep data classes (especially if using Room)
-keepclassmembers class * {
    @androidx.room.* *;
}

# For JSON serialization/deserialization
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Guide API models used by Gson in release
-keep class com.picpose.bestphotographyapp.data.remote.ApiResponse { *; }
-keep class com.picpose.bestphotographyapp.data.models.GuidePostDto { *; }
-keep class com.picpose.bestphotographyapp.data.models.ContentBlockDto { *; }
-keep class com.picpose.bestphotographyapp.data.models.MetaDto { *; }

# Warning suppression for libraries
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.runtime.**
