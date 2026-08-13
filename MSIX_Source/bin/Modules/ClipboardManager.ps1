# ClipboardManager.ps1
# Modular cross-device rich-media clipboard synchronization engine for DeX.

$script:clipWorkerControl = [System.Collections.Concurrent.ConcurrentQueue[object]]::new()
$script:clipWorkerPs = $null
$script:clipWorkerRs = $null
$script:clipWorkerAsync = $null

function Get-DeXClipboardContent {
    Add-Type -AssemblyName System.Windows.Forms, System.Drawing -ErrorAction SilentlyContinue
    if ([System.Windows.Forms.Clipboard]::ContainsImage()) {
        try {
            $img = [System.Windows.Forms.Clipboard]::GetImage()
            if ($img) {
                $ms = New-Object System.IO.MemoryStream
                $img.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
                $bytes = $ms.ToArray()
                $ms.Dispose()
                $img.Dispose()

                $sha = [System.Security.Cryptography.SHA256]::Create()
                $hashBytes = $sha.ComputeHash($bytes)
                $sha.Dispose()
                $hash = "IMG:" + ([System.BitConverter]::ToString($hashBytes)).Replace("-", "")

                return @{
                    Type = "Image"
                    Mime = "image/png"
                    Base64 = [Convert]::ToBase64String($bytes)
                    Hash = $hash
                }
            }
        } catch {}
    }

    $text = Get-Clipboard -Raw -ErrorAction Ignore
    if (-not [string]::IsNullOrWhiteSpace($text)) {
        return @{
            Type = "Text"
            Mime = "text/plain"
            Text = $text
            Hash = $text
        }
    }

    return $null
}

function Set-DeXClipboardContent {
    param(
        [string]$Text,
        [string]$ImageBase64
    )
    Add-Type -AssemblyName System.Windows.Forms, System.Drawing -ErrorAction SilentlyContinue

    if (-not [string]::IsNullOrEmpty($ImageBase64)) {
        try {
            $bytes = [Convert]::FromBase64String($ImageBase64)
            $ms = New-Object System.IO.MemoryStream(,$bytes)
            $img = [System.Drawing.Image]::FromStream($ms)
            [System.Windows.Forms.Clipboard]::SetImage($img)
            $img.Dispose()
            $ms.Dispose()
            return $true
        } catch {}
    }

    if (-not [string]::IsNullOrEmpty($Text)) {
        try {
            Set-Clipboard -Value $Text -ErrorAction Ignore
            return $true
        } catch {}
    }

    return $false
}

function Start-ClipboardSyncWorker {
    if ($null -ne $script:clipWorkerPs) { return }
    try {
        $iss = [management.automation.runspaces.initialsessionstate]::CreateDefault2()
        $rs = [runspacefactory]::CreateRunspace($iss)
        $rs.ApartmentState = [System.Threading.ApartmentState]::STA
        $rs.Open()
        $ps = [powershell]::Create()
        $ps.Runspace = $rs

        $ctl = $script:clipWorkerControl
        $api = $global:DeXLocalApi
        $adbPath = $global:AdbExePath

        [void]$ps.AddScript({
            param($ctlQueue, $apiUrl, $adb)
            $enabled = $false
            $clipLastPushed = ""
            $clipLastReceived = ""

            Add-Type -AssemblyName System.Windows.Forms, System.Drawing -ErrorAction SilentlyContinue

            while ($true) {
                $m = $null
                while ($ctlQueue.TryDequeue([ref]$m)) {
                    if ($m.ContainsKey('SetEnabled')) { $enabled = [bool]$m.SetEnabled }
                    if ($m.ContainsKey('Stop')) { return }
                }
                if ($enabled) {
                    try {
                        # 1. Learn what the phone last pushed, so we don't echo it back to the phone
                        try {
                            $state = Invoke-RestMethod -Uri "$apiUrl/local/clipboard-state" -TimeoutSec 1 -ErrorAction Stop
                            if ($state -and $state.text -and $state.text -ne $clipLastReceived) {
                                $clipLastReceived = [string]$state.text
                            }
                        } catch {}

                        # 2. Detect a fresh local clipboard change (Image or Text)
                        $hasImage = [System.Windows.Forms.Clipboard]::ContainsImage()
                        if ($hasImage) {
                            try {
                                $img = [System.Windows.Forms.Clipboard]::GetImage()
                                if ($img) {
                                    $ms = New-Object System.IO.MemoryStream
                                    $img.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
                                    $imgBytes = $ms.ToArray()
                                    $ms.Dispose()
                                    $img.Dispose()

                                    $imgB64 = [Convert]::ToBase64String($imgBytes)
                                    $sha = [System.Security.Cryptography.SHA256]::Create()
                                    $hashBytes = $sha.ComputeHash($imgBytes)
                                    $sha.Dispose()
                                    $imgHash = "IMG:" + ([System.BitConverter]::ToString($hashBytes)).Replace("-", "")

                                    if ($imgHash -ne $clipLastPushed -and $imgHash -ne $clipLastReceived) {
                                        $ip = $null
                                        try {
                                            $devices = Invoke-RestMethod -Uri "$apiUrl/local/devices" -TimeoutSec 2 -ErrorAction Stop
                                            $target = $devices | Where-Object { $_.isPaired -or $_.isAutoTrusted } | Select-Object -First 1
                                            if ($target) { $ip = $target.ip }
                                        } catch {}

                                        if ($ip) {
                                            $payloadObj = @{
                                                type = "set-clipboard"
                                                data = @{
                                                    type = "image"
                                                    mime = "image/png"
                                                    imageBase64 = $imgB64
                                                }
                                            }
                                            $jsonPayload = $payloadObj | ConvertTo-Json -Depth 3 -Compress
                                            try {
                                                $null = Invoke-RestMethod -Uri "$apiUrl/local/clipboard-push?ip=$ip" -Method Post -Body $jsonPayload -ContentType "application/json" -TimeoutSec 5 -ErrorAction Stop
                                                $clipLastPushed = $imgHash
                                            } catch {}
                                        }
                                    }
                                }
                            } catch {}
                        } else {
                            $text = Get-Clipboard -Raw -ErrorAction Ignore
                            if (-not [string]::IsNullOrWhiteSpace($text) -and
                                $text -ne $clipLastPushed -and
                                $text -ne $clipLastReceived) {

                                $ip = $null
                                try {
                                    $devices = Invoke-RestMethod -Uri "$apiUrl/local/devices" -TimeoutSec 2 -ErrorAction Stop
                                    $target = $devices | Where-Object { $_.isPaired -or $_.isAutoTrusted } | Select-Object -First 1
                                    if ($target) { $ip = $target.ip }
                                } catch {}

                                if ($ip) {
                                    $delivered = $false
                                    try {
                                        $null = Invoke-RestMethod -Uri "$apiUrl/local/clipboard-push?ip=$ip" -Method Post -Body $text -ContentType "text/plain" -TimeoutSec 3 -ErrorAction Stop
                                        $delivered = $true
                                    } catch {}
                                    if (-not $delivered) {
                                        $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
                                        $b64 = [Convert]::ToBase64String($bytes)
                                        $res = & $adb -s "${ip}:5555" shell am broadcast -a com.dexstudios.dex.SET_CLIPBOARD -e text_b64 "$b64" 2>&1
                                        if ($res -match "Broadcast completed") { $delivered = $true }
                                    }
                                    if ($delivered) { $clipLastPushed = $text }
                                }
                            }
                        }
                    } catch {}
                }
                Start-Sleep -Milliseconds 2000
            }
        }).AddArgument($ctl).AddArgument($api).AddArgument($adbPath)

        $script:clipWorkerPs = $ps
        $script:clipWorkerRs = $rs
        $script:clipWorkerAsync = $ps.BeginInvoke()
    } catch {
        Stop-ClipboardSyncWorker
    }
}

function Stop-ClipboardSyncWorker {
    if ($null -ne $script:clipWorkerControl) {
        $script:clipWorkerControl.Enqueue(@{ Stop = $true })
    }
    if ($null -ne $script:clipWorkerPs) {
        try { $script:clipWorkerPs.Dispose() } catch {}
        $script:clipWorkerPs = $null
    }
    if ($null -ne $script:clipWorkerRs) {
        try { $script:clipWorkerRs.Dispose() } catch {}
        $script:clipWorkerRs = $null
    }
    $script:clipWorkerAsync = $null
}
