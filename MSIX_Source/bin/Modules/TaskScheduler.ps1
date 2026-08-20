# TaskScheduler.ps1 - Legacy AutoConnect task cleanup migration
function Remove-LegacyAutoConnectTask {
    try {
        $service = New-Object -ComObject Schedule.Service
        $service.Connect()
        $folder = $service.GetFolder("\")
        $folder.DeleteTask("AutoConnectADB_Hotspot", 0)
    } catch {}
}

function Get-AutoConnectStatus {
    return $false
}

function Set-AutoConnectStatus([bool]$Enable) {
    Remove-LegacyAutoConnectTask
}

# Auto-cleanup on load
Remove-LegacyAutoConnectTask
