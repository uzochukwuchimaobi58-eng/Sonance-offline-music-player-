# ===================================================================
# Sonance Music Player - Production Security & Obfuscation Proguard Rules
# Protects against decompilation, reverse engineering, and tampering
# ===================================================================

# --- General Optimization & Shrinking ---
-repackageclasses 'com.sonance.musicplayer.internal'
-allowaccessmodification
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# --- Strip Sensitive Log Calls in Production ---
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# --- Preserve Kotlin Serialization Models ---
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class com.sonance.musicplayer.model.** { *; }

# --- Preserve Android Jetpack Compose & ViewModel ---
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }

# --- Preserve Media3 ExoPlayer Audio Engine ---
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.** { *; }

# --- Preserve Google Play Billing & AdMob Models ---
-keep class com.google.android.gms.ads.** { *; }
-keep class com.android.billingclient.** { *; }

# --- Preserve Firebase Crashlytics Source File mapping ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
