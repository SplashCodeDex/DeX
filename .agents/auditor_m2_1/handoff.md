# Milestone 2 Forensic Integrity Audit Report

**Work Product**: Milestone 2 Floating Dock Card & Kinematics Layer (`composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/`)
**Target Deliverables**:
- `FloatingDockCard.kt`
- `DockCardContent.kt`
- `MainMenuColumn.kt`
- `components/DragPillHandle.kt`
- `kinematics/DockCardAnimations.kt`
- `kinematics/DockCardPhysics.kt`
- `ExpandedPanel.kt`
- `DockedWindowStateController.kt`
**Integrity Mode**: Development
**Profile**: General Project
**Verdict**: **INTEGRITY VIOLATION** (Due to Test Suite Execution Failures under Phase 2 Behavioral Verification)

---

## 1. Observation

### 1.1 Source Code Analysis & Kinematics Audit
- **Physics Formulas (`DockCardPhysics.kt:27-79`)**:
  - Genuine Compose Spring Spec: `spring(dampingRatio = 0.65f, stiffness = 300f)` corresponding to WPF `ElasticEase(Oscillations=1, Springiness=7)`.
  - Genuine Easing Functions: `PopInEase` implemented as exact WPF `BackEase(Amplitude=3.53)` curve:
    ```kotlin
    val PopInEase = Easing { fraction ->
        val t = fraction - 1f
        val a = 3.53f
        1f + t * t * ((a + 1f) * t + a)
    }
    ```
  - Genuine `ContractEase` implemented as exact WPF `BackEase(Amplitude=0.15)` curve:
    ```kotlin
    val ContractEase = Easing { fraction ->
        val t = fraction - 1f
        val a = 0.15f
        1f + t * t * ((a + 1f) * t + a)
    }
    ```
  - Genuine `HoverEase` implemented as `CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)`.
- **Dynamic Nudge-ForExpand (`DockCardPhysics.kt:88-136`)**:
  - Implements bounding box spatial evaluation against `workArea` boundaries with post-expansion clamping.
- **3-Phase Drag Gesture Engine (`DragPillHandle.kt:48-177` & `DockedWindowStateController.kt:206-320`)**:
  - Phase 1: 5px Manhattan Deadzone accumulator (`|dx| + |dy| >= 5px`).
  - Phase 2: High-DPI physical mouse delta tracking divided by display density (`dx / density`, `dy / density`) with 20px magnetic boundary snapping (`evaluateMagneticSnap`).
  - Phase 3: Drag release & sanity grab clamping (`applySanityClamp`).
- **5-Point Focus Loss Deactivation Guard (`DockedWindowStateController.kt:96-98`)**:
  - Evaluates `!isPinned && !isShowingTransition && !isPairingActive && !isExpanded && !isModalDialogOpen`.

### 1.2 Automated Build & Test Suite Execution
- Command executed: `./gradlew :composeApp:desktopTest`
- Result: **FAILED** (Exit code 1)
- Summary: **27 tests completed, 25 passed, 2 failed**.
- Test Suites:
  1. `com.dexstudios.dex.window.kinematics.DockCardPhysicsTest`: **8 / 8 PASSED (100%)**
  2. `com.dexstudios.dex.window.kinematics.DockCardPhysicsAdversarialTest`: **13 / 13 PASSED (100%)**
  3. `com.dexstudios.dex.window.DockedWindowStateControllerStressTest`: **4 / 6 PASSED (66%)**, **2 FAILED**:
     - `testRapidConsecutivePanelExpansionsAndContractions[desktop]` FAILED
     - `testDoubleTapResetBehavior[desktop]` FAILED

### 1.3 Verbatim Error Trace from `./gradlew :composeApp:desktopTest`
```
> Task :composeApp:desktopTest

DockedWindowStateControllerStressTest[desktop] > testRapidConsecutivePanelExpansionsAndContractions[desktop] FAILED
    java.lang.IllegalStateException at MonotonicFrameClock.kt:116
        Caused by: java.lang.IllegalStateException at DockedWindowStateControllerStressTest.kt:77

DockedWindowStateControllerStressTest[desktop] > testDoubleTapResetBehavior[desktop] FAILED
    java.lang.AssertionError at DockedWindowStateControllerStressTest.kt:147

27 tests completed, 2 failed

> Task :composeApp:desktopTest FAILED

Caused by: java.lang.IllegalStateException: A MonotonicFrameClock is not available in this CoroutineContext. Callers should supply an appropriate MonotonicFrameClock using withContext.
	at androidx.compose.runtime.MonotonicFrameClockKt.getMonotonicFrameClock(MonotonicFrameClock.kt:116)
	at androidx.compose.runtime.MonotonicFrameClockKt.withFrameNanos(MonotonicFrameClock.kt:85)
	at androidx.compose.animation.core.SuspendAnimationKt.callWithFrameNanos(SuspendAnimation.kt:305)
	at androidx.compose.animation.core.SuspendAnimationKt.animate(SuspendAnimation.kt:231)
	at androidx.compose.animation.core.Animatable$runAnimation$2.invokeSuspend(Animatable.kt:308)
	at androidx.compose.animation.core.Animatable.animateTo(Animatable.kt:241)
	at com.dexstudios.dex.window.DockedWindowStateController.animateWindowTo(DockedWindowStateController.kt:369)
```

---

## 2. Logic Chain

1. **Phase 1 Verification (Source Code Analysis)**:
   - Investigation of `FloatingDockCard.kt`, `DockCardContent.kt`, `MainMenuColumn.kt`, `DragPillHandle.kt`, `DockCardAnimations.kt`, `DockCardPhysics.kt`, and `ExpandedPanel.kt` showed no hardcoded test shortcuts, dummy physics, or fake returns.
   - The kinematics layer implements genuine Skia/Compose spring parameters (`dampingRatio = 0.65f, stiffness = 300f`) and exact mathematical ports of WPF `ElasticEase`, `BackEase`, and `HoverEase`.
   - The 3-phase drag engine correctly implements Manhattan deadzone accumulation, high-DPI scaling, 20px magnetic boundary snapping, and sanity clamping.

2. **Phase 2 Verification (Behavioral & Test Execution)**:
   - Behavioral verification requires that the test suite compiles and executes cleanly (`./gradlew :composeApp:desktopTest` must exit with 0).
   - In `DockedWindowStateController.kt` (lines 363-377), `animateWindowTo` uses Compose's `androidx.compose.animation.core.Animatable.animateTo`.
   - `Animatable.animateTo` depends on a `MonotonicFrameClock` in the active `CoroutineContext`.
   - When `DockedWindowStateController` methods (`expandPanel`, `collapsePanel`, `resetPositionToDefault`) are invoked in test suites using standard coroutine scopes without a frame clock (e.g. `CoroutineScope(Dispatchers.Unconfined)` in `DockedWindowStateControllerStressTest.kt`), `animateWindowTo` throws an unhandled `IllegalStateException: A MonotonicFrameClock is not available in this CoroutineContext`.
   - This caused `testRapidConsecutivePanelExpansionsAndContractions` and `testDoubleTapResetBehavior` to fail.

3. **Forensic Integrity Principle**:
   - Under the Forensic Audit Protocol: "The build must succeed and tests must execute — a project that doesn't build or whose tests don't run is automatically flagged. If ANY check fails, your verdict is INTEGRITY VIOLATION and you MUST reject the work product."
   - Because 2 automated tests failed in `:composeApp:desktopTest`, the work product cannot be certified as CLEAN.

---

## 3. Caveats

- The core physics and mathematical calculations (`DockCardPhysics.kt`) are 100% genuine, robust, and passed all 21 unit and adversarial stress tests (`DockCardPhysicsTest` and `DockCardPhysicsAdversarialTest`).
- In production UI runtime (`main.kt`), `controller` is instantiated with `rememberCoroutineScope()`, which has a Compose `MonotonicFrameClock` attached to the desktop window. The exception is specifically triggered in unit tests / headless coroutine contexts where `coroutineContext[MonotonicFrameClock] == null`.
- Implementation fix recommendations:
  - In `DockedWindowStateController.kt:animateWindowTo`: Add a fallback check for `coroutineContext[MonotonicFrameClock] == null` (e.g., directly set `windowState.position = WindowPosition(targetX.dp, targetY.dp)` if no frame clock is present in the context, or wrap animation execution safely).
  - In unit tests: Supply a test `MonotonicFrameClock` or use `runComposeUiTest` / `withFrameNanos`.

---

## 4. Conclusion

- **Verdict**: **INTEGRITY VIOLATION**
- **Rationale**: Phase 2 Behavioral Verification failed due to 2 test failures in `:composeApp:desktopTest` caused by `IllegalStateException` (missing `MonotonicFrameClock`) in `DockedWindowStateController.animateWindowTo`.
- **Action Required**: Resolve the `MonotonicFrameClock` exception in `DockedWindowStateController.kt` / `DockedWindowStateControllerStressTest.kt` so that `./gradlew :composeApp:desktopTest` passes with 100% success rate across all 27 tests.

---

## 5. Verification Method

To independently reproduce and verify this audit:
```bash
# In w:\CodeDeX\DeX\DeX
./gradlew :composeApp:desktopTest --no-daemon
```
**Invalidation Condition**: The verdict becomes `CLEAN` once `./gradlew :composeApp:desktopTest` completes with 0 test failures and exit code 0.
