plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.multiplatform.library) apply false
  alias(libs.plugins.spotless) apply false
}

allprojects {
    // Aggregate convenience: make a bare `test` invocation route to this project's real
    // test task. KMP modules have no built-in `test`, so a router is registered there;
    // JVM modules (e.g. server) already own `test` from the Java plugin and are left
    // untouched — registering a second `test` breaks plugin application outright.
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        if (tasks.none { it.name == "test" }) {
            tasks.register("test") {
                dependsOn(tasks.matching { it.name == "allTests" || it.name == "desktopTest" || it.name == "jvmTest" })
            }
        }
    }
    plugins.withId("com.android.application") {
        if (tasks.none { it.name == "test" }) {
            tasks.register("test") {
                dependsOn(tasks.matching { it.name == "testDebugUnitTest" })
            }
        }
    }
}

// DX-02 baseline: Spotless + ktlint formatter/linter for every desktop module.
// Deliberately NOT wired into `check`, the pre-commit hook or CI yet — adoption is a
// separate decision. Run `./gradlew spotlessApply` to format, `spotlessCheck` to audit.
//
// Baseline rule relaxations (mirrored in .editorconfig for IDE parity), each deliberate:
//  - intellij_idea style + max_line_length=200: keeps initial adoption diff small.
//  - no-wildcard-imports off: wildcard imports are idiomatic across Compose UI sources.
//  - no-empty-file off: reserved placeholder namespaces under core/data.
//  - backing-property-naming off: codebase convention is "_xFlow -> xFlow", not "_x -> x".
subprojects {
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        val baselineOverrides = mapOf(
            "ktlint_code_style" to "intellij_idea",
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            "max_line_length" to "200",
            "ktlint_standard_no-wildcard-imports" to "disabled",
            "ktlint_standard_no-empty-file" to "disabled",
            "ktlint_standard_backing-property-naming" to "disabled",
            "ktlint_standard_property-naming" to "disabled",
            "ktlint_standard_filename" to "disabled"
        )
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/generated/**")
            ktlint(libs.versions.ktlint.get()).editorConfigOverride(baselineOverrides)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get()).editorConfigOverride(baselineOverrides)
        }
    }
}
