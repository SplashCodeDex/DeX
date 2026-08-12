# DeX-Constants.ps1 - Single source of truth for the shared protocol ports and well-known
# paths used by the PowerShell engine, mirroring DeXConstants.cs on the C# side so the two
# halves can never drift apart. Dot-sourced FIRST by Connect-Engine.ps1.
#
# Defined as $global: so the values are readable from the dot-sourced module files AND from
# the imported AdbManager.psm1 module scope, matching the existing $global:AdbExePath pattern.

$global:DeXLocalApi = "http://127.0.0.1:28425"                       # unencrypted localhost control API
$global:DeXDataRoot = Join-Path $env:LOCALAPPDATA "DeX"              # app data (theme.json, identity)

$global:DiscoveryPort = 28424                                # UDP Multicast Discovery Port
$global:LocalApiPort = 28425                                 # Local TCP HTTP API Port (engine loopback)

$global:AdbExePath = Join-Path $PSScriptRoot "adb.exe"
