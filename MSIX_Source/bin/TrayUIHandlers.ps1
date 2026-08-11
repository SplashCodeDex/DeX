. "$PSScriptRoot\Modules\UIComponents.ps1"
function Reset-SpatialPanels {
    try {
        $script:wpfWindow.FindResource("ExpandMenu").Stop($script:wpfWindow)
        $script:wpfWindow.FindResource("ContractMenu").Stop($script:wpfWindow)
        $script:wpfWindow.FindResource("ExpandSettings").Stop($script:wpfWindow)
        $script:wpfWindow.FindResource("ContractSettings").Stop($script:wpfWindow)
        $script:wpfWindow.FindResource("PopIn").Stop($script:wpfWindow)
    } catch {}

    $script:ce["mainBorder"].Width = [double]::NaN
    $script:ce["mainBorder"].Height = [double]::NaN
    $script:ce["FileExplorer"].Visibility = 'Collapsed'
    $script:ce["FileExplorer"].Opacity = 0
    $script:ce["fileTrans"].X = 150
    $script:ce["SettingsPanel"].Visibility = 'Collapsed'
    $script:ce["SettingsPanel"].Opacity = 0
    $script:ce["settingsTrans"].X = 150
    $script:ce["menuTrans"].X = 0
    $script:ce["btnCloseMenu"].Visibility = 'Collapsed'
    $script:ce["btnCloseMenu"].Opacity = 0
    $script:ce["NearbyExpandPanel"].Visibility = 'Collapsed'
    $script:ce["NearbyExpandPanel"].Opacity = 0
    # Pairing PIN/QR panel: only collapse/clear when NO pairing session is live. An active
    # session must survive window dismissal (click-away): the pairWaitTimer keeps polling
    # in the background, the completion toast still fires, and re-opening the window shows
    # the pairing screen again. The SlideIn/SlideOut holds are the panel's state in that
    # case, so Release-PinAnimations must not run either (it would snap the menu back
    # under the visible panel).
    if (-not $script:activeOutboundPairIp) {
        Release-PinAnimations
        $pinViewPanel = $script:ce["pinViewPanel"]
        if ($pinViewPanel) {
            $pinViewPanel.Visibility = 'Collapsed'
            $pinViewPanel.Opacity = 0
        }
        $pinViewTrans = $script:ce["pinViewTrans"]
        if ($pinViewTrans) { $pinViewTrans.X = 300 }
        $menuContentTrans = $script:ce["menuContentTrans"]
        if ($menuContentTrans) {
            $menuContentTrans.X = 0
            # The PopIn entrance sets menuContentTrans.Y=35 (menuTrans.Y=20, winTrans.Y=15) and
            # animates them to 0 — if the window is hidden mid-animation they freeze non-zero,
            # pushing the menu/pin content down by up to ~70px. Always clear them here.
            $menuContentTrans.Y = 0
        }
        $menuTrans = $script:ce["menuTrans"]
        if ($menuTrans) { $menuTrans.Y = 0 }
        $winTrans = $script:ce["winTrans"]
        if ($winTrans) { $winTrans.Y = 0 }
        $menuContentPanel = $script:ce["menuContentPanel"]
        if ($menuContentPanel) { $menuContentPanel.Opacity = 1 }
        Clear-PairingState
    }
    $script:ce["TopActionsPanel"].Visibility = 'Visible'
    $script:ce["btnUserJoe"].Visibility = 'Visible'
    $script:ce["btnDeviceWindows"].Visibility = 'Visible'
    $script:ce["icLivePeers"].Visibility = 'Visible'
    $btnQAPull = $script:ce["btnQAPull"]
    if ($btnQAPull) { $btnQAPull.IsChecked = $false }
}

function Invoke-ExitEngine {
    # Edge Case 20: Job and process cleanup on exit
    Get-Job | ForEach-Object { try { Stop-Job $_; Remove-Job $_ } catch {} }
    if ($script:adbLsProc -and -not $script:adbLsProc.HasExited) {
        try { $script:adbLsProc.Kill() } catch {}
    }
    $script:wpfWindow.Hide()
    $script:notifyIcon.Visible = $false
    $script:notifyIcon.Dispose()
    Stop-Process -Name "adb", "scrcpy", "DeXShareTarget" -ErrorAction SilentlyContinue
    [System.Windows.Forms.Application]::Exit()
}

function Get-ConnectedDeviceTarget {
    $statusText = $script:txtStatus.Text
    if ($statusText -match "Connected:\s*(.+)") { return $Matches[1] }
    $devicesOutput = adb devices 2>&1
    $connectedDevice = ($devicesOutput | Where-Object { $_ -match ':5555\s+device' })
    if (-not $connectedDevice) { $connectedDevice = ($devicesOutput | Where-Object { $_ -match '\bdevice\b' -and $_ -notmatch 'List of devices' }) }
    $connectedDevice = $connectedDevice | Select-Object -First 1
    if ($connectedDevice) { return $connectedDevice.Split()[0].Trim() }
    return $null
}

# Runs `adb devices -l` asynchronously (spawn + poll) and feeds the result into Update-WpfUI,
# so a cold adb server (first call can take seconds) can never block the UI thread during a
# tray action. Mirrors the non-blocking pattern used by the tray-icon click handler.
function Update-WpfUIAsync {
    try {
        $proc = New-Object System.Diagnostics.Process
        $proc.StartInfo.FileName = "adb.exe"
        $proc.StartInfo.Arguments = "devices -l"
        $proc.StartInfo.UseShellExecute = $false
        $proc.StartInfo.RedirectStandardOutput = $true
        $proc.StartInfo.CreateNoWindow = $true
        $proc.Start() | Out-Null

        $timer = New-Object System.Windows.Threading.DispatcherTimer
        $timer.Interval = [TimeSpan]::FromMilliseconds(50)
        $timer.Add_Tick({
            if ($proc.HasExited) {
                $timer.Stop()
                try {
                    $out = $proc.StandardOutput.ReadToEnd() -split "`r?`n"
                    Update-WpfUI -DevicesOutput $out
                } catch {}
                $proc.Dispose()
            }
        })
        $timer.Start()
    } catch { Write-Trace "Update-WpfUIAsync error: $_" }
}
$actionConnect = {
    $res = Invoke-AdbConnect
    if ($res.Success) {
        $script:currentTarget = $res.Target
        $script:notifyIcon.Icon = $iconConnected
        $script:notifyIcon.Text = "Connected: $($res.Name)"
        $script:txtStatus.Text = "ADB Status: $($res.Name)"
        try { $script:topActionsPanel.FindResource("ShowAdbAnim").Begin($script:wpfWindow) } catch {}
        Show-Toast -Title "ADB Connected" -Message "Successfully connected to $($res.Name)"
    } else {
        $script:notifyIcon.Icon = $iconDisconnected
        $script:notifyIcon.Text = "Disconnected"
        $script:txtStatus.Text = "ADB Status: $($res.Message)"
        try { $script:topActionsPanel.FindResource("ShowAdbAnim").Begin($script:wpfWindow) } catch {}
        Show-Toast -Title "Connection Failed" -Message $res.Message
    }
    Update-WpfUIAsync
}
$actionDisconnect = {
    $null = adb disconnect 2>&1
    $script:notifyIcon.Icon = $iconDisconnected
    $script:notifyIcon.Text = "Connect ADB: Disconnected"
    $script:txtStatus.Text = "ADB Status: Disconnected"
    try { $script:topActionsPanel.FindResource("HideAdbAnim").Begin($script:wpfWindow) } catch {}
    Show-Toast -Title "ADB Disconnected" -Message "Severed all wireless connections."
    Update-WpfUIAsync
}


$actionMirror = {
    # Quick-action toggle: start/stop the ADB-free screen mirror
    $mirrorActive = $false
    try {
        $st = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/mirror-state" -TimeoutSec 2 -ErrorAction Stop
        $mirrorActive = [bool]$st.active
    } catch {}
    
    if ($mirrorActive) {
        # Stop: close the mirror window; the server tells the phone to stop streaming
        try { Invoke-RestMethod -Uri "$global:DeXLocalApi/local/mirror-stop" -Method Post -TimeoutSec 2 -ErrorAction SilentlyContinue | Out-Null } catch {}
        Show-Toast -Title "Mirroring Stopped" -Message "Screen mirror closed."
    } else {
        # Start: resolve the active device (ADB target first, else the first paired device)
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
        if ($ip) {
            Start-MirrorSession -Ip $ip
        } else {
            Show-Toast -Title "Mirror Failed" -Message "No device connected. Open the DeX app on the phone."
        }
    }
    Update-WpfUIAsync
}

$actionPull = {
    
    if ($script:ce["FileExplorer"].Visibility -eq 'Visible') {
        $sb = $script:wpfWindow.Resources["ContractMenu"].Clone()
        $sb.Children[2].By = $null
        $sb.Children[2].To = if ($script:contractedWidth) { $script:contractedWidth } else { 300 }
        $sb.Children[3].By = $null
        $sb.Children[3].To = if ($script:contractedHeight) { $script:contractedHeight } else { 500 }
        Start-CardTransition $sb
        Restore-ExpandPosition
        $btnQAPull = $script:ce["btnQAPull"]
        if ($btnQAPull) { $btnQAPull.IsChecked = $false }
        return
    }
    
    $settingsPanel = $script:ce["SettingsPanel"]
    $isSwapping = ($settingsPanel -and $settingsPanel.Visibility -eq 'Visible')
    if ($isSwapping) {
        # Swap from Settings to File Explorer: instantly restore the window
        # to its pre-expand position so the next Nudge-ForExpand starts fresh.
        if ($null -ne $script:preExpandLeft) { $script:wpfWindow.Left = $script:preExpandLeft }
        if ($null -ne $script:preExpandTop)  { $script:wpfWindow.Top  = $script:preExpandTop }
        $script:preExpandLeft = $null; $script:preExpandTop = $null

        $settingsPanel.Visibility = 'Collapsed'
        $settingsPanel.Opacity = 0
        $script:ce["settingsTrans"].X = 150
    }

    $mainBorder = $script:ce["mainBorder"]
    if (-not $script:contractedWidth) { $script:contractedWidth = $mainBorder.ActualWidth }
    if (-not $script:contractedHeight) { $script:contractedHeight = $mainBorder.ActualHeight }
    if ([double]::IsNaN($mainBorder.Width)) { $mainBorder.Width = $mainBorder.ActualWidth }
    if ([double]::IsNaN($mainBorder.Height)) { $mainBorder.Height = $mainBorder.ActualHeight }

    # Pick expansion direction based on screen position — flips alignment
    # and nudges the window so the expanded panel never flies off-screen.
    Nudge-ForExpand 754 195

    $sb = $script:wpfWindow.Resources["ExpandMenu"].Clone()
    if ($isSwapping) {
        14, 13, 12, 11, 10, 9, 8, 7 | ForEach-Object { $sb.Children.RemoveAt($_) }
    }
    $sb.Children[0].By = $null
    $sb.Children[0].To = $script:contractedWidth + 754
    $sb.Children[1].By = $null
    $sb.Children[1].To = $script:contractedHeight + 195
    Start-CardTransition $sb
    
    $btnQAPull = $script:ce["btnQAPull"]
    if ($btnQAPull) { $btnQAPull.IsChecked = $true }
    
    $outDir = if ($script:customDownloadPath) { $script:customDownloadPath } else { Join-Path $env:USERPROFILE "Downloads\DeX" }
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    $script:wpfWindow.Dispatcher.InvokeAsync([Action]{ Load-Directory $outDir }) | Out-Null
}

$actionClipboard = {
    # Quick-action toggle: start/stop the automatic 2-way clipboard sync
    $script:clipboardSyncEnabled = -not $script:clipboardSyncEnabled
    
    $btnQAClipboard = $script:ce["btnQAClipboard"]
    if ($btnQAClipboard) {
        $btnQAClipboard.IsChecked = $script:clipboardSyncEnabled
        $btnQAClipboard.ToolTip = if ($script:clipboardSyncEnabled) { "Clipboard Sync: On" } else { "Clipboard Sync: Off" }
    }
    
    # Tell the backend whether to accept phone clipboard pushes (stops the phone -> PC direction)
    try {
        Invoke-RestMethod -Uri "$global:DeXLocalApi/local/clipboard-sync?enabled=$($script:clipboardSyncEnabled)" -Method Post -TimeoutSec 2 -ErrorAction SilentlyContinue | Out-Null
    } catch {}
    
    if ($script:clipboardSyncEnabled) {
        Show-Toast -Title "Clipboard Sync On" -Message "Phone and PC clipboards will auto-sync."
    } else {
        Show-Toast -Title "Clipboard Sync Off" -Message "Automatic clipboard sync stopped."
    }
}

$actionAuto = {
    $newState = -not (Get-AutoConnectStatus)
    Set-AutoConnectStatus -Enable $newState
    if ($newState) {
        Show-Toast -Title "Auto-Connect Enabled" -Message "Will auto-connect whenever PC joins phone hotspot."
        if (-not $script:mdnsJob) {
            $script:mdnsJob = Start-MdnsDiscovery -Queue $script:mdnsQueue
        }
    } else {
        Show-Toast -Title "Auto-Connect Disabled" -Message "Auto-connection trigger removed."
        if ($script:mdnsJob -and $script:mdnsJob.PowerShell) {
            $script:mdnsJob.PowerShell.Dispose()
            $script:mdnsJob = $null
        }
    }
    Update-WpfUI
}

# Settings Panel Toggle (avatar click expands/contracts settings)
$actionSettings = {
    $settingsPanel = $script:ce["SettingsPanel"]
    $fileExplorer = $script:ce["FileExplorer"]
    
    # If settings is already visible, contract it
    if ($settingsPanel.Visibility -eq 'Visible') {
        $sb = $script:wpfWindow.Resources["ContractSettings"].Clone()
        $sb.Children[2].By = $null
        $sb.Children[2].To = if ($script:contractedWidth) { $script:contractedWidth } else { 300 }
        $sb.Children[3].By = $null
        $sb.Children[3].To = if ($script:contractedHeight) { $script:contractedHeight } else { 500 }
        Start-CardTransition $sb
        Restore-ExpandPosition
        return
    }

    $fileExplorer = $script:ce["FileExplorer"]
    $isSwapping = ($fileExplorer -and $fileExplorer.Visibility -eq 'Visible')
    
    # If file explorer is visible, collapse it first then fall through to expand settings
    if ($isSwapping) {
        # Instantly restore pre-expand position so the next nudge starts fresh.
        if ($null -ne $script:preExpandLeft) { $script:wpfWindow.Left = $script:preExpandLeft }
        if ($null -ne $script:preExpandTop)  { $script:wpfWindow.Top  = $script:preExpandTop }
        $script:preExpandLeft = $null; $script:preExpandTop = $null

        $fileExplorer.Visibility = 'Collapsed'
        $fileExplorer.Opacity = 0
        $script:ce["fileTrans"].X = 150
        $btnQAPull = $script:ce["btnQAPull"]
        if ($btnQAPull) { $btnQAPull.IsChecked = $false }
    }

    $mainBorder = $script:ce["mainBorder"]
    if (-not $script:contractedWidth) { $script:contractedWidth = $mainBorder.ActualWidth }
    if (-not $script:contractedHeight) { $script:contractedHeight = $mainBorder.ActualHeight }
    if ([double]::IsNaN($mainBorder.Width)) { $mainBorder.Width = $mainBorder.ActualWidth }
    if ([double]::IsNaN($mainBorder.Height)) { $mainBorder.Height = $mainBorder.ActualHeight }

    # Settings panel width is 675 (not contractedWidth + 754). Use the actual delta.
    Nudge-ForExpand (675 - $script:contractedWidth) 195

    $sb = $script:wpfWindow.Resources["ExpandSettings"].Clone()
    if ($isSwapping) {
        14, 13, 12, 11, 10, 9, 8, 7 | ForEach-Object { $sb.Children.RemoveAt($_) }
    }
    $sb.Children[0].By = $null
    $sb.Children[0].To = 675
    $sb.Children[1].By = $null
    $sb.Children[1].To = $script:contractedHeight + 195
    Start-CardTransition $sb
    

    
    # Update auto-connect badge
    $txtBadge = $script:ce["txtBadgeAutoConnect"]
    $badge = $script:ce["badgeAutoConnect"]
    if ($txtBadge -and $badge) {
        $isEnabled = Get-AutoConnectStatus
        $txtBadge.Text = if ($isEnabled) { "ON" } else { "OFF" }
        if ($isEnabled) {
            $badge.Background = $script:wpfWindow.FindResource("SecondaryBrush")
            $txtBadge.Foreground = $script:wpfWindow.FindResource("SecondaryForegroundBrush")
        } else {
            $badge.Background = $script:wpfWindow.FindResource("DangerBrush")
            $txtBadge.Foreground = [System.Windows.Media.Brushes]::White
        }
    }
    
    # Update download path
    $txtDlPath = $script:ce["txtSettingsDownloadPath"]
    if ($txtDlPath) {
        $path = if ($script:customDownloadPath) { $script:customDownloadPath } else { "Downloads\DeX" }
        $txtDlPath.Text = $path
    }
}

$btnTopProfile = $script:ce["btnProfileTop"]
$btnProfileBottom = $script:ce["btnProfileBottom"]
$btnProfileTopSettings = $script:ce["btnProfileTopSettings"]

# Avatar clicks now open the settings panel instead of the popup

function Show-PairingPrompt {
    param([string]$IPPort)
    
    $xaml = @"
    <Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
            xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
            Title="Pair Device" Width="400" Height="220" WindowStartupLocation="CenterScreen"
            Background="{DynamicResource MenuBackgroundGradient}" Foreground="{DynamicResource PrimaryTextBrush}" 
            WindowStyle="None" Topmost="True" ResizeMode="NoResize"
            BorderBrush="{DynamicResource MenuBorderBrush}" BorderThickness="1" AllowsTransparency="True">
        <Window.Resources>
            <Style TargetType="Button">
                <Setter Property="Background" Value="{DynamicResource MenuBackgroundBrush}"/>
                <Setter Property="Foreground" Value="{DynamicResource PrimaryTextBrush}"/>
                <Setter Property="BorderBrush" Value="{DynamicResource MenuBorderBrush}"/>
                <Setter Property="Template">
                    <Setter.Value>
                        <ControlTemplate TargetType="Button">
                            <Border Background="{TemplateBinding Background}" CornerRadius="6" BorderBrush="{TemplateBinding BorderBrush}" BorderThickness="1">
                                <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/>
                            </Border>
                        </ControlTemplate>
                    </Setter.Value>
                </Setter>
                <Style.Triggers>
                    <Trigger Property="IsMouseOver" Value="True">
                        <Setter Property="Background" Value="{DynamicResource HoverBackgroundBrush}"/>
                    </Trigger>
                </Style.Triggers>
            </Style>
        </Window.Resources>
        <Grid Margin="20">
            <Grid.RowDefinitions>
                <RowDefinition Height="Auto"/>
                <RowDefinition Height="Auto"/>
                <RowDefinition Height="Auto"/>
                <RowDefinition Height="*"/>
            </Grid.RowDefinitions>
            <TextBlock Text="Pair New Device (mDNS)" FontWeight="Bold" FontSize="18" Foreground="{DynamicResource BrandBrush}" Grid.Row="0" Margin="0,0,0,5"/>
            <TextBlock Text="IP: $IPPort" FontSize="13" Foreground="{DynamicResource SecondaryTextBrush}" Grid.Row="1" Margin="0,0,0,15"/>
            <StackPanel Grid.Row="2">
                <TextBlock Text="Enter 6-digit Wi-Fi pairing code:" FontSize="13" Margin="0,0,0,5" Foreground="{DynamicResource PrimaryTextBrush}"/>
                <TextBox x:Name="txtPin" Height="34" FontSize="18" Background="{DynamicResource MenuBackgroundBrush}" Foreground="{DynamicResource PrimaryTextBrush}" 
                         BorderThickness="1" BorderBrush="{DynamicResource MenuBorderBrush}" Padding="5,4,0,0" VerticalContentAlignment="Center" MaxLength="6"/>
            </StackPanel>
            <StackPanel Orientation="Horizontal" HorizontalAlignment="Right" Grid.Row="3" Margin="0,20,0,0">
                <Button x:Name="btnCancel" Content="Cancel" Width="90" Height="32" Margin="0,0,10,0"/>
                <Button x:Name="btnPair" Content="Pair" Width="90" Height="32" Background="{DynamicResource BrandBrush}" Foreground="White" BorderThickness="0"/>
            </StackPanel>
        </Grid>
    </Window>
"@
    
    $reader = New-Object System.Xml.XmlNodeReader ([xml]$xaml)
    $win = [System.Windows.Markup.XamlReader]::Load($reader)
    
    # Inherit theme dictionaries from main window
    foreach ($dict in $script:wpfWindow.Resources.MergedDictionaries) {
        $win.Resources.MergedDictionaries.Add($dict)
    }
    
    $txtPin = $win.FindName("txtPin")
    $btnCancel = $win.FindName("btnCancel")
    $btnPair = $win.FindName("btnPair")
    
    # Reset the out-param BEFORE showing the dialog. The function returns the script-scope
    # variable, so a stale value from a previous attempt would otherwise be returned when
    # the user cancels (re-pairing with an old PIN and re-prompting on every tick).
    $script:resultPin = $null
    
    $btnCancel.Add_Click({
        $win.DialogResult = $false
        $win.Close()
    })
    
    $btnPair.Add_Click({
        $script:resultPin = $txtPin.Text.Trim()
        $win.DialogResult = $true
        $win.Close()
    })
    
    # Handle Drag to move
    $win.Add_MouseLeftButtonDown({ $win.DragMove() })
    
    $null = $win.ShowDialog()
    return $script:resultPin
}

$actionPushFiles = {
    try {
        Add-Type -AssemblyName PresentationFramework
        $script:wpfWindow.Dispatcher.Invoke([Action]{
            $dlg = New-Object Microsoft.Win32.OpenFileDialog
            $dlg.Multiselect = $true
            $dlg.Title = "Select files to send to Android"
            if ($null -eq $dlg) { return }
            $result = $dlg.ShowDialog()
            if ($result -eq $true) {
                $targetIp = (Get-ItemProperty "HKCU:\Software\DeX" -Name "LastIp" -ErrorAction SilentlyContinue).LastIp
                if ([string]::IsNullOrEmpty($targetIp)) { $targetIp = "127.0.0.1" }
                $exePath = Join-Path $PSScriptRoot "..\DeXShareTarget.exe"
                $argsList = @("-IP", $targetIp) + $dlg.FileNames
                Start-Process $exePath -ArgumentList $argsList
            }
        })
    } finally {
        $btn = $script:ce["btnPushFiles"]
        if ($btn) { $btn.IsChecked = $false }
    }
}

$actionPushFolder = {
    try {
        Add-Type -AssemblyName System.Windows.Forms
        $script:wpfWindow.Dispatcher.Invoke([Action]{
            $dlg = New-Object System.Windows.Forms.FolderBrowserDialog
            $dlg.Description = "Select a folder to send to Android"
            if ($null -eq $dlg) { return }
            $result = $dlg.ShowDialog()
            if ($result -eq [System.Windows.Forms.DialogResult]::OK) {
                $targetIp = (Get-ItemProperty "HKCU:\Software\DeX" -Name "LastIp" -ErrorAction SilentlyContinue).LastIp
                if ([string]::IsNullOrEmpty($targetIp)) { $targetIp = "127.0.0.1" }
                $files = Get-ChildItem -Path $dlg.SelectedPath -File -Recurse | Select-Object -ExpandProperty FullName
                if ($files.Count -eq 0) { return }
                $exePath = Join-Path $PSScriptRoot "..\DeXShareTarget.exe"
                $argsList = @("-IP", $targetIp) + $files
                Start-Process $exePath -ArgumentList $argsList
            }
        })
    } finally {
        $btn = $script:ce["btnPushFolder"]
        if ($btn) { $btn.IsChecked = $false }
    }
}


$fileExplorerPanel = $script:ce["FileExplorer"]
if ($fileExplorerPanel) {

}

