plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.multiplatform.library)
}

// SYNC LAYER (plan 031) — offline-first metadata sync between DeX peers via the
// self-hosted sync host (Hetzner VPS). PRIVACY LAW (inviolable, restated from the plan):
// file CONTENT and clipboard CONTENT are NEVER synced — metadata only; paired-token
// VALUES are never synced — fingerprint identifiers only.
//
// Layer discipline mirrors core/domain: this module depends ONLY on the wire contract
// primitives, coroutines, and serialization. The HTTP transport and DataStore persistence
// live behind ports (SyncTransport / SyncStorage) implemented by adapters in the
// platform layers; the server-side surface is plan 032.
kotlin {

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    android {
        namespace = "com.dexstudios.dex.core.sync"
        compileSdk = 36
        minSdk = 26
        withHostTest {}
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:protocol"))
            implementation(libs.coroutines)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
