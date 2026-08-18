# SettingsManager.ps1
# Centralized Settings Persistence & Registry Sync Module for DeX

function Get-SettingsFilePath {
    if (-not $global:DeXDataRoot) {
        $global:DeXDataRoot = Join-Path $env:LOCALAPPDATA "DeX"
    }
    if (-not (Test-Path $global:DeXDataRoot)) {
        New-Item -ItemType Directory -Path $global:DeXDataRoot -Force | Out-Null
    }
    return (Join-Path $global:DeXDataRoot "settings.json")
}

function Get-DeXDefaultSettings {
    $defaultDl = Join-Path $env:USERPROFILE "Downloads\DeX"
    return [ordered]@{
        CurrentTheme   = "DarkTheme"
        AppThemeMode   = "System"
        DndEnabled     = $false
        AutoConnect    = $true
        WiggleEnabled  = $true
        DownloadPath   = $defaultDl
    }
}

function Get-DeXSettings {
    $defaults = Get-DeXDefaultSettings
    $settingsFile = Get-SettingsFilePath

    if (Test-Path $settingsFile) {
        try {
            $raw = Get-Content -Path $settingsFile -Raw -ErrorAction Stop
            if (-not [string]::IsNullOrWhiteSpace($raw)) {
                $json = $raw | ConvertFrom-Json
                foreach ($prop in $defaults.Keys) {
                    if ($null -ne $json.$prop) {
                        $defaults[$prop] = $json.$prop
                    }
                }
                return $defaults
            }
        } catch {}
    }

    # Fallback to Registry if settings.json does not exist
    try {
        $regPath = "HKCU:\SOFTWARE\CodeDeX\DeX"
        if (Test-Path $regPath) {
            foreach ($prop in $defaults.Keys) {
                $val = Get-ItemPropertyValue -Path $regPath -Name $prop -ErrorAction SilentlyContinue
                if ($null -ne $val) {
                    if ($val -eq "True" -or $val -eq "False") {
                        $defaults[$prop] = [bool]::Parse($val)
                    } else {
                        $defaults[$prop] = $val
                    }
                }
            }
        }
    } catch {}

    return $defaults
}

function Save-DeXSettings([hashtable]$SettingsToSave) {
    $current = Get-DeXSettings
    if ($null -ne $SettingsToSave) {
        foreach ($key in $SettingsToSave.Keys) {
            $current[$key] = $SettingsToSave[$key]
        }
    }

    # 1. Primary Save to %LOCALAPPDATA%\DeX\settings.json
    try {
        $settingsFile = Get-SettingsFilePath
        $jsonStr = $current | ConvertTo-Json -Depth 2
        [System.IO.File]::WriteAllText($settingsFile, $jsonStr, [System.Text.Encoding]::UTF8)
    } catch {}

    # 2. Mirror Sync to Registry HKCU:\SOFTWARE\CodeDeX\DeX
    try {
        $regPath = "HKCU:\SOFTWARE\CodeDeX\DeX"
        if (-not (Test-Path $regPath)) {
            New-Item -Path $regPath -Force | Out-Null
        }
        foreach ($key in $current.Keys) {
            $valStr = [string]$current[$key]
            Set-ItemProperty -Path $regPath -Name $key -Value $valStr -Force -ErrorAction SilentlyContinue
        }
    } catch {}

    return $current
}

function Apply-DeXSettingsToUI {
    $s = Get-DeXSettings

    # 1. Theme application
    $global:CurrentTheme = $s.CurrentTheme
    $global:AppThemeMode = $s.AppThemeMode
    if ($global:AppThemeMode -eq "System") {
        $sysTheme = Get-SystemTheme
        Set-AppTheme $sysTheme
    } else {
        Set-AppTheme $global:CurrentTheme
    }

    # 2. DND state
    $script:isDndEnabled = [bool]$s.DndEnabled
    $txtBadgeDnd = $script:ce["txtBadgeDnd"]
    $badgeDnd = $script:ce["badgeDnd"]
    $btnQADnd = $script:wpfWindow.FindName("btnQADnd")
    if ($txtBadgeDnd -and $badgeDnd) {
        $txtBadgeDnd.Text = if ($script:isDndEnabled) { "ON" } else { "OFF" }
        if ($script:isDndEnabled) {
            $badgeDnd.Background = $script:wpfWindow.FindResource("DangerBrush")
            $txtBadgeDnd.Foreground = [System.Windows.Media.Brushes]::White
        } else {
            $badgeDnd.Background = $script:wpfWindow.FindResource("AccentBrush")
            $txtBadgeDnd.Foreground = $script:wpfWindow.FindResource("SecondaryTextBrush")
        }
    }
    if ($btnQADnd) { $btnQADnd.IsChecked = $script:isDndEnabled }

    # 3. Auto-Connect
    if (Get-Command "Set-AutoConnectStatus" -ErrorAction SilentlyContinue) {
        Set-AutoConnectStatus -Enable ([bool]$s.AutoConnect)
    }
    $txtBadgeAuto = $script:ce["txtBadgeAutoConnect"]
    $badgeAuto = $script:ce["badgeAutoConnect"]
    if ($txtBadgeAuto -and $badgeAuto) {
        $isAuto = [bool]$s.AutoConnect
        $txtBadgeAuto.Text = if ($isAuto) { "ON" } else { "OFF" }
        if ($isAuto) {
            $badgeAuto.Background = $script:wpfWindow.FindResource("SecondaryBrush")
            $txtBadgeAuto.Foreground = $script:wpfWindow.FindResource("SecondaryForegroundBrush")
        } else {
            $badgeAuto.Background = $script:wpfWindow.FindResource("DangerBrush")
            $txtBadgeAuto.Foreground = [System.Windows.Media.Brushes]::White
        }
    }

    # 4. Wiggle Gesture
    $script:wiggleEnabled = [bool]$s.WiggleEnabled
    $txtWiggle = $script:ce["txtSettingsWiggleToggle"]
    if ($txtWiggle) {
        $txtWiggle.Text = if ($script:wiggleEnabled) { "Enabled" } else { "Disabled" }
    }

    # 5. Download Path
    $script:customDownloadPath = $s.DownloadPath
    if (-not (Test-Path $script:customDownloadPath)) {
        try { New-Item -ItemType Directory -Path $script:customDownloadPath -Force | Out-Null } catch {}
    }
    $txtDl = $script:ce["txtSettingsDownloadPath"]
    if ($txtDl) {
        $txtDl.Text = $script:customDownloadPath
    }
}
