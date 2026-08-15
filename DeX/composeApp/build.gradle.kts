import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")
    
    android {
        namespace = "com.dexstudios.dex.desktop"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain = getByName("desktopMain")
        
        androidMain.dependencies {
            implementation(libs.androidx.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        
        commonMain.dependencies {
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.material3)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.components.resources)
            implementation(libs.jb.compose.components.uiToolingPreview)
            
            implementation(libs.androidx.lifecycle.viewmodel.compose.multiplatform)
            implementation(libs.androidx.navigation.compose)
            
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            
            implementation(libs.kotlinx.serialization.json)
        }
        
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.server.content.negotiation)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.dexstudios.dex.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "DeX"
            packageVersion = "1.0.0"
        }
    }
}
