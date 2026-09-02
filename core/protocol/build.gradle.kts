plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.multiplatform.library)
}

// LEAF MODULE — the DeX wire contract. Every platform (desktop, Android, Wear, iOS,
// watchOS) and the relay/sync server imports this exact module so the protocol can
// never drift per-platform. Adding ANY dependency here defeats that purpose: the wire
// contract must stay a pure kotlinx-serialization island (DTOs, envelope builder,
// message-type registry, canonical JSON). Transport engines, DataStore, Koin, and
// platform APIs all live in the layers ABOVE this module.
kotlin {

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    android {
        namespace = "com.dexstudios.dex.core.protocol"
        compileSdk = 36
        minSdk = 26
        withHostTest {}
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
