package com.dexstudios.dex.window.kinematics

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.platform.WorkAreaBounds
import kotlin.math.abs
import kotlin.math.max

/**
 * Mathematical Kinematics and Physics Engine for the DeX Floating Dock Card.
 *
 * Provides:
 * - 1:1 ports of WPF ElasticEase and BackEase easing curves
 * - Dynamic Nudge-ForExpand boundary math with post-expansion evaluation
 * - 20px Magnetic Edge Snapping & Off-screen grab clamping
 * - Contraction Origin Clamping (Void Prevention)
 */
object DockCardPhysics {

    // === Spring Specifications (1:1 Port of WPF ElasticEase Oscillations=1, Springiness=7) ===
    const val SPRING_DAMPING_RATIO = 0.65f
    const val SPRING_STIFFNESS = 300f

    val ElasticExpansionSpec = spring<Float>(
        dampingRatio = SPRING_DAMPING_RATIO,
        stiffness = SPRING_STIFFNESS
    )

    val ElasticDpSpec = spring<Dp>(
        dampingRatio = SPRING_DAMPING_RATIO,
        stiffness = SPRING_STIFFNESS
    )

    val ElasticIntOffsetSpec = spring<IntOffset>(
        dampingRatio = SPRING_DAMPING_RATIO,
        stiffness = SPRING_STIFFNESS
    )

    // === Pop-In Entrance Easing (1:1 Port of WPF BackEase Amplitude=3.53) ===
    val PopInEase = Easing { fraction ->
        val t = fraction - 1f
        val a = 3.53f
        1f + t * t * ((a + 1f) * t + a)
    }

    // === Pop-In Entrance Spring Spec (500ms feel) ===
    val PopInSpringSpec = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 300f
    )

    // === Button Hover Micro-Lift Easing (1:1 Port of WPF BackEase Amplitude=1.22 / CubicBezier) ===
    val HoverEase = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)

    // === Drawer Contraction Easing (1:1 Port of WPF BackEase Amplitude=0.15) ===
    val ContractEase = Easing { fraction ->
        val t = fraction - 1f
        val a = 0.15f
        1f + t * t * ((a + 1f) * t + a)
    }

    // === Magnetic Snap Settle Spec (120ms CubicEase EaseOut) ===
    val MagneticSnapSettleSpec = tween<Float>(
        durationMillis = 120,
        easing = FastOutSlowInEasing
    )

    // === Atomic 2D Reset Spec (450ms FastOutSlowInEasing) ===
    val AtomicResetSpec = tween<Float>(
        durationMillis = 450,
        easing = FastOutSlowInEasing
    )

    const val SNAP_THRESHOLD_PX = 20
    const val MANHATTAN_DEADZONE_PX = 5
    const val MIN_GRAB_PX = 60

    /**
     * Computes the required window displacement (Nudge) when expanding the card,
     * ensuring that the post-expansion dimensions never clip off-screen.
     */
    fun calculateExpansionNudge(
        currentWindowX: Int,
        currentWindowY: Int,
        cardWidth: Int = 300,
        cardHeight: Int = 430,
        expandDeltaWidth: Int,
        expandDeltaHeight: Int,
        workArea: WorkAreaBounds,
        canvasWidth: Int = 1420,
        canvasHeight: Int = 760,
        margin: Int = 25
    ): Pair<Int, Int> {
        val contentLeft = currentWindowX + canvasWidth - margin - cardWidth
        val contentRight = currentWindowX + canvasWidth - margin
        
        val isMacOS = com.dexstudios.dex.platform.DesktopEnvironment.isMacOS
        
        val contentTop = if (isMacOS) {
            currentWindowY + margin
        } else {
            currentWindowY + canvasHeight - margin - cardHeight
        }
        val contentBottom = contentTop + cardHeight

        val spaceLeft = contentLeft - workArea.left
        val spaceRight = workArea.right - contentRight
        val spaceUp = contentTop - workArea.top
        val spaceDown = workArea.bottom - contentBottom

        val canExpandLeft = spaceLeft >= (expandDeltaWidth + 20) || spaceLeft >= spaceRight

        var targetX = currentWindowX
        var targetY = currentWindowY

        if (!canExpandLeft) {
            targetX += expandDeltaWidth
        }

        val expW = cardWidth + expandDeltaWidth
        val expH = cardHeight + expandDeltaHeight
        val expTop: Int
        val expBottom: Int

        if (isMacOS) {
            // macOS uses TopDock, expands Downwards natively
            val canExpandDown = spaceDown >= (expandDeltaHeight + 20) || spaceDown >= spaceUp
            if (!canExpandDown) {
                targetY -= expandDeltaHeight
            }
            expTop = targetY + margin
            expBottom = expTop + expH
        } else {
            // Windows/Linux uses BottomDock, expands Upwards natively via Compose offset
            val canExpandUp = spaceUp >= (expandDeltaHeight + 20) || spaceUp >= spaceDown
            if (!canExpandUp) {
                targetY += expandDeltaHeight
            }
            // Due to native Compose upward offset, the expBottom is physically pinned, and expTop moves up
            expBottom = targetY + canvasHeight - margin
            expTop = expBottom - expH
        }

        // Post-expansion boundary clamping: evaluate against target expanded dimensions (e.g. 1054x625 dp)
        val expLeft = targetX + canvasWidth - margin - expW
        val expRight = targetX + canvasWidth - margin

        if (expLeft < workArea.left) targetX += (workArea.left - expLeft)
        if (expRight > workArea.right) targetX -= (expRight - workArea.right)
        if (expTop < workArea.top) targetY += (workArea.top - expTop)
        if (expBottom > workArea.bottom) targetY -= (expBottom - workArea.bottom)

        return Pair(targetX, targetY)
    }

    /**
     * Evaluates 20px magnetic boundary snapping for an active drag candidate position.
     */
    fun evaluateMagneticSnap(
        candidateContentLeft: Int,
        candidateContentTop: Int,
        cardWidth: Int,
        cardHeight: Int,
        workArea: WorkAreaBounds,
        snapThreshold: Int = SNAP_THRESHOLD_PX
    ): Pair<Int, Int> {
        val contentRight = candidateContentLeft + cardWidth
        val contentBottom = candidateContentTop + cardHeight

        var finalLeft = candidateContentLeft
        var finalTop = candidateContentTop

        // 2. Inward-only magnetic snapping (matches legacy WPF behavior)
        // We do NOT use abs() because snapping outwardly would create a 40px "sticky wall"
        // when dragging across multi-monitor boundaries, trapping the window.
        // It must only snap when the content is INWARD of the work area bounds by < 20px.
        
        if (candidateContentLeft > workArea.left && candidateContentLeft - workArea.left < snapThreshold) {
            finalLeft = workArea.left
        }
        if (contentRight < workArea.right && workArea.right - contentRight < snapThreshold) {
            finalLeft = workArea.right - cardWidth
        }
        if (candidateContentTop > workArea.top && candidateContentTop - workArea.top < snapThreshold) {
            finalTop = workArea.top
        }
        if (contentBottom < workArea.bottom && workArea.bottom - contentBottom < snapThreshold) {
            finalTop = workArea.bottom - cardHeight
        }

        return Pair(finalLeft, finalTop)
    }

    /**
     * Sanity clamps coordinates to ensure at least MIN_GRAB_PX (or 20% width) remains reachable inside the work area.
     */
    fun applySanityClamp(
        contentLeft: Int,
        contentTop: Int,
        cardWidth: Int,
        cardHeight: Int,
        workArea: WorkAreaBounds,
        minGrab: Int = MIN_GRAB_PX
    ): Pair<Int, Int> {
        val grab = max((cardWidth * 0.2f).toInt(), minGrab)
        var clampedLeft = contentLeft
        var clampedTop = contentTop

        if (contentLeft + cardWidth < workArea.left + grab) clampedLeft = workArea.left + grab - cardWidth
        if (contentLeft > workArea.right - grab) clampedLeft = workArea.right - grab
        if (contentTop + cardHeight < workArea.top + grab) clampedTop = workArea.top + grab - cardHeight
        if (contentTop > workArea.bottom - grab) clampedTop = workArea.bottom - grab

        return Pair(clampedLeft, clampedTop)
    }

    /**
     * Combined 20px magnetic snap and off-screen grab clamping.
     */
    fun calculateSnapAndClamp(
        candidateContentLeft: Int,
        candidateContentTop: Int,
        cardWidth: Int,
        cardHeight: Int,
        workArea: WorkAreaBounds,
        snapThreshold: Int = SNAP_THRESHOLD_PX,
        minGrab: Int = MIN_GRAB_PX
    ): Pair<Int, Int> {
        val (snappedLeft, snappedTop) = evaluateMagneticSnap(
            candidateContentLeft = candidateContentLeft,
            candidateContentTop = candidateContentTop,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            workArea = workArea,
            snapThreshold = snapThreshold
        )
        return applySanityClamp(
            contentLeft = snappedLeft,
            contentTop = snappedTop,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            workArea = workArea,
            minGrab = minGrab
        )
    }

    /**
     * Evaluates Contraction Origin Clamping to prevent a contracted card from stranding in off-screen void.
     */
    fun calculateContractionOrigin(
        currentWindowX: Int,
        contractedCardWidth: Int = 300,
        workArea: WorkAreaBounds,
        canvasWidth: Int = 1420,
        margin: Int = 25,
        minGrab: Int = MIN_GRAB_PX
    ): Int {
        val cRight = currentWindowX + canvasWidth - margin
        val cContractedLeft = cRight - contractedCardWidth
        val grab = max((contractedCardWidth * 0.2f).toInt(), minGrab)

        return if (cContractedLeft > workArea.right - grab) {
            val targetLeft = workArea.right - grab
            targetLeft - canvasWidth + margin + contractedCardWidth
        } else {
            currentWindowX
        }
    }
}
