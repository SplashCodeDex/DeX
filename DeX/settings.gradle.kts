@file:Suppress("UnstableApiUsage")
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("org\\.chromium.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("org\\.chromium.*")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "DeXAndroid"
include(":app")
include(":core")
project(":core").projectDir = file("../core")
include(":core:protocol")
project(":core:protocol").projectDir = file("../core/protocol")
include(":core:sync")
project(":core:sync").projectDir = file("../core/sync")
include(":core:data")
project(":core:data").projectDir = file("../core/data")
include(":core:domain")
project(":core:domain").projectDir = file("../core/domain")
include(":core:network")
project(":core:network").projectDir = file("../core/network")
include(":core:designsystem")
project(":core:designsystem").projectDir = file("../core/designsystem")

// ── One output tree per build ─────────────────────────────────────────────────────────────
// Every `:core:*` module above resolves to the SAME ../core/<name> source directory that the
// desktop build at the repo root compiles, and Gradle's default build directory is
// <projectDir>/build — so this build and the desktop build were writing their intermediates
// into one shared folder. They are not interchangeable: commonMain/composeResources is
// assembled per target, so the desktop build produces an `assembledResources/desktopMain`
// payload while this one needs `androidMain`, and each task happily read the other's output.
//
// That is not theoretical. After the design system's desktop-only artwork (avatars, device
// animations, wallpapers) was moved out of commonMain, :app:packageDebug kept packing the
// previous desktop-assembled set and reported ~27 MB of resources that no longer existed in
// any source set. `--rerun-tasks` did NOT clear it — the inputs had not changed, only the
// OTHER build's copy had — and only deleting the shared folder revealed the truth. That is
// the kind of signal that sends a fix in the wrong direction, so each build now gets its own
// namespace: shared modules land under this build's own `build/shared/<name>`.
gradle.beforeProject {
    if (path == ":core" || path.startsWith(":core:")) {
        layout.buildDirectory.set(settingsDir.resolve("build/shared/${name}"))
    }
}
