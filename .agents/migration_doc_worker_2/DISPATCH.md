## 2026-08-16T22:40:13Z

You are the Migration Plan Fix Worker (migration_doc_worker_2).
Your working directory is W:\CodeDeX\DeX\.agents\migration_doc_worker_2.
You MUST read W:\CodeDeX\DeX\.agents\ORIGINAL_REQUEST.md before doing anything else.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. An auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Context:
1. W:\CodeDeX\DeX\.agents\ORIGINAL_REQUEST.md
2. Target Document: W:\CodeDeX\DeX\UltimateMigrationPlan-WPF-Compose-UI.md
3. Challenger 1 Report: W:\CodeDeX\DeX\.agents\challenger_1\challenge.md
4. Challenger 2 Report: W:\CodeDeX\DeX\.agents\challenger_2\challenge.md
5. Challenger 1 Validation Script: W:\CodeDeX\DeX\.agents\challenger_1\test_fixes.py

Your Mission:
Update and refine `W:\CodeDeX\DeX\UltimateMigrationPlan-WPF-Compose-UI.md` to integrate the 8 critical mathematical, geometry, deactivation guard, and shader fixes identified during the gate challenge:

1. **Canvas Alignment & Resting Y Coordination**:
   Ensure `FloatingDockCard.kt` uses `Alignment.TopEnd` (or document the exact matching alignment) within the $1420 \times 760\text{ dp}$ canvas with `margin = 25.dp`, so that $Y_{\text{window}} = \text{Bottom}_{\text{work}} - 430 - 38$ correctly positions the resting card 38px above the taskbar and 13px from the right screen edge without pushing content off-screen.
2. **Contraction Clamping Void Prevention**:
   Add post-contraction position sanitization when transitioning from expanded ($1054\text{ dp}$) to contracted ($300\text{ dp}$). When contracting, clamp $X_{\text{window}} = \min(X_{\text{window}}, \text{workArea.right} - 1420 + 300 - 60)$ so the card is never left stranded in an unreachable void off-screen.
3. **Nudge-ForExpand Post-Expansion Boundary Evaluation**:
   Update `calculateExpansionNudge` so boundary clearances are calculated using the target expanded card dimensions ($1054 \times 625\text{ dp}$) rather than unexpanded dimensions, guaranteeing that cards expanding on screens $\le 1024\text{ px}$ nudge properly into the viewport.
4. **Skia Blur Sigma & Reusable Paint Shader**:
   Update `skiaDropShadow` to use Gaussian sigma $\sigma = \text{radius} / 2.0\text{f}$ for `MaskFilter.makeBlur(FilterMode.NORMAL, sigma)` and hoist `Paint` allocation outside `drawBehind` / remember the paint object to eliminate per-frame GC allocations. Clarify canvas margin padding for wide shadows.
5. **High-DPI Scaling in Drag Delta**:
   In `DockedCardWindow` / `DraggableArea`, ensure physical pixel mouse drag deltas are converted to `Dp` by dividing by screen density (`density` or DPI scale), ensuring 1:1 cursor tracking on 125%, 150%, 200% displays.
6. **Synchronized Single-Coroutine Window Position Animation**:
   In `animateWindowTo`, animate the coordinates as a single coordinated `DpOffset` or `IntOffset` within a single coroutine to eliminate asynchronous race conditions and diagonal visual tearing during double-click reset.
7. **Auto-Dismissal Deactivation Guard**:
   In `WindowFocusListener` (Section 2.1 & 1.3), guard the deactivation auto-hide with `if (!windowController.isExpanded && !windowController.isModalDialogOpen)` matching WPF `Bindings_Window.ps1` (L592–601), preventing deactivation during file explorer browsing, desktop drag-and-drop, or native file dialog interaction.
8. **Active Button Badge Contrast**:
   In `DeXQuickActionButton.kt`, invert badge container background to `#16121A` (dark) with `#FFFFFF` (white text) when `isChecked == true`, ensuring high-contrast legibility against the `#0AE66D` emerald active pill.
