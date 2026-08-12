

function Get-OrDownloadAdbBinary {
    <#
    .SYNOPSIS
        Resolves or downloads the ADB binary for Developer Tools.
    .DESCRIPTION
        Checks if adb.exe exists locally or on system PATH. If missing, downloads official
        Google platform-tools zip quietly and extracts adb.exe to local app data tools directory.
    #>
    if ($global:AdbExePath -and (Test-Path $global:AdbExePath)) {
        return $global:AdbExePath
    }

    # 1. Check system PATH
    $sysCmd = Get-Command "adb.exe" -ErrorAction SilentlyContinue
    if ($sysCmd) {
        $global:AdbExePath = $sysCmd.Source
        return $global:AdbExePath
    }

    # 2. Check local data directory
    $toolsDir = Join-Path $global:DeXDataRoot "tools"
    $adbLocal = Join-Path $toolsDir "adb.exe"
    if (Test-Path $adbLocal) {
        $global:AdbExePath = $adbLocal
        return $global:AdbExePath
    }

    # 3. Download from Google platform-tools
    try {
        if (-not (Test-Path $toolsDir)) { New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null }
        $zipPath = Join-Path $toolsDir "platform-tools.zip"
        $url = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
        
        [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
        $webClient = New-Object System.Net.WebClient
        $webClient.DownloadFile($url, $zipPath)
        
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $tempExtract = Join-Path $toolsDir "extract_temp"
        if (Test-Path $tempExtract) { Remove-Item -Path $tempExtract -Recurse -Force -ErrorAction SilentlyContinue }
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zipPath, $tempExtract)
        
        $extractedAdbDir = Join-Path $tempExtract "platform-tools"
        if (Test-Path $extractedAdbDir) {
            Get-ChildItem -Path $extractedAdbDir | Copy-Item -Destination $toolsDir -Force
        }
        Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue
        Remove-Item -Path $tempExtract -Recurse -Force -ErrorAction SilentlyContinue

        if (Test-Path $adbLocal) {
            $global:AdbExePath = $adbLocal
            return $global:AdbExePath
        }
    } catch {
        Write-Trace "Failed to download ADB binary: $_"
    }

    # Fallback to local execution directory if present
    $fallback = Join-Path $PSScriptRoot "adb.exe"
    $global:AdbExePath = $fallback
    return $global:AdbExePath
}
Export-ModuleMember -Function Get-OrDownloadAdbBinary

function adb { 
    $exe = Get-OrDownloadAdbBinary
    & $exe @args 
}
Export-ModuleMember -Function adb

function Invoke-AdbConnect {
    [CmdletBinding()]
    param(
        [string]$Target,
        [switch]$ConnectOnly
    )

    $exe = Get-OrDownloadAdbBinary
    if (-not (Test-Path $exe)) {
        return @{ Success = $false; Message = "ADB binary not available." }
    }

    $GatewayIP = $null
    if ([string]::IsNullOrWhiteSpace($Target)) {
        $GatewayIP = (Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue | 
                      Where-Object { $_.NextHop -ne '0.0.0.0' -and $_.NextHop -ne '::' } | 
                      Select-Object -First 1 -ExpandProperty NextHop)
        
        if (-not $GatewayIP) {
            if ($ConnectOnly) {
                $null = adb disconnect 2>&1
                return @{ Success = $false; Message = "No IP provided." }
            }
            
            Add-Type -AssemblyName Microsoft.VisualBasic
            $GatewayIP = [Microsoft.VisualBasic.Interaction]::InputBox("Not on Phone Hotspot. Enter Phone IP manually (e.g. 192.168.1.15):", "Connect ADB")
            if (-not $GatewayIP) {
                $null = adb disconnect 2>&1
                return @{ Success = $false; Message = "No IP provided." }
            }
        }
        
        $TargetPort = 5555
        $Target = "${GatewayIP}:5555"
    } else {
        if ($Target -match '^([0-9\.]+):(\d+)$') {
            $GatewayIP = $Matches[1]
            $TargetPort = [int]$Matches[2]
        } elseif ($Target -match '^([0-9\.]+)$') {
            $GatewayIP = $Target
            $TargetPort = 5555
            $Target = "${Target}:5555"
        } else {
            $GatewayIP = $Target -replace ':.*', ''
            $TargetPort = 5555
        }
    }
    
    # Smart Polling: Check if already connected to prevent UI freezing
    $devices = (adb devices -l 2>&1) | Out-String
    if ($devices -match ([regex]::Escape($target) + "\s+device.*?model:([^\s]+)")) {
        $devName = $Matches[1] -replace '_', ' '
        return @{ Success = $true; Target = $target; IP = $GatewayIP; Name = $devName }
    } elseif ($devices -match ([regex]::Escape($target) + "\s+device")) {
        return @{ Success = $true; Target = $target; IP = $GatewayIP; Name = $target }
    }

    # Fast TCP Ping to prevent 21-second adb freeze on unreachable IP
    if ($GatewayIP -and $TargetPort) {
        try {
            $client = [System.Net.Sockets.TcpClient]::new()
            $task = $client.ConnectAsync($GatewayIP, $TargetPort)
            if (-not $task.Wait(400)) {
                return @{ Success = $false; Message = "Device unreachable (TCP Ping timeout)." }
            }
        } catch {
            return @{ Success = $false; Message = "Device unreachable (TCP Ping failed)." }
        } finally {
            if ($client) {
                $client.Close()
                $client.Dispose()
            }
        }
    }

    # Not connected, try to connect
    $null = adb start-server 2>&1
    $result = adb connect $target 2>&1
    
    $devices = (adb devices -l 2>&1) | Out-String
    
    if ($result -like "*connected to*" -or $devices -match ([regex]::Escape($target) + "\s+device")) {
        $devName = $target
        if ($devices -match ([regex]::Escape($target) + "\s+device.*?model:([^\s]+)")) {
            $devName = $Matches[1] -replace '_', ' '
        }
        return @{ Success = $true; Target = $target; IP = $GatewayIP; Name = $devName }
    } else {
        # Clear ghost target if unreachable
        $null = adb disconnect $target 2>&1
        return @{ Success = $false; Message = "Could not reach ADB daemon on $target" }
    }
}
Export-ModuleMember -Function Invoke-AdbConnect


function Start-MdnsDiscovery {
    param(
        [Parameter(Mandatory=$true)]
        [System.Collections.Concurrent.ConcurrentQueue[object]]$Queue
    )
    # Deprecated: mDNS & UDP discovery is handled natively by C# DiscoveryBackgroundService.
    # Returns an empty handle for backwards compatibility.
    return $null
}
Export-ModuleMember -Function Start-MdnsDiscovery

function Invoke-AdbPair {
    param(
        [Parameter(Mandatory=$true)][string]$Target,
        [Parameter(Mandatory=$true)][string]$Pin
    )
    $exe = Get-OrDownloadAdbBinary
    if (-not (Test-Path $exe)) { return $false }

    $GatewayIP = $null
    $TargetPort = $null
    if ($Target -match '^([0-9\.]+):(\d+)$') {
        $GatewayIP = $Matches[1]
        $TargetPort = [int]$Matches[2]
    }
    
    if ($GatewayIP -and $TargetPort) {
        try {
            $client = [System.Net.Sockets.TcpClient]::new()
            $task = $client.ConnectAsync($GatewayIP, $TargetPort)
            if (-not $task.Wait(500)) {
                Write-Trace "Pairing failed: Device unreachable (TCP Ping timeout)."
                return $false
            }
        } catch {
            Write-Trace "Pairing failed: Device unreachable (TCP Ping failed)."
            return $false
        } finally {
            if ($client) {
                $client.Close()
                $client.Dispose()
            }
        }
    }

    $null = adb start-server 2>&1

    # Bounded adb pair: spawn and kill past 10s
    $result = ""
    try {
        $proc = New-Object System.Diagnostics.Process
        $proc.StartInfo.FileName = $exe
        $proc.StartInfo.Arguments = "pair `"$Target`" `"$Pin`""
        $proc.StartInfo.UseShellExecute = $false
        $proc.StartInfo.RedirectStandardOutput = $true
        $proc.StartInfo.RedirectStandardError = $true
        $proc.StartInfo.CreateNoWindow = $true
        $proc.Start() | Out-Null
        if (-not $proc.WaitForExit(10000)) {
            try { $proc.Kill() } catch {}
            Write-Trace "Pairing failed: adb pair timed out (10s)."
            return $false
        }
        $result = $proc.StandardOutput.ReadToEnd()
        if (-not $result) { $result = $proc.StandardError.ReadToEnd() }
        $proc.Dispose()
    } catch {
        Write-Trace "Pairing failed: $($_.Exception.Message)"
        return $false
    }
    
    if ($result -match 'Successfully paired to') {
        return $true
    } else {
        Write-Trace "Pairing failed: $result"
        return $false
    }
}
Export-ModuleMember -Function Invoke-AdbPair


function Start-UiDataPolling {
    param(
        [Parameter(Mandatory=$true)]
        [System.Collections.Concurrent.ConcurrentQueue[object]]$Queue,
        [Parameter(Mandatory=$true)]
        [string]$LocalApi
    )

    $iss = [management.automation.runspaces.initialsessionstate]::CreateDefault2()
    $ps = [powershell]::Create($iss)

    $script = {
        param($api, $queue)
        try {
            while ($true) {
                # Discovered devices (independent of mDNS — LocalSendServer UDP/gateway fallback)
                try {
                    $devices = Invoke-RestMethod -Uri "$api/local/devices" -TimeoutSec 2 -ErrorAction Stop
                    [void]$queue.Enqueue(@{ Type = 'Devices'; Data = $devices })
                } catch {}

                # Mirror window active state (quick-action toggle sync)
                try {
                    $st = Invoke-RestMethod -Uri "$api/local/mirror-state" -TimeoutSec 1 -ErrorAction Stop
                    [void]$queue.Enqueue(@{ Type = 'Mirror'; Active = [bool]$st.active })
                } catch {}

                # Phone-initiated pairing (surface the pending PIN)
                try {
                    $pp = Invoke-RestMethod -Uri "$api/local/pending-pair" -TimeoutSec 1 -ErrorAction Stop
                    if ($pp -and $pp.pin) {
                        [void]$queue.Enqueue(@{ Type = 'PendingPair'; ip = $pp.ip; fingerprint = $pp.fingerprint; alias = $pp.alias; pin = $pp.pin })
                    }
                } catch {}

                Start-Sleep -Seconds 2
            }
        } catch {
            $queue.Enqueue([pscustomobject]@{ Type = 'Error'; Message = "UI data polling error: $_" })
        }
    }

    [void]$ps.AddScript($script).AddArgument($LocalApi).AddArgument($Queue)
    $asyncResult = $ps.BeginInvoke()

    return [PSCustomObject]@{
        PowerShell = $ps
        AsyncResult = $asyncResult
    }
}
Export-ModuleMember -Function Start-UiDataPolling


