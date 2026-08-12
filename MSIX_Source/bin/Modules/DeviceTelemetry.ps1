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
            { $_ -ge 100 } { 0xE83F }  # Battery10 (full)
            { $_ -ge 90 }  { 0xE859 }  # Battery9
            { $_ -ge 80 }  { 0xE858 }  # Battery8
            { $_ -ge 70 }  { 0xE857 }  # Battery7
            { $_ -ge 60 }  { 0xE856 }  # Battery6
            { $_ -ge 50 }  { 0xE855 }  # Battery5
            { $_ -ge 40 }  { 0xE854 }  # Battery4
            { $_ -ge 30 }  { 0xE853 }  # Battery3
            { $_ -ge 20 }  { 0xE852 }  # Battery2
            { $_ -ge 10 }  { 0xE851 }  # Battery1
            default        { 0xE850 }  # Battery0 (empty)
        }
        $res.BatteryIcon = "$([char]$glyph)"
        $res.BatteryText = "${level}%"
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
