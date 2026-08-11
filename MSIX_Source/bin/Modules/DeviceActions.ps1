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
        $null = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/clipboard-push?ip=$Ip" -Method Post -Body $text -ContentType "text/plain" -TimeoutSec 3 -ErrorAction Stop
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
        $null = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/mirror?ip=$Ip" -Method Post -TimeoutSec 5 -ErrorAction Stop
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
            $devices = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/devices" -TimeoutSec 2 -ErrorAction Stop
            $target = $devices | Where-Object { $_.info.fingerprint -eq $Fingerprint } | Select-Object -First 1
            if (-not $target) { throw "Device not found in the local device list." }
            $Ip = $target.ip
            $alias = $target.info.alias
        } catch {
            Show-Toast -Title "Device Not Found" -Message "The phone is not currently discoverable."
            return
        }
    }

    # Guard: a pair-initiate is already in flight — a second click would push a duplicate
    # pair-prompt to the phone.
    if ($script:pairInitJob) {
        Show-Toast -Title "Pairing in Progress" -Message "Already requesting a PIN from the device."
        return
    }

    $script:activeOutboundPairIp = $Ip
    $script:activeOutboundPairFp = $Fingerprint
    $script:pairInitSessionIp = $Ip
    $script:pairInitSessionFp = $Fingerprint
    # The idle QR phase's expiry timer is superseded once a PIN request is sent.
    if ($script:qrPhaseTimer) { $script:qrPhaseTimer.Stop(); $script:qrPhaseTimer = $null }

    # PS 5.1: scriptblock delegates cannot run on threadpool threads, so the pair-initiate
    # POST (which waits on the phone's WebSocket and can stall on a half-dead connection)
    # runs in a background Job; a poll timer applies the result on the UI thread.
    $api = $global:DeXLocalApi
    $job = Start-Job -ScriptBlock {
        param($apiBase, $targetIp, $targetFp, $targetAlias)
        $pin = $null
        $alias = $targetAlias
        try {
            $initRes = Invoke-RestMethod -Uri "$apiBase/local/pair-initiate?ip=${targetIp}&fingerprint=${targetFp}" -Method Post -TimeoutSec 5 -ErrorAction Stop
            $pin = $initRes.pin
        } catch {}
        if (-not $alias) {
            try {
                $devices = Invoke-RestMethod -Uri "$apiBase/local/devices" -TimeoutSec 2 -ErrorAction SilentlyContinue
                $t = $devices | Where-Object { $_.info.fingerprint -eq $targetFp } | Select-Object -First 1
                if ($t) { $alias = $t.info.alias }
            } catch {}
        }
        return @{ Pin = $pin; Alias = $alias }
    } -ArgumentList $api, $Ip, $Fingerprint, $alias
    $script:pairInitJob = $job
    # Plain tick (NO GetNewClosure): closures cannot resolve functions from later
    # dot-sourced files (Test-PairingActive/Show-PinPanel failed here), and $script:
    # writes are lost in them. A plain scriptblock runs in the real engine scope.
    $timer = New-Object System.Windows.Threading.DispatcherTimer
    $timer.Interval = [TimeSpan]::FromMilliseconds(150)
    $script:pairInitTimer = $timer
    $timer.Add_Tick({
        $job = $script:pairInitJob
        if (-not $job) {
            $t = $script:pairInitTimer
            if ($t) { $t.Stop() }
            $script:pairInitTimer = $null
            return
        }
        # A freshly spawned job starts in NotStarted — keep polling until it reaches a terminal state.
        if ($job.State -notin @('Completed','Failed','Stopped')) { return }
        $t2 = $script:pairInitTimer
        if ($t2) { $t2.Stop() }
        $script:pairInitTimer = $null
        $script:pairInitJob = $null
        $result = $null
        if ($job.State -eq 'Completed') { $result = Receive-Job $job -ErrorAction SilentlyContinue }
        Remove-Job $job -Force -ErrorAction SilentlyContinue
        # The session moved on (user cancelled, switched device, or a new pairing started):
        # never resurrect this result over the current panel.
        if ($script:activeOutboundPairIp -ne $script:pairInitSessionIp -or $script:activeOutboundPairFp -ne $script:pairInitSessionFp) { return }
        if (-not $result -or -not $result.Pin) {
            Clear-PairingState
            Show-Toast -Title "Device Not Connected" -Message "The phone has no active connection. Open the DeX app on the phone, wait a few seconds, then try again."
            return
        }
        Show-PinPanel -Title "Pairing with $($result.Alias)" -Code $result.Pin -Status "Waiting for the PIN to be entered on the phone..." `
            -ShowQrToggle -HideAcceptButtons `
            -SuccessMessage "Device trusted and added to Your Devices." `
            -FailureMessage "The remote device rejected or timed out."
    })
    $timer.Start()
}
