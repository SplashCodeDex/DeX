plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coroutines)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
    }
}

android {
    namespace = "com.dexstudios.dex.core.data"
    compileSdk = 37
}
