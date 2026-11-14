# Serialization rules for the shared models come from :core (consumer-rules.pro).
#
# Compose, Media3, OkHttp, Coil, Room and kotlinx ship their own consumer rules.
# Do NOT add blanket `-keep class androidx.compose.** { *; }`-style rules: they
# stop R8 from optimizing those libraries, which costs startup time and frame
# time on low-end TV hardware.

-dontwarn okhttp3.**
-dontwarn okio.**

# Readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
