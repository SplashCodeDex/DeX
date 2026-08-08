# --- PIN Pairing Handlers ---
$script:wpfWindow.FindName("btnPinCancel").Add_Click({
    if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop() }
    
    if ($script:activeOutboundPairIp) {
        try { Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/pair-cancel?ip=$($script:activeOutboundPairIp)&fingerprint=$($script:activeOutboundPairFp)" -Method Post -ErrorAction SilentlyContinue } catch {}
    }
    
    try { $script:wpfWindow.FindName("menuViewsContainer").FindResource("SlideOutPinAnim").Begin($script:wpfWindow) } catch {}
})


$script:wpfWindow.AddHandler([System.Windows.Controls.MenuItem]::ClickEvent, [System.Windows.RoutedEventHandler]{
    param($sender, $e)
    $src = $e.OriginalSource
    if ($src -is [System.Windows.Controls.MenuItem]) {
        $menuItem = $src
        if ($menuItem.Name -eq "menuRename") {
            $fp = $menuItem.Tag
            if ($fp) {
                Add-Type -AssemblyName Microsoft.VisualBasic
                $alias = [Microsoft.VisualBasic.Interaction]::InputBox("Enter new alias for this device:", "Rename Device", "")
                if (![string]::IsNullOrWhiteSpace($alias)) {
                    try { Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/alias?fingerprint=$fp&alias=$alias" -Method Post } catch {}
                    Show-Toast -Title "Device Renamed" -Message "New alias saved."
                }
            }
        }
        elseif ($menuItem.Name -eq "menuForget") {
            $fp = $menuItem.Tag
            if ($fp) {
                try { Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/unpair?fingerprint=$fp" -Method Post } catch {}
                Show-Toast -Title "Device Forgotten" -Message "Device has been unpaired."
            }
        }
        $e.Handled = $true
    }
})




