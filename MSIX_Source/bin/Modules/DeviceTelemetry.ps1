<#
.SYNOPSIS
    DeX - Device Telemetry
.DESCRIPTION
    Centralizes device telemetry: querying battery level over ADB, caching it on
    the omni-peer entries, and formatting the device row status text shown in the
    spatial UI. Consumed by Connect-Engine.ps1.
#>

# ADB port used for wireless device queries
$script:TelemetryAdbPort = 5555
# Default staleness threshold before a cached battery value is re-queried
$script:TelemetryMaxAgeSeconds = 60

function Get-DeviceBatteryLevel {
    <#
    .SYNOPSIS
        Queries the current battery level of an ADB device via `dumpsys battery`.
    .PARAMETER DeviceIp
        IP address of the target device.
    .RETURNS
        Battery level as a string (e.g. "87") or $null when unavailable.
    #>
    param([string]$DeviceIp)

    try {
        $batLine = (& $global:AdbExePath -s "${DeviceIp}:$($script:TelemetryAdbPort)" shell dumpsys battery 2>$null) |
                   Where-Object { $_ -match '^\s*level:' } | Select-Object -First 1
        if ($batLine -match '(\d+)') {
            return $matches[1]
        }
    } catch {}
    return $null
}

function Update-PeerBattery {
    <#
    .SYNOPSIS
        Refreshes a peer's cached battery level in place when the cache is stale.
    .DESCRIPTION
        The telemetry channel (WebSocket, battery reported by the device itself) is the
        primary source and works for every device. ADB (`dumpsys battery`) remains a
        fallback for the actively connected target when no telemetry has been reported.
    .PARAMETER Peer
        The omni-peer hashtable to update (sets Battery / TelemetryAge).
    .PARAMETER DeviceIp
        IP of the device to query (used by the ADB fallback).
    .PARAMETER IsActiveTarget
        Only the actively connected ADB target is queryable; pass $false to skip the fallback.
    .PARAMETER MaxAgeSeconds
        Minimum age of the cached value before re-querying. Defaults to the module default.
    .PARAMETER BatteryLevel
        Battery reported via telemetry; when provided, it is used directly (no ADB query).
    #>
    param(
        [Parameter(Mandatory)]
        [hashtable]$Peer,
        [Parameter(Mandatory)]
        [string]$DeviceIp,
        [bool]$IsActiveTarget = $true,
        [int]$MaxAgeSeconds = $script:TelemetryMaxAgeSeconds,
        [nullable[int]]$BatteryLevel = $null
    )

    # Telemetry channel takes priority: battery reported by the device itself
    if ($null -ne $BatteryLevel) {
        $Peer.Battery = [string]$BatteryLevel
        $Peer.TelemetryAge = Get-Date
        return
    }

    # ADB fallback: only for the active target and only when the cache is stale
    if (-not $IsActiveTarget) { return }
    $cachedAt = $Peer.TelemetryAge
    if (-not $cachedAt) { return }
    if ((Get-Date) - $cachedAt -gt [timespan]::FromSeconds($MaxAgeSeconds)) {
        $level = Get-DeviceBatteryLevel -DeviceIp $DeviceIp
        if ($null -ne $level) {
            $Peer.Battery = $level
            $Peer.TelemetryAge = Get-Date
        }
    }
}

function New-OmniPeer {
    <#
    .SYNOPSIS
        Creates an omni-peer telemetry entry, preserving cached telemetry from a prior entry.
    .PARAMETER Service
        The mDNS OmniMesh service record (Name / DeviceType / Model / IPPort).
    .PARAMETER Existing
        The previous peer entry to carry Model / Battery / TelemetryAge across announcements.
    .RETURNS
        Hashtable with Name, LastSeen, Type, Model, Battery, TelemetryAge, TrustLevel.
    #>
    param(
        [Parameter(Mandatory)]
        [object]$Service,
        [hashtable]$Existing
    )

    return @{
        Name         = $Service.Name
        LastSeen     = Get-Date
        Type         = $Service.DeviceType
        Model        = if ($Service.Model)   { $Service.Model }   elseif ($Existing) { $Existing.Model }   else { $null }
        Battery      = if ($Existing) { $Existing.Battery } else { $null }
        TelemetryAge = if ($Existing) { $Existing.TelemetryAge } else { [datetime]::MinValue }
        TrustLevel   = "Guest"
    }
}

function Update-PeerWifi {
    <#
    .SYNOPSIS
        Updates a peer's cached WiFi info (SSID + RSSI) from telemetry.
    .PARAMETER Peer
        The omni-peer hashtable to update (sets WifiSsid / WifiRssi).
    .PARAMETER Ssid
        WiFi network name reported by the device ("" or $null when absent).
    .PARAMETER Rssi
        WiFi signal strength in dBm reported by the device ($null when absent).
    #>
    param(
        [Parameter(Mandatory)]
        [hashtable]$Peer,
        [string]$Ssid = "",
        [nullable[int]]$Rssi = $null
    )

    if ([string]::IsNullOrWhiteSpace($Ssid) -and $null -eq $Rssi) { return }
    $Peer.WifiSsid = if ([string]::IsNullOrWhiteSpace($Ssid)) { $null } else { $Ssid }
    $Peer.WifiRssi = $Rssi
}

function Get-DeviceSubText {
    <#
    .SYNOPSIS
        Builds the single-line status text for a device row (model + battery + wifi).
    .PARAMETER Peer
        The omni-peer hashtable containing Model, Battery, WifiSsid and WifiRssi.
    .PARAMETER Fallback
        Text used when no device information is available.
    #>
    param(
        [Parameter(Mandatory)]
        [hashtable]$Peer,
        [string]$Fallback = "OmniMesh"
    )

    if (-not $Peer.Model -and -not $Peer.Battery) { return $Fallback }

    $text = ""
    if ($Peer.Model) { $text += "$([char]0xE8EA) $($Peer.Model)" }
    if ($Peer.Battery) { $text += "  $([char]0xE83F) $($Peer.Battery)%" }
    if ($null -ne $Peer.WifiRssi -and $Peer.WifiRssi -gt -127) {
        $text += "  $([char]0xE701) $($Peer.WifiRssi)dBm"
    } elseif ($Peer.WifiSsid) {
        $text += "  $([char]0xE701) $($Peer.WifiSsid)"
    }
    return $text
}
