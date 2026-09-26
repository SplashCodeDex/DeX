plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)

    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {

    jvm("desktop")

    // ONE design system, TWO platforms — the same dual-target law `core:protocol` and
    // `core:network` already follow. Until this block existed the module could only emit a
    // desktop JVM jar, which the Android app cannot consume, so `DeX/app/ui` carried a
    // hand-copied fork of these components. The forks drifted (DynamicPillButton alone was
    // +398/-192 apart) and every physics tweak had to be hand-carried across. Anything the
    // two apps share visually belongs HERE: polish it once, both platforms wear it.
    android {
        namespace = "com.dexstudios.dex.core.designsystem"
        compileSdk = 36
        minSdk = 26
        withHostTest {}
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }

        // AGP 9's `com.android.kotlin.multiplatform.library` does NOT process Android
        // resources unless asked (unlike the legacy `com.android.library`, where it was
        // implicit). Without this switch no assets output directory is ever published to
        // the variant, so Compose Multiplatform's `copyAndroidMainComposeResourcesToAndroidAssets`
        // bridge task is created but left unconfigured — it silently contributes nothing,
        // the APK ships without `src/commonMain/composeResources`, and every `Res.readBytes`
        // call (Lottie JSONs, Fluent icon SVGs, the shared logo) fails at RUNTIME while
        // still compiling cleanly. `core:protocol`/`core:network` never exposed this because
        // they carry no resources; the design system is the first module that does.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kermit)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            api(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose.multiplatform)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            api(libs.compottie)
            api(libs.coil.compose)
            implementation(project(":core:network"))
            implementation(project(":core:data"))
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.dexstudios.dex.core.designsystem.generated.resources"
    generateResClass = always
}
