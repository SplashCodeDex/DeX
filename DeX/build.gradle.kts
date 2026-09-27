plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.android.multiplatform.library) apply false
  alias(libs.plugins.spotless) apply false
}

// Formatting/linting for the Android build — the same ktlint config the root (desktop) build
// applies, deliberately duplicated rather than left off: `DeX/app/src` holds the larger half of
// the UI and it was the only Kotlin in the repo no formatter ever looked at, which is how a file
// ends up with dead imports the desktop build would have rejected in a review gate. Baseline
// relaxations are identical to the root's so a shared `core:*` source file formats the same way
// under either build; `.editorconfig` at the repo root supplies the same defaults for both.
subprojects {
  apply(plugin = "com.diffplug.spotless")
  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    // Ratchet: check only the files that differ from HEAD.
    //
    // Without it this gate is red on day one — the Android tree predates the formatter and 126
    // of its files violate import order / trailing commas, so a plain `spotlessCheck` could only
    // be made to pass by reformatting the whole tree in one whitespace commit, which buries
    // whatever real change it rides in with. Ratcheting inverts that: every file a developer
    // actually touches is checked, the untouched backlog is not, and the backlog shrinks as
    // files get edited instead of in one blind sweep. Run `spotlessApply` to format a file (or,
    // deliberately, everything).
    ratchetFrom("HEAD")
    val baselineOverrides = mapOf(
      "ktlint_code_style" to "intellij_idea",
      "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
      "max_line_length" to "200",
      "ktlint_standard_no-wildcard-imports" to "disabled",
      "ktlint_standard_no-empty-file" to "disabled",
      "ktlint_standard_backing-property-naming" to "disabled",
      "ktlint_standard_property-naming" to "disabled",
      "ktlint_standard_filename" to "disabled",
      // The Android tree is 2-space indented (sources and build scripts alike) while `core/*`
      // and `composeApp` are 4-space. `ktlint_code_style` pins the indent rule to 4 regardless
      // of `indent_size`, so enforcing it here would rewrite every file in `DeX/app/src` for
      // whitespace alone. Indentation stays the Android tree's own convention; the rules that
      // find actual defects — dead imports, import order, trailing whitespace — stay on.
      "ktlint_standard_indent" to "disabled"
    )
    kotlin {
      target("src/**/*.kt")
      targetExclude("**/generated/**")
      ktlint(libs.versions.ktlint.get()).editorConfigOverride(baselineOverrides)
    }
  }
}
