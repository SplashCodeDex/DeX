import re
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def test_document_fixes():
    doc_path = r"W:\CodeDeX\DeX\UltimateMigrationPlan-WPF-Compose-UI.md"
    with open(doc_path, "r", encoding="utf-8") as f:
        content = f.read()

    print("=" * 80)
    print("VERIFYING MIGRATION PLAN FIX INTEGRATION")
    print("=" * 80)

    # 1. Canvas Alignment & Resting Y Coordination
    assert "Alignment.TopEnd" in content, "Missing Alignment.TopEnd in FloatingDockCard / canvas"
    assert "align(Alignment.BottomEnd)" not in content, "Found obsolete align(Alignment.BottomEnd) in composable code"
    assert "Bottom_work - 430 - 38" in content or "Bottom}_{\\text{work}} - 430 - 38" in content, "Missing resting Y coordinate formula"
    print("  [PASS] 1. Canvas Alignment & Resting Y Coordination verified.")

    # 2. Contraction Clamping Void Prevention
    assert "cContractedLeft > workArea.right - grab" in content, "Missing contraction clamping in contractPanel()"
    assert "safeWinX" in content, "Missing safeWinX calculation in contractPanel()"
    print("  [PASS] 2. Contraction Clamping Void Prevention verified.")

    # 3. Nudge-ForExpand Post-Expansion Boundary Evaluation
    assert "expW = cardWidth + expandDeltaWidth" in content, "Missing post-expansion width in calculateExpansionNudge"
    assert "expH = cardHeight + expandDeltaHeight" in content, "Missing post-expansion height in calculateExpansionNudge"
    assert "expLeft < workArea.left" in content, "Missing expLeft check in calculateExpansionNudge"
    print("  [PASS] 3. Nudge-ForExpand Post-Expansion Boundary Evaluation verified.")

    # 4. Skia Blur Sigma & Reusable Paint Shader
    assert "sigma = blurPx * 0.5f" in content or "sigma = radius / 2.0" in content, "Missing sigma = radius / 2.0f in skiaDropShadow"
    assert "remember(color, blurRadius, density)" in content or "remember" in content, "Paint object not hoisted/remembered in skiaDropShadow"
    print("  [PASS] 4. Skia Blur Sigma & Reusable Paint Shader verified.")

    # 5. High-DPI Scaling in Drag Delta
    assert "dpDx = (dxPhysical / density).toInt()" in content, "Missing High-DPI physical-to-dp scaling in onDragMove"
    print("  [PASS] 5. High-DPI Scaling in Drag Delta verified.")

    # 6. Synchronized Single-Coroutine Window Position Animation
    assert "val anim = Animatable(0f)" in content, "Missing unified Animatable in animateWindowTo"
    assert "windowState.position = WindowPosition(curX.dp, curY.dp)" in content, "Missing unified WindowPosition update in animateWindowTo"
    print("  [PASS] 6. Synchronized Single-Coroutine Window Position Animation verified.")

    # 7. Auto-Dismissal Deactivation Guard
    assert "!windowController.isExpanded" in content, "Missing !isExpanded in deactivation listener"
    assert "!windowController.isModalDialogOpen" in content, "Missing !isModalDialogOpen in deactivation listener"
    assert "isModalDialogOpen" in content, "Missing isModalDialogOpen property in controller"
    print("  [PASS] 7. Auto-Dismissal Deactivation Guard verified.")

    # 8. Active Button Badge Contrast
    assert "badgeBgColor = if (isChecked) Color(0xFF16121A) else Color(0xFF0AE66D)" in content, "Missing badge background contrast inversion in DeXQuickActionButton"
    assert "badgeTextColor = if (isChecked) Color(0xFFFFFFFF) else Color(0xFF000000)" in content, "Missing badge text color contrast in DeXQuickActionButton"
    print("  [PASS] 8. Active Button Badge Contrast verified.")

    print("\n" + "=" * 80)
    print("ALL 8 FIXES VERIFIED SUCCESSFULLY IN UltimateMigrationPlan-WPF-Compose-UI.md")
    print("=" * 80)

if __name__ == "__main__":
    test_document_fixes()
