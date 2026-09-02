plugins {
    // Versionless ids: Kotlin is already on the build classpath (root project applies the
    // multiplatform plugin with the shared Kotlin version); re-requesting with a version
    // fails Gradle's compatibility check. Shadow is NOT on the classpath, so it carries
    // its version via the catalog alias.
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.shadow)
    application
}

// DE-X RELAY/SYNC SERVER (plan 032) — the self-hosted cloud peer on the Hetzner VPS.
//
// LAW (plan 032 STOP conditions):
//  - NEVER stage file content to disk — streaming pass-through with bounded buffers only.
//  - E2EE by construction: the relay forwards opaque bytes + routing headers; per-session
//    keys live on the peers (from the pairing identity exchange), never here.
//  - Quotas are enforced BEFORE first byte (fail fast, never mid-stream).
//  - This module consumes ONLY :core:protocol + :core:sync (the two wire laws) — never
//    desktop modules (composeApp, core/network desktopMain) — so the server can never
//    grow accidental desktop dependencies.
dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:sync"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines)
    implementation(libs.google.api.client)
    implementation(libs.kermit)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.dexstudios.dex.server.MainKt")
}

// Fat JAR name pinned for the Dockerfile + deploy workflow.
tasks.shadowJar {
    archiveFileName.set("dex-server-all.jar")
}

tasks.withType<Test> {
    useJUnit()
}
