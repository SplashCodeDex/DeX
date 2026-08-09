<#
.SYNOPSIS
    DeX - Core Engine & Tray Application
.DESCRIPTION
    Manages wireless ADB hotspot connections, provides a clean System Tray UI with Auto-Connect ON/OFF toggle,
    and handles Windows Task Scheduler integration.
#>

param(
    [switch]$Background,
    [switch]$ConnectOnly,
    [switch]$SelfTest
)

# Load shared constants (ports, paths) first so every subsequent module and binding can use them.
. "$PSScriptRoot\DeX-Constants.ps1"
. "$PSScriptRoot\Modules\EngineUtils.ps1"
Import-Module "$PSScriptRoot\Modules\AdbManager.psm1" -Force
. "$PSScriptRoot\Modules\TaskScheduler.ps1"
. "$PSScriptRoot\Modules\UIComponents.ps1"
. "$PSScriptRoot\Modules\DeviceTelemetry.ps1"
. "$PSScriptRoot\Modules\DeviceActions.ps1"
$mutexName = "Global\CodeDeX_DeX_Engine"
$script:showUiEvent = New-Object System.Threading.EventWaitHandle($false, [System.Threading.EventResetMode]::AutoReset, "Global\CodeDeX_DeX_ShowUI")
$script:engineMutex = New-Object System.Threading.Mutex($false, $mutexName)
if (-not $script:engineMutex.WaitOne(0, $false)) {
    if (-not $Background -and -not $SelfTest) { $script:showUiEvent.Set() | Out-Null }
    # Another instance is already running — trigger an immediate connection attempt
    $null = Invoke-AdbConnect
    exit
}

if ($PSScriptRoot -match "WindowsApps") {
    $global:AdbExePath = "DeX-adb.exe"
} else {
    $global:AdbExePath = "$PSScriptRoot\adb.exe"
}

# Force STA Mode Threading for Windows Forms & Tray Icons
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName PresentationFramework

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:ADB_MDNS_OPENSCREEN = 1
$TaskName = "AutoConnectADB_Hotspot"
$ScriptPath = $PSCommandPath

# If called for ConnectOnly (e.g. from background Task Scheduler trigger)
if ($ConnectOnly) {
    $res = Invoke-AdbConnect
    exit
}

# Check Task Scheduler Auto-Connect status


# Create System Tray Icon
$script:notifyIcon = New-Object System.Windows.Forms.NotifyIcon
$script:notifyIcon.Text = "Connect ADB: Initializing..."


$iconConnected = Load-TrayIcon "app-icon-light.ico"
$iconDisconnected = Load-TrayIcon "app-icon-gray.ico"

$script:notifyIcon.Icon = $iconDisconnected
$script:notifyIcon.Visible = $true


# Spatial UI WPF Overlay
    $xamlFile = Join-Path $PSScriptRoot "..\Themes\MainWindow.xaml"
    $xamlRaw = [System.IO.File]::ReadAllText($xamlFile)
    $needle = "`$(`$PSScriptRoot -replace '\\', '/')"
    $binFwd = $PSScriptRoot -replace '\\', '/'
    $xaml = $xamlRaw.Replace($needle, $binFwd)

# Fail fast: if the XAML fails to parse/load, $script:wpfWindow stays null and the tray icon
# would keep running as a zombie that silently ignores every click. Exit loudly instead.
try {
    $pc = New-Object System.Windows.Markup.ParserContext
    $pc.BaseUri = New-Object System.Uri("file:///$($xamlFile.Replace('\', '/'))")
    $script:wpfWindow = [System.Windows.Markup.XamlReader]::Parse($xaml, $pc)
    if ($null -eq $script:wpfWindow) { throw "XamlReader returned a null window." }
} catch {
    $script:wpfWindow = $null
    $script:WindowLoadError = $_.Exception.Message
}

# Never-dead tray: if the spatial UI fails to load, degrade to a minimal WinForms
# fallback menu so core ADB features keep working instead of a silent zombie icon.
if ($null -eq $script:wpfWindow) {
    if ($SelfTest) {
        Write-Output "SELFTEST FATAL: $script:WindowLoadError"
        $script:notifyIcon.Visible = $false
        $script:notifyIcon.Dispose()
        exit 1
    }
    [System.Windows.MessageBox]::Show("DeX could not load its spatial interface and is running in fallback mode.`n`n$script:WindowLoadError", "DeX - Fallback Mode", 'OK', 'Warning') | Out-Null

    $script:notifyIcon.Text = "Connect ADB (Fallback Mode)"
    $fallbackMenu = New-Object System.Windows.Forms.ContextMenuStrip

    $miConnect = $fallbackMenu.Items.Add("Connect ADB Now")
    $miConnect.Add_Click({
        $res = Invoke-AdbConnect
        if ($res.Success) {
            $script:notifyIcon.Icon = $iconConnected
            $script:notifyIcon.Text = "Connected: $($res.Name)"
            Show-Toast -Title "ADB Connected" -Message "Successfully connected to $($res.Name)"
        } else {
            $script:notifyIcon.Icon = $iconDisconnected
            $script:notifyIcon.Text = "Disconnected"
            Show-Toast -Title "Connection Failed" -Message $res.Message
        }
    })

    $miAuto = $fallbackMenu.Items.Add("Auto-Connect on Hotspot")
    $miAuto.Checked = Get-AutoConnectStatus
    $miAuto.CheckOnClick = $true
    $miAuto.Add_Click({
        Set-AutoConnectStatus -Enable (-not (Get-AutoConnectStatus))
        $miAuto.Checked = Get-AutoConnectStatus
        Show-Toast -Title "Auto-Connect" -Message $(if ($miAuto.Checked) { "Enabled - will connect when PC joins phone hotspot." } else { "Disabled." })
    })

    $miGoogle = $fallbackMenu.Items.Add("Sign in with Google")
    $miGoogle.Add_Click({
        # Runs the OAuth loopback flow on the local engine (browser opens automatically)
        Start-Job {
            try {
                Invoke-RestMethod -Uri "$global:DeXLocalApi/local/settings/google-signin" -TimeoutSec 240 | Out-Null
            } catch {
                Write-Output "Google sign-in failed: $($_.Exception.Message)"
            }
        } | Out-Null
        Show-Toast -Title "Google Sign-In" -Message "Opening browser — approve the account to trust all your devices."
    })

    $miExit = $fallbackMenu.Items.Add("Exit Engine")
    $miExit.Add_Click({
        $script:notifyIcon.Visible = $false
        $script:notifyIcon.Dispose()
        [System.Windows.Forms.Application]::Exit()
    })

    $script:notifyIcon.ContextMenuStrip = $fallbackMenu
    [System.Windows.Forms.Application]::Run()
    exit
}

$themeFile = Join-Path $global:DeXDataRoot "theme.json"
if (Test-Path $themeFile) {
    try {
        $cfg = Get-Content $themeFile -Raw | ConvertFrom-Json
        $global:CurrentTheme = $cfg.CurrentTheme
        $global:AppThemeMode = $cfg.AppThemeMode
    } catch {}
}
if (-not $global:CurrentTheme) { $global:CurrentTheme = "DarkTheme" }
if (-not $global:AppThemeMode) { $global:AppThemeMode = "System" }

if ($global:AppThemeMode -eq "System") {
    Set-AppTheme (Get-SystemTheme)
} else {
    Set-AppTheme $global:CurrentTheme
}
[Microsoft.Win32.SystemEvents]::add_UserPreferenceChanged({
    param($sender, $e)
    if ($global:AppThemeMode -eq "System") {
        $t = Get-SystemTheme
        if ($global:CurrentTheme -ne $t) { Set-AppTheme $t }
    }
})

    # Load UI Bindings in current scope
    . "$PSScriptRoot\TrayUIBindings.ps1"


# Passive sync initial state on startup
$script:AutoConnectEnabled = Get-AutoConnectStatus
Update-WpfUI

if ($SelfTest) {
    # Headless self-diagnostics: prove the full tray-click -> window-show pipeline works end to end.
    # Used by CI (Validate Build workflow) and can be run locally: Connect-Engine.ps1 -SelfTest
    $stWindowCreated = ($null -ne $script:wpfWindow)
    $stTrayVisible = [bool]$script:notifyIcon.Visible
    $stShown = $false
    try {
        $eArgs = New-Object System.Windows.Forms.MouseEventArgs([System.Windows.Forms.MouseButtons]::Left, 1, 0, 0, 0)
        $invokeArgs = [Array]::CreateInstance([object], 1)
        $invokeArgs.SetValue($eArgs, 0)
        $script:notifyIcon.GetType().GetMethod('OnMouseUp', [System.Reflection.BindingFlags]'NonPublic,Instance').Invoke($script:notifyIcon, $invokeArgs)
        
        # Give the Dispatcher.BeginInvoke time to execute the Show()
        $waitCount = 0
        while (-not $script:wpfWindow.IsVisible -and $waitCount -lt 20) {
            $frame = New-Object System.Windows.Threading.DispatcherFrame
            $script:wpfWindow.Dispatcher.BeginInvoke([System.Windows.Threading.DispatcherPriority]::SystemIdle, [Action]{ $frame.Continue = $false }) | Out-Null
            [System.Windows.Threading.Dispatcher]::PushFrame($frame)
            Start-Sleep -Milliseconds 50
            $waitCount++
        }
        
        $stShown = [bool]$script:wpfWindow.IsVisible
    } catch {
        Write-Output "SELFTEST EXCEPTION: $($_.Exception.ToString())"
    }
    $stOk = $stWindowCreated -and $stTrayVisible -and $stShown
    Write-Output ("SELFTEST WindowCreated={0} TrayVisible={1} WindowShownAfterTrayClick={2}" -f $stWindowCreated, $stTrayVisible, $stShown)
    if ($script:wpfWindow.IsVisible) { $script:wpfWindow.Hide() }
    $script:notifyIcon.Visible = $false
    $script:notifyIcon.Dispose()
    exit $(if ($stOk) { 0 } else { 1 })
}

# Fix MSIX Version Path Drift: Re-register the task if already enabled so the path points to the new updated folder
if ($script:AutoConnectEnabled) {
    Set-AutoConnectStatus -Enable $true
}

# Transfer notifications queue (file received events from C# LocalSendServer)
$script:transferQueue = [System.Collections.Concurrent.ConcurrentQueue[object]]::new()
$script:mdnsQueue = [System.Collections.Concurrent.ConcurrentQueue[object]]::new()

# Start mDNS & ADB auto-discovery
$script:mdnsJob = $null



$script:mdnsJob = Start-MdnsDiscovery -Queue $script:mdnsQueue

# Background UI-data poller: fetches /local/devices, /local/mirror-state and /local/pending-pair
# off the UI thread and enqueues results, so blocking HTTP never freezes the spatial menu.
$script:uiPollQueue = [System.Collections.Concurrent.ConcurrentQueue[object]]::new()
$script:uiPollJob = Start-UiDataPolling -Queue $script:uiPollQueue -LocalApi $global:DeXLocalApi

$mdnsTimer = New-Object System.Windows.Threading.DispatcherTimer
$mdnsTimer.Interval = [TimeSpan]::FromSeconds(2)
$mdnsTimer.Add_Tick({
  try {
    # 1. Check Transfer Server Job
        $t = $null
        while ($script:transferQueue.TryDequeue([ref]$t)) {
            if ($t.Type -eq 'TransferComplete') {
                Show-Toast -Title "File Received" -Message "Saved to: $($t.File)"
                $fe = $script:wpfWindow.FindName("FileExplorer")
                if ($fe -and $fe.Visibility -eq 'Visible' -and $script:currentDirPath -match '^[A-Za-z]:\\') {
                    $script:wpfWindow.Dispatcher.InvokeAsync([Action]{ Load-Directory $script:currentDirPath }) | Out-Null
                }
            } elseif ($t.Type -eq 'Error') {
                Write-Trace $t.Message
            }
        }
        
        # 2. Check Discovery Job
        $livePeers = @()
        $received = @()
        $m = $null
        while ($script:mdnsQueue.TryDequeue([ref]$m)) {
            $received += $m
        }
        
        if ($received.Count -gt 0) {
            $uniqueServices = $received | Sort-Object -Property Type, IPPort -Unique
                
                # Check for Pairing
                $pairingTargets = $uniqueServices | Where-Object { $_.Type -eq 'Pairing' } | Select-Object -ExpandProperty IPPort
                foreach ($pt in $pairingTargets) {
                    Write-Trace "mDNS Poller found Pairing Target: $pt"
                    if (-not $script:pairedHistory) { $script:pairedHistory = @{} }
                    if (-not $script:pairedHistory[$pt]) {
                        $pin = Show-PairingPrompt -IPPort $pt
                        if ($pin) {
                            $success = Invoke-AdbPair -Target $pt -Pin $pin
                            if ($success) { $script:pairedHistory[$pt] = $true }
                        } else {
                            $script:pairedHistory[$pt] = $true
                        }
                    }
                }

                # Check for Connect
                $connectTargets = $uniqueServices | Where-Object { $_.Type -eq 'Connect' } | Select-Object -ExpandProperty IPPort
                foreach ($ct in $connectTargets) {
                    Write-Trace "mDNS Poller found Connect Target: $ct"
                    if ($script:currentTarget -ne $ct) {
                        Invoke-AdbConnect -Target $ct
                    }
                }
                
            }

            # Apply data fetched by the background UI-data poller (devices / mirror / pending-pair).
            # The poller does the blocking HTTP; this tick only drains the queue and updates the UI.
            $udpRes = $null
            $mirrorActive = $null
            $pendingPair = $null
            $qMsg = $null
            while ($script:uiPollQueue.TryDequeue([ref]$qMsg)) {
                if ($qMsg.Type -eq 'Devices') { $udpRes = $qMsg.Data }
                elseif ($qMsg.Type -eq 'Mirror') { $mirrorActive = $qMsg.Active }
                elseif ($qMsg.Type -eq 'PendingPair') { $pendingPair = $qMsg }
            }

            # Poll Outbound Pairing Status
            if ($script:activeOutboundPairIp) {
                try {
                    $outStatus = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/pair-status?ip=$($script:activeOutboundPairIp)" -TimeoutSec 1 -ErrorAction Stop
                    if ($outStatus.status -eq 'Accepted') {
                        $script:wpfWindow.FindName("pinViewPanel").Visibility = 'Collapsed'
                        $script:activeOutboundPairIp = $null
                        Show-Toast -Title "Pairing Successful" -Message "Device has been paired."
                    } elseif ($outStatus.status -eq 'Failed' -or $outStatus.status -eq 'Rejected') {
                        $script:wpfWindow.FindName("pinViewPanel").Visibility = 'Collapsed'
                        $script:activeOutboundPairIp = $null
                        Show-Toast -Title "Pairing Failed" -Message "Request was declined or timed out."
                    }
                } catch { }
            }
            # Phone-initiated pairing: no button was clicked, so surface the pending PIN here.
            # The pending-pair fetch itself runs in the background poller; $pendingPair holds the result.
            else {
                $pp = $pendingPair
                if ($pp -and $pp.pin) {
                    $script:activeOutboundPairIp = $pp.ip
                    $script:activeOutboundPairFp = $pp.fingerprint
                    Show-PinPanel -Title "Pairing with $($pp.alias)" -Code $pp.pin -Status "Waiting for remote acceptance..." `
                        -HidePanelOnTerminal `
                        -SuccessMessage "Device has been paired." `
                        -FailureMessage "Request was declined or timed out."
                }
            }

            # Apply discovered devices fetched by the background poller (LocalSendServer Gateway
            # Unicast fallback). Runs independently of mDNS; data arrives via $script:uiPollQueue.
            try {
                if ($null -ne $udpRes) {
                    $icUdp = $script:wpfWindow.FindName("icUdpPeers")
                    if ($icUdp) {
                        $liveUdp = @()
                        foreach ($p in $udpRes) {
                            $dt = [datetimeOffset]::FromUnixTimeMilliseconds($p.lastSeen).UtcDateTime
                            if (([datetime]::UtcNow) - $dt -lt [timespan]::FromSeconds(10)) {
                                if ($p.isPaired -or $p.isAutoTrusted) {
                                    # Telemetry (WebSocket) feeds battery + wifi for every device
                                    $peerRow = @{
                                        Model    = $p.info.deviceModel
                                        Battery  = if ($null -ne $p.battery) { [string]$p.battery } else { $null }
                                        WifiSsid = $p.wifiSsid
                                        WifiRssi = $p.wifiRssi
                                    }
                                    $livePeers += @{
                                        Name        = $p.info.alias
                                        SubText     = Get-DeviceSubText -Peer $peerRow
                                        IconGlyph   = "$([char]0xE8EA)"
                                        IP          = $p.ip
                                        Fingerprint = $p.info.fingerprint
                                    }
                                } else {
                                    $liveUdp += @{
                                        Ip = $p.ip
                                        Alias = $p.info.alias
                                        DeviceModel = $p.info.deviceModel
                                        DeviceType = $p.info.deviceType
                                        Fingerprint = $p.info.fingerprint
                                    }
                                }
                            }
                        }
                        
                        
                        # Only update icLivePeers when the device set actually changes
                        $newLiveFP = ($livePeers | ForEach-Object { "$($_['IP']):$($_['Name'])" } | Sort-Object) -join ','
                        if ($newLiveFP -ne $script:lastLivePeersFingerprint) {
                            $ic = $script:wpfWindow.FindName("icLivePeers")
                            if ($ic) {
                                # Animate out departing items before swapping
                                $oldIPs = @()
                                if ($ic.ItemsSource) { $oldIPs = @($ic.ItemsSource | ForEach-Object { $_['IP'] }) }
                                $newIPs = @($livePeers | ForEach-Object { $_['IP'] })
                                $departing = @($oldIPs | Where-Object { $_ -notin $newIPs })
                                if ($departing.Count -gt 0) {
                                    foreach ($container in @(0..($ic.Items.Count - 1) | ForEach-Object { $ic.ItemContainerGenerator.ContainerFromIndex($_) } | Where-Object { $_ })) {
                                        $item = $ic.ItemContainerGenerator.ItemFromContainer($container)
                                        if ($item -and $item['IP'] -and $departing -contains $item['IP']) {
                                            $fadeOut = New-Object System.Windows.Media.Animation.DoubleAnimation
                                            $fadeOut.To = 0; $fadeOut.Duration = [TimeSpan]::FromMilliseconds(300)
                                            $container.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $fadeOut)
                                        }
                                    }
                                    # Schedule the actual swap after fade-out completes
                                    $timer = New-Object System.Windows.Threading.DispatcherTimer
                                    $timer.Interval = [TimeSpan]::FromMilliseconds(320)
                                    $capturedPeers = $livePeers
                                    $capturedIc = $ic
                                    $timer.Add_Tick({
                                        param($s, $e)
                                        $capturedIc.ItemsSource = $capturedPeers
                                        $s.Stop()
                                    }.GetNewClosure())
                                    $timer.Start()
                                } else {
                                    $ic.ItemsSource = $livePeers
                                }
                            }
                            $script:lastLivePeersFingerprint = $newLiveFP
                        }
                        
                        # Only update UI when the device set actually changes (prevents re-triggering Loaded animation)
                        $newFingerprint = ($liveUdp | ForEach-Object { "$($_['Ip']):$($_['Alias'])" } | Sort-Object) -join ','
                        if ($newFingerprint -ne $script:lastUdpFingerprint) {
                            # Animate out departing items before swapping
                            $oldUdpIPs = @()
                            if ($icUdp.ItemsSource) { $oldUdpIPs = @($icUdp.ItemsSource | ForEach-Object { $_['Ip'] }) }
                            $newUdpIPs = @($liveUdp | ForEach-Object { $_['Ip'] })
                            $departingUdp = @($oldUdpIPs | Where-Object { $_ -notin $newUdpIPs })
                            if ($departingUdp.Count -gt 0) {
                                foreach ($container in @(0..($icUdp.Items.Count - 1) | ForEach-Object { $icUdp.ItemContainerGenerator.ContainerFromIndex($_) } | Where-Object { $_ })) {
                                    $item = $icUdp.ItemContainerGenerator.ItemFromContainer($container)
                                    if ($item -and $item['Ip'] -and $departingUdp -contains $item['Ip']) {
                                        $fadeOut = New-Object System.Windows.Media.Animation.DoubleAnimation
                                        $fadeOut.To = 0; $fadeOut.Duration = [TimeSpan]::FromMilliseconds(300)
                                        $container.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $fadeOut)
                                    }
                                }
                                # Schedule the actual swap after fade-out completes
                                $timer2 = New-Object System.Windows.Threading.DispatcherTimer
                                $timer2.Interval = [TimeSpan]::FromMilliseconds(320)
                                $capturedUdp = $liveUdp
                                $capturedIcUdp = $icUdp
                                $timer2.Add_Tick({
                                    param($s, $e)
                                    $capturedIcUdp.ItemsSource = $capturedUdp
                                    $s.Stop()
                                }.GetNewClosure())
                                $timer2.Start()
                            } else {
                                $icUdp.ItemsSource = $liveUdp
                            }
                            $script:lastUdpFingerprint = $newFingerprint
                        }
                    }
                }
            } catch { }
                
                # Keep the quick-action mirror toggle in sync with the actual mirror window state
                # (state fetched by the background poller; $mirrorActive holds the latest value)
                try {
                    $btnMirror = $script:wpfWindow.FindName("btnQAMirror")
                    if ($btnMirror -and $null -ne $mirrorActive) {
                        $wanted = [bool]$mirrorActive
                        if ($btnMirror.IsChecked -ne $wanted) { $btnMirror.IsChecked = $wanted }
                    }
                } catch {}
  } catch { }
    })
    $mdnsTimer.Start()

if ($Background) {
    Show-Toast -Title "Connect ADB Active" -Message "Right-click tray icon to toggle Auto-Connect ON/OFF or Connect Now."
}

# Automatic clipboard sync (PC -> phone): push fresh local copies over the WebSocket.
# Controlled by the quick-action clipboard toggle ($script:clipboardSyncEnabled).
# The phone's own pushes are learned from /local/clipboard-state so they are never echoed.
$script:clipboardSyncEnabled = $false
$script:clipLastPushed = ""
$script:clipLastReceived = ""
$script:clipboardTimer = New-Object System.Windows.Threading.DispatcherTimer
$script:clipboardTimer.Interval = [TimeSpan]::FromSeconds(2)
$script:clipboardTimer.Add_Tick({
    if (-not $script:clipboardSyncEnabled) { return }
    try {
        # 1. Learn what the phone last pushed, so we don't echo it back to the phone
        try {
            $state = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/clipboard-state" -TimeoutSec 1 -ErrorAction Stop
            if ($state -and $state.text -and $state.text -ne $script:clipLastReceived) {
                $script:clipLastReceived = [string]$state.text
            }
        } catch {}

        # 2. Detect a fresh local clipboard change and push it to the active device
        $text = Get-Clipboard -Raw -ErrorAction Ignore
        if (-not [string]::IsNullOrWhiteSpace($text) -and
            $text -ne $script:clipLastPushed -and
            $text -ne $script:clipLastReceived) {

            $ip = $null
            $currentTarget = Get-ConnectedDeviceTarget
            if ($currentTarget) { $ip = ($currentTarget -replace ':.*','') }
            if (-not $ip) {
                try {
                    $devices = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/devices" -TimeoutSec 2 -ErrorAction Stop
                    $target = $devices | Where-Object { $_.isPaired -or $_.isAutoTrusted } | Select-Object -First 1
                    if ($target) { $ip = $target.ip }
                } catch {}
            }
            if ($ip -and (Send-ClipboardToDevice -Ip $ip -Quiet)) {
                $script:clipLastPushed = $text
            }
        }
    } catch {}
})
$script:clipboardTimer.Start()

$uiTimer = New-Object System.Windows.Threading.DispatcherTimer
$uiTimer.Interval = [TimeSpan]::FromMilliseconds(150)
$uiTimer.Add_Tick({
    if ($script:showUiEvent.WaitOne(0)) {
        if ($script:wpfWindow.IsVisible) {
            $script:wpfWindow.Topmost = $true
            $script:wpfWindow.Activate()
            $script:wpfWindow.Focus()
        } else {
            $eArgs = New-Object System.Windows.Forms.MouseEventArgs([System.Windows.Forms.MouseButtons]::Left, 1, 0, 0, 0)
            $invokeArgs = [Array]::CreateInstance([object], 1)
            $invokeArgs.SetValue($eArgs, 0)
            $script:notifyIcon.GetType().GetMethod('OnMouseUp', [System.Reflection.BindingFlags]'NonPublic,Instance').Invoke($script:notifyIcon, $invokeArgs)
        }
    }
})
$uiTimer.Start()
if (-not $Background -and -not $SelfTest) { $script:showUiEvent.Set() | Out-Null }

[System.Windows.Forms.Application]::add_ApplicationExit({
    if ($script:mdnsJob -and $script:mdnsJob.PowerShell) {
        Write-Trace "Disposing mDNS Runspace..."
        $script:mdnsJob.PowerShell.Dispose()
    }
    if ($script:uiPollJob -and $script:uiPollJob.PowerShell) {
        Write-Trace "Disposing UI-data poller Runspace..."
        $script:uiPollJob.PowerShell.Dispose()
    }
    # Transfer server is hosted by DeXShareTarget.exe (C# LocalSendServer) — no PS runspace to dispose
})

[System.Windows.Forms.Application]::Run()





