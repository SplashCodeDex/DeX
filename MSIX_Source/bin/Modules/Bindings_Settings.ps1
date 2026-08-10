
$btnCopyIP = $script:wpfWindow.FindName("btnCopyIP")
if ($btnCopyIP) {
    $btnCopyIP.Add_Click({
        if (-not [string]::IsNullOrWhiteSpace($script:currentTarget)) {
            try {
                Set-Clipboard -Value $script:currentTarget -ErrorAction Stop
                Show-Toast -Title "Copied" -Message "IP Address copied to clipboard: $($script:currentTarget)"
                
                $btnCopyIP = $script:wpfWindow.FindName("btnCopyIP")
                if ($null -ne $btnCopyIP) {
                    $tb = $btnCopyIP.Content
                    if ($tb -is [System.Windows.Controls.TextBlock]) {
                        $tb.Text = [char]0x2713
                        $tb.Foreground = $script:wpfWindow.FindResource("SuccessBrush")
                        
                        $timer = New-Object System.Windows.Threading.DispatcherTimer
                        $timer.Interval = [TimeSpan]::FromSeconds(1.5)
                        $timer.Add_Tick({
                            $tb.Text = [char]0xE8C8
                            $tb.SetResourceReference([System.Windows.Controls.TextBlock]::ForegroundProperty, "SecondaryTextBrush")
                            $timer.Stop()
                        })
                        $timer.Start()
                    }
                }
            } catch {
                Show-Toast -Title "Clipboard Error" -Message "Could not copy IP. Your clipboard is locked by another app."
            }
        }
    })
}

# Settings Panel Button Handlers
# Auto-Connect toggle in settings
$btnSettingsAutoConnect = $script:wpfWindow.FindName("btnSettingsAutoConnect")
if ($btnSettingsAutoConnect) {
    $btnSettingsAutoConnect.Add_Click({
        Invoke-MenuAction $actionAuto
        # Update badge after toggle
        $txtBadge = $script:wpfWindow.FindName("txtBadgeAutoConnect")
        $badge = $script:wpfWindow.FindName("badgeAutoConnect")
        if ($txtBadge -and $badge) {
            $isEnabled = Get-AutoConnectStatus
            $txtBadge.Text = if ($isEnabled) { "ON" } else { "OFF" }
            if ($isEnabled) {
                $badge.Background = $script:wpfWindow.FindResource("SecondaryBrush")
                $txtBadge.Foreground = $script:wpfWindow.FindResource("SecondaryForegroundBrush")
            } else {
                $badge.Background = $script:wpfWindow.FindResource("DangerBrush")
                $txtBadge.Foreground = [System.Windows.Media.Brushes]::White
            }
        }
    })
}

# Connect Now button in settings
$btnSettingsConnectNow = $script:wpfWindow.FindName("btnSettingsConnectNow")
if ($btnSettingsConnectNow) {
    $btnSettingsConnectNow.Add_Click({
        Invoke-MenuAction $actionConnect
    })
}

# QR Code button in settings
$btnSettingsQrCode = $script:wpfWindow.FindName("btnSettingsQrCode")
if ($btnSettingsQrCode) {
    $btnSettingsQrCode.Add_Click({
        $pinCodeContent = $script:wpfWindow.FindName("pinCodeContent")
        $qrCodeContent = $script:wpfWindow.FindName("qrCodeContent")
        $txtQrBtnIcon = $script:wpfWindow.FindName("txtQrBtnIcon")
        $txtQrBtnText = $script:wpfWindow.FindName("txtQrBtnText")

        if ($qrCodeContent.Visibility -eq 'Visible') {
            # User clicked "Request PIN"
            $fp = $script:activeOutboundPairFp
            if (-not $fp) {
                Show-Toast -Title "No Device Selected" -Message "Select a device from the nearby list first."
                return
            }
            Start-PinPairing -Fingerprint $fp
        } else {
            # User clicked "QR CODE" to go back, cancel pending pairing if any
            try {
                if ($script:activeOutboundPairFp) {
                    Invoke-RestMethod -Uri "$global:DeXLocalApi/local/unpair?fingerprint=$($script:activeOutboundPairFp)" -Method Post -ErrorAction SilentlyContinue
                }
            } catch {}

            if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop() }
            $script:activeOutboundPairIp = $null
            $script:activeOutboundPairFp = $null
            $txtTimeout = $script:wpfWindow.FindName("txtPinTimeout")
            if ($txtTimeout) { $txtTimeout.Text = "" }
            if (-not (Show-QrCode)) {
                Show-Toast -Title "Network Error" -Message "Could not determine local IP address."
            }
            # Slide the QR view back in over the PIN view (mirror of the Request PIN switch).
            try { $script:wpfWindow.FindName("menuViewsContainer").FindResource("SwitchPinToQrAnim").Begin($script:wpfWindow) } catch {}
        }
    })
}

# DND toggle in settings
$script:isDndEnabled = $false
$btnSettingsDnd = $script:wpfWindow.FindName("btnSettingsDnd")
if ($btnSettingsDnd) {
    $btnSettingsDnd.Add_Click({
        $script:isDndEnabled = -not $script:isDndEnabled
        $stateStr = if ($script:isDndEnabled) { "true" } else { "false" }
        try { Invoke-RestMethod -Uri "$global:DeXLocalApi/local/dnd?enabled=$stateStr" -Method Post } catch {}
        
        $txtBadge = $script:wpfWindow.FindName("txtBadgeDnd")
        $badge = $script:wpfWindow.FindName("badgeDnd")
        if ($txtBadge -and $badge) {
            $txtBadge.Text = if ($script:isDndEnabled) { "ON" } else { "OFF" }
            if ($script:isDndEnabled) {
                $badge.Background = $script:wpfWindow.FindResource("DangerBrush")
                $txtBadge.Foreground = [System.Windows.Media.Brushes]::White
            } else {
                $badge.Background = $script:wpfWindow.FindResource("AccentBrush")
                $txtBadge.Foreground = $script:wpfWindow.FindResource("SecondaryTextBrush")
            }
        }
    })
}

# Theme toggle in settings
$btnSettingsTheme = $script:wpfWindow.FindName("btnSettingsTheme")
if ($btnSettingsTheme) {
    $btnSettingsTheme.Add_Click({
        $global:AppThemeMode = "Manual"
        if ($global:CurrentTheme -eq "DarkTheme") {
            Set-AppTheme "LightTheme"
        } else {
            Set-AppTheme "DarkTheme"
        }
    })
}

# Wiggle Toggle button in settings
$btnSettingsWiggleToggle = $script:wpfWindow.FindName("btnSettingsWiggleToggle")
if ($btnSettingsWiggleToggle) {
    $btnSettingsWiggleToggle.Add_Click({
        $script:wiggleEnabled = -not $script:wiggleEnabled
        $txtSettingsWiggleToggle = $script:wpfWindow.FindName("txtSettingsWiggleToggle")
        if ($txtSettingsWiggleToggle) {
            $txtSettingsWiggleToggle.Text = if ($script:wiggleEnabled) { "Enabled" } else { "Disabled" }
        }
    })
}

# Download Path button in settings
$btnSettingsDownloadPath = $script:wpfWindow.FindName("btnSettingsDownloadPath")
if ($btnSettingsDownloadPath) {
    $btnSettingsDownloadPath.Add_Click({
        Add-Type -AssemblyName System.Windows.Forms
        $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
        $dialog.Description = "Select Download Destination Directory"
        if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            $script:customDownloadPath = $dialog.SelectedPath
            $txtDlPath = $script:wpfWindow.FindName("txtSettingsDownloadPath")
            if ($txtDlPath) {
                $txtDlPath.Text = $script:customDownloadPath
            }
            Show-Toast -Title "Download Location" -Message "Files will be saved to: $($script:customDownloadPath)"
        }
    })
}

# About button in settings
$btnSettingsAbout = $script:wpfWindow.FindName("btnSettingsAbout")
if ($btnSettingsAbout) {
    $btnSettingsAbout.Add_Click({
        Start-Process "https://github.com/SplashCodeDex/DeX"
        $script:wpfWindow.Hide()
    })
}

# Google Sign-In button in settings (PC-side OAuth loopback flow)
# Applies a fetched profile to the settings UI. UI thread only.
function Apply-GoogleProfile($profile) {
    $txtSub = $script:wpfWindow.FindName("txtSettingsSubtitle")
    $txtGoogle = $script:wpfWindow.FindName("txtSettingsGoogleState")
    if ($profile -and $profile.email) {
        $txtName = $script:wpfWindow.FindName("txtProfileName")
        $txtEmail = $script:wpfWindow.FindName("txtProfileEmail")
        $avatar = $script:wpfWindow.FindName("imgProfileAvatar")
        $btnSignOut = $script:wpfWindow.FindName("btnSettingsSignOut")
        if ($txtName) { $txtName.Text = if ($profile.name) { $profile.name } else { $profile.email } }
        if ($txtEmail) { $txtEmail.Text = $profile.email }
        if ($btnSignOut) { $btnSignOut.Visibility = 'Visible' }
        if ($txtSub) { $txtSub.Text = if ($profile.name) { $profile.name } else { $profile.email } }
        if ($txtGoogle) { $txtGoogle.Text = "Signed in as $($profile.email)" }
        if ($avatar -and $profile.picture) {
            try {
                $bitmap = New-Object System.Windows.Media.Imaging.BitmapImage
                $bitmap.BeginInit()
                $bitmap.UriSource = New-Object Uri($profile.picture)
                $bitmap.CacheOption = [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
                $bitmap.EndInit()
                $avatar.Fill = New-Object System.Windows.Media.ImageBrush($bitmap)
            } catch {}
        }
        $script:profileApplied = $true
    } else {
        $btnSignOut = $script:wpfWindow.FindName("btnSettingsSignOut")
        if ($btnSignOut) { $btnSignOut.Visibility = 'Collapsed' }
        if ($txtSub) { $txtSub.Text = "DeX" }
        if ($txtGoogle) { $txtGoogle.Text = "Trust all devices signed in with your email" }
    }
}

# Non-blocking profile refresh: fetch in a background job, apply on the next
# retry-timer tick (UI thread). The old synchronous REST call (5s timeout)
# could freeze the whole window — including the spatial menu.
function Update-ProfileUI {
    try {
        $null = Start-Job -Name "ProfileFetch" -ScriptBlock {
            param($uri)
            try {
                $p = Invoke-RestMethod -Uri $uri -TimeoutSec 5 -ErrorAction Stop
                [pscustomobject]@{ Email = $p.email; Name = $p.name; Picture = $p.picture }
            } catch { $null }
        } -ArgumentList "$global:DeXLocalApi/local/settings/google-profile"
    } catch {
        # Never let a job-spawn failure break this module's load — the avatar
        # click wiring at the bottom of this file must always attach.
    }
}

$btnSettingsSignOut = $script:wpfWindow.FindName("btnSettingsSignOut")
if ($btnSettingsSignOut) {
    $btnSettingsSignOut.Add_Click({
        try {
            Invoke-RestMethod -Uri "$global:DeXLocalApi/local/settings/signout" -Method Post -TimeoutSec 5 | Out-Null
            Update-ProfileUI
            Show-Toast -Title "Signed Out" -Message "This PC no longer trusts same-email devices automatically."
        } catch {
            Show-Toast -Title "Sign Out" -Message "Could not reach the local engine."
        }
    })
}

$btnSettingsGoogleSignIn = $script:wpfWindow.FindName("btnSettingsGoogleSignIn")
if ($btnSettingsGoogleSignIn) {
    $btnSettingsGoogleSignIn.Add_Click({
        # Non-blocking: the OAuth flow can take minutes (browser approval), so it
        # runs in a background job and a poll timer applies the outcome. The old
        # synchronous call froze the whole window for up to 240s.
        if (Get-Job -Name "GoogleSignIn" -ErrorAction SilentlyContinue) {
            Show-Toast -Title "Google Sign-In" -Message "Sign-in already in progress — check your browser."
            return
        }
        Show-Toast -Title "Google Sign-In" -Message "Opening browser — approve the account to trust all your devices."
        $null = Start-Job -Name "GoogleSignIn" -ScriptBlock {
            param($uri)
            try {
                $html = Invoke-RestMethod -Uri $uri -TimeoutSec 240 -ErrorAction Stop
                # The endpoint always answers 200 with an HTML page; the text tells
                # us whether the user actually approved.
                if ($html -match 'Signed in as') { 'signed-in' }
                elseif ($html -match 'not configured') { 'not-configured' }
                else { 'cancelled' }
            } catch { $null }
        } -ArgumentList "$global:DeXLocalApi/local/settings/google-signin"
    })
}

# Polls the Google Sign-In job and applies the outcome on the UI thread.
$script:googleSignInTimer = New-Object System.Windows.Threading.DispatcherTimer
$script:googleSignInTimer.Interval = [TimeSpan]::FromSeconds(2)
$script:googleSignInTimer.Add_Tick({
    $job = Get-Job -Name "GoogleSignIn" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $job) { return }
    if ($job.State -ne 'Completed') {
        # Clean up failed/stopped jobs so a later click can start a fresh flow
        if ($job.State -eq 'Failed' -or $job.State -eq 'Stopped') {
            Remove-Job $job -Force
            Show-Toast -Title "Google Sign-In" -Message "Could not reach the local engine."
        }
        return
    }
    $status = Receive-Job $job -ErrorAction SilentlyContinue
    Remove-Job $job -Force
    switch ($status) {
        'signed-in' {
            Show-Toast -Title "Google Sign-In" -Message "Signed in — same-email devices are now auto-trusted."
            Update-ProfileUI
        }
        'not-configured' { Show-Toast -Title "Google Sign-In" -Message "Google Sign-In is not configured on this PC." }
        'cancelled'      { Show-Toast -Title "Google Sign-In" -Message "Sign-in failed or was cancelled." }
        default          { Show-Toast -Title "Google Sign-In" -Message "Could not reach the local engine." }
    }
})
$script:googleSignInTimer.Start()

# Populate the profile placeholder with the last signed-in Google account.
# The engine may still be starting when the bindings load, so retry off the UI
# thread — the old blocking loop (6 × (5s timeout + 2s sleep)) could freeze the
# whole window for tens of seconds at startup.
$script:profileApplied = $false
$script:profileRetryCount = 0
$script:profileRetryTimer = New-Object System.Windows.Threading.DispatcherTimer
$script:profileRetryTimer.Interval = [TimeSpan]::FromSeconds(2)
$script:profileRetryTimer.Add_Tick({
    # Reap a finished fetch job (this tick runs on the UI thread, so applying
    # the result here is safe).
    $done = Get-Job -Name "ProfileFetch" -ErrorAction SilentlyContinue | Where-Object { $_.State -eq 'Completed' } | Select-Object -First 1
    if ($done) {
        $profile = Receive-Job $done -ErrorAction SilentlyContinue
        Remove-Job $done -Force
        if ($null -ne $profile) { Apply-GoogleProfile $profile }
    }
    if ($script:profileApplied) {
        $script:profileRetryTimer.Stop()
        return
    }
    $script:profileRetryCount++
    if ($script:profileRetryCount -gt 6) {
        $script:profileRetryTimer.Stop()
        return
    }
    if (-not (Get-Job -Name "ProfileFetch" -ErrorAction SilentlyContinue)) {
        Update-ProfileUI
    }
})
Update-ProfileUI
$script:profileRetryTimer.Start()

# Reset Identity & Trust button in settings
$btnSettingsResetIdentity = $script:wpfWindow.FindName("btnSettingsResetIdentity")
if ($btnSettingsResetIdentity) {
    $btnSettingsResetIdentity.Add_Click({
        Remove-Item "$global:DeXDataRoot\identity.json" -Force -ErrorAction SilentlyContinue
        [System.Windows.MessageBox]::Show("Trust identity reset. DeX will now restart.", "DeX", 'OK', 'Information') | Out-Null
        $script:notifyIcon.Visible = $false
        $script:notifyIcon.Dispose()
        [System.Windows.Forms.Application]::Exit()
    })
}
if ($btnTopProfile) { $btnTopProfile.Add_Click({ Invoke-MenuAction $actionSettings }) }
if ($btnProfileBottom) { $btnProfileBottom.Add_Click({ Invoke-MenuAction $actionSettings }) }
if ($btnProfileTopSettings) { $btnProfileTopSettings.Add_Click({ Invoke-MenuAction $actionSettings }) }
