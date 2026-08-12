# R8 / ProGuard rules for DeX Android
# `proguard-android-optimize.txt` is inherited from the SDK (optimize + obfuscate).

# -- kotlinx.serialization --
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dexstudios.dex.**$$serializer { *; }
-keepclassmembers class com.dexstudios.dex.** {
    *** Companion;
}
-keepclasseswithmembers class com.dexstudios.dex.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# -- Ktor (CIO engine + ContentNegotiation) --
# Broad keep rules for Ktor are usually not needed as it ships with consumer rules.
# Only keep specific things if reflection-based discovery is used and not covered.
-dontwarn io.ktor.**

# -- OkHttp / Okio (WebSocket) --
-dontwarn okhttp3.**
-dontwarn okio.**

# -- Cronet (native library) --
-dontwarn org.chromium.**

# -- Koin (DI) --
# Koin 3+ usually doesn't need broad keep rules if using DSL.

# -- Timber --
-dontwarn timber.log.**

# -- Compose --
# Compose is designed to work with R8; broad keep rules are not needed.

# -- Google Play Services (Sign-In, QR scanner) --
-dontwarn com.google.android.gms.**

# -- ML Kit (QR barcode scanner) --
-dontwarn com.google.mlkit.**

# -- Backdrop (glass library) --
-keep class com.kyant.backdrop.**

# -- App-specific: keep data classes and DTOs that are JSON-serialized --
-keep class com.dexstudios.dex.network.**Dto { *; }
-keep class com.dexstudios.dex.network.*State { *; }
-keep class com.dexstudios.dex.network.*Outcome { *; }
