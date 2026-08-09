
$script:wpfWindow.FindName("btnCopyIP").Add_Click({
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
                    Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/unpair?fingerprint=$($script:activeOutboundPairFp)" -Method Post -ErrorAction SilentlyContinue
                }
            } catch {}

            if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop() }
            
            $pb = $script:wpfWindow.FindName("pbPinTimeout")
            if ($pb) { 
                $pb.BeginAnimation([System.Windows.Controls.Primitives.RangeBase]::ValueProperty, $null)
                $pb.Value = 100 
            }

            $localIp = [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) | Where-Object { $_.AddressFamily -eq 'InterNetwork' -and -not [System.Net.IPAddress]::IsLoopback($_) } | Select-Object -First 1 -ExpandProperty IPAddressToString
            if ($localIp) {
                $imgQrCode = $script:wpfWindow.FindName("imgQrCode")
                if ($imgQrCode) {
                    $bitmap = New-Object System.Windows.Media.Imaging.BitmapImage
                    $bitmap.BeginInit()
                    $bitmap.UriSource = New-Object Uri("http://127.0.0.1:53318/local/qr?ip=$localIp")
                    $bitmap.CacheOption = [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
                    $bitmap.EndInit()
                    $imgQrCode.Source = $bitmap
                }
                $pinCodeContent.Visibility = 'Collapsed'
                $qrCodeContent.Visibility = 'Visible'
                $txtQrBtnIcon.Visibility = 'Collapsed'
                $txtQrBtnText.Text = "Request PIN"
            } else {
                Show-Toast -Title "Network Error" -Message "Could not determine local IP address."
            }
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
        try { Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/dnd?enabled=$stateStr" -Method Post } catch {}
        
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
function Update-ProfileUI {
    $profile = $null
    try {
        $profile = Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/settings/google-profile" -TimeoutSec 5
    } catch {}

    if ($profile -and $profile.email) {
        $txtName = $script:wpfWindow.FindName("txtProfileName")
        $txtEmail = $script:wpfWindow.FindName("txtProfileEmail")
        $avatar = $script:wpfWindow.FindName("imgProfileAvatar")
        $btnSignOut = $script:wpfWindow.FindName("btnSettingsSignOut")
        if ($txtName) { $txtName.Text = if ($profile.name) { $profile.name } else { $profile.email } }
        if ($txtEmail) { $txtEmail.Text = $profile.email }
        if ($btnSignOut) { $btnSignOut.Visibility = 'Visible' }
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
        return $true
    } else {
        $btnSignOut = $script:wpfWindow.FindName("btnSettingsSignOut")
        if ($btnSignOut) { $btnSignOut.Visibility = 'Collapsed' }
        return $false
    }
}

$btnSettingsSignOut = $script:wpfWindow.FindName("btnSettingsSignOut")
if ($btnSettingsSignOut) {
    $btnSettingsSignOut.Add_Click({
        try {
            Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/settings/signout" -Method Post | Out-Null
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
        try {
            Show-Toast -Title "Google Sign-In" -Message "Opening browser — approve the account to trust all your devices."
            Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/settings/google-signin" -TimeoutSec 240 | Out-Null
            Update-ProfileUI
        } catch {
            Show-Toast -Title "Google Sign-In" -Message "Could not reach the local engine."
        }
    })
}

# Populate the profile placeholder with the last signed-in Google account.
# Retry: the local engine may still be starting when the bindings load.
for ($i = 0; $i -lt 6; $i++) {
    if (Update-ProfileUI) { break }
    Start-Sleep -Seconds 2
}

# Reset Identity & Trust button in settings
$btnSettingsResetIdentity = $script:wpfWindow.FindName("btnSettingsResetIdentity")
if ($btnSettingsResetIdentity) {
    $btnSettingsResetIdentity.Add_Click({
        Remove-Item "$env:LOCALAPPDATA\DeX\identity.json" -Force -ErrorAction SilentlyContinue
        [System.Windows.MessageBox]::Show("Trust identity reset. DeX will now restart.", "DeX", 'OK', 'Information') | Out-Null
        $script:notifyIcon.Visible = $false
        $script:notifyIcon.Dispose()
        [System.Windows.Forms.Application]::Exit()
    })
}
if ($btnTopProfile) { $btnTopProfile.Add_Click({ Invoke-MenuAction $actionSettings }) }
if ($btnProfileBottom) { $btnProfileBottom.Add_Click({ Invoke-MenuAction $actionSettings }) }
if ($btnProfileTopSettings) { $btnProfileTopSettings.Add_Click({ Invoke-MenuAction $actionSettings }) }
