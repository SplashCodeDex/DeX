## 2026-08-17T00:19:38Z
You are teamwork_preview_worker for Milestone 1: Desktop Window & Shell Architecture (R1).
Working directory: w:\CodeDeX\DeX\.agents\worker_m1\
Parent conversation ID: 56b8cce9-9bf3-4084-b06c-25e03e0eccf5

Please read:
- w:\CodeDeX\DeX\.agents\ORIGINAL_REQUEST.md
- w:\CodeDeX\DeX\PROJECT.md
- w:\CodeDeX\DeX\.agents\explorer_survey_1\handoff.md
- w:\CodeDeX\DeX\.agents\explorer_survey_2\handoff.md
- w:\CodeDeX\DeX\.agents\explorer_survey_3\handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A forensic auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

You have exclusive write ownership of:
- w:\CodeDeX\DeX\DeX\composeApp\src\desktopMain\kotlin\com\dexstudios\dex\main.kt
- w:\CodeDeX\DeX\DeX\composeApp\src\desktopMain\kotlin\com\dexstudios\dex\platform\ScreenBoundsHelper.kt
- w:\CodeDeX\DeX\DeX\composeApp\src\desktopMain\kotlin\com\dexstudios\dex\platform\TaskbarWorkAreaProvider.kt
- w:\CodeDeX\DeX\DeX\composeApp\src\desktopMain\kotlin\com\dexstudios\dex\window\DockedWindowStateController.kt

Implementation Requirements:
1. `TaskbarWorkAreaProvider.kt`: Implement DPI-aware multi-monitor taskbar insets provider and cursor-based active monitor detection using Java AWT (`GraphicsEnvironment`, `Toolkit.getDefaultToolkit().getScreenInsets()`, `MouseInfo`). Implement exact resting position formula:
   X = workArea.right - 1420 + 12
   Y = workArea.bottom - 430 - 38
2. `DockedWindowStateController.kt`: Implement full desktop docked window state controller managing `isVisible`, `isPinned`, `isShowingTransition`, `isPairingActive`, `isModalDialogOpen`, `expandedPanel` (`None`, `FileExplorer`, `Settings`, `Pairing`), drag state, double-tap reset, panel toggle methods, and the 5-point guard check:
   `shouldDismissOnFocusLoss() = !isPinned && !isShowingTransition && !isPairingActive && !isExpanded && !isModalDialogOpen`
3. `main.kt`:
   - Setup `Window(undecorated = true, transparent = true, alwaysOnTop = true, resizable = false, title = "DeX")`.
   - Setup `window.type = java.awt.Window.Type.UTILITY` inside `LaunchedEffect(window)` for taskbar icon suppression.
   - Attach `WindowFocusListener` enforcing `if (controller.shouldDismissOnFocusLoss()) { controller.isVisible = false }`.
   - Wire `DropTarget` for external Windows Explorer file transfers.
   - Setup `Tray(icon = Res.drawable.dex_logo, tooltip = "DeX", ...)` with 300ms click debounce and native context menu (`Show/Hide DeX`, separator, `Quit`).
   - Clean DI and ViewModel scoping.
4. Verify compilation by running:
   `./gradlew :composeApp:compileKotlinDesktop` in `w:\CodeDeX\DeX\DeX` and ensure exit code 0.

Write your implementation report to `w:\CodeDeX\DeX\.agents\worker_m1\handoff.md` and track progress in `w:\CodeDeX\DeX\.agents\worker_m1\progress.md`. Send a message to parent when done.
