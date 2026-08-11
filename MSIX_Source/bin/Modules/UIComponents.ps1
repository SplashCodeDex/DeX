function Load-TrayIcon([string]$FileName) {
    $binRoot = Split-Path $PSScriptRoot -Parent
    $iconPath = Join-Path $binRoot $FileName
    if (Test-Path $iconPath) {
        return [System.Drawing.Icon]::new($iconPath)
    }
    return $null
}
#Export-ModuleMember -Function Load-TrayIcon

function Release-PinAnimations {
    $w = $script:wpfWindow
    $c = $w.FindName("menuViewsContainer")
    try { $c.FindResource("SlideInPinAnim").Stop($w) } catch {}
    try { $c.FindResource("SlideOutPinAnim").Stop($w) } catch {}
    try { $c.FindResource("SwitchQrToPinAnim").Stop($w) } catch {}
    try { $c.FindResource("SwitchPinToQrAnim").Stop($w) } catch {}
}

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
            
            $dock = $script:wpfWindow.FindName("dockDownloadToast")
            if ($null -eq $dock) { return }
            $dockTrans = $null
            $dockScale = $null
            $dockTg = $dock.RenderTransform
            if ($dockTg -is [System.Windows.Media.TransformGroup]) {
                $dockScale = $dockTg.Children[0]
                $dockTrans = $dockTg.Children[1]
            }
            
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
# Card shadow removed for performance (DropShadowEffect is CPU-rendered).
# Keep stubs so existing callers don't break.
function Suspend-CardEffect {}
function Restore-CardEffect {}

function Start-CardTransition($storyboard) {
    $storyboard.Begin($script:wpfWindow, $true)
}

function Nudge-ForExpand($expandW, $expandH) {
    # Multi-directional expansion. Right+Top alignment is immutable — the
    # panel always grows left+down internally. To make it appear to grow
    # right or up, we animate the WINDOW position in sync with the expand
    # storyboard (800ms, BouncyEase). The menu slides smoothly into the
    # open space instead of snapping.
    #
    #   Expand LEFT:   no nudge (natural: Right-aligned width↑ → left)
    #   Expand RIGHT:  animate window → right by expandW
    #   Expand DOWN:   no nudge (natural: Top-aligned   height↑ → down)
    #   Expand UP:     animate window → up    by expandH
    #
    $mb = (dxEl "mainBorder")
    $wa = [System.Windows.SystemParameters]::WorkArea
    $winW = if ($script:wpfWindow.Width  -gt 0) { $script:wpfWindow.Width  } else { 1420 }
    $cw   = if ($mb.ActualWidth  -gt 0) { $mb.ActualWidth  } else { 300 }
    $ch   = if ($mb.ActualHeight -gt 0) { $mb.ActualHeight } else { 430 }

    # Save pre-nudge position BEFORE any changes, so contract can restore.
    $script:preExpandLeft = $script:wpfWindow.Left
    $script:preExpandTop  = $script:wpfWindow.Top

    $cLeft   = $script:wpfWindow.Left + $winW - 25 - $cw
    $cRight  = $script:wpfWindow.Left + $winW - 25
    $cTop    = $script:wpfWindow.Top + 25
    $cBottom = $cTop + $ch

    $spaceL = $cLeft   - $wa.Left
    $spaceR = $wa.Right  - $cRight
    $spaceU = $cTop    - $wa.Top
    $spaceD = $wa.Bottom - $cBottom

    $goLeft = ($spaceL -ge $spaceR) -or ($spaceL -ge $expandW + 20)
    $goDown = ($spaceD -ge $spaceU) -or ($spaceD -ge $expandH + 20)

    $toX = $script:wpfWindow.Left
    $toY = $script:wpfWindow.Top
    if (-not $goLeft) { $toX += $expandW }
    if (-not $goDown) { $toY -= $expandH }

    # Sanity clamp — the nudge destination must not push content off-screen.
    $ncLeft   = $toX + $winW - 25 - $cw
    $ncRight  = $toX + $winW - 25
    $ncTop    = $toY + 25
    $ncBottom = $ncTop + $ch
    if ($ncLeft   -lt $wa.Left)   { $toX += ($wa.Left   - $ncLeft)   }
    if ($ncRight  -gt $wa.Right)  { $toX -= ($ncRight  - $wa.Right)  }
    if ($ncTop    -lt $wa.Top)    { $toY += ($wa.Top    - $ncTop)    }
    if ($ncBottom -gt $wa.Bottom) { $toY -= ($ncBottom - $wa.Bottom) }

    # Animate the window slide in sync with the 800ms expand storyboard.
    # Same easing curve so the menu and panel move as one.
    if ($toX -ne $script:wpfWindow.Left -or $toY -ne $script:wpfWindow.Top) {
        $ease = try { $script:wpfWindow.FindResource("BouncyEase") } catch { $null }
        if (-not $ease) { $ease = (New-Object System.Windows.Media.Animation.CubicEase) }
        $dur = [TimeSpan]::FromSeconds(0.8)

        if ($toX -ne $script:wpfWindow.Left) {
            $ax = New-Object System.Windows.Media.Animation.DoubleAnimation -Property @{
                From = $script:wpfWindow.Left; To = $toX; Duration = $dur
                EasingFunction = $ease; FillBehavior = 'Stop'
            }
            $script:wpfWindow.Left = $toX
            $script:wpfWindow.BeginAnimation([System.Windows.Window]::LeftProperty, $ax)
        }
        if ($toY -ne $script:wpfWindow.Top) {
            $ay = New-Object System.Windows.Media.Animation.DoubleAnimation -Property @{
                From = $script:wpfWindow.Top; To = $toY; Duration = $dur
                EasingFunction = $ease; FillBehavior = 'Stop'
            }
            $script:wpfWindow.Top = $toY
            $script:wpfWindow.BeginAnimation([System.Windows.Window]::TopProperty, $ay)
        }
    }
}

function Restore-ExpandPosition {
    # Animate the window back to its pre-expand position, in sync with
    # the contract storyboard (0.25s content fade + 0.35s panel shrink).
    # Same BackEase as the contract so the window and panel move as one.
    if ($null -eq $script:preExpandLeft -and $null -eq $script:preExpandTop) { return }

    $ease = New-Object System.Windows.Media.Animation.BackEase
    $ease.Amplitude = 0.15; $ease.EasingMode = 'EaseOut'
    $dur  = [TimeSpan]::FromSeconds(0.35)
    $wait = [TimeSpan]::FromSeconds(0.25)

    if ($null -ne $script:preExpandLeft -and $script:preExpandLeft -ne $script:wpfWindow.Left) {
        $ax = New-Object System.Windows.Media.Animation.DoubleAnimation -Property @{
            From = $script:wpfWindow.Left; To = $script:preExpandLeft
            Duration = $dur; BeginTime = $wait
            EasingFunction = $ease; FillBehavior = 'Stop'
        }
        $script:wpfWindow.Left = $script:preExpandLeft
        $script:wpfWindow.BeginAnimation([System.Windows.Window]::LeftProperty, $ax)
    }
    if ($null -ne $script:preExpandTop -and $script:preExpandTop -ne $script:wpfWindow.Top) {
        $ay = New-Object System.Windows.Media.Animation.DoubleAnimation -Property @{
            From = $script:wpfWindow.Top; To = $script:preExpandTop
            Duration = $dur; BeginTime = $wait
            EasingFunction = $ease; FillBehavior = 'Stop'
        }
        $script:wpfWindow.Top = $script:preExpandTop
        $script:wpfWindow.BeginAnimation([System.Windows.Window]::TopProperty, $ay)
    }

    $script:preExpandLeft = $null
    $script:preExpandTop  = $null
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

    $pinT = $script:wpfWindow.FindName("pinContentTrans")
    if ($pinT) {
        $pinT.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, $null)
        $pinT.X = 140
    }
    $qrT = $script:wpfWindow.FindName("qrContentTrans")
    if ($qrT) {
        $qrT.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, $null)
        $qrT.X = 0
    }
    $txtQrBtnIcon = $script:wpfWindow.FindName("txtQrBtnIcon")
    if ($txtQrBtnIcon) { $txtQrBtnIcon.Visibility = 'Collapsed' }
    $txtQrBtnText = $script:wpfWindow.FindName("txtQrBtnText")
    if ($txtQrBtnText) { $txtQrBtnText.Text = "PIN CODE" }

    # Fetch the PNG over HTTP in a background job (bounded 3s), then set the image on
    # the UI thread from the byte stream. The QR view is already shown; a slow/failed
    # fetch degrades to an empty QR frame instead of freezing the app.
    # NOTE: a plain [System.Action] + BeginInvoke cannot run scriptblocks on threadpool
    # threads under PowerShell 5.1 ("The object must be a runtime Reflection object."),
    # so the fetch runs in a real background job instead (same pattern as Start-AsyncSafBrowse).
    # WebClient.DownloadData is used (not Invoke-RestMethod) because Invoke-RestMethod
    # decodes the image/png body to a String, which would never match the byte[] check.
    $imgQrCode = $script:wpfWindow.FindName("imgQrCode")
    if ($imgQrCode) {
        $api = $global:DeXLocalApi
        $qrIp = $localIp
        $qrJob = Start-Job -ScriptBlock {
            param($apiUrl, $ip)
            try {
                # HttpWebRequest (always available in .NET Framework) so the response body
                # stays raw bytes; Invoke-RestMethod would decode image/png to a String.
                $req = [System.Net.HttpWebRequest]::Create("$apiUrl/local/qr?ip=$ip")
                $req.Timeout = 3000
                $resp = $req.GetResponse()
                try {
                    $ms = New-Object System.IO.MemoryStream
                    $resp.GetResponseStream().CopyTo($ms)
                    $bytes = $ms.ToArray()
                    # Unary comma prevents PowerShell from unrolling the byte[] in the pipeline.
                    if ($bytes -and $bytes.Length -gt 0) { , $bytes } else { $null }
                } finally { $resp.Close() }
            } catch { $null }
        } -ArgumentList $api, $qrIp

        $qrTimer = New-Object System.Windows.Threading.DispatcherTimer
        $qrTimer.Interval = [TimeSpan]::FromMilliseconds(50)
        $qrTimer.Add_Tick({
            if ($qrJob.State -notin @('Running', 'NotStarted')) {
                $qrTimer.Stop()
                try {
                    $out = Receive-Job $qrJob -ErrorAction SilentlyContinue
                    $bytes = $null
                    if ($out -is [byte[]]) { $bytes = $out }
                    elseif ($out -is [array] -and $out.Count -eq 1 -and $out[0] -is [byte[]]) { $bytes = $out[0] }
                    if ($bytes -and $bytes.Length -gt 0) {
                        $ms = New-Object System.IO.MemoryStream(,$bytes)
                        try {
                            $bitmap = New-Object System.Windows.Media.Imaging.BitmapImage
                            $bitmap.BeginInit()
                            $bitmap.CacheOption = [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
                            $bitmap.StreamSource = $ms
                            $bitmap.EndInit()
                            $bitmap.Freeze()
                            $imgQrCode.Source = $bitmap
                        } catch {} finally {
                            if ($ms) { $ms.Dispose() }
                        }
                    }
                } catch {} finally {
                    Remove-Job $qrJob -Force -ErrorAction SilentlyContinue
                }
            }
        }.GetNewClosure())
        $qrTimer.Start()
    }
    return $true
}

# Shows the PIN pairing panel, runs the 60s progress animation, and starts the 1s status
# poll until the pairing is accepted, rejected, or times out. Centralizes the PIN display +
# poll timer shared by the outbound (Start-PinPairing) and phone-initiated (Connect-Engine)
# flows. Callers must set $script:activeOutboundPairIp / $script:activeOutboundPairFp first.
# Behavior switches preserve each flow's exact button/layout/toast differences.

function Set-PinContentView {
    param([switch]$ShowQr)
    $w = $script:wpfWindow
    if ($ShowQr) {
        $w.FindName("pinCodeContent").Visibility = 'Collapsed'
        $w.FindName("qrCodeContent").Visibility = 'Visible'
    } else {
        $w.FindName("pinCodeContent").Visibility = 'Visible'
        $w.FindName("qrCodeContent").Visibility = 'Collapsed'
    }
}

function Show-PinPanel {
    param(
        [string]$Title,
        [string]$Code,
        [string]$Status,
        [string]$Subtitle = "",
        [switch]$ShowQrToggle,
        [switch]$HideAcceptButtons,
        [switch]$HidePanelOnTerminal,
        [string]$SuccessMessage,
        [string]$FailureMessage
    )
    $w = $script:wpfWindow
    $w.FindName("txtPinTitle").Text = $Title
    # Reset the subtitle for the PIN view (clears the QR-view hint / XAML placeholder).
    $w.FindName("txtPinSubtitle").Text = $Subtitle

    $ic = $w.FindName("icPinDigits")
    if ($ic) {
        $digits = [System.Collections.ArrayList]::new()
        foreach ($c in $Code.ToCharArray()) { $null = $digits.Add($c.ToString()) }
        $ic.ItemsSource = $digits
    }

    $w.FindName("txtPinStatus").Text = $Status
    Set-PinContentView -ShowQr:$false
    if ($HideAcceptButtons) {
        $w.FindName("btnPinAccept").Visibility = 'Collapsed'
        $w.FindName("btnPinAcceptOnce").Visibility = 'Collapsed'
    }
    $w.FindName("btnSettingsQrCode").Visibility = if ($ShowQrToggle) { 'Visible' } else { 'Collapsed' }
    $w.FindName("btnPinCancel").Visibility = 'Visible'
    $w.FindName("txtQrBtnIcon").Visibility = 'Visible'
    $w.FindName("txtQrBtnText").Text = "QR CODE"

    $pinViewPanel = $w.FindName("pinViewPanel")
    if ($pinViewPanel.Visibility -eq 'Visible' -and $pinViewPanel.Opacity -gt 0) {
        try { $w.FindName("menuViewsContainer").FindResource("SwitchQrToPinAnim").Stop($w) } catch {}
        try { $w.FindName("menuViewsContainer").FindResource("SwitchPinToQrAnim").Stop($w) } catch {}
        $pinT = $w.FindName("pinContentTrans")
        if ($pinT) {
            $pinT.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, $null)
            $pinT.X = 140
        }
        try { $w.FindName("menuViewsContainer").FindResource("SwitchQrToPinAnim").Begin($w) } catch {}
    } else {
        Release-PinAnimations
        $pinT = $w.FindName("pinContentTrans")
        if ($pinT) {
            $pinT.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, $null)
            $pinT.X = 0
        }
        $qrT = $w.FindName("qrContentTrans")
        if ($qrT) {
            $qrT.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, $null)
            $qrT.X = -140
        }
        $pinViewPanel.Visibility = 'Visible'
        try { $w.FindName("menuViewsContainer").FindResource("SlideInPinAnim").Begin($w) } catch {}
    }

    $txtTimeout = $w.FindName("txtPinTimeout")
    if ($txtTimeout) { $txtTimeout.Text = "Expires in 60s" }

    if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop() }
    # PLAIN tick (NO GetNewClosure): a closure runs in a detached module scope where direct
    # $script: reads/writes are lost AND captured locals are re-copied on every invocation
    # (a countdown would never advance). A plain scriptblock runs in the real engine scope,
    # so it reads the session context stored below and advances the countdown persistently.
    $timer = New-Object System.Windows.Threading.DispatcherTimer
    $timer.Interval = [TimeSpan]::FromMilliseconds(1000)
    $script:pairWaitTimer = $timer
    $script:pairWaitSessionIp = $script:activeOutboundPairIp
    $script:pairWaitSessionFp = $script:activeOutboundPairFp
    $script:pairWaitTimeoutText = $txtTimeout
    $script:pairWaitHideOnTerminal = $HidePanelOnTerminal
    $script:pairWaitSuccessMsg = $SuccessMessage
    $script:pairWaitFailureMsg = $FailureMessage
    $script:pairWaitSeconds = 60
    $timer.Add_Tick({
        $t = $script:pairWaitTimer
        if (-not $t -or -not $t.IsEnabled) { return }
        # Cancellation guard: cancelling/replacing the pairing stops this timer from the
        # engine scope; a tick that was already queued must not act after the fact.
        if ($script:activeOutboundPairIp -ne $script:pairWaitSessionIp -or
            $script:activeOutboundPairFp -ne $script:pairWaitSessionFp) {
            $t.Stop()
            return
        }
        $script:pairWaitSeconds--
        if ($script:pairWaitTimeoutText -and $script:pairWaitSeconds -ge 0) {
            $script:pairWaitTimeoutText.Text = "Expires in $($script:pairWaitSeconds)s"
        }
        # Real expiry: cancel + close instead of staying stuck on "Expires in 0s".
        if ($script:pairWaitSeconds -le 0) {
            $t.Stop()
            Stop-PairingSession -SlideOut
            Show-Toast -Title "Pairing Timed Out" -Message $script:pairWaitFailureMsg
            return
        }
        try {
            $st = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/pair-status?ip=$($script:pairWaitSessionIp)&fingerprint=$($script:pairWaitSessionFp)" -TimeoutSec 1 -ErrorAction Stop
            # The user cancelled while the poll was in flight — drop the stale result.
            if (-not $t.IsEnabled) { return }
            if ($st.status -eq 'Accepted' -or $st.status -eq 'Rejected' -or $st.status -eq 'Failed') {
                $t.Stop()
                if ($script:pairWaitHideOnTerminal) { $script:wpfWindow.FindName("pinViewPanel").Visibility = 'Collapsed' }
                Clear-PairingState
                try { $script:wpfWindow.FindName("menuViewsContainer").FindResource("SlideOutPinAnim").Begin($script:wpfWindow) } catch {}
                if ($st.status -eq 'Accepted') { Show-Toast -Title "Pairing Successful" -Message $script:pairWaitSuccessMsg }
                else { Show-Toast -Title "Pairing Failed" -Message $script:pairWaitFailureMsg }
            }
        } catch {}
    })
    $timer.Start()
}

function Clear-PairingState {
    if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop(); $script:pairWaitTimer = $null }
    if ($script:qrPhaseTimer) { $script:qrPhaseTimer.Stop(); $script:qrPhaseTimer = $null }
    # An inbound pairing keeps the window on top; restore the user's prior z-order now.
    if ($null -ne $script:priorWindowTopmost) {
        try { $script:wpfWindow.Topmost = [bool]$script:priorWindowTopmost } catch {}
        $script:priorWindowTopmost = $null
    }
    $script:activeOutboundPairIp = $null
    $script:activeOutboundPairFp = $null
    $script:pairInitSessionIp = $null
    $script:pairInitSessionFp = $null
    $script:pairWaitSessionIp = $null
    $script:pairWaitSessionFp = $null
    $script:pairWaitTimeoutText = $null
    $script:pairWaitHideOnTerminal = $false
    $script:pairWaitSuccessMsg = ""
    $script:pairWaitFailureMsg = ""
    $script:pairWaitSeconds = 60
    $txtTimeout = $script:wpfWindow.FindName("txtPinTimeout")
    if ($txtTimeout) { $txtTimeout.Text = "" }
    # Never leave a stale QR bitmap behind (it may show a previous session's URL).
    $imgQr = $script:wpfWindow.FindName("imgQrCode")
    if ($imgQr) { $imgQr.Source = $null }
}

# Stops every pairing session timer/job without touching the session state or the server.
# Used by the "QR CODE" back-switch, which keeps the device context so "Request PIN" works
# again from the QR view.
function Stop-PairingTimers {
    if ($script:pairInitTimer) { $script:pairInitTimer.Stop(); $script:pairInitTimer = $null }
    if ($script:pairInitJob) { Remove-Job $script:pairInitJob -Force -ErrorAction SilentlyContinue; $script:pairInitJob = $null }
    if ($script:pairWaitTimer) { $script:pairWaitTimer.Stop(); $script:pairWaitTimer = $null }
    if ($script:qrPhaseTimer) { $script:qrPhaseTimer.Stop(); $script:qrPhaseTimer = $null }
}

# User-initiated cancellation (Cancel button, Escape, switching to a different device, or
# timeout): stops every session timer, cancels the pending pairing server-side, and clears
# the session state. -SlideOut also plays the panel exit animation.
function Stop-PairingSession {
    param([switch]$SlideOut)
    Stop-PairingTimers
    $cancelledFp = $script:activeOutboundPairFp
    if ($script:activeOutboundPairIp) {
        try {
            Invoke-RestMethod -Uri "$global:DeXLocalApi/local/pair-cancel?ip=$($script:activeOutboundPairIp)&fingerprint=$($script:activeOutboundPairFp)" -Method Post -ErrorAction SilentlyContinue
        } catch {}
    }
    Clear-PairingState
    # One-shot, time-bounded suppression: the poller fetches /local/pending-pair every 2s,
    # so a result for the attempt we just cancelled may ALREADY be queued. Without this, the
    # next tick would slide the PIN panel straight back in over the dismissal. The window
    # covers the poll cadence; a genuine NEW attempt after it simply shows again.
    if ($cancelledFp) {
        $script:suppressPendingPairFp = $cancelledFp
        $script:suppressPendingPairUntil = [datetime]::UtcNow.AddSeconds(6)
    }
    if ($SlideOut) {
        try { $script:wpfWindow.FindName("menuViewsContainer").FindResource("SlideOutPinAnim").Begin($script:wpfWindow) } catch {}
    }
}

# Starts the idle-QR-phase expiry: if the user never taps "Request PIN" (or returns to the
# QR view and idles), close the panel after 60s instead of leaving the session dangling.
function Start-QrPhaseTimer {
    if ($script:qrPhaseTimer) { $script:qrPhaseTimer.Stop() }
    # PLAIN tick (NO GetNewClosure): the closure variant reads $script:pairWaitTimer /
    # $script:pairInitJob from its detached scope (always empty), which would make the
    # "PIN phase active" check unreliable. A plain tick runs in the real engine scope.
    $timer = New-Object System.Windows.Threading.DispatcherTimer
    $timer.Interval = [TimeSpan]::FromSeconds(60)
    $script:qrPhaseTimer = $timer
    $timer.Add_Tick({
        $t = $script:qrPhaseTimer
        if (-not $t -or -not $t.IsEnabled) { return }
        # Only auto-cancel while still in the idle QR phase (no PIN session and no request
        # in flight) — otherwise the session carries on.
        $pinPhaseActive = ($script:pairWaitTimer -and $script:pairWaitTimer.IsEnabled)
        if (-not $pinPhaseActive -and -not $script:pairInitJob) {
            Stop-PairingSession -SlideOut
            Show-Toast -Title "Pairing Expired" -Message "The pairing request expired."
        }
        if ($t) { $t.Stop() }
    })
    $timer.Start()
}



