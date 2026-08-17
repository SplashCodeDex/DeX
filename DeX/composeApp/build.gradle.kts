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
            implementation(libs.androidx.datastore.preferences)
            
            implementation(project(":core:network"))
            implementation(project(":core:data"))
            implementation(project(":core:designsystem"))
            implementation(project(":feature:discovery"))
            implementation(project(":feature:history"))
            implementation(project(":feature:settings"))
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
            implementation(libs.compose.native.tray)
            implementation(libs.coroutines.swing)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.server.content.negotiation)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.dexstudios.dex.MainKt"
        javaHome = "C:/Program Files/Eclipse Adoptium/jdk-26.0.2.10-hotspot"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "DeX"
            packageVersion = "1.0.0"
            
            macOS {
                bundleID = "com.dexstudios.dex"
                appCategory = "public.app-category.utilities"
                // iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
            }
            
            windows {
                menuGroup = "DeX Studios"
                upgradeUuid = "6ac1f203-bde0-4040-a2f3-f8a6dcda330c"
                dirChooser = true
                shortcut = true
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }
        }
    }
}
