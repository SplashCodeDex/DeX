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
