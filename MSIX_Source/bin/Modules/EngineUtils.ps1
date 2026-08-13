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

# Bounded decode cache for local file thumbnails, so re-entering a folder reuses frozen
# bitmaps instead of re-decoding every visit. Keyed by path + length + last-write time to
# stay fresh. Thread-safe: read/written by both the UI thread and thumbnail threadpool.
$script:thumbCache = [System.Collections.Concurrent.ConcurrentDictionary[string, object]]::new()
$script:thumbCacheMax = 200

# Directory-navigation state (UI thread only): a monotonically increasing sequence number
# so a stale async SAF browse completion can detect it was superseded, and a flag that
# keeps the Load-Directory reentrancy guard armed while a phone browse is in flight.
$script:dirLoadSeq = 0
$script:asyncBrowsePending = $false

# Clipboard sync STA worker. Get-Clipboard only works on an STA thread, but running it on the
# UI thread (via a DispatcherTimer) stalls the whole spatial UI whenever the clipboard owner
# is slow. This uses the same proven pattern as Start-UiDataPolling: a dedicated PowerShell
# runspace (here forced to STA via RunspaceFactory.ApartmentState) looping forever, driven by
# a ConcurrentQueue mailbox. The engine's 2s tick only enqueues the enable toggle — the
# runspace does the clipboard reads, HTTP and adb off the UI thread.
$script:clipWorkerControl = [System.Collections.Concurrent.ConcurrentQueue[object]]::new()
$script:clipWorkerPs = $null
$script:clipWorkerRs = $null
$script:clipWorkerAsync = $null

function Start-ClipboardSyncWorker {
    if ($null -ne $script:clipWorkerPs) { return }
    try {
        $iss = [management.automation.runspaces.initialsessionstate]::CreateDefault2()
        $rs = [runspacefactory]::CreateRunspace($iss)
        $rs.ApartmentState = [System.Threading.ApartmentState]::STA
        $rs.Open()
        $ps = [powershell]::Create()
        $ps.Runspace = $rs

        $ctl = $script:clipWorkerControl
        $api = $global:DeXLocalApi
        $adbPath = $global:AdbExePath

        [void]$ps.AddScript({
            param($ctlQueue, $apiUrl, $adb)
            $enabled = $false
            $clipLastPushed = ""
            $clipLastReceived = ""
            while ($true) {
                $m = $null
                while ($ctlQueue.TryDequeue([ref]$m)) {
                    if ($m.ContainsKey('SetEnabled')) { $enabled = [bool]$m.SetEnabled }
                    if ($m.ContainsKey('Stop')) { return }
                }
                if ($enabled) {
                    try {
                        # 1. Learn what the phone last pushed, so we don't echo it back to the phone
                        try {
                            $state = Invoke-RestMethod -Uri "$apiUrl/local/clipboard-state" -TimeoutSec 1 -ErrorAction Stop
                            if ($state -and $state.text -and $state.text -ne $clipLastReceived) {
                                $clipLastReceived = [string]$state.text
                            }
                        } catch {}

                        # 2. Detect a fresh local clipboard change and push it to a trusted device
                        $text = Get-Clipboard -Raw -ErrorAction Ignore
                        if (-not [string]::IsNullOrWhiteSpace($text) -and
                            $text -ne $clipLastPushed -and
                            $text -ne $clipLastReceived) {

                            $ip = $null
                            try {
                                $devices = Invoke-RestMethod -Uri "$apiUrl/local/devices" -TimeoutSec 2 -ErrorAction Stop
                                $target = $devices | Where-Object { $_.isPaired -or $_.isAutoTrusted } | Select-Object -First 1
                                if ($target) { $ip = $target.ip }
                            } catch {}

                            if ($ip) {
                                # WebSocket push first; adb broadcast fallback (same as Send-ClipboardToDevice)
                                $delivered = $false
                                try {
                                    $null = Invoke-RestMethod -Uri "$apiUrl/local/clipboard-push?ip=$ip" -Method Post -Body $text -ContentType "text/plain" -TimeoutSec 3 -ErrorAction Stop
                                    $delivered = $true
                                } catch {}
                                if (-not $delivered) {
                                    $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
                                    $b64 = [Convert]::ToBase64String($bytes)
                                    $res = & $adb -s "${ip}:5555" shell am broadcast -a com.dexstudios.dex.SET_CLIPBOARD -e text_b64 "$b64" 2>&1
                                    if ($res -match "Broadcast completed") { $delivered = $true }
                                }
                                if ($delivered) { $clipLastPushed = $text }
                            }
                        }
                    } catch {}
                }
                Start-Sleep -Milliseconds 2000
            }
        }).AddArgument($ctl).AddArgument($api).AddArgument($adbPath)

        $script:clipWorkerPs = $ps
        $script:clipWorkerRs = $rs
        $script:clipWorkerAsync = $ps.BeginInvoke()
    } catch {
        Write-Trace "Clipboard STA worker failed to start: $($_.Exception.Message)"
    }
}

function Stop-ClipboardSyncWorker {
    if ($null -eq $script:clipWorkerPs) { return }
    try {
        $script:clipWorkerControl.Enqueue(@{ Stop = $true })
        # Let the loop notice the Stop (bounded), then force-stop before disposing the
        # runspace — disposing a runspace with a running pipeline BLOCKS until it ends.
        if ($null -ne $script:clipWorkerAsync) {
            try { $script:clipWorkerAsync.AsyncWaitHandle.WaitOne(2000) | Out-Null } catch {}
        }
        try { $script:clipWorkerPs.Stop() } catch {}
        if ($null -ne $script:clipWorkerAsync) {
            try { $script:clipWorkerPs.EndInvoke($script:clipWorkerAsync) } catch {}
        }
        try { $script:clipWorkerPs.Dispose() } catch {}
        if ($null -ne $script:clipWorkerRs) {
            try { $script:clipWorkerRs.Dispose() } catch {}
        }
    } catch {}
    $script:clipWorkerPs = $null
    $script:clipWorkerRs = $null
    $script:clipWorkerAsync = $null
}

function Load-ThumbnailAsync($targetItem, $fullPath, $fileName, $isDir, $metaStr) {
    if ($isDir -or -not ("ThumbHelper" -as [type])) { return }

    # Local files only (the cache key needs the file's path + mtime). Phone thumbs are fresh per browse.
    $cacheKey = $null
    if ($fullPath -match '^[A-Za-z]:\\') {
        try {
            $fi = Get-Item -LiteralPath $fullPath -ErrorAction Stop
            $cacheKey = "$fullPath|$($fi.Length)|$($fi.LastWriteTime.Ticks)"
        } catch { }
    }

    # Already decoded for this key — reuse the frozen bitmap on the UI thread (cheap, no re-decode).
    if ($cacheKey -and $script:thumbCache.ContainsKey($cacheKey)) {
        $cached = $script:thumbCache[$cacheKey]
        if ($cached) {
            $uiAction = [System.Action]{
                $targetItem.Content = [PSCustomObject]@{ Name = $fileName; FullPath = $fullPath; IsDir = $isDir; Meta = $metaStr; Thumb = $cached; NoThumb = 'Collapsed' }
            }
            $script:wpfWindow.Dispatcher.InvokeAsync($uiAction) | Out-Null
        }
        return
    }

    # PS 5.1 cannot run scriptblock delegates on threadpool threads ("no runspace
    # available") — the old BeginInvoke threw here and aborted the whole listing loop.
    # Decode on the UI thread at Background priority instead: 100px decodes are fast and
    # run between input events (the UI thread is the one place scriptblocks can run).
    # ponytail: UI-thread decode; if video/PDF-heavy folders jank, move GetThumb onto a
    # method-group Task (real CLR delegates DO run on threadpool threads).
    $script:wpfWindow.Dispatcher.InvokeAsync([System.Windows.Threading.DispatcherPriority]::Background, [System.Action]{
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
                if ($cacheKey -and -not $script:thumbCache.ContainsKey($cacheKey)) {
                    $script:thumbCache[$cacheKey] = $bs
                    # Evict a batch of oldest entries instead of nuking the whole cache, so
                    # recently viewed folders keep their thumbs on re-entry.
                    if ($script:thumbCache.Count -gt $script:thumbCacheMax) {
                        foreach ($key in @($script:thumbCache.Keys | Select-Object -First 25)) {
                            $script:thumbCache.TryRemove($key, [ref]$null) | Out-Null
                        }
                    }
                }
                $targetItem.Content = [PSCustomObject]@{ Name = $fileName; FullPath = $fullPath; IsDir = $isDir; Meta = $metaStr; Thumb = $bs; NoThumb = 'Collapsed' }
            }
        } catch {
            # Silently fail for unsupported files
        }
    }) | Out-Null
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

    # SAF convention: a tree URI and its document URI identify the SAME folder
    # (tree/primary:DCIM == document/primary:DCIM). Normalize both so drill-down from a
    # granted root pushes instead of crossfading, and pop matches the parent correctly.
    $oldCmp = $oldPath -replace '/tree/', '/document/'
    $newCmp = $newPath -replace '/tree/', '/document/'

    if ($newCmp.StartsWith($oldCmp, [System.StringComparison]::OrdinalIgnoreCase)) { return 'push' }
    if ($oldCmp.StartsWith($newCmp, [System.StringComparison]::OrdinalIgnoreCase)) { return 'pop' }
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
    # Re-entry during a pending async SAF browse is allowed — it supersedes the stale
    # request (the older completion sees a bumped sequence and drops itself). Other
    # concurrent loads stay guarded.
    if ($script:isLoadingDir -and -not $script:asyncBrowsePending) { return }
    $script:dirLoadSeq++
    $mySeq = $script:dirLoadSeq
    $script:isLoadingDir = $true
    $script:asyncBrowsePending = $false   # supersede any in-flight browse
    
    try {
        if ($null -ne $script:searchTimer) { $script:searchTimer.Stop() }
        
        # Edge Case 27: Normalize and sanitize directory path.
        # SAF content:// URIs are opaque document identifiers, NOT filesystem paths — they
        # must be passed to the phone verbatim. Prefixing "/" here (the ADB-era fallback)
        # made the SAF branch below never match, so phone browsing always came up empty.
        $isSafUri = $dirPath -like 'content://*'
        $isLocal = [System.IO.Path]::IsPathRooted($dirPath) -and $dirPath -match '^[A-Za-z]:\\'
        if ($isSafUri) {
            # Leave untouched: scheme://authority/tree|document/<id> stays exactly as-is.
        } elseif (-not $isLocal) {
            $dirPath = ($dirPath -replace '(?<!:)/+', '/').Trim()
            if (-not $dirPath.StartsWith("/")) { $dirPath = "/" + $dirPath }
            if (-not $dirPath.EndsWith("/")) { $dirPath = $dirPath + "/" }
        } else {
            if (-not $dirPath.EndsWith("\")) { $dirPath = $dirPath + "\" }
        }

        # Auto-reset search bar text so the new directory displays all items cleanly.
        # Placeholder matches the active mode: local history vs. phone folders.
        if ($null -ne $script:txtSearch) {
            $script:txtSearch.Text = if ($isSafUri) { "Search files..." } else { "Search transfers..." }
            $script:txtSearch.Foreground = $script:wpfWindow.FindResource("SecondaryTextBrush")
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
            # Show the Up button in every mode; disable it only at a hard root.
            # Local history: root is the download folder itself, but subfolders are
            # browsable (folder bundles) so Up must work while inside one.
            $atRoot = $false
            if ($isLocal) {
                $atRoot = $dirPath.TrimEnd('\') -notmatch '\\'  # drive root like C:\
            } elseif ($isSafUri) {
                $atRoot = $false
            } else {
                $atRoot = $dirPath -eq "/sdcard/" -or $dirPath -eq "/sdcard"
            }
            $script:btnUpDir.Visibility = 'Visible'
            if ($atRoot) {
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
                # Transfer History: newest files first, but also list subfolders so received
                # folder bundles (which recreate their relative structure under Downloads\DeX)
                # stay reachable and browsable. Directories sort after files, by name.
                $dirs = @(Get-ChildItem -Path $dirPath -Directory | Sort-Object Name)
                $files = @(Get-ChildItem -Path $dirPath -File | Sort-Object LastWriteTime -Descending | Select-Object -First 50)
                foreach ($child in @($files) + @($dirs)) {
                    if ($child.PSIsContainer) {
                        $lines += $child.Name + "\"
                        $dt = $child.LastWriteTime.ToString("MMM d, h:mm tt")
                        $script:localFileMeta[$child.Name] = "Folder · $dt"
                    } else {
                        $lines += $child.Name
                        $bytes = $child.Length
                        if ($bytes -ge 1GB) { $sz = "{0:N1} GB" -f ($bytes / 1GB) }
                        elseif ($bytes -ge 1MB) { $sz = "{0:N1} MB" -f ($bytes / 1MB) }
                        elseif ($bytes -ge 1KB) { $sz = "{0:N0} KB" -f ($bytes / 1KB) }
                        else { $sz = "$bytes B" }
                        $dt = $child.LastWriteTime.ToString("MMM d, h:mm tt")
                        $script:localFileMeta[$child.Name] = "$sz · $dt"
                    }
                }
            }
        } elseif ($script:isMockMode) {
            $lines = @("DeX_Transfers/")
            for ($i = 1; $i -le 49; $i++) {
                $ext = if ($i % 4 -eq 0) { ".pdf" } elseif ($i % 3 -eq 0) { ".mp4" } elseif ($i % 2 -eq 0) { ".png" } else { ".jpg" }
                $lines += "dummy_file_$i$ext"
            }
        } elseif ($dirPath.StartsWith("content://")) {
            # SAF File Explorer Mode: browse a granted phone folder over the WebSocket.
            # The HTTP round-trip can take up to 30s on a slow phone — run it on a
            # background thread so the tray UI never freezes, then render on the UI
            # thread when the phone answers. The async helper keeps isLoadingDir armed
            # until it finishes, so the reentrancy guard still holds end-to-end.
            Start-AsyncSafBrowse -DirPath $dirPath -Transition $transition -HadItems $hadItems -MySeq $mySeq
            return
        } else {
            # Unknown/non-SAF remote path (no longer used by File Explorer) — empty listing
            $lines = @()
        }

        # Render the listing on the UI thread (item building, empty state, stagger,
        # transition). For SAF folders this runs from the async browse completion.
        Show-DirectoryListing -DirPath $dirPath -Lines $lines -IsLocal $isLocal -IsSafUri $isSafUri -Transition $transition -HadItems $hadItems
    } finally {
        # SAF browse keeps the guard armed until its background fetch completes —
        # only the async completion (or a newer navigation) clears it.
        if (-not $script:asyncBrowsePending) {
            $script:isLoadingDir = $false
        }
    }
}

# Renders a prepared directory listing into the file grid: sorts phone entries, manages
# the empty-state overlay, builds ListBoxItems with staggered entrance, kicks off
# thumbnails and plays the push/pop/switch motion. MUST run on the UI thread.
function Show-DirectoryListing([string]$DirPath, $Lines, [bool]$IsLocal, [bool]$IsSafUri, [string]$Transition, [bool]$HadItems) {
    # Sort phone listings: folders first, then files, alphabetically (SAF returns unsorted).
    if ($IsSafUri -and $Lines.Count -gt 0) {
        $Lines = @($Lines | Sort-Object @{Expression = { $_ -notlike '*/' }}, @{Expression = { $_ }})
    }
    
    # Edge Case 5: Empty Folder State Toggle
    $emptyOverlay = $script:wpfWindow.FindName("emptyFolderState")
    if ($null -ne $emptyOverlay) {
        # Localize the overlay copy to the active mode: history vs. phone folders.
        $txtEmptyTitle = $script:wpfWindow.FindName("txtEmptyStateTitle")
        $txtEmptySub = $script:wpfWindow.FindName("txtEmptyStateSub")
        if ($null -ne $txtEmptyTitle -and $null -ne $txtEmptySub) {
            if ($IsLocal) {
                $txtEmptyTitle.Text = "No transfers yet"
                $txtEmptySub.Text = "Received files appear here"
            } elseif ($IsSafUri) {
                $txtEmptyTitle.Text = "Folder is empty"
                $txtEmptySub.Text = "This phone folder has no files"
            } else {
                $txtEmptyTitle.Text = "Nothing here"
                $txtEmptySub.Text = "This directory is empty"
            }
        }
        if (-not $Lines -or $Lines.Count -eq 0) {
            $emptyOverlay.Visibility = 'Visible'
            $emptyOverlay.Opacity = 1.0
        } else {
            $emptyOverlay.Visibility = 'Collapsed'
            $emptyOverlay.Opacity = 0.0
        }
    }
    
    $idx = 0
    foreach ($line in $Lines) {
        $isDir = $line.EndsWith("/") -or $line.EndsWith("\")
        $name = $line.TrimEnd('/', '\', '*', '@', '=')
        if ($isDir) {
            $full = if ($IsLocal) { $DirPath + $name + "\" } else { $DirPath + $name + "/" }
            $template = $script:wpfWindow.Resources["FolderGridTemplate"]
        } else {
            $full = $DirPath + $name
            $template = $script:wpfWindow.Resources["FileGridTemplate"]
        }
        
        # SAF File Explorer mode: files/dirs are identified by their document URI, not a path
        $phoneMeta = $null
        if (-not $IsLocal -and $script:phoneFileMeta -and $script:phoneFileMeta[$name]) {
            $phoneMeta = $script:phoneFileMeta[$name]
            $full = $phoneMeta.Uri
        }
        
        $meta = if ($IsLocal -and $script:localFileMeta[$name]) {
            $script:localFileMeta[$name]
        } elseif ($phoneMeta -and -not $phoneMeta.IsDir) {
            Format-FileSize $phoneMeta.Size
        } else { "" }
        $thumb = $null
        $noThumb = 'Visible'
        $phoneThumbB64 = $null
        if ($phoneMeta -and $phoneMeta.Thumb) { $phoneThumbB64 = $phoneMeta.Thumb }
        $item = New-Object System.Windows.Controls.ListBoxItem
        $item.Content = [PSCustomObject]@{ Name = $name; FullPath = $full; IsDir = $isDir; Meta = $meta; Thumb = $thumb; NoThumb = $noThumb }
        $item.ContentTemplate = $template
        $item.Tag = $full
        
        if ($IsLocal -and -not $isDir) {
            # History context menu is file-only: Delete/Open on a directory would be
            # dangerous or useless, and folder bundles are navigated by double-click.
            $ctx = $script:wpfWindow.Resources["TransferContextMenu"]
            if ($ctx) { $item.ContextMenu = $ctx }
            
            # Kick off async thumbnail generation
            Load-ThumbnailAsync $item $full $name $isDir $meta
        }
        
        # Staggered entrance for the first viewport of items; the rest appear instantly so
        # a large folder load doesn't spawn dozens of concurrent entrance animations.
        if ($idx -lt 12) {
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
        }

        $script:lbFiles.Items.Add($item)

        # Phone (SAF) thumbnails decode off the UI thread, mirroring local thumbnails.
        if ($phoneThumbB64) {
            Load-PhoneThumbAsync $item $phoneThumbB64 $name $full $isDir $meta
        }
        $idx++
    }

    # New listing is ready — play the push/pop/switch motion (first load
    # has no snapshot and just staggers in).
    if ($Transition -ne 'none' -and $HadItems) { Start-ExplorerTransition $Transition }
}

# Browsing a phone folder runs on a background thread (a slow phone can take up to 30s
# to answer). The result is marshaled back to the UI thread where Show-DirectoryListing
# renders it. A sequence token drops stale completions if the user navigates away while
# the browse is in flight, and the reentrancy guard stays armed until we finish.
# NOTE: closures created with GetNewClosure run in a detached module scope — $script:
# variable REASSIGNMENT inside them is lost. State writes therefore go through
# Complete-AsyncBrowse (a script-scope function, which owns the real script state);
# the closure only mutates members of the shared $browse hashtable and passes values.
function Start-AsyncSafBrowse([string]$DirPath, [string]$Transition, [bool]$HadItems, [int]$MySeq) {
    $script:asyncBrowsePending = $true
    $ip = Get-FileExplorerTargetIp
    if (-not $ip) {
        # No eligible device: fall through to an empty listing (History hint already shown
        # by the toggle). Release the guard synchronously.
        $script:asyncBrowsePending = $false
        $script:isLoadingDir = $false
        Show-DirectoryListing -DirPath $DirPath -Lines @() -IsLocal $false -IsSafUri $true -Transition $Transition -HadItems $HadItems
        return
    }

    # Shared mutable result container: member writes propagate through the closure
    # boundary (same object reference), unlike $script: reassignment.
    $browse = @{ Lines = @(); Meta = @{} }

    # PS 5.1 cannot run scriptblock delegates on threadpool threads — the old
    # BeginInvoke threw here, breaking every phone folder navigation. The browse POST
    # (up to 30s on a slow device) runs in a background Job; a poll timer completes it.
    if ($script:safBrowseJob) { Remove-Job $script:safBrowseJob -Force -ErrorAction SilentlyContinue }
    if ($script:safBrowseTimer) { $script:safBrowseTimer.Stop() }
    $api = $global:DeXLocalApi
    $browseIp = $ip
    $job = Start-Job -ScriptBlock {
        param($apiBase, $targetIp, $folderUri)
        $lines = @()
        $meta = @{}
        try {
            $body = @{ ip = $targetIp; folderUri = $folderUri } | ConvertTo-Json
            $res = Invoke-RestMethod -Uri "$apiBase/local/dex/browse" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 30 -ErrorAction Stop
            if ($res.entries) {
                foreach ($prop in $res.entries.PSObject.Properties) {
                    $name = $prop.Value.name
                    $isDir = [bool]$prop.Value.isDirectory
                    $size = [long]$prop.Value.size
                    $thumb = if ($prop.Value.thumb) { $prop.Value.thumb } else { $null }
                    $meta[$name] = @{ Uri = $prop.Name; Size = $size; IsDir = $isDir; Thumb = $thumb }
                    if ($isDir) { $lines += "$name/" } else { $lines += $name }
                }
            }
        } catch {
            Write-Trace "Browse Error: $_"
        }
        return @{ Lines = $lines; Meta = $meta }
    } -ArgumentList $api, $browseIp, $DirPath
    $script:safBrowseJob = $job
    # Plain tick (NO GetNewClosure): closures cannot resolve functions from later
    # dot-sourced files — Complete-AsyncBrowse failed from a detached closure. A plain
    # scriptblock runs in the real engine scope; the call context rides in script state.
    $script:safBrowseContext = @{ MySeq = $MySeq; DirPath = $DirPath; Transition = $Transition; HadItems = $HadItems }
    $timer = New-Object System.Windows.Threading.DispatcherTimer
    $timer.Interval = [TimeSpan]::FromMilliseconds(250)
    $script:safBrowseTimer = $timer
    $timer.Add_Tick({
        $job = $script:safBrowseJob
        if (-not $job) { $script:safBrowseTimer.Stop(); return }
        # A freshly spawned job starts in NotStarted — keep polling until it reaches a terminal state.
        if ($job.State -notin @('Completed','Failed','Stopped')) { return }
        $script:safBrowseTimer.Stop()
        $script:safBrowseJob = $null
        $result = $null
        if ($job.State -eq 'Completed') { $result = Receive-Job $job -ErrorAction SilentlyContinue }
        Remove-Job $job -Force -ErrorAction SilentlyContinue
        $ctx = $script:safBrowseContext
        # Complete-AsyncBrowse drops stale results (sequence token), releases the load
        # guard and renders — all real script-state writes live in that function.
        if ($result -and $result.Meta) {
            Complete-AsyncBrowse -MySeq $ctx.MySeq -Meta $result.Meta -Lines $result.Lines -DirPath $ctx.DirPath -Transition $ctx.Transition -HadItems $ctx.HadItems
        } else {
            Complete-AsyncBrowse -MySeq $ctx.MySeq -Meta @{} -Lines @() -DirPath $ctx.DirPath -Transition $ctx.Transition -HadItems $ctx.HadItems
        }
    })
    $timer.Start()
}

# Runs on the UI thread when an async SAF browse finishes. Drops stale results (the user
# navigated on while we were fetching) and otherwise renders the listing and releases the
# load guard. A script-scope function so its $script: writes hit the real state.
function Complete-AsyncBrowse([int]$MySeq, $Meta, $Lines, [string]$DirPath, [string]$Transition, [bool]$HadItems) {
    if ($MySeq -ne $script:dirLoadSeq) { return }   # stale — a newer navigation owns the view
    $script:asyncBrowsePending = $false
    $script:isLoadingDir = $false
    $script:phoneFileMeta = $Meta
    Show-DirectoryListing -DirPath $DirPath -Lines $Lines -IsLocal $false -IsSafUri $true -Transition $Transition -HadItems $HadItems
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
        try {
            $bmp = New-Object System.Windows.Media.Imaging.BitmapImage
            $bmp.BeginInit()
            $bmp.CacheOption = [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
            $bmp.StreamSource = $ms
            $bmp.EndInit()
            $bmp.Freeze()
            return $bmp
        } finally {
            # BitmapCacheOption.OnLoad decodes eagerly, so the stream is safe to release
            # even if EndInit throws (no leak on the failure path either).
            $ms.Dispose()
        }
    } catch { return $null }
}

# Async decode of a phone (SAF) base64 thumbnail, mirroring Load-ThumbnailAsync so the
# base64 decode + BitmapImage construction never blocks the UI thread during a browse.
# PS 5.1 note: scriptblock delegates cannot run on threadpool threads, so the decode runs
# on the UI thread at Background priority (idle time), same as Load-ThumbnailAsync.
function Load-PhoneThumbAsync($targetItem, $base64, $name, $full, $isDir, $meta) {
    $script:wpfWindow.Dispatcher.InvokeAsync([System.Windows.Threading.DispatcherPriority]::Background, [System.Action]{
        $bmp = Convert-PhoneThumb $base64
        if ($bmp) {
            $targetItem.Content = [PSCustomObject]@{ Name = $name; FullPath = $full; IsDir = $isDir; Meta = $meta; Thumb = $bmp; NoThumb = 'Collapsed' }
        }
    }) | Out-Null
}

# Resolves the phone the File Explorer browses. Explorer is scoped to the SELECTED device
# (the one the user tapped, which opened the panel in History mode). The device must be
# online over the WebSocket AND on the LAN — WAN (same-email) devices are History-only.
# Falls back to the first eligible LAN device so the toggle never silently targets nothing.
function Get-FileExplorerTargetIp {
    try {
        $devices = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/devices" -TimeoutSec 2 -ErrorAction Stop

        # Preferred: the selected device, when it is online + LAN + trusted.
        if ($script:selectedDeviceIp) {
            $sel = $devices | Where-Object { $_.ip -eq $script:selectedDeviceIp } | Select-Object -First 1
            if ($sel -and $sel.isOnline -and $sel.isLan -and ($sel.isPaired -or $sel.isAutoTrusted)) {
                return $sel.ip
            }
        }

        # Fallback: first eligible LAN device (keeps direct open + mock flows working).
        $target = $devices | Where-Object { $_.isOnline -and $_.isLan -and ($_.isPaired -or $_.isAutoTrusted) } | Select-Object -First 1
        if ($target -and $target.ip) { return $target.ip }
    } catch {}
    $lastIp = (Get-ItemProperty "HKCU:\Software\DeX" -Name "LastIp" -ErrorAction SilentlyContinue).LastIp
    if ($lastIp) { return $lastIp }
    return $null
}

# True when the File Explorer toggle may activate: a device was selected AND it is
# online + LAN + trusted. WAN-only (same-email) devices never qualify — History only.
function Test-ExplorerEligibleDevice {
    if (-not $script:selectedDeviceIp) { return $false }
    try {
        $devices = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/devices" -TimeoutSec 2 -ErrorAction Stop
        $sel = $devices | Where-Object { $_.ip -eq $script:selectedDeviceIp } | Select-Object -First 1
        return ($null -ne $sel -and $sel.isOnline -and $sel.isLan -and ($sel.isPaired -or $sel.isAutoTrusted))
    } catch { return $false }
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

function Save-EngineState {
    try {
        if (-not (Test-Path "HKCU:\Software\DeX")) {
            New-Item -Path "HKCU:\Software\DeX" -Force | Out-Null
        }
        if ($script:currentDirPath) {
            Set-ItemProperty -Path "HKCU:\Software\DeX" -Name "LastFolder" -Value $script:currentDirPath -ErrorAction SilentlyContinue
        }
        if ($null -ne $script:wpfWindow) {
            Set-ItemProperty -Path "HKCU:\Software\DeX" -Name "WindowLeft" -Value ([int]$script:wpfWindow.Left) -ErrorAction SilentlyContinue
            Set-ItemProperty -Path "HKCU:\Software\DeX" -Name "WindowTop" -Value ([int]$script:wpfWindow.Top) -ErrorAction SilentlyContinue
        }
    } catch {}
}

function Restore-EngineState {
    try {
        $reg = Get-ItemProperty "HKCU:\Software\DeX" -ErrorAction SilentlyContinue
        if ($null -ne $reg) {
            if ($reg.LastFolder -and (Test-Path $reg.LastFolder)) {
                $script:currentDirPath = $reg.LastFolder
            }
            if ($null -ne $reg.WindowLeft -and $null -ne $reg.WindowTop -and $null -ne $script:wpfWindow) {
                $screen = [System.Windows.Forms.Screen]::FromPoint([System.Drawing.Point]::new([int]$reg.WindowLeft, [int]$reg.WindowTop))
                if ($null -ne $screen) {
                    $script:wpfWindow.Left = [double]$reg.WindowLeft
                    $script:wpfWindow.Top = [double]$reg.WindowTop
                }
            }
        }
    } catch {}
}


