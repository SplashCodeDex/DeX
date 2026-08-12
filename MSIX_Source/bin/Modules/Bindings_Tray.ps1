
$script:notifyIcon.Add_MouseUp({
    param($sender, $e)
    Write-Trace "MouseUp fired! Button: $($e.Button)"
    if ($e.Button -eq 'Right' -or $e.Button -eq 'Left') {
        $now = [DateTime]::Now
        # Debounce: reject double-fired MouseUp events from a single physical click
        if (($now - $script:lastMouseUpTime).TotalMilliseconds -lt 300) {
            Write-Trace "MouseUp: Debounced (too fast)"
            return
        }
        $script:lastMouseUpTime = $now
        Write-Trace "IsVisible: $($script:wpfWindow.IsVisible) | Ms since lastDeactivated: $(($now - $script:lastDeactivated).TotalMilliseconds)"
        if ($script:wpfWindow.IsVisible -or (($now - $script:lastDeactivated).TotalMilliseconds -lt 400)) {
            # Edge Case: If window was visible with expanded panels, fully reset state on hide
            Write-Trace "MouseUp: Hiding window (debounce or visible)"
            $script:wpfWindow.Hide()
            Reset-SpatialPanels
            $script:lastDeactivated = $now
            return
        }
        
        try {
            Update-WpfUI
        } catch { Write-Trace "Update-WpfUI error: $_" }
        
        # Edge Case 27 & 28: Dynamic work area bounds clipping protection & window activation focus
        # Also reset containers to contracted state so PopIn shows clean window.
        (dxEl "FileExplorer").Visibility = 'Collapsed'
        (dxEl "FileExplorer").Opacity = 0
        (dxEl "fileTrans").X = 150
        (dxEl "SettingsPanel").Visibility = 'Collapsed'
        (dxEl "SettingsPanel").Opacity = 0
        (dxEl "settingsTrans").X = 150
        (dxEl "menuTrans").X = 0
        (dxEl "btnCloseMenu").Visibility = 'Collapsed'
        (dxEl "btnCloseMenu").Opacity = 0
        (dxEl "TopActionsPanel").Visibility = 'Visible'
        (dxEl "btnUserJoe").Visibility = 'Visible'
        (dxEl "btnDeviceWindows").Visibility = 'Visible'
        (dxEl "icLivePeers").Visibility = 'Visible'
        (dxEl "btnUser1").Visibility = 'Visible'
        (dxEl "btnUser2").Visibility = 'Visible'
        (dxEl "btnUser3").Visibility = 'Visible'

        $workArea = [System.Windows.SystemParameters]::WorkArea
        $winWidth = if ($script:wpfWindow.Width -gt 0 -and -not [double]::IsNaN($script:wpfWindow.Width)) { $script:wpfWindow.Width } else { 1420 }
        $contentH = if ((dxEl "mainBorder").ActualHeight -gt 0) { (dxEl "mainBorder").ActualHeight } else { 430 }

        if (-not $script:isLocationPinned) {
            $left = $workArea.Right - $winWidth + 13
            $top = $workArea.Bottom - $contentH - 38

            if ($left -lt $workArea.Left) { $left = $workArea.Left - 13 }
            if ($top -lt $workArea.Top) { $top = $workArea.Top - 13 }

            $script:wpfWindow.Left = $left
            $script:wpfWindow.Top = $top
        }
        $script:wpfWindow.Topmost = $true

        $script:lastDeactivated = [DateTime]::Now

        # Set PopIn initial state before Show so the first frame is already
        # at the storyboard's From values — no flash, no pause/resume needed.
        (dxEl "winScale").ScaleX = 0.85
        (dxEl "winScale").ScaleY = 0.85
        (dxEl "winTrans").Y = 15
        (dxEl "menuTrans").Y = 20
        (dxEl "menuContentTrans").Y = 35
        (dxEl "menuContentPanel").Opacity = 0
        (dxEl "mainBorder").Opacity = 0

        # Guard: suppress Deactivated during show+animate to prevent double-flash race
        if ($script:showMenuGuardTimer) { $script:showMenuGuardTimer.Stop() }
        $script:isShowingMenu = $true

        $script:wpfWindow.Show()
        $script:wpfWindow.Activate()
        $script:wpfWindow.Focus()

        try {
            $sb = $script:wpfWindow.FindResource("PopIn")
            if ($sb) { Start-CardTransition $sb }
        } catch { Write-Trace "PopIn failed: $_" }

        # Clear the guard after PopIn animation completes (~800ms covers the longest 750ms tween)
        $script:showMenuGuardTimer = New-Object System.Windows.Threading.DispatcherTimer
        $script:showMenuGuardTimer.Interval = [TimeSpan]::FromMilliseconds(800)
        $script:showMenuGuardTimer.Add_Tick({
            $script:isShowingMenu = $false
            $script:showMenuGuardTimer.Stop()
        })
        $script:showMenuGuardTimer.Start()
    }
})
$btnDeviceWindows = (dxEl "btnDeviceWindows")
if ($btnDeviceWindows) { $btnDeviceWindows.Add_Click({ $script:isMockMode = $true; Invoke-MenuAction $actionPull }) }
