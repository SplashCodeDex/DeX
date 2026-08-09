
function adb { & $global:AdbExePath @args }
Export-ModuleMember -Function adb

function Invoke-AdbConnect {
    [CmdletBinding()]
    param(
        [string]$Target,
        [switch]$ConnectOnly
    )

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
            if (-not $task.Wait(500)) {
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
    Write-Trace "Starting Omni-Mesh Discovery Runspace (mDNS + UDP Multicast)..."
    
    $iss = [management.automation.runspaces.initialsessionstate]::CreateDefault2()
    $ps = [powershell]::Create($iss)
    
    $script = {
        param($adbPath, $computerName, $queue)
        
        try {
            $lastMdns = [datetime]::MinValue

            while ($true) {
                $now = Get-Date

                # A. ADB mDNS Services polling (every 15 seconds)
                if ($now - $lastMdns -gt [timespan]::FromSeconds(15)) {
                    $output = & $adbPath mdns services 2>&1
                    if ($null -ne $output) {
                        $lines = $output -split '`r?`n'
                        foreach ($line in $lines) {
                            if ($line -match '_adb-tls-connect\._tcp\.\s+([0-9\.]+:[0-9]+)') {
                                [void]$queue.Enqueue(@{ Type = 'Connect'; IPPort = $matches[1] })
                            }
                            if ($line -match '_adb-tls-pairing\._tcp\.\s+([0-9\.]+:[0-9]+)') {
                                [void]$queue.Enqueue(@{ Type = 'Pairing'; IPPort = $matches[1] })
                            }
                        }
                    }
                    $lastMdns = $now
                }
                
                Start-Sleep -Milliseconds 100
            }
        } catch {
            $queue.Enqueue([pscustomobject]@{ Type = 'Error'; Message = "mDNS polling error: $_" })
        }
    }
    
    [void]$ps.AddScript($script).AddArgument($global:AdbExePath).AddArgument($env:COMPUTERNAME).AddArgument($Queue)
    
    $asyncResult = $ps.BeginInvoke()
    
    return [PSCustomObject]@{
        PowerShell = $ps
        AsyncResult = $asyncResult
    }
}

Export-ModuleMember -Function Start-MdnsDiscovery

function Invoke-AdbPair {
    param(
        [Parameter(Mandatory=$true)][string]$Target,
        [Parameter(Mandatory=$true)][string]$Pin
    )
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
    $result = adb pair $Target $Pin 2>&1
    
    if ($result -match 'Successfully paired to') {
        return $true
    } else {
        Write-Trace "Pairing failed: $result"
        return $false
    }
}
Export-ModuleMember -Function Invoke-AdbPair


function Start-UiDataPolling {
    <#
    .SYNOPSIS
        Background runspace that polls the local control API for UI state (discovered devices,
        mirror activity, pending-pair PIN) and enqueues the results. Keeps the blocking HTTP
        calls off the WPF UI thread so a slow localhost response can never freeze the interface.
        Mirrors Start-MdnsDiscovery (same runspace + ConcurrentQueue pattern).
    .PARAMETER Queue
        ConcurrentQueue the runspace enqueues @{Type='Devices'|'Mirror'|'PendingPair'; ...} messages into.
    .PARAMETER LocalApi
        Base URL of the local control API (e.g. http://127.0.0.1:53318).
    #>
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
                # Discovered devices (independent of mDNS — the LocalSendServer UDP/gateway fallback)
                try {
                    $devices = Invoke-RestMethod -Uri "$api/local/devices" -TimeoutSec 2 -ErrorAction Stop
                    [void]$queue.Enqueue(@{ Type = 'Devices'; Data = $devices })
                } catch {}

                # Mirror window active state (quick-action toggle sync)
                try {
                    $st = Invoke-RestMethod -Uri "$api/local/mirror-state" -TimeoutSec 1 -ErrorAction Stop
                    [void]$queue.Enqueue(@{ Type = 'Mirror'; Active = [bool]$st.active })
                } catch {}

                # Phone-initiated pairing (surface the pending PIN; only when one exists)
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

