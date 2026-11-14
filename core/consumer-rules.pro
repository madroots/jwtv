# Applied to every app that depends on :core (TV and phone), so both shrink
# the shared models the same way.

# ── Kotlin serialization ──────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer {
    static **$$serializer INSTANCE;
}
-keep,includedescriptorclasses class org.jw.tv.api.** { *; }
-keepclassmembers class org.jw.tv.api.** { *; }
