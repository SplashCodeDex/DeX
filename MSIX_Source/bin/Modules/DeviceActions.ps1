<#
.SYNOPSIS
    DeX - Device Actions
.DESCRIPTION
    Centralizes per-device actions used by the spatial UI context menus:
    clipboard push, screen mirror, and PIN pairing initiation.
#>

function Send-ClipboardToDevice {
    <#
    .SYNOPSIS
        Pushes the PC clipboard to a specific device.
    .DESCRIPTION
        Prefers the WebSocket channel (works without ADB, requires the DeX app on the
        phone to be running). Falls back to the ADB SET_CLIPBOARD broadcast.
    .PARAMETER Ip
        IP address of the target device.
    .PARAMETER Quiet
        Suppresses toasts (used by the automatic sync watcher).
    .RETURNS
        $true when the text was delivered, $false otherwise.
    #>
    param(
        [Parameter(Mandatory)][string]$Ip,
        [switch]$Quiet
    )

    $text = Get-Clipboard -Raw -ErrorAction Ignore
    if ([string]::IsNullOrWhiteSpace($text)) {
        if (-not $Quiet) { Show-Toast -Title "Clipboard Empty" -Message "Nothing to sync." }
        return $false
    }

    # 1. WebSocket path (no ADB): the local server forwards the text to the phone's app
    try {
        $null = Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/clipboard-push?ip=$Ip" -Method Post -Body $text -ContentType "text/plain" -TimeoutSec 3 -ErrorAction Stop
        if (-not $Quiet) { Show-Toast -Title "Clipboard Synced" -Message "Sent to $Ip." }
        return $true
    } catch {
        # Fall through to the ADB broadcast path
    }

    # 2. ADB fallback: SET_CLIPBOARD broadcast via the ADB transport
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
    $b64 = [Convert]::ToBase64String($bytes)

    $res = adb -s "${Ip}:5555" shell am broadcast -a com.dexstudios.dex.SET_CLIPBOARD -e text_b64 "$b64" 2>&1
    if ($res -match "Broadcast completed") {
        if (-not $Quiet) { Show-Toast -Title "Clipboard Synced" -Message "Sent to $Ip." }
        return $true
    }
    if (-not $Quiet) { Show-Toast -Title "Clipboard Sync Failed" -Message "Open the DeX app on the phone (or connect ADB) and try again." }
    return $false
}

function Start-MirrorSession {
    <#
    .SYNOPSIS
        Starts an ADB-free screen-mirror session with a device.
    .DESCRIPTION
        Asks the local server to push a mirror-start over the WebSocket; the phone
        shows the system screen-capture consent and streams JPEG frames back. The
        desktop opens a mirror window. No ADB/scrcpy involved.
    .PARAMETER Ip
        IP address of the target device.
    #>
    param([Parameter(Mandatory)][string]$Ip)

    try {
        $null = Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/mirror?ip=$Ip" -Method Post -TimeoutSec 5 -ErrorAction Stop
        Show-Toast -Title "Mirroring Started" -Message "Phone screen mirror opened."
    } catch {
        Show-Toast -Title "Mirror Failed" -Message "Open the DeX app on the phone and allow screen sharing."
    }
}

function Start-PinPairing {
    <#
    .SYNOPSIS
        Initiates PIN pairing with a device: pushes a pair-prompt, shows the PIN
        panel, and monitors the pairing status until accepted/rejected/timeout.
    .PARAMETER Fingerprint
        Fingerprint of the target device.
    .PARAMETER Ip
        IP of the target device. Resolved from the device list when omitted.
    #>
    param(
        [Parameter(Mandatory)][string]$Fingerprint,
        [string]$Ip = ""
    )

    $alias = ""
    if (-not $Ip) {
        try {
            $devices = Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/devices" -TimeoutSec 2 -ErrorAction Stop
            $target = $devices | Where-Object { $_.info.fingerprint -eq $Fingerprint } | Select-Object -First 1
            if (-not $target) { throw "Device not found in the local device list." }
            $Ip = $target.ip
            $alias = $target.info.alias
        } catch {
            Show-Toast -Title "Device Not Found" -Message "The phone is not currently discoverable."
            return
        }
    }

    $script:activeOutboundPairIp = $Ip
    $script:activeOutboundPairFp = $Fingerprint

    try {
        $initRes = Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/pair-initiate?ip=${Ip}&fingerprint=${Fingerprint}" -Method Post -TimeoutSec 5 -ErrorAction Stop
        $pin = $initRes.pin
        if (-not $pin) {
            Show-Toast -Title "Device Not Connected" -Message "The phone has no active connection. Open the DeX app on the phone, wait a few seconds, then try again."
            return
        }
    } catch {
        Show-Toast -Title "Request Failed" -Message $_.Exception.Message
        return
    }

    if (-not $alias) {
        try {
            $devices = Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/devices" -TimeoutSec 2 -ErrorAction SilentlyContinue
            $t = $devices | Where-Object { $_.info.fingerprint -eq $Fingerprint } | Select-Object -First 1
            if ($t) { $alias = $t.info.alias }
        } catch {}
    }

    $w = $script:wpfWindow
    $w.FindName("txtPinTitle").Text = "Pairing with $alias"
    $w.FindName("txtPinCode").Text = $pin
    $w.FindName("txtPinStatus").Text = "Waiting for remote acceptance..."

    $w.FindName("qrCodeContent").Visibility = 'Collapsed'
    $w.FindName("pinCodeContent").Visibility = 'Visible'
    $w.FindName("btnPinAccept").Visibility = 'Collapsed'
    $w.FindName("btnPinAcceptOnce").Visibility = 'Collapsed'
    $w.FindName("btnSettingsQrCode").Visibility = 'Visible'
    $w.FindName("btnPinCancel").Visibility = 'Visible'
    $w.FindName("txtQrBtnIcon").Visibility = 'Visible'
    $w.FindName("txtQrBtnText").Text = "QR CODE"
    $w.FindName("pinViewPanel").Visibility = 'Visible'
    try { $w.FindName("menuViewsContainer").FindResource("SlideInPinAnim").Begin($w) } catch {}

    $pb = $w.FindName("pbPinTimeout")
    if ($pb) {
        $anim = New-Object System.Windows.Media.Animation.DoubleAnimation
        $anim.From = 100; $anim.To = 0; $anim.Duration = [TimeSpan]::FromSeconds(60)
        $pb.BeginAnimation([System.Windows.Controls.Primitives.RangeBase]::ValueProperty, $anim)
    }

    if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop() }
    $script:pairWaitTimer = New-Object System.Windows.Threading.DispatcherTimer
    $script:pairWaitTimer.Interval = [TimeSpan]::FromMilliseconds(1000)
    $script:pairWaitTimer.Add_Tick({
        try {
            $st = Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/pair-status?ip=$($script:activeOutboundPairIp)" -TimeoutSec 1 -ErrorAction Stop
            if ($st.status -eq 'Accepted' -or $st.status -eq 'Rejected' -or $st.status -eq 'Failed') {
                $script:pairWaitTimer.Stop()
                try { $script:wpfWindow.FindName("menuViewsContainer").FindResource("SlideOutPinAnim").Begin($script:wpfWindow) } catch {}
                $script:activeOutboundPairIp = $null
                $script:activeOutboundPairFp = $null
                if ($st.status -eq 'Accepted') { Show-Toast -Title "Pairing Successful" -Message "Device trusted and added to Your Devices." }
                else { Show-Toast -Title "Pairing Failed" -Message "The remote device rejected or timed out." }
            }
        } catch {}
    }.GetNewClosure())
    $script:pairWaitTimer.Start()
}
