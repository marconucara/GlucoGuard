# GlucoGuard ProGuard Rules

# Keep the data models used for GSON serialization
-keep class com.glucoguard.app.api.** { *; }

# Keep GSON internal classes
-keep class com.google.gson.** { *; }

# Keep Android classes used by name
-keepclassmembers class * extends android.app.Service { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver { *; }

# Optimize OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
