plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.kotlin.serialization)
}

kotlin {

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kermit)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coroutines)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
    }
}
