
function Load-TrayIcon([string]$FileName) {
    $binRoot = Split-Path $PSScriptRoot -Parent
    $iconPath = Join-Path $binRoot $FileName
    if (Test-Path $iconPath) {
        return [System.Drawing.Icon]::new($iconPath)
    }
    return $null
}
#Export-ModuleMember -Function Load-TrayIcon

function Show-Toast {
    param([string]$Title, [string]$Message)
    try {
        [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
        [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom, ContentType = WindowsRuntime] | Out-Null
        $escTitle = [System.Security.SecurityElement]::Escape($Title)
        $escMsg = [System.Security.SecurityElement]::Escape($Message)
        $xmlString = @"
<toast>
  <visual>
    <binding template="ToastGeneric">
      <text>$escTitle</text>
      <text>$escMsg</text>
      <image placement="appLogoOverride" hint-crop="none" src="file:///$((Split-Path $PSScriptRoot -Parent) -replace '\\', '/')/app-icon.ico"/>
    </binding>
  </visual>
</toast>
"@
        $xml = New-Object Windows.Data.Xml.Dom.XmlDocument
        $xml.LoadXml($xmlString)
        $toast = [Windows.UI.Notifications.ToastNotification]::new($xml)
        $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier("Connect ADB")
        $notifier.Show($toast)
    } catch {}
}
#Export-ModuleMember -Function Show-Toast

function Set-AppTheme {
    param([string]$ThemeName)
    $binRoot = Split-Path $PSScriptRoot -Parent
    $themePath = Join-Path $binRoot "..\Themes\$ThemeName.xaml"
    if (Test-Path $themePath) {
        $xmlReader = [System.Xml.XmlReader]::Create($themePath)
        $newDict = [System.Windows.Markup.XamlReader]::Load($xmlReader)
        $xmlReader.Close()
        
        $appStylesPath = Join-Path $binRoot "..\Themes\AppStyles.xaml"
        $appStylesDict = $null
        if (Test-Path $appStylesPath) {
            $xmlReader2 = [System.Xml.XmlReader]::Create($appStylesPath)
            $appStylesDict = [System.Windows.Markup.XamlReader]::Load($xmlReader2)
            $xmlReader2.Close()
        }

        $script:wpfWindow.Resources.MergedDictionaries.Clear()
        if ($appStylesDict) {
            $script:wpfWindow.Resources.MergedDictionaries.Add($appStylesDict)
        }
        $script:wpfWindow.Resources.MergedDictionaries.Add($newDict)
        $global:CurrentTheme = $ThemeName

        $txtTheme = $script:wpfWindow.FindName("txtSettingsTheme")
        if ($txtTheme) {
            $txtTheme.Text = if ($ThemeName -eq "DarkTheme") { "Dark" } else { "Light" }
        }
        try {
            $themeFile = Join-Path $global:DeXDataRoot "theme.json"
            @{ CurrentTheme = $global:CurrentTheme; AppThemeMode = $global:AppThemeMode } | ConvertTo-Json -Depth 2 | Set-Content -Path $themeFile -Force -ErrorAction SilentlyContinue
        } catch {}
    }
}
#Export-ModuleMember -Function Set-AppTheme

function Get-SystemTheme {
    try {
        $regKey = "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Themes\Personalize"
        $val = Get-ItemPropertyValue -Path $regKey -Name "AppsUseLightTheme" -ErrorAction SilentlyContinue
        if ($val -eq 1) { return "LightTheme" }
    } catch {}
    return "DarkTheme"
}
#Export-ModuleMember -Function Get-SystemTheme

function Show-DownloadDockToast([string]$pathText) {
    $dock = $script:wpfWindow.FindName("dockDownloadToast")
    $txt = $script:wpfWindow.FindName("txtDownloadToast")
    if ($null -ne $dock -and $null -ne $txt) {
        # Edge Case 23: Truncate long path text with middle ellipsis while preserving full path in ToolTip
        $dispText = $pathText
        if ($dispText.Length -gt 35) {
            $dispText = $dispText.Substring(0, 15) + "..." + $dispText.Substring($dispText.Length - 15)
        }
        $txt.Text = "Saved to $dispText"
        $txt.ToolTip = $pathText
        $dock.Visibility = 'Visible'
        
        $tg = $dock.RenderTransform
        $dockScale = $null
        $dockTrans = $null
        if ($tg -is [System.Windows.Media.TransformGroup]) {
            $dockScale = $tg.Children[0]
            $dockTrans = $tg.Children[1]
        }
        
        # Bouncy BackEase overshoot for dynamic spring entrance
        $bouncyEase = New-Object System.Windows.Media.Animation.BackEase
        $bouncyEase.Amplitude = 0.6
        $bouncyEase.EasingMode = [System.Windows.Media.Animation.EasingMode]::EaseOut
        
        $daOp = New-Object System.Windows.Media.Animation.DoubleAnimation
        $daOp.To = 1.0
        $daOp.Duration = [TimeSpan]::FromSeconds(0.25)
        $dock.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $daOp)
        
        if ($null -ne $dockTrans) {
            $daY = New-Object System.Windows.Media.Animation.DoubleAnimation
            $daY.To = 0.0
            $daY.Duration = [TimeSpan]::FromSeconds(0.45)
            $daY.EasingFunction = $bouncyEase
            $dockTrans.BeginAnimation([System.Windows.Media.TranslateTransform]::YProperty, $daY)
        }
        if ($null -ne $dockScale) {
            $daS = New-Object System.Windows.Media.Animation.DoubleAnimation
            $daS.To = 1.0
            $daS.Duration = [TimeSpan]::FromSeconds(0.45)
            $daS.EasingFunction = $bouncyEase
            $dockScale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleXProperty, $daS)
            $dockScale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleYProperty, $daS)
        }
        
        if ($null -ne $script:dockTimer) { $script:dockTimer.Stop() }
        $script:dockTimer = New-Object System.Windows.Threading.DispatcherTimer
        $script:dockTimer.Interval = [TimeSpan]::FromSeconds(4)
        $script:dockTimer.Add_Tick({
            $script:dockTimer.Stop()
            
            $easeIn = New-Object System.Windows.Media.Animation.CubicEase
            $easeIn.EasingMode = [System.Windows.Media.Animation.EasingMode]::EaseIn
            
            $fadeOut = New-Object System.Windows.Media.Animation.DoubleAnimation
            $fadeOut.To = 0.0
            $fadeOut.Duration = [TimeSpan]::FromSeconds(0.35)
            $fadeOut.EasingFunction = $easeIn
            $fadeOut.Add_Completed({
                $dock.Visibility = 'Collapsed'
            })
            
            $dock.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $fadeOut)
            if ($null -ne $dockTrans) {
                $daYExit = New-Object System.Windows.Media.Animation.DoubleAnimation
                $daYExit.To = 25.0
                $daYExit.Duration = [TimeSpan]::FromSeconds(0.35)
                $daYExit.EasingFunction = $easeIn
                $dockTrans.BeginAnimation([System.Windows.Media.TranslateTransform]::YProperty, $daYExit)
            }
            if ($null -ne $dockScale) {
                $daSExit = New-Object System.Windows.Media.Animation.DoubleAnimation
                $daSExit.To = 0.8
                $daSExit.Duration = [TimeSpan]::FromSeconds(0.35)
                $daSExit.EasingFunction = $easeIn
                $dockScale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleXProperty, $daSExit)
                $dockScale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleYProperty, $daSExit)
            }
        })
        
        # Edge Case 19: Pause auto-hide while mouse hovers over dock
        $dock.Add_MouseEnter({
            if ($null -ne $script:dockTimer) { $script:dockTimer.Stop() }
        })
        $dock.Add_MouseLeave({
            if ($null -ne $script:dockTimer) {
                $script:dockTimer.Stop()
                $script:dockTimer.Start()
            }
        })
        
        $script:dockTimer.Start()
    }
}
#Export-ModuleMember -Function Show-DownloadDockToast

function Invoke-MenuAction([scriptblock]$Action) {
    & $Action
}
#Export-ModuleMember -Function Invoke-MenuAction

function Update-WpfUI {
    param([string[]]$DevicesOutput)
    
    trap {
        Write-Trace "Update-WpfUI Trap: $($_.Exception.Message) at line $($_.InvocationInfo.ScriptLineNumber)"
        continue
    }
    
    if (-not $DevicesOutput) {
        $DevicesOutput = adb devices -l 2>&1
    }

    $brushConverter = New-Object System.Windows.Media.BrushConverter
    
    $connectedDevice = ($DevicesOutput | Where-Object { $_ -match ':5555\s+device' })
    if (-not $connectedDevice) { $connectedDevice = ($DevicesOutput | Where-Object { $_ -match '\bdevice\b' -and $_ -notmatch 'List of devices' }) }
    $connectedDevice = $connectedDevice | Select-Object -First 1

    $mainBorder = $script:wpfWindow.FindName("mainBorder")
    $oldWidth = 0; $oldHeight = 0
    if ($null -ne $mainBorder) {
        $oldWidth = $mainBorder.ActualWidth
        $oldHeight = $mainBorder.ActualHeight
    }
    
    if ($connectedDevice) {
        $target = $connectedDevice.Split()[0].Trim()
        $devName = $target
        if ($connectedDevice -match 'model:([^\s]+)') {
            $devName = $Matches[1] -replace '_', ' '
        }
        $script:currentTarget = $target
        $script:notifyIcon.Icon = $iconConnected
        $script:notifyIcon.Text = "Connected: $devName"
        $script:txtStatus.Text = "ADB Status: $devName"
        try { $script:topActionsPanel.FindResource("ShowAdbAnim").Begin($script:wpfWindow) } catch {}
        $btnQAConnect = $script:wpfWindow.FindName("btnQAConnect")
        if ($null -ne $btnQAConnect) { $btnQAConnect.IsChecked = $true }
        $script:wpfWindow.FindName("btnCopyIP").Visibility = 'Visible'
    } else {
        $script:notifyIcon.Icon = $iconDisconnected
        $script:notifyIcon.Text = "Disconnected"
        $script:txtStatus.Text = "ADB Status: Disconnected"
        try { $script:topActionsPanel.FindResource("HideAdbAnim").Begin($script:wpfWindow) } catch {}
        $btnQAConnect = $script:wpfWindow.FindName("btnQAConnect")
        if ($null -ne $btnQAConnect) { $btnQAConnect.IsChecked = $false }
        $script:wpfWindow.FindName("btnCopyIP").Visibility = 'Collapsed'
    }
}
#Export-ModuleMember -Function Update-WpfUI

# The DropShadowEffect on mainBorder is the priciest per-frame cost in the whole UI: on a
# layered (AllowsTransparency) window it re-rasterizes the full card region on every
# repaint. Suspending it around size/scale transitions AND around bulk list swaps (which
# repaint the card) keeps the visuals identical while removing the re-blur cost on each.
# Idempotent flag (not a counter): overlapping suspenders (a transition + a refresh) share
# one suspend, and the FIRST restorer re-applies the shadow — so rapid double transitions
# can never leave the shadow permanently off.
$script:cardShadowSuspended = $false
function Suspend-CardEffect {
    if ($script:cardShadowSuspended) { return }
    $mb = $script:wpfWindow.FindName("mainBorder")
    if ($mb -and $null -ne $mb.Effect) { $mb.Effect = $null }
    $script:cardShadowSuspended = $true
}
function Restore-CardEffect {
    if (-not $script:cardShadowSuspended) { return }
    $script:cardShadowSuspended = $false
    $mb = $script:wpfWindow.FindName("mainBorder")
    if ($mb -and $null -eq $mb.Effect) {
        try { $mb.Effect = $script:wpfWindow.FindResource("MainShadow") } catch {}
    }
}

# Begins a card resize/scale transition (Expand/Contract/PopIn) with the software drop shadow
# suspended for its duration. The DropShadowEffect is the priciest per-frame cost while
# mainBorder animates its size/scale, so we clear it up front and restore it ~0.95s later.
# NOTE: the restore is inlined (not a Restore-CardEffect call) because GetNewClosure()
# creates an isolated scope that cannot reliably resolve functions from the dot-sourced
# parent script — a call here threw "not recognized" at runtime.
$script:cardShadowRestoreTimer = $null
function Start-CardTransition($storyboard) {
    Suspend-CardEffect

    if ($null -ne $script:cardShadowRestoreTimer) { $script:cardShadowRestoreTimer.Stop() }
    $timer = New-Object System.Windows.Threading.DispatcherTimer
    $timer.Interval = [TimeSpan]::FromMilliseconds(950)
    $timer.Add_Tick({
        param($s, $e)
        $s.Stop()
        if ($script:cardShadowSuspended) {
            $script:cardShadowSuspended = $false
            $mb = $script:wpfWindow.FindName("mainBorder")
            if ($mb -and $null -eq $mb.Effect) {
                try { $mb.Effect = $script:wpfWindow.FindResource("MainShadow") } catch {}
            }
        }
    }.GetNewClosure())
    $script:cardShadowRestoreTimer = $timer
    $timer.Start()

    $storyboard.Begin($script:wpfWindow, $true)
}

# Resolves the PC's LAN IP and loads the pairing QR code into the panel, showing the QR
# view (hiding the PIN view) and arming the "Request PIN" button. Returns $true when the
# QR is shown, or $false when no LAN IP could be resolved (caller decides how to react).
# The QR bitmap is fetched OFF the UI thread: the old code built a BitmapImage with
# CacheOption=OnLoad + an HTTP UriSource, which downloads synchronously on the calling
# thread (the engine runs on Windows PowerShell 5.1 / .NET Framework, where that WebRequest
# has a 100-second default timeout) — any stall froze the whole tray UI on every
# discovered-device click.
function Show-QrCode {
    $localIp = $null
    try {
        $dnsTask = [System.Net.Dns]::GetHostAddressesAsync([System.Net.Dns]::GetHostName())
        if (-not $dnsTask.Wait(2000)) { return $false }
        $localIp = $dnsTask.Result |
            Where-Object { $_.AddressFamily -eq 'InterNetwork' -and -not [System.Net.IPAddress]::IsLoopback($_) } |
            Select-Object -First 1 -ExpandProperty IPAddressToString
    } catch { return $false }
    if (-not $localIp) { return $false }

    $script:wpfWindow.FindName("pinCodeContent").Visibility = 'Collapsed'
    $script:wpfWindow.FindName("qrCodeContent").Visibility = 'Visible'
    $txtQrBtnIcon = $script:wpfWindow.FindName("txtQrBtnIcon")
    if ($txtQrBtnIcon) { $txtQrBtnIcon.Visibility = 'Collapsed' }
    $txtQrBtnText = $script:wpfWindow.FindName("txtQrBtnText")
    if ($txtQrBtnText) { $txtQrBtnText.Text = "Request PIN" }

    # Fetch the PNG over HTTP on a background thread (bounded 3s), then set the image on
    # the UI thread from the byte stream. The QR view is already shown; a slow/failed
    # fetch degrades to an empty QR frame instead of freezing the app.
    $action = [System.Action]{
        try {
            $bytes = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/qr?ip=$localIp" -TimeoutSec 3 -ErrorAction Stop
            if ($bytes -is [byte[]] -and $bytes.Length -gt 0) {
                $ms = New-Object System.IO.MemoryStream(,$bytes)
                $uiAction = [System.Action]{
                    try {
                        $imgQrCode = $script:wpfWindow.FindName("imgQrCode")
                        if ($imgQrCode) {
                            $bitmap = New-Object System.Windows.Media.Imaging.BitmapImage
                            $bitmap.BeginInit()
                            $bitmap.CacheOption = [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
                            $bitmap.StreamSource = $ms
                            $bitmap.EndInit()
                            $bitmap.Freeze()
                            $imgQrCode.Source = $bitmap
                        }
                    } catch {} finally {
                        if ($ms) { $ms.Dispose() }
                    }
                }
                $script:wpfWindow.Dispatcher.InvokeAsync($uiAction) | Out-Null
            }
        } catch {}
    }
    $null = $action.BeginInvoke($null, $null)
    return $true
}

# Shows the PIN pairing panel, runs the 60s progress animation, and starts the 1s status
# poll until the pairing is accepted, rejected, or times out. Centralizes the PIN display +
# poll timer shared by the outbound (Start-PinPairing) and phone-initiated (Connect-Engine)
# flows. Callers must set $script:activeOutboundPairIp / $script:activeOutboundPairFp first.
# Behavior switches preserve each flow's exact button/layout/toast differences.
function Show-PinPanel {
    param(
        [string]$Title,
        [string]$Code,
        [string]$Status,
        # Show the QR-toggle button (btnSettingsQrCode). Outbound pairing shows it; phone-initiated hides it.
        [switch]$ShowQrToggle,
        # Collapse btnPinAccept/btnPinAcceptOnce. Outbound pairing hides them.
        [switch]$HideAcceptButtons,
        # Collapse pinViewPanel when the pairing completes. Phone-initiated flow does this;
        # the outbound flow relies on the slide-out animation only.
        [switch]$HidePanelOnTerminal,
        [string]$SuccessMessage,
        [string]$FailureMessage
    )
    $w = $script:wpfWindow
    $w.FindName("txtPinTitle").Text = $Title
    $w.FindName("txtPinCode").Text = $Code
    $w.FindName("txtPinStatus").Text = $Status
    $w.FindName("qrCodeContent").Visibility = 'Collapsed'
    $w.FindName("pinCodeContent").Visibility = 'Visible'
    if ($HideAcceptButtons) {
        $w.FindName("btnPinAccept").Visibility = 'Collapsed'
        $w.FindName("btnPinAcceptOnce").Visibility = 'Collapsed'
    }
    $w.FindName("btnSettingsQrCode").Visibility = if ($ShowQrToggle) { 'Visible' } else { 'Collapsed' }
    $w.FindName("btnPinCancel").Visibility = 'Visible'
    $w.FindName("txtQrBtnIcon").Visibility = 'Visible'
    $w.FindName("txtQrBtnText").Text = "QR CODE"
    $w.FindName("pinViewPanel").Visibility = 'Visible'
    try { $w.FindName("menuViewsContainer").FindResource("SlideInPinAnim").Begin($w) } catch {}

    $pb = $w.FindName("pbPinTimeout")
    if ($pb) {
        $anim = New-Object System.Windows.Media.Animation.DoubleAnimation
        $anim.From = 100; $anim.To = 0; $anim.Duration = [TimeSpan]::FromSeconds(60)
        $pb.BeginAnimation([System.Windows.Controls.Primitives.RangeBase]::ValueProperty, $anim)
    }

    if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop() }
    $script:pairWaitTimer = New-Object System.Windows.Threading.DispatcherTimer
    $script:pairWaitTimer.Interval = [TimeSpan]::FromMilliseconds(1000)
    $script:pairWaitTimer.Add_Tick({
        try {
            $st = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/pair-status?ip=$($script:activeOutboundPairIp)" -TimeoutSec 1 -ErrorAction Stop
            if ($st.status -eq 'Accepted' -or $st.status -eq 'Rejected' -or $st.status -eq 'Failed') {
                $script:pairWaitTimer.Stop()
                if ($HidePanelOnTerminal) { $script:wpfWindow.FindName("pinViewPanel").Visibility = 'Collapsed' }
                try { $script:wpfWindow.FindName("menuViewsContainer").FindResource("SlideOutPinAnim").Begin($script:wpfWindow) } catch {}
                $script:activeOutboundPairIp = $null
                $script:activeOutboundPairFp = $null
                if ($st.status -eq 'Accepted') { Show-Toast -Title "Pairing Successful" -Message $SuccessMessage }
                else { Show-Toast -Title "Pairing Failed" -Message $FailureMessage }
            }
        } catch {}
    }.GetNewClosure())
    $script:pairWaitTimer.Start()
}


