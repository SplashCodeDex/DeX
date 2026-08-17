# Compose Desktop Build System & Dependency Survey Report

**Author**: `explorer_survey_2` (Teamwork preview explorer)  
**Target Project**: Compose Multiplatform Desktop (`w:\CodeDeX\DeX\DeX`)  
**Parent Conversation ID**: `56b8cce9-9bf3-4084-b06c-25e03e0eccf5`  
**Date**: 2026-08-17  

---

## 1. Observation

### 1.1 Gradle Build System Configuration & Versions
Inspected build files:
- **`w:\CodeDeX\DeX\DeX\settings.gradle.kts`**:
  - Root project: `"DeX"`
  - Submodules: `:app`, `:composeApp`, `:core:network`, `:core:data`, `:core:designsystem`, `:feature:discovery`, `:feature:history`, `:feature:settings` (L38–47).
  - Repositories: `google()`, `mavenCentral()`, `maven("https://jitpack.io")`, `maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")` (L20–31).
  - Toolchain plugin: `id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"` (L34–36).
- **`w:\CodeDeX\DeX\DeX\gradle.properties`**:
  - `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8` (L9)
  - `org.gradle.caching=true` (L12)
  - `org.gradle.configuration-cache=true` (L15)
  - `android.useAndroidX=true` (L23)
  - `android.nonTransitiveRClass=true` (L29)
- **`w:\CodeDeX\DeX\DeX\gradle\libs.versions.toml`**:
  - Kotlin: `2.4.10` (L13)
  - Compose Multiplatform: `1.11.1` (L24)
  - Compose Material3: `1.11.0-alpha07` (L25)
  - Android Gradle Plugin: `9.3.1` (L2)
  - Backdrop (LiquidGlass): `2.0.0` (`io.github.kyant0:backdrop`, L32, L96)
  - Compose Native Tray: `2.1.0` (`dev.nucleusframework:composenativetray`, L27, L42)
  - Coroutines: `1.11.0` (`kotlinx-coroutines-core`, `kotlinx-coroutines-swing`, L11, L70–71)
  - Koin BOM: `4.2.2` (`koin-core`, `koin-compose`, `koin-compose-viewmodel`, L30, L87–93)
  - Ktor: `3.5.2` (L15, L74–83)
  - Coil: `3.5.0` (`io.coil-kt.coil3:coil-compose`, L37, L101)

### 1.2 Module Dependency Structure & Desktop Targets
- **`:composeApp` (`w:\CodeDeX\DeX\DeX\composeApp\build.gradle.kts`)**:
  - Targets: `jvm("desktop")` (L12) and `android` with JVM 17 target (L14–21).
  - `commonMain` dependencies:
    ```kotlin
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
    ```
  - `desktopMain` dependencies:
    ```kotlin
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.native.tray)
    implementation(libs.coroutines.swing)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    ```
  - Desktop Application Packaging Config:
    ```kotlin
    compose.desktop {
        application {
            mainClass = "com.dexstudios.dex.MainKt"
            nativeDistributions {
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                packageName = "DeX"
                packageVersion = "1.0.0"
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
    ```

- **`:core:designsystem` (`w:\CodeDeX\DeX\DeX\core\designsystem\build.gradle.kts`)**:
  - Targets `android` and `jvm()` (L9–18).
  - Explicitly exports:
    ```kotlin
    api(libs.backdrop)        // io.github.kyant0:backdrop:2.0.0
    api(libs.coil.compose)
    api(libs.jb.compose.components.resources)
    ```
  - Contains glass abstractions in `commonMain/kotlin/com/dexstudios/dex/core/designsystem/components/glass/`:
    - `LiquidGlassConfig.kt` (Curated presets: `Default`, `IconButton`, `NavBar`, `Dialog`, `Frosted`, `DynamicIsland`, `Flat`, `FlatInteractive`).
    - `LiquidGlassPanel.kt` (`LiquidGlassPanel(backdrop: Backdrop, ...)`).
    - `LiquidGlassIconButton.kt`
    - `LiquidToastNotification.kt`
    - `GlassScrollEdge.kt`

### 1.3 Desktop Window & Platform Interop Observations
- **`w:\CodeDeX\DeX\DeX\composeApp\src\desktopMain\kotlin\com\dexstudios\dex\main.kt`**:
  - Window Config:
    ```kotlin
    Window(
        onCloseRequest = { isVisible = false },
        visible = isVisible,
        state = windowState,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        title = "DeX"
    )
    ```
  - Tray: `Tray(icon = Res.drawable.dex_logo, tooltip = "DeX", primaryAction = { isVisible = !isVisible })` via `dev.nucleusframework:composenativetray:2.1.0`.
  - AWT Drop Target: `window.dropTarget = DropTarget().apply { ... }` for file dragging.
  - Focus Deactivation: `java.awt.event.WindowFocusListener` dismissing card when `!isPinned`.
  - Taskbar Icon Suppression: `window.type = java.awt.Window.Type.UTILITY`.
  - Multi-Monitor Work Area: `ScreenBoundsHelper.getWorkAreaBounds()` using `GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds` and `Toolkit.getDefaultToolkit().getScreenInsets(gc)`.
  - JNA status: JNA is currently not included in `libs.versions.toml`. All current desktop windowing, screen bounds, multi-monitor insets, tray integration, drag-and-drop, and focus listeners are accomplished using standard Java AWT APIs (`GraphicsEnvironment`, `Toolkit`, `MouseInfo`, `DropTarget`, `WindowFocusListener`) and `composenativetray`.

### 1.4 Verification of Gradle Commands & Build Health
1. **Compilation Command**: `.\gradlew :composeApp:compileKotlinDesktop`
   - **Result**: `BUILD SUCCESSFUL in 20s` (Exit code: `0`).
   - All modules (`:core:data`, `:core:network`, `:core:designsystem`, `:feature:discovery`, `:feature:history`, `:feature:settings`, `:composeApp`) compiled without errors.
2. **Packaging Command**: `.\gradlew :composeApp:desktopJar`
   - **Result**: `BUILD SUCCESSFUL in 3s` (Exit code: `0`).
   - Desktop executable JAR packaged into `composeApp/build/libs/composeApp-desktop.jar`.
3. **Dry-Run Task Verification**: `.\gradlew :composeApp:compileKotlinDesktop --dry-run`
   - **Result**: `BUILD SUCCESSFUL in 8s` (Exit code: `0`).

---

## 2. Logic Chain

1. **Gradle Build Architecture**:
   - The project is configured as a modern Gradle Kotlin Multiplatform multi-project build with Kotlin `2.4.10`, Compose Multiplatform `1.11.1`, AGP `9.3.1`, and Java 17 bytecode targets across JVM modules.
   - Gradle configuration cache (`org.gradle.configuration-cache=true`) and build cache (`org.gradle.caching=true`) are both active and successfully storing/reusing cached task graphs.

2. **LiquidGlass Dependency & Skia Integration**:
   - `io.github.kyant0:backdrop:2.0.0` is declared in `libs.versions.toml` and exported transitively from `:core:designsystem` via `api(libs.backdrop)`.
   - Because `:composeApp` depends on `:core:designsystem`, all Backdrop APIs (`drawBackdrop`, `layerBackdrop`, `rememberLayerBackdrop`, `blur`, `lens`, `vibrancy`, `Highlight`, `Shadow`, `InnerShadow`) are directly available across `desktopMain` and `commonMain`.
   - Skiko (the Kotlin Multiplatform Skia bridge) is bundled as part of Compose Multiplatform Desktop (`compose.desktop.currentOs`). It exposes Skia runtime shader effects (`org.jetbrains.skia.RuntimeEffect`), paint shaders, and Gaussian blur filters (`org.jetbrains.skia.MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)`).

3. **Window Transparency & Floating Canvas Physics**:
   - Compose Desktop `Window(undecorated = true, transparent = true, alwaysOnTop = true, resizable = false)` configures the underlying Skiko DirectX DirectComposition swapchain on Windows with per-pixel alpha transparency (`DXGI_ALPHA_MODE_PREMULTIPLIED`).
   - The transparent canvas design ($1420 \times 760\text{ dp}$ with `Alignment.TopEnd` and $25\text{ dp}$ internal padding) avoids dynamic OS window resizing, preventing Direct3D swapchain recreation stutter and eliminating window flicker.
   - Java AWT `window.type = Window.Type.UTILITY` suppresses the application from the Windows taskbar.

4. **Native Interop & JNA Assessment**:
   - Standard Java AWT (`GraphicsEnvironment.getLocalGraphicsEnvironment()`, `Toolkit.getDefaultToolkit().getScreenInsets()`, `MouseInfo.getPointerInfo()`, `java.awt.dnd.DropTarget`) fully handles multi-monitor work areas, taskbar insets, global cursor coordinates during drag-pill tracking, and drag-and-drop file transfers without requiring external native DLLs.
   - JNA (`net.java.dev.jna:jna:5.14.0`) is not currently present or required for core window docking, transparency, or liquid glass rendering. It remains optional only if low-level global Win32 mouse hooks (e.g. "wiggle-to-open") or direct DWM HWND attribute manipulation are added in future iterations.

---

## 3. Caveats

1. **Host OS Desktop Pass-Through Blur vs In-App Backdrop**:
   - `io.github.kyant0:backdrop` operates by capturing composables in its visual tree via `Modifier.layerBackdrop(backdrop)`. It blurs content within the Compose app tree (or an app-owned wallpaper/scene).
   - Host OS desktop pixels (wallpaper and third-party windows behind the transparent Compose window) pass through via Skiko's per-pixel alpha channel. For the card surface over the desktop, the custom `skiaDropShadow` + subpixel border glow + surface tint (`surfaceTintAlpha = 0.82f`) provides the dark frosted card appearance matching WPF.
2. **Multi-Monitor DPI Coordinate Conversions**:
   - `MouseInfo.getPointerInfo().location` returns physical screen pixels. During active drag pill operations, physical pixel deltas must be divided by `LocalDensity.current.density` ($\rho = \text{DPI}/96.0$) before applying to Compose `WindowState.position` to ensure 1:1 cursor tracking on $125\%$, $150\%$, and $200\%$ scaling displays.
3. **5-Point Deactivation Guard Parity**:
   - The `WindowFocusListener` in `main.kt` currently checks `if (!isPinned) isVisible = false`. To achieve 100% parity with WPF `Bindings_Window.ps1` (L592–601), it should incorporate the 5-point guard: `!isPinned && !isShowingTransition && !isPairingActive && !isExpanded && !isModalDialogOpen`.

---

## 4. Conclusion

1. **Build Health**: The Compose Multiplatform Desktop project build system is healthy, properly configured, and builds with 0 errors.
2. **Compilation & Packaging Commands**:
   - Build/Compile: `./gradlew :composeApp:compileKotlinDesktop`
   - Package Executable JAR: `./gradlew :composeApp:desktopJar`
   - Android target: `./gradlew assembleDebug`
3. **Dependency Readiness**: All required dependencies for the 1:1 Floating Docked Card UI—including `io.github.kyant0:backdrop:2.0.0`, Skiko Skia graphics, Compose Native Tray `2.1.0`, Kotlinx Coroutines Swing `1.11.0`, Koin BOM `4.2.2`, and Ktor `3.5.2`—are present, resolved, and verified in the project.

---

## 5. Verification Method

To independently verify all findings:
1. **Compile Desktop Target**:
   ```pwsh
   cd w:\CodeDeX\DeX\DeX
   .\gradlew :composeApp:compileKotlinDesktop
   ```
   *Expected output*: `BUILD SUCCESSFUL` with exit code `0`.
2. **Package Desktop JAR**:
   ```pwsh
   cd w:\CodeDeX\DeX\DeX
   .\gradlew :composeApp:desktopJar
   ```
   *Expected output*: `BUILD SUCCESSFUL` with JAR output at `w:\CodeDeX\DeX\DeX\composeApp\build\libs\composeApp-desktop.jar`.
3. **Verify Dependency Declarations**:
   - Inspect `w:\CodeDeX\DeX\DeX\gradle\libs.versions.toml` for `backdrop = "2.0.0"`, `compose-multiplatform = "1.11.1"`, `composeNativeTray = "2.1.0"`.
   - Inspect `w:\CodeDeX\DeX\DeX\core\designsystem\build.gradle.kts` line 31 for `api(libs.backdrop)`.
   - Inspect `w:\CodeDeX\DeX\DeX\composeApp\src\desktopMain\kotlin\com\dexstudios\dex\main.kt` for `Window(undecorated = true, transparent = true, alwaysOnTop = true)` and `Tray(...)`.
