import io
import re

ui_comp = r'W:\CodeDeX\DeX\MSIX_Source\bin\Modules\UIComponents.ps1'
bind_set = r'W:\CodeDeX\DeX\MSIX_Source\bin\Modules\Bindings_Settings.ps1'
bind_win = r'W:\CodeDeX\DeX\MSIX_Source\bin\Modules\Bindings_Window.ps1'

def patch_file(path, replacements):
    with io.open(path, 'r', encoding='utf-8') as f:
        c = f.read()
    for patt, repl in replacements:
        c = re.sub(patt, repl, c)
    with io.open(path, 'w', encoding='utf-8-sig') as f:
        f.write(c)

# Bindings_Settings
patch_file(bind_set, [
    (r'(?s)\s*\$pb = \$script:wpfWindow\.FindName\(\"pbPinTimeout\"\)\s*if \(\$pb\) \{\s*\$pb\.BeginAnimation\(\[System\.Windows\.Controls\.Primitives\.RangeBase\]::ValueProperty, \$null\)\s*\$pb\.Value = 100\s*\}\s*\n', '\n            $txtTimeout = $script:wpfWindow.FindName(\"txtPinTimeout\")\n            if ($txtTimeout) { $txtTimeout.Text = \"\" }\n')
])

# Bindings_Window
patch_file(bind_win, [
    (r'(?s)\s*\# Reset progress bar to full \(100\)\s*\$pb = \$script:wpfWindow\.FindName\(\"pbPinTimeout\"\)\s*if \(\$pb\) \{\s*\$pb\.BeginAnimation\(\[System\.Windows\.Controls\.Primitives\.RangeBase\]::ValueProperty, \$null\)\s*\$pb\.Value = 100\s*\}\s*\n', '\n                    $txtTimeout = $script:wpfWindow.FindName(\"txtPinTimeout\")\n                    if ($txtTimeout) { $txtTimeout.Text = \"\" }\n')
])

new_funcs = """
function Set-PinContentView {
    param([switch]$ShowQr)
    $w = $script:wpfWindow
    if ($ShowQr) {
        $w.FindName("pinCodeContent").Visibility = 'Collapsed'
        $w.FindName("qrCodeContent").Visibility = 'Visible'
    } else {
        $w.FindName("pinCodeContent").Visibility = 'Visible'
        $w.FindName("qrCodeContent").Visibility = 'Collapsed'
    }
}

function Show-PinPanel {
    param(
        [string]$Title,
        [string]$Code,
        [string]$Status,
        [switch]$ShowQrToggle,
        [switch]$HideAcceptButtons,
        [switch]$HidePanelOnTerminal,
        [string]$SuccessMessage,
        [string]$FailureMessage
    )
    $w = $script:wpfWindow
    $w.FindName("txtPinTitle").Text = $Title

    $ic = $w.FindName("icPinDigits")
    if ($ic) {
        $digits = [System.Collections.ArrayList]::new()
        foreach ($c in $Code.ToCharArray()) { $null = $digits.Add($c.ToString()) }
        $ic.ItemsSource = $digits
    }

    $w.FindName("txtPinStatus").Text = $Status
    Set-PinContentView -ShowQr:$false
    if ($HideAcceptButtons) {
        $w.FindName("btnPinAccept").Visibility = 'Collapsed'
        $w.FindName("btnPinAcceptOnce").Visibility = 'Collapsed'
    }
    $w.FindName("btnSettingsQrCode").Visibility = if ($ShowQrToggle) { 'Visible' } else { 'Collapsed' }
    $w.FindName("btnPinCancel").Visibility = 'Visible'
    $w.FindName("txtQrBtnIcon").Visibility = 'Visible'
    $w.FindName("txtQrBtnText").Text = "QR CODE"

    if ($w.FindName("pinViewPanel").Visibility -eq 'Visible') {
        $pinT = $w.FindName("pinContentTrans")
        if ($pinT) {
            $pinT.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, $null)
            $pinT.X = 140
        }
        try { $w.FindName("menuViewsContainer").FindResource("SwitchQrToPinAnim").Begin($w) } catch {}
    } else {
        $w.FindName("pinViewPanel").Visibility = 'Visible'
        $pinT = $w.FindName("pinContentTrans")
        if ($pinT) {
            $pinT.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, $null)
            $pinT.X = 0
        }
        try { $w.FindName("menuViewsContainer").FindResource("SlideInPinAnim").Begin($w) } catch {}
    }

    $txtTimeout = $w.FindName("txtPinTimeout")
    $script:pinTimeoutSeconds = 60
    if ($txtTimeout) { $txtTimeout.Text = "Expires in $($script:pinTimeoutSeconds)s" }

    if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop() }
    $script:pairWaitTimer = New-Object System.Windows.Threading.DispatcherTimer
    $script:pairWaitTimer.Interval = [TimeSpan]::FromMilliseconds(1000)
    $script:pairWaitTimer.Add_Tick({
        $script:pinTimeoutSeconds--
        if ($txtTimeout -and $script:pinTimeoutSeconds -ge 0) {
            $txtTimeout.Text = "Expires in $($script:pinTimeoutSeconds)s"
        }
        try {
            $st = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/pair-status?ip=$($script:activeOutboundPairIp)" -TimeoutSec 1 -ErrorAction Stop
            if ($st.status -eq 'Accepted' -or $st.status -eq 'Rejected' -or $st.status -eq 'Failed') {
                $script:pairWaitTimer.Stop()
                if ($HidePanelOnTerminal) { $script:wpfWindow.FindName("pinViewPanel").Visibility = 'Collapsed' }
                try { $script:wpfWindow.FindName("menuViewsContainer").FindResource("SlideOutPinAnim").Begin($script:wpfWindow) } catch {}
                $script:activeOutboundPairIp = $null
                $script:activeOutboundPairFp = $null
                if ($st.status -eq 'Accepted') { Show-Toast -Title "Pairing Successful" -Message $SuccessMessage }
                else { Show-Toast -Title "Pairing Failed" -Message $FailureMessage }
            }
        } catch {}
    }.GetNewClosure())
    $script:pairWaitTimer.Start()
}

function Clear-PairingState {
    if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop() }
    $script:activeOutboundPairIp = $null
    $script:activeOutboundPairFp = $null
    $txtTimeout = $script:wpfWindow.FindName("txtPinTimeout")
    if ($txtTimeout) { $txtTimeout.Text = "" }
}
"""

patch_file(ui_comp, [
    (r'(?s)function Show-PinPanel \{.*?\n\}', new_funcs)
])
