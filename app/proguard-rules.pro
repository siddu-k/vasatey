# This file contains the definitive ProGuard rules for the Vasatey app.

# --- Keep application-specific classes ---

# Keep Picovoice and Porcupine classes
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**

# Keep all data model classes used for serialization (e.g., with Gson/Retrofit)
-keep class com.sriox.vasatey.models.** { *; }
-keepclassmembers class com.sriox.vasatey.models.** { *; }


# --- Industry-standard rules for common libraries ---

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.** { *; }
-keepnames class kotlinx.coroutines.flow.internal.** { *; }
-keepclassmembers class kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    <fields>;
}

# Retrofit & OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

-dontwarn okio.**
-keep class okio.** { *; }

# Gson
-keep class com.google.gson.stream.** { *; }

# Keep data classes that are serialized by Gson
-keepclasseswithmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep -$$SerializedName fields for obfuscated data classes.
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
