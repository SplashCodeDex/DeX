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
. "$PSScriptRoot\Modules\ClipboardManager.ps1"
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

if (-not ('DeX.Wpf.ElementCache' -as [type])) {
    Add-Type @'
using System;
using System.Collections;

namespace DeX.Wpf
{
    public class ElementCache : IDictionary
    {
        private readonly Func<string, object> _finder;
        private readonly Hashtable _cache = new Hashtable(StringComparer.OrdinalIgnoreCase);

        public ElementCache(Func<string, object> finder)
        {
            _finder = finder;
        }

        public object this[object key]
        {
            get
            {
                if (key == null) return null;
                string name = key.ToString();
                if (!_cache.ContainsKey(name))
                {
                    object val = _finder != null ? _finder(name) : null;
                    if (val != null)
                    {
                        _cache[name] = val;
                    }
                }
                return _cache[name];
            }
            set
            {
                if (key != null)
                {
                    _cache[key.ToString()] = value;
                }
            }
        }

        public bool Contains(object key)
        {
            return this[key] != null;
        }

        public void Add(object key, object value)
        {
            if (key != null)
            {
                _cache[key.ToString()] = value;
            }
        }

        public void Clear()
        {
            _cache.Clear();
        }

        public IDictionaryEnumerator GetEnumerator()
        {
            return _cache.GetEnumerator();
        }

        public void Remove(object key)
        {
            if (key != null)
            {
                _cache.Remove(key.ToString());
            }
        }

        public bool IsFixedSize
        {
            get { return false; }
        }

        public bool IsReadOnly
        {
            get { return false; }
        }

        public ICollection Keys
        {
            get { return _cache.Keys; }
        }

        public ICollection Values
        {
            get { return _cache.Values; }
        }

        public void CopyTo(Array array, int index)
        {
            _cache.CopyTo(array, index);
        }

        public int Count
        {
            get { return _cache.Count; }
        }

        public bool IsSynchronized
        {
            get { return false; }
        }

        public object SyncRoot
        {
            get { return _cache.SyncRoot; }
        }

        IEnumerator IEnumerable.GetEnumerator()
        {
            return _cache.GetEnumerator();
        }
    }
}
'@
}

if (-not ('DeXWin32.Fg' -as [type])) {
    Add-Type -Namespace DeXWin32 -Name Fg -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
[DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
'@
}

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
$script:notifyIcon.Text = "DeX: Initializing..."


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

    $script:notifyIcon.Text = "DeX Engine (Fallback Mode)"
    $fallbackMenu = New-Object System.Windows.Forms.ContextMenuStrip

    $miConnect = $fallbackMenu.Items.Add("Connect ADB (Dev Tools)")
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
        Invoke-ExitEngine
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

# Dynamic element cache: intercepts indexer and property lookups in O(1) time.
# If an element has not been accessed yet, it automatically calls FindName on the WPF
# Window, caches the live control in memory, and returns it. Works transparently for both
# $script:ce["name"] and $script:ce.name with zero manual $initElements maintenance.
$finder = [Func[string, object]]{
    param($name)
    if ($null -ne $script:wpfWindow) { $script:wpfWindow.FindName($name) } else { $null }
}
$script:ce = New-Object DeX.Wpf.ElementCache($finder)
$script:dxEl = $script:ce
function dxEl([string]$name) {
    return $script:ce[$name]
}

    # Load UI Bindings in current scope
    . "$PSScriptRoot\TrayUIBindings.ps1"


. "$PSScriptRoot\Modules\SettingsManager.ps1"
. "$PSScriptRoot\Modules\ClipboardManager.ps1"

# Passive sync initial state on startup
$script:AutoConnectEnabled = Get-AutoConnectStatus
Apply-DeXSettingsToUI
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

$script:mdnsJob = $null

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

            # Apply data fetched by the background UI-data poller (devices / mirror / pending-pair).
            # The poller does the blocking HTTP; this tick only drains the queue and updates the UI.
            $livePeers = @()
            $udpRes = $null
            $mirrorActive = $null
            $pendingPair = $null
            $qMsg = $null
            while ($script:uiPollQueue.TryDequeue([ref]$qMsg)) {
                if ($qMsg.Type -eq 'Devices') { $udpRes = $qMsg.Data }
                elseif ($qMsg.Type -eq 'Mirror') { $mirrorActive = $qMsg.Active }
                elseif ($qMsg.Type -eq 'PendingPair') { $pendingPair = $qMsg }
            }

            # Phone-initiated pairing: no button was clicked, so surface the pending PIN here.
            # The pending-pair fetch itself runs in the background poller; $pendingPair holds
            # the result. Status polling for BOTH pairing directions is owned by Show-PinPanel's
            # pairWaitTimer (1s, started when the panel opens) — the old inline /local/pair-status
            # poll here duplicated it, and worse, polled forever (404, swallowed) after a
            # discovered-device click that never sent pair-initiate, blocking the UI thread
            # ~1s every 2s.
            if (-not $script:activeOutboundPairIp) {
                $pp = $pendingPair
                if ($pp -and $pp.pin) {
                    # The user may have just cancelled this exact attempt: its result was
                    # already queued by the poller, so it must not slide the panel back in
                    # over the dismissal. Suppression is time-bounded (see Stop-PairingSession)
                    # so a genuine new attempt for the same device still shows.
                    if ($pp.fingerprint -eq $script:suppressPendingPairFp -and [datetime]::UtcNow -lt $script:suppressPendingPairUntil) {
                        $script:suppressPendingPairFp = $null
                        $script:suppressPendingPairUntil = $null
                    } else {
                        # A phone-initiated pairing needs the PC user to READ the PIN from the
                        # screen. Surface the window whenever it isn't already the focused,
                        # on-screen window (hidden in the tray, minimized, or behind other
                        # apps) — otherwise the PIN panel would be invisible and stall.
                        $w = $script:wpfWindow
                        if (-not $w.IsVisible -or -not $w.IsActive) {
                            Write-Trace "Auto-showing window for inbound pairing (visible=$($w.IsVisible) active=$($w.IsActive))"
                            $script:isShowingMenu = $true
                            if (-not $w.IsVisible) { $w.Show() }
                            $w.Activate()
                            $w.Focus()
                            # WPF Activate() is blocked by the Windows foreground lock when a
                            # background process calls it, leaving the window behind other apps
                            # (the "flicker" the user never sees the PIN in). Force it to the
                            # foreground with the Win32 API.
                            try {
                                $hwnd = [System.Windows.Interop.WindowInteropHelper]::new($w).Handle
                                [DeXWin32.Fg]::ShowWindow($hwnd, 9) | Out-Null   # SW_RESTORE
                                [DeXWin32.Fg]::SetForegroundWindow($hwnd) | Out-Null
                            } catch {}
                            # The foreground lock reclaims focus ~1s later, dropping the window
                            # behind the previously focused app even though it stays "visible".
                            # Keep it on top for the pairing's duration so the PIN stays in view;
                            # restore the user's prior z-order when the pairing ends.
                            $script:priorWindowTopmost = $w.Topmost
                            $w.Topmost = $true
                            # Reset any leftover chrome state from a previous show/hide animation
                            # (a window hidden mid-PopIn can reappear translucent, scaled, or
                            # offset down by the frozen entrance Y-transforms).
                            $wb = $w.FindName("mainBorder")
                            if ($wb) { $wb.Opacity = 1 }
                            $ws2 = $w.FindName("winScale")
                            if ($ws2) { $ws2.ScaleX = 1; $ws2.ScaleY = 1 }
                            $wTrans = $w.FindName("winTrans")
                            if ($wTrans) { $wTrans.Y = 0 }
                            $mTrans = $w.FindName("menuTrans")
                            if ($mTrans) { $mTrans.Y = 0 }
                            $mcTrans = $w.FindName("menuContentTrans")
                            if ($mcTrans) { $mcTrans.X = 0; $mcTrans.Y = 0 }
                            if (-not $script:showMenuGuardTimer) {
                                $script:showMenuGuardTimer = New-Object System.Windows.Threading.DispatcherTimer
                                $script:showMenuGuardTimer.Interval = [TimeSpan]::FromMilliseconds(1000)
                                $script:showMenuGuardTimer.Add_Tick({
                                    $script:isShowingMenu = $false
                                    $script:showMenuGuardTimer.Stop()
                                    $script:showMenuGuardTimer = $null
                                })
                            }
                            $script:showMenuGuardTimer.Stop()
                            $script:showMenuGuardTimer.Start()
                        }
                        $script:activeOutboundPairIp = $pp.ip
                        $script:activeOutboundPairFp = $pp.fingerprint
                        try {
                            Show-PinPanel -Title "Pairing with $($pp.alias)" -Code $pp.pin -Status "Waiting for the PIN to be entered on the phone..." `
                                -HidePanelOnTerminal `
                                -HideAcceptButtons `
                                -SuccessMessage "Device has been paired." `
                                -FailureMessage "Request was declined or timed out."
                            # Guarantee the PIN panel is on-screen even if the slide-in
                            # storyboard failed to run (panel starts collapsed at X=300).
                            $pv = $script:wpfWindow.FindName("pinViewPanel")
                            if ($pv) { $pv.Opacity = 1 }
                            $pt = $script:wpfWindow.FindName("pinViewTrans")
                            if ($pt) { $pt.X = 0 }
                            $mcp = $script:wpfWindow.FindName("menuContentPanel")
                            if ($mcp) { $mcp.Opacity = 0 }
                            Write-Trace "Inbound PIN panel shown"
                        } catch {
                            Write-Trace "Show-PinPanel failed for inbound pairing: $_"
                        }
                    }
                }
            }

            # Apply discovered devices fetched by the background poller (LocalSendServer Gateway
            # Unicast fallback). Runs independently of mDNS; data arrives via $script:uiPollQueue.
            # The card drop shadow is suspended for the swap so the repaint that follows a device
            # set change doesn't re-rasterize the blur on a layered window (the priciest per-frame
            # cost); it is restored immediately after (or on any error).
            try {
                if ($null -ne $udpRes) {
                    Suspend-CardEffect
                    try {
                    $icUdp = $script:wpfWindow.FindName("icUdpPeers")
                    if ($icUdp) {
                        $liveUdp = @()
                        foreach ($p in $udpRes) {
                            $dt = [datetimeOffset]::FromUnixTimeMilliseconds($p.lastSeen).UtcDateTime
                            # Show devices that announced within the freshness window OR are
                            # WebSocket-online. The server deliberately keeps WS-connected
                            # devices listed even when their LAN announcements lapse (mDNS
                            # queries run every 15-31s, telemetry every 60s — both exceed the
                            # 10s window), so those must stay clickable instead of vanishing.
                            if (([datetime]::UtcNow) - $dt -lt [timespan]::FromSeconds(10) -or $p.isOnline) {
                                if ($p.isPaired -or $p.isAutoTrusted) {
                                    # Telemetry (WebSocket) feeds battery + wifi for every device
                                    $peerRow = @{
                                        Model    = $p.info.deviceModel
                                        Battery  = if ($null -ne $p.battery) { [string]$p.battery } else { $null }
                                        WifiSsid = $p.wifiSsid
                                        WifiRssi = $p.wifiRssi
                                    }
                                    $adbConnected = ($script:currentTarget -and $p.ip -eq ($script:currentTarget -replace ':.*',''))
                                    $livePeers += @{
                                        Name        = $p.info.alias
                                        SubText     = Get-DeviceSubText -Peer $peerRow
                                        IconGlyph   = "$([char]0xE8EA)"
                                        IP          = $p.ip
                                        Fingerprint = $p.info.fingerprint
                                        ConnectAdbVisibility = if ($adbConnected) { 'Collapsed' } else { 'Visible' }
                                        DisconnectAdbVisibility = if ($adbConnected) { 'Visible' } else { 'Collapsed' }
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
                } finally {
                    Restore-CardEffect
                }
            }
        } catch { }

            # Keep the quick-action mirror toggle in sync with the actual mirror window state
            try {
                $btnMirror = $script:wpfWindow.FindName("btnQAMirror")
                if ($btnMirror -and $null -ne $mirrorActive) {
                    $wanted = [bool]$mirrorActive
                    if ($btnMirror.IsChecked -ne $wanted) { $btnMirror.IsChecked = $wanted }
                }
            } catch {}
        } catch {}
    })
    $mdnsTimer.Start()

if ($Background) {
    Show-Toast -Title "DeX Engine Active" -Message "DeX background engine is active and ready."
}

# Automatic clipboard sync (PC -> phone): push fresh local copies over the WebSocket.
# Controlled by the quick-action clipboard toggle ($script:clipboardSyncEnabled).
# The phone's own pushes are learned from /local/clipboard-state so they are never echoed.
# All the blocking work (clipboard reads + HTTP + adb) runs on the dedicated STA runspace
# (Start-ClipboardSyncWorker); this tick only forwards the toggle, so a slow clipboard
# owner or cold adb server can never stall the UI dispatcher.
$script:clipboardSyncEnabled = $false
$script:clipboardTimer = New-Object System.Windows.Threading.DispatcherTimer
$script:clipboardTimer.Interval = [TimeSpan]::FromSeconds(2)
$script:clipboardTimer.Add_Tick({
    if ($script:clipboardSyncEnabled) { Start-ClipboardSyncWorker }
    # Always forward the toggle (both enable AND disable) so the worker stops when turned off.
    $script:clipWorkerControl.Enqueue(@{ SetEnabled = [bool]$script:clipboardSyncEnabled })
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

$script:cleanExitBlock = {
    try {
        if ($script:mdnsJob -and $script:mdnsJob.PowerShell) {
            $script:mdnsJob.PowerShell.Dispose()
        }
        if ($script:uiPollJob -and $script:uiPollJob.PowerShell) {
            $script:uiPollJob.PowerShell.Dispose()
        }
        if (Get-Command Stop-ClipboardSyncWorker -ErrorAction SilentlyContinue) {
            Stop-ClipboardSyncWorker
        }
    } catch {}
}

[System.Windows.Forms.Application]::add_ApplicationExit($script:cleanExitBlock)
[System.AppDomain]::CurrentDomain.add_ProcessExit($script:cleanExitBlock)

[System.Windows.Forms.Application]::Run()





