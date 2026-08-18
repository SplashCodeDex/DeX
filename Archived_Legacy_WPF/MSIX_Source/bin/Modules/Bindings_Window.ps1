# Win32 interop for premium window drag. GetCursorPos tracks physical cursor
# position; GetDpiForWindow returns the correct DPI for whatever monitor the
# window is on mid-drag (critical on mixed-DPI multi-monitor setups).
if (-not $script:dragWin32Added) {
    Add-Type -Namespace DeXWin32 -Name DragMove -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT lpPoint);
[DllImport("user32.dll")] public static extern uint GetDpiForWindow(IntPtr hwnd);
[StructLayout(LayoutKind.Sequential)] public struct POINT { public int X; public int Y; }
'@
    $script:dragWin32Added = $true
}

# Cache the window handle once — reused every frame during drag for DPI query.
$script:dragHwnd = [System.Windows.Interop.WindowInteropHelper]::new($script:wpfWindow).Handle

# Cache the DPI once at startup as a fallback for pre-1607 systems that lack
# GetDpiForWindow. This gets overwritten per-frame during actual drags.
try {
    $dpi = [System.Windows.Media.VisualTreeHelper]::GetDpi($script:wpfWindow)
    $script:dpiScaleX = $dpi.DpiScaleX
    $script:dpiScaleY = $dpi.DpiScaleY
} catch {
    $script:dpiScaleX = 1.0
    $script:dpiScaleY = 1.0
}

$btnExit = $script:wpfWindow.FindName("btnExit")
if ($btnExit) {
    $btnExit.Add_Click({
    $txtExitBtn = $script:wpfWindow.FindName("txtExitBtn")
    $btnProfileBottom = $script:wpfWindow.FindName("btnProfileBottom")
    $isShift = [System.Windows.Input.Keyboard]::Modifiers -match 'Shift'
    $hasActivePull = ($null -ne $script:activePulls -and $script:activePulls.Count -gt 0)
    $hasActiveMirror = ($null -ne $script:mirrorProc -and -not $script:mirrorProc.HasExited)
    
    if ($isShift) {
        # Proceed to exit immediately
    } elseif ($txtExitBtn.Text -eq "Exit Engine") {
        # Return/show the UI showing active transfer progress if a transfer or mirror is active
        if ($hasActivePull -or $hasActiveMirror) {
            if (-not $script:wpfWindow.IsVisible) { $script:wpfWindow.Show() }
            $script:wpfWindow.Activate()
            $fileExplorer = $script:wpfWindow.FindName("FileExplorer")
            if ($null -ne $fileExplorer) { $fileExplorer.Visibility = 'Visible' }
            if ($hasActivePull) {
                Refresh-PullDock
                $dock = $script:wpfWindow.FindName("dockPullProgress")
                if ($null -ne $dock) {
                    $dock.Visibility = 'Visible'
                    $dock.Opacity = 1.0
                }
            }
        }

        $btnExit = $script:wpfWindow.FindName("btnExit")
        $parentGrid = $btnExit.Parent
        $parentGrid.Width = $parentGrid.ActualWidth # Prevent layout popping
        
        $txtExitBtn.Text = if ($hasActivePull -or $hasActiveMirror) { "Transfer Active! Click to Force Exit" } else { "Cancel / Shift+Click Exit" }

        
        $ease = New-Object System.Windows.Media.Animation.CubicEase; $ease.EasingMode = 'EaseOut'
        
        if ($btnProfileBottom.Visibility.ToString() -eq 'Visible') {
            $animExpand = New-Object System.Windows.Media.Animation.ThicknessAnimation
            $animExpand.To = New-Object System.Windows.Thickness(-62, 0, 0, 0)
            $animExpand.Duration = [TimeSpan]::FromSeconds(0.3)
            $animExpand.EasingFunction = $ease
            $btnExit.BeginAnimation([System.Windows.FrameworkElement]::MarginProperty, $animExpand)
            
            $btnProfileBottom.RenderTransformOrigin = New-Object System.Windows.Point(0.5, 0.5)
            $scale = New-Object System.Windows.Media.ScaleTransform
            $btnProfileBottom.RenderTransform = $scale
            $animScale = New-Object System.Windows.Media.Animation.DoubleAnimation
            $animScale.To = 0.6
            $animScale.Duration = [TimeSpan]::FromSeconds(0.3)
            $animScale.EasingFunction = $ease
            $scale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleXProperty, $animScale)
            $scale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleYProperty, $animScale)
        }
        
        $btnExit.Background = $script:wpfWindow.FindResource("AccentBrush")
        
        $script:exitTimer = New-Object System.Windows.Threading.DispatcherTimer
        $script:exitTimer.Interval = [TimeSpan]::FromSeconds(3)
        $script:exitTimer.Add_Tick({
            $tTxt = $script:wpfWindow.FindName("txtExitBtn")
            $tBtn = $script:wpfWindow.FindName("btnExit")
            $tAvatar = $script:wpfWindow.FindName("btnProfileBottom")

            $easeOut = New-Object System.Windows.Media.Animation.CubicEase; $easeOut.EasingMode = 'EaseOut'

            # Fade text out → swap → fade in, 0.15s each side.
            $fadeOut = New-Object System.Windows.Media.Animation.DoubleAnimation
            $fadeOut.To = 0; $fadeOut.Duration = [TimeSpan]::FromSeconds(0.15); $fadeOut.EasingFunction = $easeOut
            $fadeOut.Add_Completed({
                $tTxt.Text = "Exit Engine"
                $fadeIn = New-Object System.Windows.Media.Animation.DoubleAnimation
                $fadeIn.To = 1; $fadeIn.Duration = [TimeSpan]::FromSeconds(0.15); $fadeIn.EasingFunction = $easeOut
                $tTxt.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $fadeIn)
            }.GetNewClosure())
            $tTxt.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $fadeOut)

            # Contract the exit button margin.
            $animContract = New-Object System.Windows.Media.Animation.ThicknessAnimation
            $animContract.To = New-Object System.Windows.Thickness(0)
            $animContract.Duration = [TimeSpan]::FromSeconds(0.3)
            $animContract.EasingFunction = $easeOut
            $tBtn.BeginAnimation([System.Windows.FrameworkElement]::MarginProperty, $animContract)

            # Avatar scales back AFTER the text transition (0.15+0.15 = 0.3s delay).
            if ($null -ne $tAvatar.RenderTransform -and $tAvatar.RenderTransform -is [System.Windows.Media.ScaleTransform]) {
                $animScaleBack = New-Object System.Windows.Media.Animation.DoubleAnimation
                $animScaleBack.To = 1.0
                $animScaleBack.Duration = [TimeSpan]::FromSeconds(0.3)
                $animScaleBack.BeginTime = [TimeSpan]::FromSeconds(0.35)
                $animScaleBack.EasingFunction = $easeOut
                $tAvatar.RenderTransform.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleXProperty, $animScaleBack)
                $tAvatar.RenderTransform.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleYProperty, $animScaleBack)
            }

            $tBtn.ClearValue([System.Windows.Controls.Control]::BackgroundProperty)
            $tBtn.Parent.Width = [Double]::NaN

            $script:exitTimer.Stop()
        })
        $script:exitTimer.Start()
        return
    } else {
        # Cancel the exit state
        $btnExit = $script:wpfWindow.FindName("btnExit")

        $easeOut = New-Object System.Windows.Media.Animation.CubicEase; $easeOut.EasingMode = 'EaseOut'

        # Fade text out → swap → fade in.
        $fadeOut = New-Object System.Windows.Media.Animation.DoubleAnimation
        $fadeOut.To = 0; $fadeOut.Duration = [TimeSpan]::FromSeconds(0.15); $fadeOut.EasingFunction = $easeOut
        $fadeOut.Add_Completed({
            $txtExitBtn.Text = "Exit Engine"
            $fadeIn = New-Object System.Windows.Media.Animation.DoubleAnimation
            $fadeIn.To = 1; $fadeIn.Duration = [TimeSpan]::FromSeconds(0.15); $fadeIn.EasingFunction = $easeOut
            $txtExitBtn.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $fadeIn)
        }.GetNewClosure())
        $txtExitBtn.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $fadeOut)

        # Contract margin.
        $animContract = New-Object System.Windows.Media.Animation.ThicknessAnimation
        $animContract.To = New-Object System.Windows.Thickness(0)
        $animContract.Duration = [TimeSpan]::FromSeconds(0.3)
        $animContract.EasingFunction = $easeOut
        $btnExit.BeginAnimation([System.Windows.FrameworkElement]::MarginProperty, $animContract)

        # Avatar scales back after text transition.
        if ($null -ne $btnProfileBottom.RenderTransform -and $btnProfileBottom.RenderTransform -is [System.Windows.Media.ScaleTransform]) {
            $animScaleBack = New-Object System.Windows.Media.Animation.DoubleAnimation
            $animScaleBack.To = 1.0
            $animScaleBack.Duration = [TimeSpan]::FromSeconds(0.3)
            $animScaleBack.BeginTime = [TimeSpan]::FromSeconds(0.35)
            $animScaleBack.EasingFunction = $easeOut
            $btnProfileBottom.RenderTransform.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleXProperty, $animScaleBack)
            $btnProfileBottom.RenderTransform.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleYProperty, $animScaleBack)
        }

        $btnExit.ClearValue([System.Windows.Controls.Control]::BackgroundProperty)
        $btnExit.Parent.Width = [Double]::NaN

        if ($null -ne $script:exitTimer) { $script:exitTimer.Stop() }
        return
    }
    
    if ($null -ne $script:exitTimer) { $script:exitTimer.Stop() }
    Invoke-ExitEngine
    })
}

$script:wpfWindow.Add_KeyDown({
    param($sender, $e)
    # Don't intercept keys when typing in the search bar or any text box
    $isInputFocused = ($null -ne $script:txtSearch) -and (
        $script:txtSearch.IsKeyboardFocused -or 
        $script:txtSearch.IsKeyboardFocusWithin -or 
        $script:txtSearch.IsFocused -or 
        ($null -ne $e.OriginalSource -and $e.OriginalSource.GetType().FullName -match "TextBox")
    )
    if ($isInputFocused) {
        if ($e.Key -eq [System.Windows.Input.Key]::Escape) {
            if ($script:txtSearch.Text -and $script:txtSearch.Text -ne "Search transfers...") {
                $script:txtSearch.Text = ""
            } else {
                [System.Windows.Input.Keyboard]::ClearFocus()
            }
            $e.Handled = $true
        }
        return
    }
    if ($e.Key -eq [System.Windows.Input.Key]::Escape) {
        $settingsPanel = (dxEl "SettingsPanel")
        $fileExplorer = (dxEl "FileExplorer")
        $pinPanel = $script:wpfWindow.FindName("pinViewPanel")
        
        # While the QR/PIN request screen is shown, Escape cancels the pairing (same as the
        # Cancel button) instead of being a swallowed no-op — otherwise the expanded pairing
        # card can never be dismissed from the keyboard.
        if ($pinPanel -and $pinPanel.Visibility -eq [System.Windows.Visibility]::Visible) {
            $btnPinCancel = $script:wpfWindow.FindName("btnPinCancel")
            if ($btnPinCancel) {
                $btnPinCancel.RaiseEvent((New-Object System.Windows.RoutedEventArgs([System.Windows.Controls.Primitives.ButtonBase]::ClickEvent)))
            }
            $e.Handled = $true
            return
        }
        
        # If settings is visible, contract it instead of hiding the whole window
        if ($settingsPanel.Visibility -eq 'Visible') {
            $sb = $script:wpfWindow.Resources["ContractSettings"].Clone()
            $sb.Children[2].By = $null
            $sb.Children[2].To = if ($script:contractedWidth) { $script:contractedWidth } else { 300 }
            Start-CardTransition $sb
            Restore-ExpandPosition
            $e.Handled = $true
            return
        }

        # Edge Case: Reset all expanded panels before hiding
        $script:wpfWindow.Hide()
        $script:lastDeactivated = [DateTime]::Now
        Reset-SpatialPanels
        $e.Handled = $true
    } elseif (($e.Key -eq [System.Windows.Input.Key]::Up -and ($e.KeyboardDevice.Modifiers -band [System.Windows.Input.ModifierKeys]::Alt)) -or ($e.Key -eq [System.Windows.Input.Key]::Back)) {
        # Edge Case 25: Alt + Up Arrow / Backspace navigates Up Directory. Fires in both
        # File Explorer (SAF) and local history modes; the click handler itself no-ops at
        # roots (Phone Folders, drive root), so raising it unconditionally is safe.
        if ((dxEl "FileExplorer").Visibility -eq 'Visible' -and $null -ne $script:btnUpDir -and -not [string]::IsNullOrEmpty($script:currentDirPath)) {
            $script:btnUpDir.RaiseEvent((New-Object System.Windows.RoutedEventArgs([System.Windows.Controls.Primitives.ButtonBase]::ClickEvent)))
            $e.Handled = $true
        }
    } elseif ($e.Key -eq [System.Windows.Input.Key]::C) {
        Invoke-MenuAction $actionConnect
        $e.Handled = $true
    } elseif ($e.Key -eq [System.Windows.Input.Key]::D) {
        Invoke-MenuAction $actionDisconnect
        $e.Handled = $true
    } elseif ($e.Key -eq [System.Windows.Input.Key]::M) {
        Invoke-MenuAction $actionMirror
        $e.Handled = $true
    } elseif ($e.Key -eq [System.Windows.Input.Key]::P) {
        Invoke-MenuAction $actionPull
        $e.Handled = $true
    } elseif ($e.Key -eq [System.Windows.Input.Key]::Q) {
        Invoke-ExitEngine
        $e.Handled = $true
    }
})


$script:lastDeactivated = [DateTime]::MinValue


$script:dragPill = $script:wpfWindow.FindName("dragPill")
$script:btnToggleTopmost = $script:wpfWindow.FindName("btnToggleTopmost")

if ($script:dragPill) {
    $script:dragPill.Add_MouseLeftButtonDown({
        if ($_.ClickCount -eq 2) {
            if ($script:hasBeenDragged) {
                if ($script:isLocationPinned) {
                    $anim = New-Object System.Windows.Media.Animation.ThicknessAnimation
                    $anim.To = "5,0,-5,0"
                    $anim.Duration = [TimeSpan]::FromSeconds(0.05)
                    $anim.AutoReverse = $true
                    $anim.RepeatBehavior = New-Object System.Windows.Media.Animation.RepeatBehavior(3)
                    if ($script:btnToggleTopmost) { $script:btnToggleTopmost.BeginAnimation([System.Windows.FrameworkElement]::MarginProperty, $anim) }
                } else {
                    $mb = (dxEl "mainBorder")
                    $contentW = if ($mb -and $mb.ActualWidth  -gt 0) { $mb.ActualWidth  } else { 300 }
                    $contentH = if ($mb -and $mb.ActualHeight -gt 0) { $mb.ActualHeight } else { 430 }
                    $winWidth = if ($script:wpfWindow.Width -gt 0 -and -not [double]::IsNaN($script:wpfWindow.Width)) { $script:wpfWindow.Width } else { 1420 }
                    $workArea = [System.Windows.SystemParameters]::WorkArea
                    # Position content bottom-right with 13px gap from work area edges.
                    # Layout: HorizontalAlignment=Right, VerticalAlignment=Top, Margin=25.
                    #   contentLeft = windowLeft + winWidth - 25 - contentW
                    #   contentTop  = windowTop  + 25
                    $contentLeft = $workArea.Right  - $contentW - 13
                    $contentTop  = $workArea.Bottom - $contentH - 13
                    $left = $contentLeft - $winWidth + 25 + $contentW
                    $top  = $contentTop  - 25
                    if ($left -lt $workArea.Left) { $left = $workArea.Left - 13 }
                    if ($top  -lt $workArea.Top)  { $top  = $workArea.Top  - 13 }

                    $ease = $script:wpfWindow.FindResource("BouncyEase")
                    $animX = New-Object System.Windows.Media.Animation.DoubleAnimation -Property @{ From = $script:wpfWindow.Left; To = $left; Duration = "0:0:0.45"; EasingFunction = $ease; FillBehavior = "Stop" }
                    $animY = New-Object System.Windows.Media.Animation.DoubleAnimation -Property @{ From = $script:wpfWindow.Top; To = $top; Duration = "0:0:0.45"; EasingFunction = $ease; FillBehavior = "Stop" }

                    $script:wpfWindow.Left = $left
                    $script:wpfWindow.Top = $top

                    $script:wpfWindow.BeginAnimation([System.Windows.Window]::LeftProperty, $animX)
                    $script:wpfWindow.BeginAnimation([System.Windows.Window]::TopProperty, $animY)
                    
                    $script:hasBeenDragged = $false
                }
            }
            $_.Handled = $true
        } else {
            $topPanel = (dxEl "TopActionsPanel")
            $dragPillAccent = (dxEl "dragPillAccent")
            if ($dragPillAccent) {
                $anim = New-Object System.Windows.Media.Animation.DoubleAnimation
                $anim.To = 1
                $anim.Duration = [TimeSpan]::FromSeconds(0.1)
                $dragPillAccent.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $anim)
            }
            if ($topPanel) {
                try { $topPanel.FindResource("ShowPinAnim").Begin($script:wpfWindow) } catch {}
            }
            if ($null -eq $script:pinTimer) {
                $script:pinTimer = New-Object System.Windows.Threading.DispatcherTimer
                $script:pinTimer.Interval = [TimeSpan]::FromSeconds(3)
                $script:pinTimer.Add_Tick({
                    if (-not $script:isLocationPinned) {
                        $topPanel = (dxEl "TopActionsPanel")
                        if ($topPanel) {
                            try { $topPanel.FindResource("HidePinAnim").Begin($script:wpfWindow) } catch {}
                        }
                    }
                    $script:pinTimer.Stop()
                })
            }
            if (-not $script:isLocationPinned) {
                $script:pinTimer.Stop()
                $script:pinTimer.Start()
            }
            
            if ($_.ButtonState -eq [System.Windows.Input.MouseButtonState]::Pressed) {
                # Drag pending: wait for the move handler to cross the 5px
                # dead zone before committing. Prevents accidental 1px drags
                # from resetting hasBeenDragged (which gates double-click-to-reset).
                # Clear any saved pre-expand position — dragging overrides it.
                $script:preExpandLeft = $null; $script:preExpandTop = $null

                $pt = New-Object DeXWin32.DragMove+POINT
                [DeXWin32.DragMove]::GetCursorPos([ref]$pt)
                $script:dragStartCursorX = $pt.X
                $script:dragStartCursorY = $pt.Y

                # Capture the content's current screen position.
                $mb = (dxEl "mainBorder")
                $contentW = if ($mb -and $mb.ActualWidth -gt 0) { $mb.ActualWidth } else { 300 }
                $contentH = if ($mb -and $mb.ActualHeight -gt 0) { $mb.ActualHeight } else { 430 }
                $winW = if ($script:wpfWindow.Width -gt 0 -and -not [double]::IsNaN($script:wpfWindow.Width)) { $script:wpfWindow.Width } else { 1420 }
                $script:dragContentLeft   = $script:wpfWindow.Left + $winW - 25 - $contentW
                $script:dragContentTop    = $script:wpfWindow.Top + 25
                $script:dragContentWidth  = $contentW
                $script:dragContentHeight = $contentH

                $script:dragPending = $true
                $script:dragMonitorRect = $null  # force re-query on first move frame
                $script:wpfWindow.CaptureMouse()
            }
        }
    })
}

# Drag-move handler. Three phases:
#  1. Pending:  accumulator < 5px → no-op (dead zone filters click jitter).
#  2. Commit:   accumulator ≥ 5px → first real drag frame. Set hasBeenDragged,
#               fade the pill accent, and lock in the start position.
#  3. Active:   DPI-corrected delta from the commit point, per-monitor work
#               area for magnetism, and WPF property setters for the reposition.
$script:wpfWindow.Add_PreviewMouseMove({
    if (-not $script:dragPending -and -not $script:isDragging) { return }

    $pt = New-Object DeXWin32.DragMove+POINT
    [DeXWin32.DragMove]::GetCursorPos([ref]$pt)
    $dx = ($pt.X - $script:dragStartCursorX)
    $dy = ($pt.Y - $script:dragStartCursorY)

    # --- Phase 1: dead zone (5px Manhattan distance) ---
    if ($script:dragPending -and -not $script:isDragging) {
        if ([Math]::Abs($dx) + [Math]::Abs($dy) -lt 5) { return }

        # Commit: this is now a real drag.
        $script:dragPending = $false
        $script:isDragging  = $true
        $script:hasBeenDragged = $true

        # Re-lock the start cursor position at the commit point so the
        # content doesn't teleport — it was stationary during the dead zone.
        $script:dragStartCursorX = $pt.X
        $script:dragStartCursorY = $pt.Y
        $dx = 0; $dy = 0

        # Fade the pill accent now that a real drag is underway.
        $pill = (dxEl "dragPillAccent")
        if ($pill) {
            $anim = New-Object System.Windows.Media.Animation.DoubleAnimation
            $anim.To = 0
            $anim.Duration = [TimeSpan]::FromSeconds(0.15)
            $pill.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $anim)
        }
        return
    }

    # --- Phase 3: active drag ---
    # Per-frame DPI: GetDpiForWindow returns the correct scale for the monitor
    # the window is currently on, even during cross-monitor drags on mixed-DPI
    # setups (laptop @150% + external @100%).
    $dpi = [DeXWin32.DragMove]::GetDpiForWindow($script:dragHwnd)
    if ($dpi -gt 0) {
        $scale = $dpi / 96.0
    } else {
        # Fallback for pre-Anniversary Update systems (unlikely on Win11)
        $scale = $script:dpiScaleX
    }
    $newLeft = $script:dragContentLeft + ($dx / $scale)
    $newTop  = $script:dragContentTop  + ($dy / $scale)
    $cw = $script:dragContentWidth
    $ch = $script:dragContentHeight

    # Per-monitor work area for magnetism. Screen.FromPoint allocates and
    # queries GDI on every call — only re-query when the cursor crosses a
    # monitor boundary. For same-monitor frames this is a pure rect check.
    if ($script:dragMonitorRect -and
        $pt.X -ge $script:dragMonitorRect.Left -and $pt.X -lt $script:dragMonitorRect.Right -and
        $pt.Y -ge $script:dragMonitorRect.Top  -and $pt.Y -lt $script:dragMonitorRect.Bottom) {
        $wa = $script:dragMonitorWorkArea
    } else {
        $cursorPt = New-Object System.Drawing.Point($pt.X, $pt.Y)
        try {
            $screen = [System.Windows.Forms.Screen]::FromPoint($cursorPt)
            $script:dragMonitorRect = $screen.Bounds
            $script:dragMonitorWorkArea = $screen.WorkingArea
            $wa = $script:dragMonitorWorkArea
        } catch {
            $wa = [System.Windows.SystemParameters]::WorkArea
        }
    }
    $snap = 20

    if ($newTop -gt $wa.Top -and $newTop - $wa.Top -lt $snap) {
        $newTop = $wa.Top
    }
    if ($newTop + $ch -lt $wa.Bottom -and $wa.Bottom - ($newTop + $ch) -lt $snap) {
        $newTop = $wa.Bottom - $ch
    }
    if ($newLeft -gt $wa.Left -and $newLeft - $wa.Left -lt $snap) {
        $newLeft = $wa.Left
    }
    if ($newLeft + $cw -lt $wa.Right -and $wa.Right - ($newLeft + $cw) -lt $snap) {
        $newLeft = $wa.Right - $cw
    }

    # Record which edges are magnetically engaged for the snap animation on release.
    $script:dragSnappedTop    = ($newTop -eq $wa.Top)
    $script:dragSnappedBottom = ($newTop + $ch -eq $wa.Bottom)
    $script:dragSnappedLeft   = ($newLeft -eq $wa.Left)
    $script:dragSnappedRight  = ($newLeft + $cw -eq $wa.Right)
    $script:dragSnappedWa = $wa

    # Convert content position → window position.
    $winW = if ($script:wpfWindow.Width -gt 0 -and -not [double]::IsNaN($script:wpfWindow.Width)) { $script:wpfWindow.Width } else { 1420 }
    $script:wpfWindow.Left = $newLeft - $winW + 25 + $cw
    $script:wpfWindow.Top  = $newTop  - 25
})

# Drag-up handler. Three exit paths:
#  1. Was only pending (never crossed dead zone) → just clean up, no-op.
#  2. Was dragging but not magnetically snapped  → sanity clamp + pill fade.
#  3. Was dragging and magnetically snapped      → animate to the snapped edge
#     over 120ms with an ease-out, then clamp + fade.
$script:wpfWindow.Add_PreviewMouseLeftButtonUp({
    if (-not $script:dragPending -and -not $script:isDragging) { return }

    $wasDragging = $script:isDragging
    $script:dragPending = $false
    $script:isDragging  = $false
    try { $script:wpfWindow.ReleaseMouseCapture() } catch {}

    if (-not $wasDragging) {
        # Never crossed the dead zone — just restore the pill and exit.
        $pill = (dxEl "dragPillAccent")
        if ($pill) {
            $anim = New-Object System.Windows.Media.Animation.DoubleAnimation
            $anim.To = 0
            $anim.Duration = [TimeSpan]::FromSeconds(0.15)
            $pill.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $anim)
        }
        return
    }

    # Check for snap animation: if any edge was magnetically engaged during
    # the last move frame, animate to the ideal snapped position with a short
    # ease-out so the edge-dock feels intentional rather than glitchy.
    if ($script:dragSnappedTop -or $script:dragSnappedBottom -or
        $script:dragSnappedLeft -or $script:dragSnappedRight) {

        $wa = $script:dragSnappedWa
        $cw = $script:dragContentWidth
        $ch = $script:dragContentHeight
        $winW = if ($script:wpfWindow.Width -gt 0 -and -not [double]::IsNaN($script:wpfWindow.Width)) { $script:wpfWindow.Width } else { 1420 }

        # Compute the ideal content position for the snapped edges.
        $snapLeft = if ($script:dragSnappedLeft)   { $wa.Left               } else { $script:wpfWindow.Left + $winW - 25 - $cw }
        $snapTop  = if ($script:dragSnappedTop)    { $wa.Top                } else { $script:wpfWindow.Top  + 25 }

        # Handle bottom/right snaps (they set a specific edge, not top-left).
        if ($script:dragSnappedRight)  { $snapLeft = $wa.Right  - $cw }
        if ($script:dragSnappedBottom) { $snapTop  = $wa.Bottom - $ch }

        $snapWinLeft = $snapLeft - $winW + 25 + $cw
        $snapWinTop  = $snapTop  - 25

        $ease = New-Object System.Windows.Media.Animation.CubicEase
        $ease.EasingMode = 'EaseOut'
        $animX = New-Object System.Windows.Media.Animation.DoubleAnimation -Property @{
            From = $script:wpfWindow.Left; To = $snapWinLeft
            Duration = [TimeSpan]::FromSeconds(0.12); EasingFunction = $ease
            FillBehavior = 'Stop'
        }
        $animY = New-Object System.Windows.Media.Animation.DoubleAnimation -Property @{
            From = $script:wpfWindow.Top; To = $snapWinTop
            Duration = [TimeSpan]::FromSeconds(0.12); EasingFunction = $ease
            FillBehavior = 'Stop'
        }
        $script:wpfWindow.Left = $snapWinLeft
        $script:wpfWindow.Top  = $snapWinTop
        $script:wpfWindow.BeginAnimation([System.Windows.Window]::LeftProperty, $animX)
        $script:wpfWindow.BeginAnimation([System.Windows.Window]::TopProperty,  $animY)
    }

    # Clear drag state for next drag.
    $script:dragSnappedTop = $false
    $script:dragSnappedBottom = $false
    $script:dragSnappedLeft = $false
    $script:dragSnappedRight = $false
    $script:dragSnappedWa = $null
    $script:dragMonitorRect = $null
    $script:dragMonitorWorkArea = $null

    # Post-drag sanity: keep at least 20% of content (min 60px) reachable.
    $mb = (dxEl "mainBorder")
    $cw = if ($mb -and $mb.ActualWidth  -gt 0) { $mb.ActualWidth  } else { 300 }
    $ch = if ($mb -and $mb.ActualHeight -gt 0) { $mb.ActualHeight } else { 430 }
    $winW = if ($script:wpfWindow.Width  -gt 0 -and -not [double]::IsNaN($script:wpfWindow.Width))  { $script:wpfWindow.Width  } else { 1420 }
    $wa = [System.Windows.SystemParameters]::WorkArea
    $grab = [Math]::Max($cw * 0.2, 60)

    $cLeft   = $script:wpfWindow.Left + $winW - 25 - $cw
    $cTop    = $script:wpfWindow.Top  + 25
    $cRight  = $cLeft + $cw
    $cBottom = $cTop  + $ch

    if ($cRight  -lt $wa.Left  + $grab) { $script:wpfWindow.Left = $wa.Left  + $grab - $winW + 25 + $cw }
    if ($cLeft   -gt $wa.Right - $grab) { $script:wpfWindow.Left = $wa.Right - $grab - $winW + 25 + $cw }
    if ($cBottom -lt $wa.Top   + $grab) { $script:wpfWindow.Top  = $wa.Top   + $grab - 25 - $ch }
    if ($cTop    -gt $wa.Bottom - $grab) { $script:wpfWindow.Top  = $wa.Bottom - $grab - 25 - $ch }

    # Fade the drag-pill accent back to rest.
    $pill = (dxEl "dragPillAccent")
    if ($pill) {
        $anim = New-Object System.Windows.Media.Animation.DoubleAnimation
        $anim.To = 0
        $anim.Duration = [TimeSpan]::FromSeconds(0.15)
        $pill.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $anim)
    }
})

if ($script:btnToggleTopmost) {
    $script:btnToggleTopmost.Add_Click({
        $script:isLocationPinned = -not $script:isLocationPinned
        
        if ($script:isLocationPinned) {
            $this.ToolTip = "Unpin Location"
            if ($script:pinTimer) { $script:pinTimer.Stop() }
        } else {
            $this.ToolTip = "Pin Location"
            if ($script:pinTimer) {
                $script:pinTimer.Stop()
                $script:pinTimer.Start()
            }
        }
    })
}


# Click-outside closes menu ONLY when contracted (not expanded)
$script:wpfWindow.Add_Deactivated({
    # Guard: suppress Deactivated during show+PopIn animation to prevent double-flash
    if ($script:isShowingMenu) { return }
    Write-Trace "Deactivated fired! IsVisible: $($script:wpfWindow.IsVisible)"
    if ($script:wpfWindow.IsVisible) {
        # If menu is expanded, do NOT close on click-outside (use Close button instead)
        if ((dxEl "FileExplorer").Visibility -eq 'Visible') { return }
        if ((dxEl "SettingsPanel").Visibility -eq 'Visible') { return }
        # Keep the QR/PIN request screen visible on click-outside; only Cancel dismisses it
        $pinPanel = (dxEl "pinViewPanel")
        if ($pinPanel -and $pinPanel.Visibility -eq [System.Windows.Visibility]::Visible) { return }
        # Also keep the window while ANY pairing session is active — an auto-shown inbound
        # pairing (window surfaced from the tray) must not be dismissed before the user
        # reads the PIN, even if the panel's render state is mid-transition.
        if ($script:activeOutboundPairIp -or $script:pairWaitTimer) { return }
        $now = [DateTime]::Now
        Write-Trace "Deactivated - Ms since last: $(($now - $script:lastDeactivated).TotalMilliseconds)"
        if (($now - $script:lastDeactivated).TotalMilliseconds -gt 200) {
            Write-Trace "Deactivated: Hiding window (pinPanel=$($pinPanel.Visibility))"
            try { $script:wpfWindow.FindResource("PopIn").Stop($script:wpfWindow) } catch {}
            $script:wpfWindow.Hide()
            $script:lastDeactivated = $now
        }
    }
})

$script:wpfWindow.Add_Closing({
    param($sender, $e)
    if (-not $script:isExiting) {
        $e.Cancel = $true
        $script:wpfWindow.Hide()
    }
})

# Close button handler (only visible when expanded)
$btnCloseMenu = $script:wpfWindow.FindName("btnCloseMenu")
if ($btnCloseMenu) {
    $btnCloseMenu.Add_Click({
    $settingsPanel = (dxEl "SettingsPanel")
    $fileExplorer = (dxEl "FileExplorer")
    
    # If settings is visible, contract it instead of hiding the whole window
    if ($settingsPanel.Visibility -eq 'Visible') {
        $sb = $script:wpfWindow.Resources["ContractSettings"].Clone()
        $sb.Children[2].By = $null
        $sb.Children[2].To = if ($script:contractedWidth) { $script:contractedWidth } else { 300 }
        Start-CardTransition $sb
        Restore-ExpandPosition
        return
    }

    # If FileExplorer is visible, contract it instead of hiding the whole window (consistent UX)
    if ($fileExplorer.Visibility -eq 'Visible') {
        $sb = $script:wpfWindow.Resources["ContractMenu"].Clone()
        $sb.Children[2].By = $null
        $sb.Children[2].To = if ($script:contractedWidth) { $script:contractedWidth } else { 300 }
        Start-CardTransition $sb
        Restore-ExpandPosition
        $btnQAPull = $script:wpfWindow.FindName("btnQAPull")
        if ($btnQAPull) { $btnQAPull.IsChecked = $false }
        return
    }
    
    # Edge Case: Reset all expanded panels before hiding
    $script:wpfWindow.Hide()
    $script:lastDeactivated = [DateTime]::Now
    Reset-SpatialPanels
    })
}
$script:wpfWindow.Add_PreviewMouseLeftButtonUp({
    param($sender, $e)
    $element = $e.OriginalSource
    while ($element -and $element -isnot [System.Windows.Controls.Primitives.ButtonBase]) {
        $element = [System.Windows.Media.VisualTreeHelper]::GetParent($element)
    }
    
    if ($element -and $element -is [System.Windows.Controls.Primitives.ButtonBase]) {
        # Check if the Button has an IP Tag (Omni-Mesh device)
        if ($element.Tag -and $element.Tag -match '^\d+\.\d+\.\d+\.\d+') {
            $ip = $element.Tag -replace ':.*', ''
            
            # Check if this device is in the Discovered Devices (Guest) list
            $icUdpPeers = $script:wpfWindow.FindName("icUdpPeers")
            $targetPeer = $null
            if ($icUdpPeers -and $icUdpPeers.ItemsSource) {
                $targetPeer = $icUdpPeers.ItemsSource | Where-Object { $_.Ip -eq $ip } | Select-Object -First 1
            }
            $isGuest = ($targetPeer -ne $null)

            if ($isGuest) {
                try {
                    # Re-clicking the same device while its panel is open: no-op (don't
                    # reset a session the user is mid-way through).
                    $pinViewPanel = $script:wpfWindow.FindName("pinViewPanel")
                    if ($script:activeOutboundPairIp -eq $ip -and $pinViewPanel -and $pinViewPanel.Visibility -eq 'Visible') {
                        $e.Handled = $true
                        return
                    }
                    # Switching to a different device (or starting fresh after a stale
                    # session): fully cancel any previous pairing first — otherwise the old
                    # session's in-flight job/timer can resurrect its PIN over the new panel.
                    Stop-PairingSession
                    $script:activeOutboundPairIp = $ip
                    $script:activeOutboundPairFp = $targetPeer.Fingerprint
                    
                    $script:wpfWindow.FindName("txtPinTitle").Text = "Pairing with $($targetPeer.Alias)"
                    $script:wpfWindow.FindName("txtPinSubtitle").Text = "Scan this code with your phone, or tap PIN CODE"
                    
                    $script:wpfWindow.FindName("btnPinAccept").Visibility = 'Collapsed'
                    $script:wpfWindow.FindName("btnPinAcceptOnce").Visibility = 'Collapsed'
                    $script:wpfWindow.FindName("btnSettingsQrCode").Visibility = 'Visible'
                    $script:wpfWindow.FindName("btnPinCancel").Visibility = 'Visible'
                    
                    # Reveal the pairing panel (it starts Collapsed and translated to X=300;
                    # the SlideInPinAnim storyboard then animates it into view).
                    if ($pinViewPanel) {
                        $pinViewPanel.Visibility = 'Visible'
                        $pinViewPanel.Opacity = 1
                    }
                    $pinViewTrans = $script:wpfWindow.FindName("pinViewTrans")
                    if ($pinViewTrans) { $pinViewTrans.X = 0 }
                    
                    # Clear any stale QR bitmap from a previous session before fetching.
                    $imgQr = $script:wpfWindow.FindName("imgQrCode")
                    if ($imgQr) { $imgQr.Source = $null }
                    
                    # Show QR Code initially instead of PIN
                    $null = Show-QrCode
                    
                    $txtTimeout = $script:wpfWindow.FindName("txtPinTimeout")
                    if ($txtTimeout) { $txtTimeout.Text = "" }
                    try { $script:wpfWindow.FindName("menuViewsContainer").FindResource("SlideInPinAnim").Begin($script:wpfWindow) } catch {}
                    
                    # Idle QR phase expiry: if the user never taps "Request PIN", close the
                    # panel after 60s instead of leaving the session dangling forever.
                    Start-QrPhaseTimer
                    
                    $e.Handled = $true
                    return
                } catch {
                    Show-Toast -Title "Pairing Failed" -Message $_.Exception.Message
                    $e.Handled = $true
                    return
                }
            }

            # Record the tapped device as the File Explorer target FIRST: tapping a device
            # opens the panel in History mode, and the Explorer toggle then browses THIS
            # device (online + LAN + trusted only; WAN devices stay History-only).
            $icLivePeers = $script:wpfWindow.FindName("icLivePeers")
            $livePeer = $null
            if ($icLivePeers -and $icLivePeers.ItemsSource) {
                $livePeer = $icLivePeers.ItemsSource | Where-Object { $_['IP'] -eq $ip } | Select-Object -First 1
            }
            $script:selectedDeviceIp = $ip
            $script:currentTarget = $ip
            $script:selectedDeviceFp = if ($livePeer) { $livePeer['Fingerprint'] } else { "" }

            # History always opens for a known peer — it works over the WebSocket even for
            # WAN devices where ADB is unreachable. Only expand if not already visible
            # (actionPull would otherwise contract the panel).
            $fePanel = (dxEl "FileExplorer")
            if ($fePanel -and $fePanel.Visibility -ne 'Visible') {
                Invoke-MenuAction $actionPull
            }


            
            $e.Handled = $true
        }
    }
})
