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
