# DeX — Dependency & Toolchain Update Plan

Prepared: 2026-08-16
Scope: `DeX/` Kotlin Multiplatform + Compose Multiplatform project (KMP modules `:app`, `:composeApp`, `:core:*`, `:feature:*`).

---

## 1. Verdict

The version catalog (`gradle/libs.versions.toml`) is **already aligned to the latest stable toolchain** for most artifacts. The major items — Kotlin, AGP, Compose Multiplatform, Ktor, Koin, Coil, coroutines, serialization — are current as of August 2026. The real work is:

1. **Two genuine version bugs/skews** (Compose artifacts, KSP) and one unverifiable number (Compose BOM).
2. **Hardcoded/duplicated dependencies** in module build files that bypass the catalog.
3. **Deprecation migrations** (Compose testing v1→v2, KSP1→KSP2 confirmation, AGP 9 built-in Kotlin).
4. **Verify-only items** for a handful of version numbers that could not be independently confirmed.

---

## 2. Confirmed latest stable (verified against official sources, Aug 2026)

| Category | Current | Latest stable | Status |
|---|---|---|---|
| Kotlin (KGP) | 2.4.10 | 2.4.10 (Jul 14, 2026) | OK |
| AGP | 9.3.1 | 9.3.1 (Jul 23, 2026; 9.4.0 is alpha) | OK |
| Compose Multiplatform plugin | 1.11.1 | 1.11.1 | OK |
| Ktor | 3.5.2 | 3.5.2 (Aug 4, 2026) | OK |
| kotlinx-coroutines | 1.11.0 | 1.11.0 (May 2026) | OK |
| kotlinx-serialization-json | 1.11.0 | 1.11.0 | OK |
| Koin BOM | 4.2.2 | 4.2.2 (Jun 15, 2026) | OK |
| Coil (coil3) | 3.5.0 | 3.5.0 (Jun 10, 2026) | OK |
| Navigation 3 | 1.1.6 | 1.1.6 stable (1.2.0-alpha07 is preview) | OK |

Gradle wrapper: `9.7.0` — compatible with AGP 9.3.x (AGP 9.3 requires Gradle ≥ 9.5). Confirm the newest 9.x patch is used if a later one exists, but 9.7.0 is fine.

---

## 3. Critical issues — must fix

### 3.1 Compose artifact version skew (BUG)
`libs.versions.toml`:
- `compose-multiplatform = "1.11.1"` (plugin)
- `compose-artifacts = "1.8.2"`  ← used by `jb-compose-runtime/foundation/material3/ui/components-resources/components-uiToolingPreview`

The plugin is 1.11.1 but the `org.jetbrains.compose.*` artifacts resolve to **1.8.2**. This forces a downgrade of the Compose runtime/foundation/M3 UI against a 1.11.1 compiler → class/API mismatch risk, missing API symbols, and silent binary incompatibility.

**Fix (preferred):** drop the per-artifact `jb-compose-*` entries and use the Compose Gradle plugin's built-in `compose.*` accessors (already partially used: `compose.desktop.currentOs`, `compose.components.resources`):
```kotlin
// commonMain
implementation(compose.runtime)
implementation(compose.foundation)
implementation(compose.material3)
implementation(compose.ui)
implementation(compose.components.resources)
implementation(compose.components.uiToolingPreview)
```
This guarantees version alignment with the plugin forever.

**Fix (minimal):** set `compose-artifacts = "1.11.1"`.

### 3.2 KSP version (verify)
`ksp = "2.3.11"`. The latest published KSP2 is **`2.3.10`** (verified via Maven Central + Kotlin docs, which pair Kotlin 2.4.10 with KSP `2.3.10`). `2.3.11` likely does not exist and will fail resolution.

- **Fix:** set `ksp = "2.3.10"`.
- Confirm KSP1 is NOT in use anywhere (KSP1 is deprecated and incompatible with Kotlin ≥2.3 / AGP ≥9). The plugin `com.google.devtools.ksp` is KSP2 — good. Verify `androidx.appfunctions:appfunctions-compiler` supports KSP2.

### 3.3 Compose BOM number (verify)
`androidxComposeBom = "2026.08.00"`. Latest BOM confirmed on Google Maven is **`2026.06.01`** (Jul 1, 2026). `2026.08.00` may be ahead of what is actually published (there was a known case of `2026.06.00` not resolving at first).

- **Fix:** check the BOM mapping page (https://developer.android.com/develop/ui/compose/bom/bom-mapping) and set the latest **actually published** BOM (likely `2026.06.01`). Do not trust a date string that has not been published to `dl.google.com`.

---

## 4. Cleanup — hardcoded & duplicated dependencies

These bypass the version catalog and will drift:

1. `core/network/build.gradle.kts:34` — `implementation("androidx.core:core-ktx:1.13.1")`
   → replace with `implementation(libs.androidx.core.ktx)` (1.19.0).
2. `core/data/build.gradle.kts:28` — `implementation("io.insert-koin:koin-core")`
   → replace with `implementation(libs.koin.core)` (BOM-managed).
3. `core/data/build.gradle.kts:13-18` — duplicated `compilerOptions { jvmTarget ... }` block. Remove one.
4. `core/designsystem/build.gradle.kts:14-19` — same duplicated `compilerOptions` block. Remove one.
5. Inconsistent JVM target naming: `composeApp` uses `jvm("desktop")`; other modules use `jvm()`. Harmless, but standardize if a `desktop` target name is needed for source-set references.
6. `libs.versions.toml` has entries that are unused or redundant with plugin accessors once 3.1 is applied (the `jb-compose-*` library block) — remove after migrating.

---

## 5. Deprecation & migration items

1. **Compose UI testing v1 → v2 (required).** With Compose 1.11 (BOM 2026.x), the v1 testing APIs (`androidx.compose.ui.test`) are deprecated; v2 is default. The project uses `androidx-compose-ui-test-junit4` and `androidx-compose-ui-test-manifest`. Migrate tests to the v2 `ComposeUiTest` API (`runComposeUiTest`, `ExperimentalTestApi` removed, `StandardTestDispatcher` semantics).
2. **AGP 9 built-in Kotlin.** The `:app` module already omits `org.jetbrains.kotlin.android` and relies on AGP's built-in Kotlin (uses the `kotlin { }` block). Confirm this is intentional and complete — do NOT re-add a Kotlin Android plugin. Remove the temporary `android.builtInKotlin=false` opt-out if it ever appears in `gradle.properties`.
3. **KSP1 → KSP2.** Already on `com.google.devtools.ksp` (KSP2). Verify the AppFunctions processor works; no action beyond 3.2.
4. **Compose compiler `stabilityConfigurationPath`.** Kotlin 2.4 changed the default stability of non-final classes to *Unknown*. `compose_stability.conf` may need updating so annotated classes stay `Stable` where needed; otherwise UI recomposition scope may silently widen. Review the file against the Kotlin 2.4 / Compose compiler stability model.
5. **Navigation redundancy.** The project uses BOTH:
   - JetBrains multiplatform Nav2: `org.jetbrains.androidx.navigation:navigation-compose` 2.9.2 (`libs.androidx.navigation.compose`), and
   - `androidx.navigation3:*` (1.1.6).
   Decide on one navigation stack. Navigation 3 is the forward-looking stable choice for KMP; consider migrating `composeApp` off the JetBrains `navigation-compose` artifact to remove the duplicate.
6. **`lifecycle-viewmodel-navigation3` / `androidx.lifecycle:lifecycle-viewmodel-navigation3`** is stable (2.11.0) — no change, but keep it aligned with `androidxLifecycle`.
7. **`appfunctions`** is `1.0.0-alpha10` (pre-release by design). There is no "latest stable"; keep tracking alphas if the feature is required, otherwise gate it.

---

## 6. Verify-only versions (could not be independently confirmed against an official release page)

These are plausible but must be confirmed before the bump is considered "latest":

| Version key | Current | Where to verify |
|---|---|---|
| `cronet` | 500.0.1 | google maven `org.chromium.net:cronet-embedded` |
| `backdrop` | 2.0.0 | `io.github.kyant0:backdrop` (GitHub Kyant0/backdrop) |
| `composeNativeTray` | 2.1.0 | `dev.nucleusframework:composenativetray` (GitHub) |
| `jmdns` | 3.6.3 | `org.jmdns:jmdns` |
| `orgjson` | 20260814 | `org.json:json` (date-stamped) |
| `okhttp` | 5.5.0 | `com.squareup.okhttp3:okhttp` |
| `mockk` | 1.14.11 | `io.mockk:mockk` |
| `timber` | 5.0.1 | `com.jakewharton.timber:timber` |
| `profileinstaller` | 1.4.1 | `androidx.profileinstaller` |
| `playServicesAuth` | 21.6.0 | `com.google.android.gms:play-services-auth` |
| `play-services-code-scanner` | 16.1.0 | hardcoded in catalog |
| `googleid` | 1.2.0 | `com.google.android.libraries.identity.googleid:googleid` |
| `credentials` | 1.6.0 | `androidx.credentials` |
| `androidxCore` | 1.19.0 | `androidx.core:core-ktx` |
| `androidxLifecycle` | 2.11.0 | `androidx.lifecycle` |
| `androidxActivity` | 1.13.0 | `androidx.activity` |
| `datastore` | 1.2.1 | `androidx.datastore` |
| `workManager` | 2.11.2 | `androidx.work` |
| `androidx-navigation-compose` (JetBrains) | 2.9.2 | `org.jetbrains.androidx.navigation:navigation-compose` |
| `androidx-test` / `-ext` / `espresso` | 1.7.0 / 1.3.0 / 3.7.0 | `androidx.test` |
| `androidGradlePlugin` / `ksp` | see 3.2 / 3.3 | — |

---

## 7. Recommended execution order (phased)

1. **Phase 0 — baseline:** `git status` clean, run a full build to capture current warnings/errors.
2. **Phase 1 — catalog corrections (low risk, high value):**
   - Fix `compose-artifacts` → use `compose.*` accessors (or 1.11.1).
   - Fix `ksp` → 2.3.10.
   - Correct `androidxComposeBom` to the latest published BOM.
   - Remove now-unused `jb-compose-*` catalog entries.
3. **Phase 2 — cleanup:** replace the two hardcoded deps, remove duplicate `compilerOptions` blocks.
4. **Phase 3 — verify-only sweep:** confirm every version in Section 6 against Maven Central / Google Maven / GitHub releases, update as needed.
5. **Phase 4 — deprecations:** Compose testing v1→v2 migration; navigation stack consolidation; review `compose_stability.conf`; confirm AGP built-in Kotlin.
6. **Phase 5 — validation:**
   ```
   ./gradlew :app:assembleDebug
   ./gradlew :composeApp:desktopRunDistributable  (or :composeApp:runDistributable)
   ./gradlew test
   ```
   Run lint + `./gradlew :app:lintDebug` and fix any new deprecation warnings.

---

## 8. Notes / risks

- Compose 1.12 (upcoming) will require `compileSdk 37` + AGP 9 — the project already targets `compileSdk 37`, so it is future-ready.
- Do **not** chase AGP `9.4.0-alpha*`, Compose BOM `-alpha/-beta`, or Navigation3 `1.2.0-alpha*` — the request is *stable* versions only.
- `androidx.appfunctions` has no stable release; keep at the latest alpha only if actually used, else remove to avoid KSP/compiler churn.
