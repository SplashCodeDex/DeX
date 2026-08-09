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
