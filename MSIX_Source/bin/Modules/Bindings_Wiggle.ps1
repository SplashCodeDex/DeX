
# --- Wiggle-to-Open Feature ---
try {
    Add-Type -TypeDefinition @"
    using System;
    using System.Runtime.InteropServices;
    public class Win32Input {
        [DllImport("user32.dll")]
        public static extern short GetAsyncKeyState(int vKey);
        [DllImport("user32.dll")]
        public static extern int GetSystemMetrics(int nIndex);
    }
"@
} catch {}

$script:wiggleHistory = @()
$script:wiggleEnabled = $true
$script:wiggleReversalsThreshold = 3
$script:wiggleGraceTicks = 0
$script:wiggleLastTick = [DateTime]::Now
$script:wiggleTimer = New-Object System.Windows.Threading.DispatcherTimer
# 50ms = 20Hz sampling: the wiggle detector's history window is sized for 50ms
# ticks (20 samples = 1s). 20ms ran at 50Hz on the UI thread forever, which
# fought the spatial menu's PopIn/Expand tweens and made them jank.
$script:wiggleTimer.Interval = [TimeSpan]::FromMilliseconds(50)

$script:wiggleTimer.Add_Tick({
    # Feature off: skip all Win32 polling / history work. When disabled there is no
    # openedViaWiggle window to auto-close, so an early return is safe.
    if (-not $script:wiggleEnabled) { return }

    if ($script:wpfWindow.IsVisible) {
        if ($script:openedViaWiggle) {
            $btn = if ([Win32Input]::GetSystemMetrics(23) -ne 0) { 0x02 } else { 0x01 }
            $isDown = ([Win32Input]::GetAsyncKeyState($btn) -band 0x8000) -ne 0
            if (-not $isDown) {
                $script:openedViaWiggle = $false
                # Run at Background priority so WPF Drop events fire first before we hide
                $script:wpfWindow.Dispatcher.InvokeAsync({
                    if ($script:wpfWindow.IsVisible) {
                        $script:wpfWindow.Hide()
                        Reset-SpatialPanels
                        $script:lastDeactivated = [DateTime]::Now
                    }
                }, [System.Windows.Threading.DispatcherPriority]::Background) | Out-Null
            }
        }
        return
    }
    
    $now = [DateTime]::Now
    if (($now - $script:wiggleLastTick).TotalMilliseconds -gt 150) {
        $script:wiggleHistory = @()
    }
    $script:wiggleLastTick = $now

    $btn = if ([Win32Input]::GetSystemMetrics(23) -ne 0) { 0x02 } else { 0x01 }
    $isDown = ([Win32Input]::GetAsyncKeyState($btn) -band 0x8000) -ne 0
    if (-not $isDown) {
        $script:wiggleGraceTicks++
        if ($script:wiggleGraceTicks -gt 2) {
            $script:wiggleHistory = @()
        }
        return
    }
    $script:wiggleGraceTicks = 0

    $pos = [System.Windows.Forms.Cursor]::Position
    $script:wiggleHistory += $pos.X
    if ($script:wiggleHistory.Count -gt 20) {
        # Keep last 1 second of history (20 * 50ms = 1000ms)
        $script:wiggleHistory = $script:wiggleHistory[-20..-1]
    }

    # Wiggle detection logic: count direction reversals
    if ($script:wiggleHistory.Count -ge 5) {
        $reversals = 0
        $lastDir = 0 # 1 for right, -1 for left
        $minX = $script:wiggleHistory[0]
        $maxX = $script:wiggleHistory[0]
        
        for ($i = 1; $i -lt $script:wiggleHistory.Count; $i++) {
            $prev = $script:wiggleHistory[$i-1]
            $curr = $script:wiggleHistory[$i]
            if ($curr -lt $minX) { $minX = $curr }
            if ($curr -gt $maxX) { $maxX = $curr }
            
            $diff = $curr - $prev
            if ([Math]::Abs($diff) -gt 5) { # Minimum delta to be considered a movement
                $dir = if ($diff -gt 0) { 1 } else { -1 }
                if ($lastDir -ne 0 -and $dir -ne $lastDir) {
                    $reversals++
                }
                $lastDir = $dir
            }
        }
        
        $totalDist = $maxX - $minX
        # A wiggle is threshold or more reversals in a localized area (< 150 pixels)
        if ($script:wiggleEnabled -and $reversals -ge $script:wiggleReversalsThreshold -and $totalDist -lt 150) {
            # Wiggle detected! Reset history
            $script:wiggleHistory = @()
            
            # Show the menu near the cursor
            if (-not $script:wpfWindow.IsVisible) {
                # Set PopIn initial state before Show — matches storyboard From values.
                (dxEl "winScale").ScaleX = 0.85
                (dxEl "winScale").ScaleY = 0.85
                (dxEl "winTrans").Y = 15
                (dxEl "menuTrans").Y = 20
                (dxEl "menuContentTrans").Y = 35
                (dxEl "menuContentPanel").Opacity = 0
                (dxEl "mainBorder").Opacity = 0


                $script:openedViaWiggle = $true

                # Position content (not window) centered around cursor.
                # Layout: HorizontalAlignment=Right, VerticalAlignment=Top, Margin=25.
                $contentW = if ((dxEl "mainBorder").ActualWidth  -gt 0) { (dxEl "mainBorder").ActualWidth  } else { 300 }
                $contentH = if ((dxEl "mainBorder").ActualHeight -gt 0) { (dxEl "mainBorder").ActualHeight } else { 430 }
                $winWidth = if ($script:wpfWindow.Width -gt 0 -and -not [double]::IsNaN($script:wpfWindow.Width)) { $script:wpfWindow.Width } else { 1420 }

                $targetLeft = $pos.X - $winWidth + 25 + ($contentW / 2)
                $targetTop  = $pos.Y - 25 - ($contentH / 2)

                # Clamp so content stays within the correct monitor's work area
                $workArea = [System.Windows.Forms.Screen]::FromPoint($pos).WorkingArea
                $cLeft   = $targetLeft + $winWidth - 25 - $contentW
                $cTop    = $targetTop  + 25
                $cRight  = $targetLeft + $winWidth - 25
                $cBottom = $targetTop  + 25 + $contentH

                if ($cLeft   -lt $workArea.Left)   { $targetLeft = $workArea.Left   - $winWidth + 25 + $contentW }
                if ($cTop    -lt $workArea.Top)    { $targetTop  = $workArea.Top    - 25 }
                if ($cRight  -gt $workArea.Right)  { $targetLeft = $workArea.Right  - $winWidth + 25 }
                if ($cBottom -gt $workArea.Bottom) { $targetTop  = $workArea.Bottom - 25 - $contentH }

                $script:wpfWindow.Left = $targetLeft
                $script:wpfWindow.Top = $targetTop
                $script:wpfWindow.Topmost = $true

                $script:wpfWindow.Show()
                # (Activate removed to prevent dragging focus loss)

                try {
                    $sb = $script:wpfWindow.FindResource("PopIn")
                    if ($sb) { Start-CardTransition $sb }
                } catch {}
            }
        }
    }
})
$script:wiggleTimer.Start()
