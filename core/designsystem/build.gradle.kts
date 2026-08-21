plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.material3)
            implementation(libs.jb.compose.ui)
            api(libs.jb.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose.multiplatform)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            api(libs.backdrop)
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
