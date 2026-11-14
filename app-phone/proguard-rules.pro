# Serialization rules for the shared models come from :core (consumer-rules.pro).
# Compose, Media3, OkHttp, Coil, Room and kotlinx ship their own consumer rules.

-dontwarn okhttp3.**
-dontwarn okio.**

# Readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
