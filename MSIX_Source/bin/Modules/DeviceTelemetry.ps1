<#
.SYNOPSIS
    DeX - Device Telemetry
.DESCRIPTION
    Formats device telemetry (battery + wifi reported by devices over the
    WebSocket) into the device row status text shown in the spatial UI.
    Consumed by Connect-Engine.ps1.
#>

function Get-DeviceSubText {
    <#
    .SYNOPSIS
        Builds the single-line status text for a device row (model + battery + wifi).
    .PARAMETER Peer
        Hashtable containing Model, Battery, WifiSsid and WifiRssi.
    .PARAMETER Fallback
        Text used when no device information is available.
    #>
    param(
        [Parameter(Mandatory)]
        [hashtable]$Peer,
        [string]$Fallback = "OmniMesh"
    )

    if (-not $Peer.Model -and -not $Peer.Battery) { 
        return @{
            ModelIcon = ""
            ModelText = $Fallback
            BatteryIcon = ""
            BatteryText = ""
            WifiIcon = ""
            WifiText = ""
        }
    }

    $res = @{
        ModelIcon = ""
        ModelText = ""
        BatteryIcon = ""
        BatteryText = ""
        WifiIcon = ""
        WifiText = ""
    }

    if ($Peer.Model) { 
        $res.ModelIcon = ""
        $res.ModelText = $Peer.Model
    }
    if ($Peer.Battery) {
        $level = [int]$Peer.Battery
        $glyph = switch ($level) {
            { $_ -ge 90 } { 0xEBAC }  # full
            { $_ -ge 70 } { 0xEBA9 }  # ~3/4
            { $_ -ge 50 } { 0xEBA7 }  # half
            { $_ -ge 30 } { 0xEBA5 }  # ~1/4
            { $_ -ge 10 } { 0xEBA3 }  # low
            default      { 0xEBA2 }  # empty
        }
        $res.BatteryIcon = "$([char]$glyph)"
        $res.BatteryText = ""
    }
    
    if ($null -ne $Peer.WifiRssi -and $Peer.WifiRssi -gt -127) {
        $res.WifiIcon = "$([char]0xE701)"
        $res.WifiText = "$($Peer.WifiRssi)dBm"
    } elseif ($Peer.WifiSsid) {
        $res.WifiIcon = "$([char]0xE701)"
        $res.WifiText = $Peer.WifiSsid
    }

    return $res
}
