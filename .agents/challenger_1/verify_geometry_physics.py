import math
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def run_tests():

    print("=" * 80)
    print("STARTING EMPIRICAL GEOMETRY & PHYSICS VERIFICATION SUITE")
    print("=" * 80)

    canvas_w = 1420
    canvas_h = 760
    margin = 25
    contracted_w = 300
    contracted_h = 430
    expanded_w_fe = 1054
    expanded_h_fe = 625  # or 695
    expand_dw_fe = 754
    expand_dh_fe = 195

    # ---------------------------------------------------------
    # TEST SUITE 1: RESTING COORDINATES & TASKBAR ORIENTATIONS
    # ---------------------------------------------------------
    print("\n--- TEST SUITE 1: Resting Coordinates & Taskbars ---")
    
    topologies = [
        {"name": "Single 1080p Bottom Taskbar", "bounds": (0, 0, 1920, 1080), "insets": (0, 0, 0, 48)},
        {"name": "Single 1080p Top Taskbar", "bounds": (0, 0, 1920, 1080), "insets": (0, 48, 0, 0)},
        {"name": "Single 1080p Left Taskbar", "bounds": (0, 0, 1920, 1080), "insets": (72, 0, 0, 0)},
        {"name": "Single 1080p Right Taskbar", "bounds": (0, 0, 1920, 1080), "insets": (0, 0, 72, 0)},
        {"name": "Secondary Left Monitor (-1920, 0)", "bounds": (-1920, 0, 1920, 1080), "insets": (0, 0, 0, 48)},
        {"name": "Secondary Above Monitor (0, -1080)", "bounds": (0, -1080, 1920, 1080), "insets": (0, 0, 0, 48)},
        {"name": "Secondary Right Monitor (1920, 0)", "bounds": (1920, 0, 1920, 1080), "insets": (0, 0, 0, 48)},
        {"name": "Small Laptop (1366x768)", "bounds": (0, 0, 1366, 768), "insets": (0, 0, 0, 40)},
        {"name": "Scaled Display (1280x720 effective)", "bounds": (0, 0, 1280, 720), "insets": (0, 0, 0, 40)},
    ]

    for topo in topologies:
        bx, by, bw, bh = topo["bounds"]
        ileft, itop, iright, ibottom = topo["insets"]
        wa_left = bx + ileft
        wa_top = by + itop
        wa_right = bx + bw - iright
        wa_bottom = by + bh - ibottom

        # Formula in plan:
        win_x = wa_right - 1420 + 12
        win_y = wa_bottom - 430 - 38

        # Case A: TopEnd Alignment (WPF faithful)
        card_screen_left_topend = win_x + canvas_w - margin - contracted_w
        card_screen_right_topend = win_x + canvas_w - margin
        card_screen_top_topend = win_y + margin
        card_screen_bottom_topend = card_screen_top_topend + contracted_h

        gap_right_topend = wa_right - card_screen_right_topend
        gap_bottom_topend = wa_bottom - card_screen_bottom_topend

        # Case B: BottomEnd Alignment (as written in Section 7.2 FloatingDockCard.kt)
        card_screen_left_botend = win_x + canvas_w - margin - contracted_w
        card_screen_right_botend = win_x + canvas_w - margin
        card_screen_bottom_botend = win_y + canvas_h - margin
        card_screen_top_botend = card_screen_bottom_botend - contracted_h

        gap_right_botend = wa_right - card_screen_right_botend
        gap_bottom_botend = wa_bottom - card_screen_bottom_botend

        print(f"Topology: {topo['name']}")
        print(f"  WorkArea: L={wa_left}, T={wa_top}, R={wa_right}, B={wa_bottom}")
        print(f"  Window Origin: X={win_x}, Y={win_y}")
        print(f"  [TopEnd Alignment] Card Screen Rect: ({card_screen_left_topend}, {card_screen_top_topend}) -> ({card_screen_right_topend}, {card_screen_bottom_topend})")
        print(f"                     Right Gap: {gap_right_topend}px, Bottom Gap: {gap_bottom_topend}px")
        print(f"  [BottomEnd Alignment] Card Screen Rect: ({card_screen_left_botend}, {card_screen_top_botend}) -> ({card_screen_right_botend}, {card_screen_bottom_botend})")
        print(f"                       Right Gap: {gap_right_botend}px, Bottom Gap: {gap_bottom_botend}px (NEGATIVE MEANS OFF-SCREEN!)")
        
        assert gap_right_topend == 13, f"Expected 13px right gap, got {gap_right_topend}"
        assert gap_bottom_topend == 13, f"Expected 13px bottom gap, got {gap_bottom_topend}"
        if gap_bottom_botend < 0:
            print(f"  ❌ CRITICAL ANOMALY: BottomEnd alignment pushes card {abs(gap_bottom_botend)}px below taskbar / screen!")

    # ---------------------------------------------------------
    # TEST SUITE 2: NUDGE-FOREXPAND ALGORITHM
    # ---------------------------------------------------------
    print("\n--- TEST SUITE 2: Nudge-ForExpand Stress Testing ---")

    def calculate_expansion_nudge_plan(current_x, current_y, card_w, card_h, delta_w, delta_h, wa):
        content_left = current_x + canvas_w - margin - card_w
        content_right = current_x + canvas_w - margin
        content_top = current_y + margin
        content_bottom = content_top + card_h

        space_left = content_left - wa['left']
        space_right = wa['right'] - content_right
        space_up = content_top - wa['top']
        space_down = wa['bottom'] - content_bottom

        can_expand_left = (space_left >= space_right) or (space_left >= delta_w + 20)
        can_expand_down = (space_down >= space_up) or (space_down >= delta_h + 20)

        target_x = current_x
        target_y = current_y

        if not can_expand_left:
            target_x += delta_w
        if not can_expand_down:
            target_y -= delta_h

        # Plan clamping
        clamped_left = target_x + canvas_w - margin - card_w
        clamped_right = target_x + canvas_w - margin
        clamped_top = target_y + margin
        clamped_bottom = clamped_top + card_h

        if clamped_left < wa['left']:
            target_x += (wa['left'] - clamped_left)
        if clamped_right > wa['right']:
            target_x -= (clamped_right - wa['right'])
        if clamped_top < wa['top']:
            target_y += (wa['top'] - clamped_top)
        if clamped_bottom > wa['bottom']:
            target_y -= (clamped_bottom - wa['bottom'])

        return target_x, target_y

    def calculate_expansion_nudge_corrected(current_x, current_y, card_w, card_h, delta_w, delta_h, wa):
        content_left = current_x + canvas_w - margin - card_w
        content_right = current_x + canvas_w - margin
        content_top = current_y + margin
        content_bottom = content_top + card_h

        space_left = content_left - wa['left']
        space_right = wa['right'] - content_right
        space_up = content_top - wa['top']
        space_down = wa['bottom'] - content_bottom

        can_expand_left = (space_left >= space_right) or (space_left >= delta_w + 20)
        can_expand_down = (space_down >= space_up) or (space_down >= delta_h + 20)

        target_x = current_x
        target_y = current_y

        if not can_expand_left:
            target_x += delta_w
        if not can_expand_down:
            target_y -= delta_h

        # Corrected clamping using POST-EXPANSION dimensions
        exp_w = card_w + delta_w
        exp_h = card_h + delta_h

        # Left edge of expanded content inside canvas is (canvas_w - margin - exp_w)
        exp_content_left = target_x + canvas_w - margin - exp_w
        exp_content_right = target_x + canvas_w - margin
        exp_content_top = target_y + margin
        exp_content_bottom = exp_content_top + exp_h

        if exp_content_left < wa['left']:
            target_x += (wa['left'] - exp_content_left)
        if exp_content_right > wa['right']:
            target_x -= (exp_content_right - wa['right'])
        if exp_content_top < wa['top']:
            target_y += (wa['top'] - exp_content_top)
        if exp_content_bottom > wa['bottom']:
            target_y -= (exp_content_bottom - wa['bottom'])

        return target_x, target_y

    wa_std = {'left': 0, 'top': 0, 'right': 1920, 'bottom': 1032}

    # Scenario A: Resting position expand
    rest_x = 1920 - 1420 + 12
    rest_y = 1032 - 430 - 38
    plan_tx, plan_ty = calculate_expansion_nudge_plan(rest_x, rest_y, 300, 430, 754, 195, wa_std)
    corr_tx, corr_ty = calculate_expansion_nudge_corrected(rest_x, rest_y, 300, 430, 754, 195, wa_std)
    print(f"Scenario A (Resting Position Expand):")
    print(f"  Start: ({rest_x}, {rest_y})")
    print(f"  Plan Nudge: ({plan_tx}, {plan_ty}) [delta_y = {plan_ty - rest_y}]")
    print(f"  Corr Nudge: ({corr_tx}, {corr_ty}) [delta_y = {corr_ty - rest_y}]")

    # Scenario B: Dragged near Left Screen Edge (x=20)
    # content_left = x + 1420 - 25 - 300 = x + 1095 = 20 => x = -1075
    left_x = -1075
    left_y = 200
    plan_tx, plan_ty = calculate_expansion_nudge_plan(left_x, left_y, 300, 430, 754, 195, wa_std)
    corr_tx, corr_ty = calculate_expansion_nudge_corrected(left_x, left_y, 300, 430, 754, 195, wa_std)
    
    # Calculate screen position of expanded content for both
    # Expanded content left = target_x + 1420 - 25 - 1054 = target_x + 341
    # Expanded content right = target_x + 1420 - 25 = target_x + 1395
    plan_exp_left = plan_tx + 341
    plan_exp_right = plan_tx + 1395
    corr_exp_left = corr_tx + 341
    corr_exp_right = corr_tx + 1395

    print(f"\nScenario B (Card near Left Screen Edge x=20):")
    print(f"  Start Window X: {left_x} (Card Left = 20)")
    print(f"  Plan Target X: {plan_tx} -> Expanded Content Left = {plan_exp_left}, Right = {plan_exp_right}")
    print(f"  Corr Target X: {corr_tx} -> Expanded Content Left = {corr_exp_left}, Right = {corr_exp_right}")

    # Scenario C: Tight Monitor (1024x768)
    wa_tight = {'left': 0, 'top': 0, 'right': 1024, 'bottom': 728}
    # Expanded width 1054 + 25 + 13 = 1092 > 1024!
    tight_x = 1024 - 1420 + 12
    tight_y = 728 - 430 - 38
    plan_tx, plan_ty = calculate_expansion_nudge_plan(tight_x, tight_y, 300, 430, 754, 195, wa_tight)
    corr_tx, corr_ty = calculate_expansion_nudge_corrected(tight_x, tight_y, 300, 430, 754, 195, wa_tight)
    print(f"\nScenario C (Tight Screen 1024x768, Card 1054 > Screen 1024):")
    print(f"  Plan Target X: {plan_tx} -> Expanded Left = {plan_tx + 341}, Right = {plan_tx + 1395}")
    print(f"  Corr Target X: {corr_tx} -> Expanded Left = {corr_tx + 341}, Right = {corr_tx + 1395}")

    # ---------------------------------------------------------
    # TEST SUITE 3: DRAG, MAGNETISM, AND CONTRACTION CLAMPING
    # ---------------------------------------------------------
    print("\n--- TEST SUITE 3: Drag Snapping & Contraction Clamping ---")
    
    # Simulate user dragging while expanded (1054w) near right edge, then contracting (300w)
    # Right edge clamp: grab = max(1054*0.2, 60) = 210
    # clampedLeft = wa.right - grab = 1920 - 210 = 1710
    # winX = clampedLeft - canvasWidth + cardMargin + currentCardW
    # winX = 1710 - 1420 + 25 + 1054 = 1369
    win_x_dragged_expanded = 1369
    
    # While expanded, card right edge in screen:
    expanded_screen_right = win_x_dragged_expanded + canvas_w - margin
    expanded_screen_left = expanded_screen_right - 1054
    print(f"Expanded card at right edge clamp:")
    print(f"  Window X: {win_x_dragged_expanded}")
    print(f"  Expanded Screen Rect: Left = {expanded_screen_left}, Right = {expanded_screen_right}")
    print(f"  Visible portion inside screen [0, 1920]: {min(1920, expanded_screen_right) - max(0, expanded_screen_left)}px (Grab = 210px)")

    # Now contract: width shrinks from 1054 to 300 without moving window
    contracted_screen_right = win_x_dragged_expanded + canvas_w - margin
    contracted_screen_left = contracted_screen_right - 300
    print(f"\nAfter contracting to 300px without window repositioning:")
    print(f"  Contracted Screen Rect: Left = {contracted_screen_left}, Right = {contracted_screen_right}")
    if contracted_screen_left >= 1920:
        print(f"  ❌ FATAL DEFECT CONFIRMED: Contracted card is COMPLETELY OFF-SCREEN ({contracted_screen_left} >= 1920)!")
        print(f"     Distance off-screen: {contracted_screen_left - 1920}px past the right monitor border!")

    # ---------------------------------------------------------
    # TEST SUITE 4: SKIA BLUR SHADER MATH
    # ---------------------------------------------------------
    print("\n--- TEST SUITE 4: Skia Gaussian Blur & Shadow Math ---")
    
    blur_radius_dp = 24.0
    sigma_plan = blur_radius_dp # Plan passed blurRadius directly as sigma
    sigma_skia_correct = blur_radius_dp / 2.0 # Skia standard sigma = radius / 2
    
    # Gaussian 3-sigma spread
    spread_plan = 3.0 * sigma_plan
    spread_correct = 3.0 * sigma_skia_correct
    
    print(f"Blur Radius: {blur_radius_dp} dp")
    print(f"  Plan Skia Sigma passed: {sigma_plan} -> 3-sigma shadow spread = {spread_plan} dp")
    print(f"  Correct Skia Sigma: {sigma_skia_correct} -> 3-sigma shadow spread = {spread_correct} dp")
    print(f"  Window Margin M: {margin} dp")
    print(f"  Plan Shadow + Offset Y (8dp) = {spread_plan + 8} dp (EXCEEDS {margin}dp margin by {spread_plan + 8 - margin}dp -> HARD CLIPPED!)")
    print(f"  Correct Shadow + Offset Y (8dp) = {spread_correct + 8} dp (EXCEEDS {margin}dp margin by {spread_correct + 8 - margin}dp -> HARD CLIPPED!)")
    print(f"  Conclusion: Canvas internal padding/margin MUST be increased to at least 48dp, or shadow spread adjusted, to avoid rectangular clipping of blur shadows at the window canvas boundaries.")

    print("\n" + "=" * 80)
    print("ALL EMPIRICAL TESTS EXECUTED.")
    print("=" * 80)

if __name__ == "__main__":
    run_tests()
