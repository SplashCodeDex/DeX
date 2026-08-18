## 2026-08-16T22:34:41Z
You are the Migration Plan Authoring Worker (migration_doc_worker_1).
Your working directory is W:\CodeDeX\DeX\.agents\migration_doc_worker_1.
You MUST read W:\CodeDeX\DeX\.agents\ORIGINAL_REQUEST.md before doing anything else.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. An auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Context & Inputs to Read:
1. W:\CodeDeX\DeX\.agents\ORIGINAL_REQUEST.md
2. W:\CodeDeX\DeX\.agents\PROJECT.md
3. W:\CodeDeX\DeX\.agents\wpf_spec_miner_1\analysis.md (WPF reverse-engineered specs, storyboards, exact formulas, tokens, quick actions, file explorer)
4. W:\CodeDeX\DeX\.agents\compose_window_explorer_1\analysis.md (Compose Desktop windowing, Skiko transparency, TaskbarWorkAreaProvider, fixed transparent canvas, drag/magnetism pipeline)
5. W:\CodeDeX\DeX\.agents\liquid_glass_ui_explorer_1\analysis.md (LiquidGlass backdrop library v2.0.0, Skia shaders, design tokens, quick action buttons, lazy file explorer)
6. Target Document: W:\CodeDeX\DeX\UltimateMigrationPlan-WPF-Compose-UI.md

Your Mission:
Write and append a comprehensive, production-grade, highly detailed 1:1 Visual & UX Parity Migration Specification for the Floating Docked Card Interface into `W:\CodeDeX\DeX\UltimateMigrationPlan-WPF-Compose-UI.md`.

The new section MUST be exhaustive and include:
1. Executive Architectural Blueprint:
   - WPF / Win32 vs Compose Multiplatform architecture comparison.
   - Core libraries and dependencies (`io.github.kyant0:backdrop:2.0.0`, Compose Multiplatform 1.8.x+, Skiko, Kotlin Coroutines, Java AWT/JNA).
2. Window Shell, Docking & Geometry Mechanics:
   - Window properties (`Window(undecorated = true, transparent = true, alwaysOnTop = true, resizable = false)`).
   - Taskbar-aware work area calculations (`TaskbarWorkAreaProvider` using `GraphicsEnvironment` + `Toolkit.getScreenInsets` across Bottom/Top/Left/Right taskbar placements, multi-monitor, DPI scaling).
   - Resting position formula: $1420 \times 760\text{ dp}$ fixed transparent bounding canvas with card positioned at $13\text{ dp}$ right gap and $38\text{ dp}$ above taskbar.
   - Zero-flicker state transition strategy: Fixed canvas vs OS window resizing analysis.
   - `Nudge-ForExpand` dynamic coordinate shifting algorithm when card expands near screen bounds.
   - 3-Phase Drag, Magnetic Snapping ($20\text{ px}$ threshold, $120\text{ ms}$ snap), Minimum Reachability Clamp ($60\text{ px}$), and Double-Click Reset ($450\text{ ms}$ `BouncyEase`).
3. Liquid Glass & Visual Effects Architecture:
   - `io.github.kyant0:backdrop` (v2.0.0) desktop implementation (`drawBackdrop`, `rememberLayerBackdrop`, `Backdrop`, `vibrancy`, `blur`, `lens`, `Highlight`, `Shadow`).
   - Skia fallback shader pipeline for Desktop OS transparency.
   - Drop shadow rendering (Skia BlurMaskFilter / Canvas drawRoundRect) with exact ambient/key shadow levels.
   - Subpixel antialiased border glow (`#FFFFFF` with $0.15$ alpha, $1\text{ dp}$ border).
4. State Machine & Kinematic Physics:
   - Dimension state table: Contracted ($300 \times 500\text{ dp}$), File Explorer Expanded ($1054 \times 695\text{ dp}$), Settings Expanded ($675 \times 695\text{ dp}$).
   - Mathematical port of WPF `ElasticEase` (stiffness 300, damping 0.65, $800\text{ ms}$) and `BackEase(0.15)` ($600\text{ ms}$) to Jetpack Compose animation specs (`spring(dampingRatio = 0.65f, stiffness = 300f)` and custom `CubicBezierEasing`).
   - PopIn & Dismissal animations ($500\text{ ms}$ / $250\text{ ms}$ fade-out).
5. Tactile Quick Action Buttons:
   - 4 action pills ($56 \times 44\text{ dp}$, `CornerRadius=20dp`): DND, Mirror, Pull, Clipboard Sync, plus dynamic Close Menu danger pill.
   - Tactile micro-interactions: Hover scale $1.08\times$ / translateY $-3\text{ dp}$, Press scale $0.85\times$ / translateY $+3\text{ dp}$, Checked active state (`#0AE66D`).
   - Tooltips, badge counters, and click command dispatchers.
6. Embedded File Explorer & Directory Navigation:
   - Header (Up directory, $150\text{ ms}$ debounced search bar, view mode toggle, avatar).
   - Grid body (`LazyVerticalGrid`, $100 \times 105\text{ dp}$ file cards, thumbnail caching with $4\text{ dp}$ clip, file type icon resolution, context menu, double-click protection).
   - Footer (Send Files/Folders, floating download toast, and pull progress dock).
   - Drag & drop integration via Compose Desktop `onExternalDrag`.
7. Production Kotlin / Compose Implementation Reference:
   - Full, working code snippets for `DockedWindowStateController`, `TaskbarWorkAreaProvider`, `LiquidGlassCard`, `QuickActionsToolbar`, `FileExplorerGrid`, and `DockedCardWindow`.
8. Complete Design Tokens Matrix (Dark & Light theme colors, typography, elevations, corner radii).
