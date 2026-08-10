# --- PIN Pairing Handlers ---
$btnPinCancel = $script:wpfWindow.FindName("btnPinCancel")
if ($btnPinCancel) {
    $btnPinCancel.Add_Click({
    if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop() }
    
    if ($script:activeOutboundPairIp) {
        try { Invoke-RestMethod -Uri "$global:DeXLocalApi/local/pair-cancel?ip=$($script:activeOutboundPairIp)&fingerprint=$($script:activeOutboundPairFp)" -Method Post -ErrorAction SilentlyContinue } catch {}
    }
    Clear-PairingState
    try { $script:wpfWindow.FindName("menuViewsContainer").FindResource("SlideOutPinAnim").Begin($script:wpfWindow) } catch {}
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
            "menuDisconnect" {
                $ip = $menuItem.Tag
                if ($ip) {
                    $null = adb disconnect "${ip}:5555" 2>&1
                    Show-Toast -Title "ADB Disconnected" -Message "Disconnected $ip."
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




