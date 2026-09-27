# Rakshak ProGuard Rules
# Keep application class
-keep class com.rakshak.RakshakApplication { *; }

# Keep all enums
-keepclassmembers enum * { *; }

# Keep data classes used in JSON / log serialization
-keepclassmembers class com.rakshak.core.** { *; }

# Kotlin serialization / coroutines
-keep class kotlinx.coroutines.** { *; }

# AndroidX lifecycle
-keep class androidx.lifecycle.** { *; }
