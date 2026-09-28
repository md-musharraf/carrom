# Royal Carrom Classic ProGuard Rules
# Security: Obfuscate all application code to prevent reverse engineering

# Keep the entry point
-keep class com.example.royalcarromclassic.MainActivity { *; }

# Preserve Compose-related classes
-keep class * extends androidx.compose.runtime.Composer { *; }
-dontwarn androidx.compose.**

# Keep data classes used by SharedPreferences
-keep class com.example.royalcarromclassic.data.PlayerStats { *; }
-keep class com.example.royalcarromclassic.data.GameState { *; }

# kotlinx.serialization: keep generated serializers for the online DTOs
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.example.royalcarromclassic.online.**$$serializer { *; }
-keepclassmembers class com.example.royalcarromclassic.online.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.royalcarromclassic.online.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Socket.IO / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keep class io.socket.** { *; }

# Credential Manager loads its Play Services provider reflectively.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# Optimize aggressively
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Strip debug info
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
