plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidLibrary {
        namespace = "com.dexstudios.dex.feature.discovery"
        compileSdk = 34
        minSdk = 24
    }
    jvm("desktop")
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.androidx.lifecycle.viewmodel.compose.multiplatform)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(project(":core:network"))
            implementation(project(":core:data"))
        }
    }
}



