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
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# -- OkHttp / Okio (WebSocket) --
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# -- Cronet (native library) --
-keep class org.chromium.net.** { *; }
-dontwarn org.chromium.**

# -- Koin (DI) --
-keep class org.koin.** { *; }

# -- Timber --
-dontwarn timber.log.**

# -- Coil (image loading) --
-keep class coil3.** { *; }

# -- Compose --
-keep class androidx.compose.** { *; }

# -- Google Play Services (Sign-In, QR scanner) --
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# -- ML Kit (QR barcode scanner) --
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# -- Backdrop (glass library) --
-keep class com.kyant.backdrop.** { *; }

# -- App-specific: keep data classes and DTOs that are JSON-serialized --
-keep class com.dexstudios.dex.network.**Dto { *; }
-keep class com.dexstudios.dex.network.*State { *; }
-keep class com.dexstudios.dex.network.*Outcome { *; }
