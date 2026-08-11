# --- PIN Pairing Handlers ---
$btnPinCancel = $script:ce["btnPinCancel"]
if ($btnPinCancel) {
    $btnPinCancel.Add_Click({
    # Centralized cancellation: stops every session timer (pairWaitTimer, pairInitTimer,
    # qrPhaseTimer), removes the in-flight pair-initiate job, cancels the pending pairing
    # server-side, clears the session state, and slides the panel out. Idempotent, so a
    # double-click (or Escape + click) is harmless.
    Stop-PairingSession -SlideOut
    })
}


$script:wpfWindow.AddHandler([System.Windows.Controls.MenuItem]::ClickEvent, [System.Windows.RoutedEventHandler]{
    param($sender, $e)
    $src = $e.OriginalSource
    if ($src -is [System.Windows.Controls.MenuItem]) {
        $menuItem = $src
        switch ($menuItem.Name) {
            "menuRename" {
                $fp = $menuItem.Tag
                if ($fp) {
                    Add-Type -AssemblyName Microsoft.VisualBasic
                    $alias = [Microsoft.VisualBasic.Interaction]::InputBox("Enter new alias for this device:", "Rename Device", "")
                    if (![string]::IsNullOrWhiteSpace($alias)) {
                        try { Invoke-RestMethod -Uri "$global:DeXLocalApi/local/alias?fingerprint=$fp&alias=$alias" -Method Post } catch {}
                        Show-Toast -Title "Device Renamed" -Message "New alias saved."
                    }
                }
            }
            "menuForget" {
                $fp = $menuItem.Tag
                if ($fp) {
                    try { Invoke-RestMethod -Uri "$global:DeXLocalApi/local/unpair?fingerprint=$fp" -Method Post } catch {}
                    Show-Toast -Title "Device Forgotten" -Message "Device has been unpaired."
                }
            }
            "menuGuestForget" {
                $fp = $menuItem.Tag
                if ($fp) {
                    try { Invoke-RestMethod -Uri "$global:DeXLocalApi/local/unpair?fingerprint=$fp" -Method Post } catch {}
                    Show-Toast -Title "Device Forgotten" -Message "Device removed from the trusted list."
                }
            }
            "menuCopyIp" {
                $ip = $menuItem.Tag
                if ($ip) {
                    Set-Clipboard -Value $ip
                    Show-Toast -Title "IP Copied" -Message "$ip copied to clipboard."
                }
            }
            "menuGuestCopyIp" {
                $ip = $menuItem.Tag
                if ($ip) {
                    Set-Clipboard -Value $ip
                    Show-Toast -Title "IP Copied" -Message "$ip copied to clipboard."
                }
            }
            "menuClipboard" {
                $ip = $menuItem.Tag
                if ($ip) { Send-ClipboardToDevice -Ip $ip }
            }
            "menuMirror" {
                $ip = $menuItem.Tag
                if ($ip) { Start-MirrorSession -Ip $ip }
            }
            "menuDisconnectAdb" {
                $ip = $menuItem.Tag
                if ($ip) {
                    $null = adb disconnect "${ip}:5555" 2>&1
                    Show-Toast -Title "ADB Disconnected" -Message "Disconnected $ip."
                    Update-WpfUIAsync
                }
            }
            "menuConnectAdb" {
                $ip = $menuItem.Tag
                if ($ip) {
                    $adbJob = Start-Job -ScriptBlock {
                        param($targetIp, $mod, $adb)
                        $global:AdbExePath = $adb
                        Import-Module $mod -DisableNameChecking
                        Invoke-AdbConnect -Target $targetIp
                    } -ArgumentList $ip, (Join-Path $PSScriptRoot "AdbManager.psm1"), $global:AdbExePath
                    
                    $adbTimer = New-Object System.Windows.Threading.DispatcherTimer
                    $adbTimer.Interval = [TimeSpan]::FromMilliseconds(200)
                    $adbTimer.Add_Tick({
                        param($s, $e)
                        if ($adbJob.State -notin @('Running', 'NotStarted')) {
                            $s.Stop()
                            try {
                                $res = Receive-Job -Job $adbJob -ErrorAction SilentlyContinue
                                if ($res.Success) {
                                    Show-Toast -Title "ADB Connected" -Message "Successfully connected to $($res.Name)"
                                    Update-WpfUIAsync
                                } else {
                                    Show-Toast -Title "Connection Failed" -Message $res.Message
                                }
                            } catch {}
                            Remove-Job -Job $adbJob -Force
                        }
                    }.GetNewClosure())
                    $adbTimer.Start()
                }
            }
            "menuPair" {
                $fp = $menuItem.Tag
                if ($fp) { Start-PinPairing -Fingerprint $fp }
            }
            "menuGuestConnect" {
                $ip = $menuItem.Tag
                if ($ip) {
                    $res = Invoke-AdbConnect -Target $ip
                    if ($res.Success) {
                        Invoke-MenuAction $actionPull
                    } else {
                        Show-Toast -Title "Connection Failed" -Message $res.Message
                    }
                }
            }
        }
        $e.Handled = $true
    }
})




