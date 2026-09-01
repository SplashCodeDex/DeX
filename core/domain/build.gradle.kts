plugins {
    alias(libs.plugins.kotlin.multiplatform)

    alias(libs.plugins.kotlin.serialization)
}

// DOMAIN LAYER (plan 026) — platform-neutral use cases and state machines for the DeX
// ecosystem. Every future peer (desktop, Android, Wear, iOS, watchOS) consumes these
// verbatim. Ports are declared here; adapters live in core/network (or future platform
// modules). Adding Ktor server/client engines, DataStore, Koin, Compose, or any
// platform API here defeats the layer: this module depends ONLY on the wire contract,
// the shared data primitives, and coroutines.
kotlin {

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":core:protocol"))
            implementation(project(":core:data"))
            implementation(libs.coroutines)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.mockk)
        }
    }
}
