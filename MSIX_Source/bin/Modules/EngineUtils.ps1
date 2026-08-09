if (-not ("ThumbHelper" -as [type])) {
    $thumbCode = @"
using System;
using System.Runtime.InteropServices;
using System.Windows.Interop;
using System.Windows.Media.Imaging;
using System.Windows;

public class ThumbHelper {
    [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
    public static extern void SHCreateItemFromParsingName(string path, IntPtr pbc, [MarshalAs(UnmanagedType.LPStruct)] Guid riid, out object ppv);

    [DllImport("gdi32.dll")]
    public static extern bool DeleteObject(IntPtr hObject);

    public static BitmapSource GetThumb(string path, int size) {
        Guid iid = new Guid("bcc18b79-ba16-442f-80c4-8a15c3ed75a8");
        object item;
        SHCreateItemFromParsingName(path, IntPtr.Zero, iid, out item);
        
        IntPtr hBitmap;
        // SIIGBF_MEMORYONLY (0x2) is safer for performance, but 0x0 will fetch or generate
        ((dynamic)item).GetImage(new System.Drawing.Size(size, size), 0x0, out hBitmap);
        
        BitmapSource bs = Imaging.CreateBitmapSourceFromHBitmap(hBitmap, IntPtr.Zero, Int32Rect.Empty, BitmapSizeOptions.FromEmptyOptions());
        bs.Freeze();
        DeleteObject(hBitmap);
        return bs;
    }
}
"@
    try {
        Add-Type -TypeDefinition $thumbCode -ReferencedAssemblies "System.Drawing", "PresentationCore", "WindowsBase", "Microsoft.CSharp" -ErrorAction SilentlyContinue
    } catch {}
}

function Load-ThumbnailAsync($targetItem, $fullPath, $fileName, $isDir, $metaStr) {
    if ($isDir -or -not ("ThumbHelper" -as [type])) { return }
    $action = [System.Action]{
        try {
            $bs = $null
            if ($fileName -match '\.(jpg|jpeg|png|webp|bmp)$') {
                $bmp = New-Object System.Windows.Media.Imaging.BitmapImage
                $bmp.BeginInit()
                $bmp.UriSource = New-Object System.Uri("file:///$fullPath")
                $bmp.DecodePixelWidth = 100
                $bmp.CacheOption = [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
                $bmp.EndInit()
                $bmp.Freeze()
                $bs = $bmp
            } else {
                $bs = [ThumbHelper]::GetThumb($fullPath, 100)
            }
            if ($bs) {
                $uiAction = [System.Action]{
                    $targetItem.Content = [PSCustomObject]@{ Name = $fileName; FullPath = $fullPath; IsDir = $isDir; Meta = $metaStr; Thumb = $bs; NoThumb = 'Collapsed' }
                }
                $script:wpfWindow.Dispatcher.Invoke($uiAction)
            }
        } catch {
            # Silently fail for unsupported files
        }
    }
    $null = $action.BeginInvoke($null, $null)
}

# ============================================================================
# iOS-style directory drill-down (push/pop) — mirrors NavigationTransitions.kt
# on the Android app: same curves, durations, parallax, scale and dim.
# ============================================================================
$script:IosPushMs = 400
$script:IosPopMs = 350
$script:IosSwitchMs = 250
$script:IosParallaxDivisor = 3
$script:IosBehindScale = 0.96
$script:IosBehindAlpha = 0.7
$script:explorerSnapshot = $null

# Builds a keyframe animation with the exact iOS cubic-bezier curve.
# $Frames = array of @(value, timeMs) pairs, e.g. @(@(0.7, 400), @(0, 550)).
function New-IosFrameAnimation([object[]]$Frames, [double]$X1, [double]$Y1, [double]$X2, [double]$Y2) {
    $anim = New-Object System.Windows.Media.Animation.DoubleAnimationUsingKeyFrames
    foreach ($frame in $Frames) {
        $kf = New-Object System.Windows.Media.Animation.SplineDoubleKeyFrame
        $kf.KeyTime = [System.Windows.Media.Animation.KeyTime]::FromTimeSpan([TimeSpan]::FromMilliseconds($frame[1]))
        $kf.Value = $frame[0]
        $kf.KeySpline = New-Object System.Windows.Media.Animation.KeySpline($X1, $Y1, $X2, $Y2)
        $anim.KeyFrames.Add($kf) | Out-Null
    }
    return $anim
}

# Classifies a directory navigation: 'push' (deeper), 'pop' (back up),
# 'switch' (mode change), or 'none' (first load / same path).
function Get-DirectoryTransition($oldPath, $newPath) {
    if ([string]::IsNullOrEmpty($oldPath) -or $oldPath -eq $newPath) { return 'none' }
    if ($oldPath -eq 'Phone Folders') { return 'push' }
    if ($newPath -eq 'Phone Folders') { return 'pop' }
    if ($newPath.StartsWith($oldPath, [System.StringComparison]::OrdinalIgnoreCase)) { return 'push' }
    if ($oldPath.StartsWith($newPath, [System.StringComparison]::OrdinalIgnoreCase)) { return 'pop' }
    return 'switch'
}

function Remove-ExplorerSnapshot {
    $snap = $script:explorerSnapshot
    if ($null -ne $snap) {
        if ($null -ne $snap.Parent) { $snap.Parent.Children.Remove($snap) }
        $script:explorerSnapshot = $null
    }
}

# Clones the outgoing listing into a read-only overlay so it can animate away
# while the real list swaps to the new directory underneath.
function New-ExplorerSnapshot {
    Remove-ExplorerSnapshot
    $list = $script:lbFiles
    if ($null -eq $list -or $list.Items.Count -eq 0) { return }

    $snap = New-Object System.Windows.Controls.ListBox
    $snap.Background = [System.Windows.Media.Brushes]::Transparent
    $snap.BorderThickness = '0'
    $snap.Padding = '0,0,10,0'
    $snap.Focusable = $false
    $snap.IsHitTestVisible = $false
    $snap.ItemContainerStyle = $list.ItemContainerStyle
    $snap.ItemsPanel = $list.ItemsPanel
    $snap.SetValue([System.Windows.Controls.ScrollViewer]::HorizontalScrollBarVisibilityProperty, [System.Windows.Controls.ScrollBarVisibility]::Disabled)
    $snap.SetValue([System.Windows.Controls.ScrollViewer]::VerticalScrollBarVisibilityProperty, [System.Windows.Controls.ScrollBarVisibility]::Hidden)
    foreach ($item in @($list.Items)) {
        $copy = New-Object System.Windows.Controls.ListBoxItem
        $copy.Content = $item.Content
        $copy.ContentTemplate = $item.ContentTemplate
        $copy.Tag = $item.Tag
        $snap.Items.Add($copy) | Out-Null
    }
    [System.Windows.Controls.Grid]::SetRow($snap, 1)
    $list.Parent.Children.Add($snap)
    $script:explorerSnapshot = $snap
}

# Plays the iOS push/pop/switch motion between the snapshot (outgoing) and the
# real list (incoming), then removes the snapshot and resets the list visuals.
function Start-ExplorerTransition($direction) {
    $snap = $script:explorerSnapshot
    if ($null -eq $snap) { return }
    $list = $script:lbFiles
    $w = [Math]::Max(1, $list.ActualWidth)

    # Fresh visual state for the incoming real list
    $list.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $null)
    $list.RenderTransform = $null
    $listGroup = New-Object System.Windows.Media.TransformGroup
    $listTrans = New-Object System.Windows.Media.TranslateTransform
    $listScale = New-Object System.Windows.Media.ScaleTransform
    $listGroup.Children.Add($listTrans) | Out-Null
    $listGroup.Children.Add($listScale) | Out-Null
    $list.RenderTransform = $listGroup
    $list.RenderTransformOrigin = New-Object System.Windows.Point(0.5, 0.5)

    $snapGroup = New-Object System.Windows.Media.TransformGroup
    $snapTrans = New-Object System.Windows.Media.TranslateTransform
    $snapScale = New-Object System.Windows.Media.ScaleTransform
    $snapGroup.Children.Add($snapTrans) | Out-Null
    $snapGroup.Children.Add($snapScale) | Out-Null
    $snap.RenderTransform = $snapGroup
    $snap.RenderTransformOrigin = New-Object System.Windows.Point(0.5, 0.5)

    $cleanupMs = 300
    switch ($direction) {
        'push' {
            [System.Windows.Controls.Panel]::SetZIndex($list, 1)
            [System.Windows.Controls.Panel]::SetZIndex($snap, 0)
            # Incoming: slides in from the right, un-receding from 96%/70%
            $listTrans.X = $w
            $listScale.ScaleX = $script:IosBehindScale; $listScale.ScaleY = $script:IosBehindScale
            $list.Opacity = $script:IosBehindAlpha
            $listTrans.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, (New-IosFrameAnimation @(@(0, $script:IosPushMs)) 0.32 0.72 0 1))
            $listScale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleXProperty, (New-IosFrameAnimation @(@(1, $script:IosPushMs)) 0.32 0.72 0 1))
            $listScale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleYProperty, (New-IosFrameAnimation @(@(1, $script:IosPushMs)) 0.32 0.72 0 1))
            $list.BeginAnimation([System.Windows.UIElement]::OpacityProperty, (New-IosFrameAnimation @(@(1, $script:IosPushMs)) 0.32 0.72 0 1))
            # Outgoing: parallax left 1/3, recede to 96%, dim to 70%, then fade away
            $snapTrans.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, (New-IosFrameAnimation @(@(-$w / $script:IosParallaxDivisor, $script:IosPushMs)) 0.32 0.72 0 1))
            $snapScale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleXProperty, (New-IosFrameAnimation @(@($script:IosBehindScale, $script:IosPushMs)) 0.32 0.72 0 1))
            $snapScale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleYProperty, (New-IosFrameAnimation @(@($script:IosBehindScale, $script:IosPushMs)) 0.32 0.72 0 1))
            $snap.BeginAnimation([System.Windows.UIElement]::OpacityProperty, (New-IosFrameAnimation @(@($script:IosBehindAlpha, $script:IosPushMs), @(0, ($script:IosPushMs + 150))) 0.32 0.72 0 1))
            $cleanupMs = $script:IosPushMs + 200
        }
        'pop' {
            [System.Windows.Controls.Panel]::SetZIndex($list, 0)
            [System.Windows.Controls.Panel]::SetZIndex($snap, 1)
            # Revealed list: returns from the left parallax, un-receding from 96%/70%
            $listTrans.X = -$w / $script:IosParallaxDivisor
            $listScale.ScaleX = $script:IosBehindScale; $listScale.ScaleY = $script:IosBehindScale
            $list.Opacity = $script:IosBehindAlpha
            $listTrans.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, (New-IosFrameAnimation @(@(0, $script:IosPopMs)) 0.22 1 0.36 1))
            $listScale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleXProperty, (New-IosFrameAnimation @(@(1, $script:IosPopMs)) 0.22 1 0.36 1))
            $listScale.BeginAnimation([System.Windows.Media.ScaleTransform]::ScaleYProperty, (New-IosFrameAnimation @(@(1, $script:IosPopMs)) 0.22 1 0.36 1))
            $list.BeginAnimation([System.Windows.UIElement]::OpacityProperty, (New-IosFrameAnimation @(@(1, $script:IosPopMs)) 0.22 1 0.36 1))
            # Outgoing: slides out the way it came
            $snapTrans.BeginAnimation([System.Windows.Media.TranslateTransform]::XProperty, (New-IosFrameAnimation @(@($w, $script:IosPopMs)) 0.22 1 0.36 1))
            $snap.BeginAnimation([System.Windows.UIElement]::OpacityProperty, (New-IosFrameAnimation @(@(0, $script:IosPopMs)) 0.22 1 0.36 1))
            $cleanupMs = $script:IosPopMs + 100
        }
        'switch' {
            [System.Windows.Controls.Panel]::SetZIndex($list, 0)
            [System.Windows.Controls.Panel]::SetZIndex($snap, 1)
            $list.Opacity = 0
            $list.BeginAnimation([System.Windows.UIElement]::OpacityProperty, (New-IosFrameAnimation @(@(1, $script:IosSwitchMs)) 0.32 0.72 0 1))
            $snap.BeginAnimation([System.Windows.UIElement]::OpacityProperty, (New-IosFrameAnimation @(@(0, $script:IosSwitchMs)) 0.32 0.72 0 1))
            $cleanupMs = $script:IosSwitchMs + 100
        }
    }

    # Remove the snapshot and reset the list once the motion completes. The
    # snapshot identity guard keeps a stale cleanup timer from clobbering a
    # newer transition when the user navigates quickly.
    $timer = New-Object System.Windows.Threading.DispatcherTimer
    $timer.Interval = [TimeSpan]::FromMilliseconds($cleanupMs)
    $handler = {
        $timer.Stop()
        if ($null -ne $snap.Parent) { $snap.Parent.Children.Remove($snap) }
        if ($script:explorerSnapshot -eq $snap) {
            $script:explorerSnapshot = $null
            $list.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $null)
            $list.RenderTransform = $null
        }
    }.GetNewClosure()
    $timer.Add_Tick($handler)
    $timer.Start()
}

function Load-Directory($dirPath) {
    if ($script:isLoadingDir) { return }
    $script:isLoadingDir = $true
    
    try {
        if ($null -ne $script:searchTimer) { $script:searchTimer.Stop() }
        
        # Auto-reset search bar text so the new directory displays all items cleanly
        if ($null -ne $script:txtSearch) {
            $script:txtSearch.Text = "Search transfers..."
            $script:txtSearch.Foreground = $script:wpfWindow.FindResource("SecondaryTextBrush")
        }
        
        # Edge Case 27: Normalize and sanitize directory path
        $isLocal = [System.IO.Path]::IsPathRooted($dirPath) -and $dirPath -match '^[A-Za-z]:\\'
        if (-not $isLocal) {
            $dirPath = ($dirPath -replace '(?<!:)/+', '/').Trim()
            if (-not $dirPath.StartsWith("/")) { $dirPath = "/" + $dirPath }
            if (-not $dirPath.EndsWith("/")) { $dirPath = $dirPath + "/" }
        } else {
            if (-not $dirPath.EndsWith("\")) { $dirPath = $dirPath + "\" }
        }
        
        # iOS-style drill-down: snapshot the outgoing listing before clearing it
        # so it can animate away while the new directory slides in (push/pop).
        $oldDir = $script:currentDirPath
        $transition = Get-DirectoryTransition $oldDir $dirPath
        $hadItems = $script:lbFiles.Items.Count -gt 0
        if ($transition -ne 'none' -and $hadItems) { New-ExplorerSnapshot }

        $script:currentDirPath = $dirPath
        $script:lbFiles.Items.Clear()
        $script:phoneFileMeta = @{}
        
        if ($null -ne $script:btnUpDir) {
            if ($dirPath -eq "/sdcard/" -or $dirPath -eq "/sdcard") {
                $script:btnUpDir.Opacity = 0.4
                $script:btnUpDir.Cursor = [System.Windows.Input.Cursors]::Arrow
            } else {
                $script:btnUpDir.Opacity = 1.0
                $script:btnUpDir.Cursor = [System.Windows.Input.Cursors]::Hand
            }
        }
        
        if ($isLocal) {
            $lines = @()
            $script:localFileMeta = @{}
            if (Test-Path $dirPath) {
                Get-ChildItem -Path $dirPath -File | Sort-Object LastWriteTime -Descending | Select-Object -First 50 | ForEach-Object {
                    $lines += $_.Name
                    $bytes = $_.Length
                    if ($bytes -ge 1GB) { $sz = "{0:N1} GB" -f ($bytes / 1GB) }
                    elseif ($bytes -ge 1MB) { $sz = "{0:N1} MB" -f ($bytes / 1MB) }
                    elseif ($bytes -ge 1KB) { $sz = "{0:N0} KB" -f ($bytes / 1KB) }
                    else { $sz = "$bytes B" }
                    $dt = $_.LastWriteTime.ToString("MMM d, h:mm tt")
                    $script:localFileMeta[$_.Name] = "$sz · $dt"
                }
            }
        } elseif ($script:isMockMode) {
            $lines = @("DeX_Transfers/")
            for ($i = 1; $i -le 49; $i++) {
                $ext = if ($i % 4 -eq 0) { ".pdf" } elseif ($i % 3 -eq 0) { ".mp4" } elseif ($i % 2 -eq 0) { ".png" } else { ".jpg" }
                $lines += "dummy_file_$i$ext"
            }
        } elseif ($dirPath.StartsWith("content://")) {
            # SAF File Explorer Mode: browse a granted phone folder over the WebSocket
            $script:phoneFileMeta = @{}
            $ip = Get-FileExplorerTargetIp
            $lines = @()
            if ($ip) {
                try {
                    $body = @{ ip = $ip; folderUri = $dirPath } | ConvertTo-Json
                    $res = Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/dex/browse" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 30 -ErrorAction Stop
                    if ($res.entries) {
                        foreach ($prop in $res.entries.PSObject.Properties) {
                            $name = $prop.Value.name
                            $isDir = [bool]$prop.Value.isDirectory
                            $size = [long]$prop.Value.size
                            $thumb = if ($prop.Value.thumb) { $prop.Value.thumb } else { $null }
                            $script:phoneFileMeta[$name] = @{ Uri = $prop.Name; Size = $size; IsDir = $isDir; Thumb = $thumb }
                            if ($isDir) { $lines += "$name/" } else { $lines += $name }
                        }
                    }
                } catch {
                    Write-Trace "Browse Error: $_"
                    $lines = @()
                }
            }
        } else {
            # Unknown/non-SAF remote path (no longer used by File Explorer) — empty listing
            $lines = @()
        }

        # Sort phone listings: folders first, then files, alphabetically (SAF returns unsorted).
        if ($dirPath.StartsWith("content://") -and $lines.Count -gt 0) {
            $lines = @($lines | Sort-Object @{Expression = { $_ -notlike '*/' }}, @{Expression = { $_ }})
        }
        
        # Edge Case 5: Empty Folder State Toggle
        $emptyOverlay = $script:wpfWindow.FindName("emptyFolderState")
        if ($null -ne $emptyOverlay) {
            if (-not $lines -or $lines.Count -eq 0) {
                $emptyOverlay.Visibility = 'Visible'
                $emptyOverlay.Opacity = 1.0
            } else {
                $emptyOverlay.Visibility = 'Collapsed'
                $emptyOverlay.Opacity = 0.0
            }
        }
        
        $idx = 0
        foreach ($line in $lines) {
            $isDir = $line.EndsWith("/") -or $line.EndsWith("\")
            $name = $line.TrimEnd('/', '\', '*', '@', '=')
            if ($isDir) {
                $full = if ($isLocal) { $dirPath + $name + "\" } else { $dirPath + $name + "/" }
                $template = $script:wpfWindow.Resources["FolderGridTemplate"]
            } else {
                $full = $dirPath + $name
                $template = $script:wpfWindow.Resources["FileGridTemplate"]
            }
            
            # SAF File Explorer mode: files/dirs are identified by their document URI, not a path
            $phoneMeta = $null
            if (-not $isLocal -and $script:phoneFileMeta -and $script:phoneFileMeta[$name]) {
                $phoneMeta = $script:phoneFileMeta[$name]
                $full = $phoneMeta.Uri
            }
            
            $meta = if ($isLocal -and $script:localFileMeta[$name]) {
                $script:localFileMeta[$name]
            } elseif ($phoneMeta -and -not $phoneMeta.IsDir) {
                Format-FileSize $phoneMeta.Size
            } else { "" }
            $thumb = $null
            $noThumb = 'Visible'
            if ($phoneMeta -and $phoneMeta.Thumb) {
                $thumb = Convert-PhoneThumb $phoneMeta.Thumb
                if ($thumb) { $noThumb = 'Collapsed' }
            }
            $item = New-Object System.Windows.Controls.ListBoxItem
            $item.Content = [PSCustomObject]@{ Name = $name; FullPath = $full; IsDir = $isDir; Meta = $meta; Thumb = $thumb; NoThumb = $noThumb }
            $item.ContentTemplate = $template
            $item.Tag = $full
            
            if ($isLocal) {
                $ctx = $script:wpfWindow.Resources["TransferContextMenu"]
                if ($ctx) { $item.ContextMenu = $ctx }
                
                # Kick off async thumbnail generation
                Load-ThumbnailAsync $item $full $name $isDir $meta
            }
            
            # Staggered Entrance Animation
            $trans = New-Object System.Windows.Media.TranslateTransform
            $trans.Y = 80
            $item.RenderTransform = $trans
            $item.Opacity = 0
            
            $delay = [TimeSpan]::FromMilliseconds($idx * 35)
            
            $daY = New-Object System.Windows.Media.Animation.DoubleAnimation
            $daY.To = 0
            $daY.Duration = [TimeSpan]::FromSeconds(0.6)
            $daY.BeginTime = $delay
            $daY.EasingFunction = $script:wpfWindow.Resources["HoverEase"]
            
            $daOp = New-Object System.Windows.Media.Animation.DoubleAnimation
            $daOp.To = 1
            $daOp.Duration = [TimeSpan]::FromSeconds(0.4)
            $daOp.BeginTime = $delay
            
            $trans.BeginAnimation([System.Windows.Media.TranslateTransform]::YProperty, $daY)
            $item.BeginAnimation([System.Windows.Controls.ListBoxItem]::OpacityProperty, $daOp)
            
            $script:lbFiles.Items.Add($item)
            $idx++
        }

        # New listing is ready — play the push/pop/switch motion (first load
        # has no snapshot and just staggers in).
        if ($transition -ne 'none' -and $hadItems) { Start-ExplorerTransition $transition }
    } finally {
        $script:isLoadingDir = $false
    }
}

function Format-FileSize([long]$bytes) {
    if ($bytes -lt 0) { return "" }
    if ($bytes -ge 1GB) { return "{0:N1} GB" -f ($bytes / 1GB) }
    if ($bytes -ge 1MB) { return "{0:N1} MB" -f ($bytes / 1MB) }
    if ($bytes -ge 1KB) { return "{0:N0} KB" -f ($bytes / 1KB) }
    return "$bytes B"
}

# Decodes a phone-provided base64 JPEG thumbnail into a WPF BitmapSource for the file list.
function Convert-PhoneThumb([string]$base64) {
    try {
        $bytes = [System.Convert]::FromBase64String($base64)
        $ms = New-Object System.IO.MemoryStream(,$bytes)
        $bmp = New-Object System.Windows.Media.Imaging.BitmapImage
        $bmp.BeginInit()
        $bmp.CacheOption = [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
        $bmp.StreamSource = $ms
        $bmp.EndInit()
        $bmp.Freeze()
        $ms.Dispose()
        return $bmp
    } catch { return $null }
}

# Resolves the connected phone's LAN IP without ADB: prefer the local engine's first
# paired/auto-trusted device, falling back to the last known IP.
function Get-FileExplorerTargetIp {
    try {
        $devices = Invoke-RestMethod -Uri "http://127.0.0.1:53318/local/devices" -TimeoutSec 2 -ErrorAction Stop
        $target = $devices | Where-Object { $_.isPaired -or $_.isAutoTrusted } | Select-Object -First 1
        if ($target -and $target.ip) { return $target.ip }
    } catch {}
    $lastIp = (Get-ItemProperty "HKCU:\Software\DeX" -Name "LastIp" -ErrorAction SilentlyContinue).LastIp
    if ($lastIp) { return $lastIp }
    return $null
}

function global:Write-Trace($msg) {    # Rotate: keep the log forensically useful by capping it at ~200KB (retains last 500 lines)
    $tracePath = "$env:TEMP\connect-adb-trace.txt"
    try {
        if ((Test-Path $tracePath) -and ((Get-Item $tracePath).Length -gt 200KB)) {
            Get-Content $tracePath -Tail 500 | Set-Content $tracePath
        }
    } catch {}
    Out-File -FilePath $tracePath -InputObject "[$(Get-Date -Format 'HH:mm:ss.fff')] $msg" -Append
}

