
$script:btnUpDir = $script:wpfWindow.FindName("btnUpDir")
$script:currentDirPath = ""
$script:explorerMode = $false

# The device the user tapped (which opened the panel in History mode). File Explorer is
# scoped to THIS device; the toggle is disabled unless it is online + LAN + trusted.
$script:selectedDeviceIp = ""
$script:selectedDeviceFp = ""

$script:isLoadingDir = $false
$script:isShowingMenu = $false
$script:showMenuGuardTimer = $null
$script:lastMouseUpTime = [DateTime]::MinValue


# Forwards a PC File Explorer request to the local engine, which relays it to the phone
# over the WebSocket and returns the phone's reply. Returns $null on failure.
function Invoke-DexEndpoint([string]$Name, [string]$Ip, $Extra) {
    $body = @{ ip = $Ip }
    if ($Extra) { foreach ($k in $Extra.Keys) { $body[$k] = $Extra[$k] } }
    return Invoke-RestMethod -Uri "$global:DeXLocalApi/local/dex/$Name" -Method Post `
        -Body ($body | ConvertTo-Json -Depth 10) -ContentType "application/json" -ErrorAction Stop
}

# --- Pull progress dock (live progress + cancel for pulling files off the phone) ---
# Multiple pulls can overlap safely: each requestId gets its own poll task; the dock shows
# the most recently started pull, and Cancel targets that pull. A ConcurrentDictionary
# keeps the map safe: the poll task runs on a threadpool thread while the UI thread reads
# it. Only the UI thread mutates entries (via Dispatcher marshaling in Start-PullProgressPoll).
$script:activePulls = [System.Collections.Concurrent.ConcurrentDictionary[string, object]]::new()
$script:pullSeq = 0
$script:pullPollTimer = $null

function Set-PullProgressUi([int]$Pct, [string]$Status) {
    $dock = $script:wpfWindow.FindName("dockPullProgress")
    $bar = $script:wpfWindow.FindName("prgPullProgress")
    $txt = $script:wpfWindow.FindName("txtPullTitle")
    if ($null -eq $dock) { return }
    if ($dock.Visibility -eq 'Collapsed') {
        $dock.Visibility = 'Visible'
        $dock.Opacity = 0
        $dock.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $null)
        $da = New-Object System.Windows.Media.Animation.DoubleAnimation
        $da.To = 1.0; $da.Duration = [TimeSpan]::FromMilliseconds(200)
        $dock.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $da)
    }
    if ($null -ne $txt) { $txt.Text = $Status }
    if ($null -ne $bar) { $bar.Value = $Pct }
}

function Hide-PullProgressDock {
    $dock = $script:wpfWindow.FindName("dockPullProgress")
    if ($null -eq $dock) { return }
    $da = New-Object System.Windows.Media.Animation.DoubleAnimation
    $da.To = 0.0; $da.Duration = [TimeSpan]::FromMilliseconds(250)
    $da.Completed = { $dock.Visibility = 'Collapsed' }.GetNewClosure()
    $dock.BeginAnimation([System.Windows.UIElement]::OpacityProperty, $da)
}

# The requestId whose progress is shown in the shared dock (the most recently started one).
function Get-ActivePullId {
    $best = $null; $bestSeq = -1
    foreach ($k in @($script:activePulls.Keys)) {
        $entry = $script:activePulls[$k]
        if ($null -ne $entry -and $entry.Seq -gt $bestSeq) { $best = $k; $bestSeq = $entry.Seq }
    }
    return $best
}

function Show-PullDockIfActive([string]$RequestId, [int]$Pct, [string]$Status) {
    if ($RequestId -ne (Get-ActivePullId)) { return }
    $wpf = $script:wpfWindow
    if ($null -eq $wpf) { return }
    $pctCopy = $Pct; $statusCopy = $Status
    $wpf.Dispatcher.InvokeAsync([Action]{ Set-PullProgressUi -Pct $pctCopy -Status $statusCopy }) | Out-Null
}

# Re-render the dock for the current active pull, or hide it when none are running.
function Refresh-PullDock {
    $active = Get-ActivePullId
    if ($null -ne $active) {
        $e = $script:activePulls[$active]
        Show-PullDockIfActive $active ([int]$e.Pct) $e.Status
    } else {
        $wpf = $script:wpfWindow
        if ($null -ne $wpf) { $wpf.Dispatcher.InvokeAsync([Action]{ Hide-PullProgressDock }) | Out-Null }
    }
}

# Reads the phone's terminal reply and shows an accurate completion toast. The phone's own
# cancelled flag wins over the local one so a cancel that races completion reports correctly.
# The status HTTP round trip runs on the CALLER's thread (a background poll thread); only the
# toast / explorer / list-refresh UI work is marshaled onto the dispatcher.
function Show-PullCompleteToast([string]$RequestId, [string]$OutDir) {
    $st = $null
    try { $st = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/dex/pull-status?requestId=$RequestId" -TimeoutSec 3 -ErrorAction Stop } catch {}
    if ($null -eq $st) { return }
    $result = $st.result
    $cancelled = $false; $savedN = 0; $failedN = 0
    if ($result) {
        $cancelled = [bool]$result.cancelled
        if ($result.saved) { $savedN = @($result.saved.PSObject.Properties).Count }
        if ($result.failed) { $failedN = @($result.failed.PSObject.Properties).Count }
    } else {
        $cancelled = [bool]$st.cancelled
    }
    $wpf = $script:wpfWindow
    if ($null -eq $wpf) { return }
    $cCopy = $cancelled; $sCopy = $savedN; $fCopy = $failedN; $oCopy = $OutDir
    # Capture $script:currentDirPath as a local: a GetNewClosure closure reads $script:
    # variables from its detached scope (always empty), so the direct read below would
    # never match and the history list would not refresh after a completed pull.
    $dirCopy = $script:currentDirPath
    $wpf.Dispatcher.InvokeAsync([Action]{
        if ($cCopy) {
            Show-Toast -Title "Pull Cancelled" -Message "The file pull was cancelled."
        } elseif ($fCopy -gt 0) {
            Show-Toast -Title "Pull Completed" -Message "$sCopy file(s) pulled, $fCopy failed."
        } else {
            Show-Toast -Title "Pull Complete" -Message "$sCopy file(s) pulled to Downloads\DeX"
            # Only surface the folder on a clean success — never after cancel/partial failure.
            try { Start-Process "explorer.exe" -ArgumentList "`"$oCopy`"" } catch {}
        }
        # Pulled files land in the local history folder: refresh the list so they appear
        # immediately instead of on the next manual reload.
        if ($dirCopy -match '^[A-Za-z]:\\') {
            Load-Directory $dirCopy
        }
    }) | Out-Null
}

# Polls one pull's status on a background JOB. The deadline is activity-based: it only
# gives up when the phone stops sending progress (or a hard cap is hit), never on a fixed cap.
# PS 5.1 cannot run scriptblock delegates on threadpool threads — the old
# Task.Run([Action]{...}) threw "no runspace available" here, so the poll loop ran in a
# real background job instead (the codebase's Start-AsyncSafBrowse pattern), and a single
# UI-thread DispatcherTimer drains the job output. The drain tick is a PLAIN scriptblock
# (no GetNewClosure): a closure runs in a detached module scope where direct $script: reads
# are empty, so it could never touch $script:activePulls. Thread-safety: the job only
# computes values; every mutation of $script:activePulls happens on the UI thread.
function Start-PullProgressPoll([string]$RequestId, [string]$OutDir) {
    $script:pullSeq++
    $entry = @{ Task = $null; Seq = $script:pullSeq; OutDir = $OutDir; Pct = 0; Status = "Pulling files from phone..." }
    $script:activePulls[$RequestId] = $entry

    $api = $global:DeXLocalApi
    $rid = $RequestId
    $job = Start-Job -ScriptBlock {
        param($apiBase, $requestId)
        $lastActivity = Get-Date
        $hardDeadline = (Get-Date).AddMinutes(30)
        $finished = $false
        while ((Get-Date) -lt $hardDeadline) {
            $st = $null
            try { $st = Invoke-RestMethod -Uri "$apiBase/local/dex/pull-status?requestId=$requestId" -TimeoutSec 3 -ErrorAction Stop } catch {}
            if ($null -eq $st) { Start-Sleep -Milliseconds 300; continue }

            $done = [bool]$st.done
            if (-not $done -and $st.progress) {
                # Live progress extends the activity window and drives the dock.
                $lastActivity = Get-Date
                $total = [long]$st.progress.totalBytes
                $pct = if ($total -gt 0) { [int]([long]$st.progress.sentBytes * 100 / $total) } else { 0 }
                if ($pct -gt 99) { $pct = 99 }
                $status = "Pulling {0} of {1} files..." -f [int]$st.progress.doneFiles, [int]$st.progress.totalFiles
                [pscustomobject]@{ Pct = $pct; Status = $status; Done = $false; Stalled = $false }
            } elseif ($done) {
                $finished = $true
                [pscustomobject]@{ Pct = 100; Status = "Pull finished"; Done = $true; Stalled = $false }
            }
            if ($finished) { break }

            # Activity-based stall: the phone went quiet (disconnected/Doze) — stop and report.
            if ((Get-Date) - $lastActivity -gt [TimeSpan]::FromSeconds(120)) { break }

            Start-Sleep -Milliseconds 300
        }
        if (-not $finished) {
            [pscustomobject]@{ Pct = 0; Status = "Stalled"; Done = $false; Stalled = $true }
        }
    } -ArgumentList $api, $rid
    $entry.Task = $job

    # Ensure the shared drain timer is running (created once; stops when no pulls remain).
    if (-not $script:pullPollTimer) {
        $script:pullPollTimer = New-Object System.Windows.Threading.DispatcherTimer
        $script:pullPollTimer.Interval = [TimeSpan]::FromMilliseconds(250)
        $script:pullPollTimer.Add_Tick({
            $anyActive = $false
            foreach ($k in @($script:activePulls.Keys)) {
                $e = $script:activePulls[$k]
                $job = $null
                if ($null -ne $e) { $job = $e.Task }
                if ($null -eq $job) { continue }
                $anyActive = $true

                $out = @(Receive-Job $job -ErrorAction SilentlyContinue)
                $terminal = $null
                foreach ($r in $out) {
                    if ($r.Done -or $r.Stalled) { $terminal = $r; break }
                    $e.Pct = [int]$r.Pct
                    $e.Status = $r.Status
                    Show-PullDockIfActive $k ([int]$r.Pct) $r.Status
                }

                if ($null -ne $terminal) {
                    # Terminal: remove the entry, promote the next pull, and report.
                    Remove-Job $job -Force -ErrorAction SilentlyContinue
                    $e.Task = $null
                    if ($terminal.Done) {
                        $outDir = $e.OutDir
                        $script:activePulls.TryRemove($k, [ref]$null) | Out-Null
                        Refresh-PullDock
                        Show-PullCompleteToast $k $outDir
                    } else {
                        $script:activePulls.TryRemove($k, [ref]$null) | Out-Null
                        Refresh-PullDock
                        Show-Toast -Title "Pull Stalled" -Message "The phone stopped responding; the pull may be incomplete."
                    }
                } elseif ($job.State -notin @('Running', 'NotStarted')) {
                    # The job ended without a terminal record — clean up quietly.
                    Remove-Job $job -Force -ErrorAction SilentlyContinue
                    $e.Task = $null
                    $script:activePulls.TryRemove($k, [ref]$null) | Out-Null
                    Refresh-PullDock
                }
            }
            if (-not $anyActive) {
                $t = $script:pullPollTimer
                if ($t) { $t.Stop(); $script:pullPollTimer = $null }
            }
        })
    }
    if (-not $script:pullPollTimer.IsEnabled) { $script:pullPollTimer.Start() }
}

$script:btnCancelPull = $script:wpfWindow.FindName("btnCancelPull")
if ($script:btnCancelPull) {
    $script:btnCancelPull.Add_Click({
        $active = Get-ActivePullId
        if ($active) {
            try {
                Invoke-RestMethod -Uri "$global:DeXLocalApi/local/dex/pull-cancel?requestId=$active" -Method Post -TimeoutSec 3 -ErrorAction SilentlyContinue | Out-Null
            } catch {}
        }
    })
}


$script:btnUpDir.Add_Click({
    $curr = $script:currentDirPath
    if ([string]::IsNullOrEmpty($curr) -or $curr -eq "Phone Folders") { return }

    # SAF File Explorer mode: content:// document URIs. The parent of a document URI is the
    # same URI with the last %2F-encoded segment removed; at the granted root (tree URI)
    # "up" returns to the Phone Folders root list.
    if ($curr -like 'content://*') {
        $docMarker = '/document/'
        $idx = $curr.IndexOf($docMarker)
        if ($idx -ge 0) {
            $docId = $curr.Substring($idx + $docMarker.Length)
            $lastSep = $docId.LastIndexOf('%2F', [System.StringComparison]::OrdinalIgnoreCase)
            if ($lastSep -gt 0) {
                $parent = $curr.Substring(0, $idx + $docMarker.Length + $lastSep)
                Load-Directory $parent
                return
            }
        }
        # Tree URI or root document: back to the granted-folders list.
        Show-PhoneFoldersRoot
        return
    }

    # Local history mode: subfolders are browsable (folder bundles) — go one level up.
    if ($curr -match '^[A-Za-z]:\\') {
        $trimmed = $curr.TrimEnd('\')
        if ($trimmed.Length -gt 3) {   # not at the drive root
            $newDir = $trimmed.Substring(0, $trimmed.LastIndexOf('\') + 1)
            Load-Directory $newDir
        }
        return
    }

    # Legacy remote path (/sdcard/...)
    $trimmed = $curr.TrimEnd('/')
    $lastSlash = $trimmed.LastIndexOf('/')
    if ($lastSlash -ge 0) {
        $newDir = $trimmed.Substring(0, $lastSlash + 1)
        Load-Directory $newDir
    }
})

# E8B7 = folder glyph. XAML decodes "&#xE8B7;" at parse time, but assigning that literal
# string from PowerShell renders the raw text — always set the actual character instead.
function Set-ExplorerToggleGlyph($btn) {
    if ($null -ne $btn) { $btn.Content = [string][char]0xE8B7 }
}

# Shows the File Explorer root: the phone's SAF-granted folders. Shared by the mode toggle
# and by btnUpDir when "up" leaves a granted folder's root. Falls back to the async grant
# flow when the phone has no granted folders yet.
function Show-PhoneFoldersRoot {
    $ip = Get-FileExplorerTargetIp
    $folders = @()
    if ($ip) {
        try {
            $res = Invoke-DexEndpoint "list-folders" $ip $null
            $folders = @($res.folders.PSObject.Properties | ForEach-Object {
                [PSCustomObject]@{ Name = $_.Value.name; Uri = $_.Value.uri }
            })
        } catch {
            $folders = @()
        }
    }

    if ($folders.Count -eq 0) {
        # No folders granted yet — ask the phone to open its folder picker (async, non-blocking)
        if ($ip) {
            Start-GrantFolderFlow $ip
            Show-Toast -Title "Folder Access" -Message "Grant a folder on your phone to enable File Explorer mode."
        } else {
            Show-Toast -Title "No Device" -Message "Open the DeX app on your phone and connect first."
        }
        $script:explorerMode = $false
        Set-ExplorerToggleGlyph $script:btnToggleExplorerMode
        return
    }

    # Show granted folders as the file list (crossfade from transfer history)
    $hadItems = $script:lbFiles.Items.Count -gt 0
    if ($hadItems) { New-ExplorerSnapshot }
    $script:lbFiles.Items.Clear()
    foreach ($folder in $folders) {
        $item = New-Object System.Windows.Controls.ListBoxItem
        $item.Content = [PSCustomObject]@{ Name = $folder.Name; FullPath = $folder.Uri; IsDir = $true; Meta = "Phone folder"; Thumb = $null; NoThumb = 'Visible' }
        $item.ContentTemplate = $script:wpfWindow.Resources["FolderGridTemplate"]
        $item.Tag = $folder.Uri
        $script:lbFiles.Items.Add($item)
    }
    if ($hadItems) { Start-ExplorerTransition 'switch' }
    $script:currentDirPath = "Phone Folders"
    Set-ExplorerToggleGlyph $script:btnToggleExplorerMode
    $up = $script:btnUpDir
    if ($null -ne $up) { $up.Visibility = 'Visible'; $up.Opacity = 0.4; $up.Cursor = [System.Windows.Input.Cursors]::Arrow }
}

# Asks the phone to open its SAF folder picker. Runs the HTTP call on a background thread so
# the WPF UI never freezes for the (up to 190s) grant window, and applies the result on the
# UI thread. The phone's own picker result is surfaced; a failure just toasts.
# PS 5.1 note: scriptblock delegates cannot run on threadpool threads, so the grant POST
# runs in a background Job; a poll timer applies the result on the UI thread.
function Start-GrantFolderFlow([string]$Ip) {
    if ($script:grantJob) { Remove-Job $script:grantJob -Force -ErrorAction SilentlyContinue }
    if ($script:grantTimer) { $script:grantTimer.Stop() }
    $api = $global:DeXLocalApi
    $job = Start-Job -ScriptBlock {
        param($apiBase, $targetIp)
        try {
            $res = Invoke-RestMethod -Uri "$apiBase/local/dex/grant-folder" -Method Post `
                -Body (@{ ip = $targetIp } | ConvertTo-Json) -ContentType "application/json" -TimeoutSec 200 -ErrorAction Stop
            return [bool]$res.granted
        } catch {
            return $false
        }
    } -ArgumentList $api, $Ip
    $script:grantJob = $job
    # Plain tick (NO GetNewClosure): closures cannot resolve functions from later
    # dot-sourced files — a plain scriptblock runs in the real engine scope.
    $timer = New-Object System.Windows.Threading.DispatcherTimer
    $timer.Interval = [TimeSpan]::FromMilliseconds(250)
    $script:grantTimer = $timer
    $timer.Add_Tick({
        $job = $script:grantJob
        if (-not $job) { $script:grantTimer.Stop(); return }
        # A freshly spawned job starts in NotStarted — keep polling until it reaches a terminal state.
        if ($job.State -notin @('Completed','Failed','Stopped')) { return }
        $script:grantTimer.Stop()
        $script:grantJob = $null
        $granted = $false
        if ($job.State -eq 'Completed') { $granted = [bool](Receive-Job $job -ErrorAction SilentlyContinue) }
        Remove-Job $job -Force -ErrorAction SilentlyContinue
        if ($granted) {
            Show-Toast -Title "Folder Granted" -Message "Your phone folder is now accessible from File Explorer."
            # Folders changed — reload the root so the fresh grant shows up immediately,
            # but only if the user is still looking at the File Explorer panel.
            $fe = $script:wpfWindow.FindName("FileExplorer")
            if ($fe -and $fe.Visibility -eq 'Visible') { Show-PhoneFoldersRoot }
        }
        # A false result (user cancelled the picker) needs no extra toast: the
        # "Grant a folder" prompt already told the user what to do.
    })
    $timer.Start()
}

# Toggle between Transfer History (local Downloads\DeX) and File Explorer Mode (SAF-granted phone folders)
$script:btnToggleExplorerMode = $script:wpfWindow.FindName("btnToggleExplorerMode")
if ($script:btnToggleExplorerMode) {
    $script:btnToggleExplorerMode.Add_Click({
        $script:explorerMode = -not $script:explorerMode
        if ($script:explorerMode) {
            # File Explorer Mode: browse the SELECTED device's SAF-granted folders. The
            # toggle is LAN+online+trusted only; WAN (same-email) devices stay History-only.
            if (-not (Test-ExplorerEligibleDevice)) {
                $script:explorerMode = $false
                Set-ExplorerToggleGlyph $script:btnToggleExplorerMode
                Show-Toast -Title "Explorer Unavailable" -Message "Tap a phone connected on your network to browse its files."
                return
            }
            Show-PhoneFoldersRoot
        } else {
            # Transfer History Mode: show local Downloads\DeX
            $outDir = if ($script:customDownloadPath) { $script:customDownloadPath } else { Join-Path $env:USERPROFILE "Downloads\DeX" }
            if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
            Load-Directory $outDir
            Set-ExplorerToggleGlyph $script:btnToggleExplorerMode
            $script:btnToggleExplorerMode.ToolTip = "Toggle File Explorer Mode"
        }
    })
}

$script:customDownloadPath = ""
$script:dockTimer = $null


$btnChange = $script:wpfWindow.FindName("btnChangeDownloadPath")
if ($null -ne $btnChange) {
    $btnChange.Add_Click({
        Add-Type -AssemblyName System.Windows.Forms
        $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
        $dialog.Description = "Select Download Destination Directory"
        if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            $script:customDownloadPath = $dialog.SelectedPath
            $dispName = [System.IO.Path]::GetFileName($script:customDownloadPath)
            if ([string]::IsNullOrWhiteSpace($dispName)) { $dispName = $script:customDownloadPath }
            Show-DownloadDockToast $dispName
        }
    })
}

$script:lastDoubleClickTime = 0

$script:lbFiles.Add_MouseDoubleClick({
    # Edge Case 15: Double-Click Speed Threshold Guard
    $now = [DateTime]::Now.Ticks / [TimeSpan]::TicksPerMillisecond
    if ($now - $script:lastDoubleClickTime -lt 400) { return }
    $script:lastDoubleClickTime = $now
    
    $selectedItems = $script:lbFiles.SelectedItems
    if ($null -ne $selectedItems -and $selectedItems.Count -gt 0) {
        # Check if a single folder is double clicked
        if ($selectedItems.Count -eq 1) {
            $sel = $selectedItems[0]
            if ($null -ne $sel -and $null -ne $sel.Content) {
                $data = $sel.Content
                if ($data.IsDir) {
                    Load-Directory $data.FullPath
                    return
                }
            }
        }
        
        # Batch pull all selected file items
        $fileItems = @($selectedItems | Where-Object { $null -ne $_.Content -and -not $_.Content.IsDir })
        if ($fileItems.Count -eq 0) { return }
        
        $firstPath = $fileItems[0].Content.FullPath
        if ([System.IO.Path]::IsPathRooted($firstPath) -and $firstPath -match '^[A-Za-z]:\\') {
            $dangerousExts = @('.exe','.bat','.cmd','.ps1','.vbs','.vbe','.msi','.scr','.com','.pif','.wsf')
            $missing = @()
            foreach ($item in $fileItems) {
                $fp = $item.Content.FullPath
                if (-not (Test-Path $fp)) {
                    $missing += $item
                } else {
                    $ext = [System.IO.Path]::GetExtension($fp).ToLower()
                    if ($dangerousExts -contains $ext) {
                        Start-Process explorer.exe -ArgumentList "/select,`"$fp`""
                    } else {
                        Start-Process $fp
                    }
                }
            }
            if ($missing.Count -gt 0) {
                $missing | ForEach-Object { $script:lbFiles.Items.Remove($_) }
                Show-DownloadDockToast "$($missing.Count) file(s) missing."
            }
            return
        }
        
        $outDir = if ($script:customDownloadPath) { 
            $script:customDownloadPath 
        } else { 
            Join-Path $env:USERPROFILE "Downloads\DeX" 
        }
        
        try {
            if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
        } catch {
            $outDir = Join-Path $env:TEMP "dex"
            if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
        }
        
        # Pull selected phone files: ask the phone to push them back to the PC over the
        # WebSocket (they land in Downloads\DeX via the standard upload path). Async so we
        # can show live progress and allow cancel.
        $pullIp = Get-FileExplorerTargetIp
        $pullFiles = @($fileItems | ForEach-Object {
            $n = $_.Content.Name
            $sz = 0
            if ($script:phoneFileMeta -and $script:phoneFileMeta[$n]) { $sz = [long]$script:phoneFileMeta[$n].Size }
            @{ name = $n; uri = $_.Content.FullPath; size = $sz }
        })
        
        if ($pullIp) {
            try {
                $body = @{ ip = $pullIp; files = @($pullFiles) } | ConvertTo-Json -Depth 10
                $start = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/dex/pull" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 10 -ErrorAction Stop
                if ($start.requestId) {
                    Start-PullProgressPoll -RequestId $start.requestId -OutDir $outDir
                }
            } catch {
                Show-Toast -Title "Pull Failed" -Message "Could not start pulling from the phone."
            }
        }
        
        $dispName = if ($script:customDownloadPath) { 
            [System.IO.Path]::GetFileName($script:customDownloadPath) 
        } else { 
            "Downloads\DeX" 
        }
        
        if ($fileItems.Count -gt 1) {
            Show-DownloadDockToast "$($fileItems.Count) files to $dispName"
        } else {
            Show-DownloadDockToast $dispName
        }
    }
})

# Edge Case 11 & 14: lbFiles KeyDown for Ctrl+A (visible only), Escape deselect, and Enter key execution
$script:lbFiles.Add_KeyDown({
    param($sender, $e)
    if ($e.Key -eq [System.Windows.Input.Key]::A -and ($e.KeyboardDevice.Modifiers -band [System.Windows.Input.ModifierKeys]::Control)) {
        foreach ($item in $script:lbFiles.Items) {
            if ($item.Visibility -eq 'Visible') {
                $item.IsSelected = $true
            } else {
                $item.IsSelected = $false
            }
        }
        $e.Handled = $true
    } elseif ($e.Key -eq [System.Windows.Input.Key]::Escape) {
        $script:lbFiles.UnselectAll()
        $e.Handled = $true
    }
})
$btnPushFiles = $script:wpfWindow.FindName("btnPushFiles")
if ($btnPushFiles) { $btnPushFiles.Add_Click({ Invoke-MenuAction $actionPushFiles }) }
$btnPushFolder = $script:wpfWindow.FindName("btnPushFolder")
if ($btnPushFolder) { $btnPushFolder.Add_Click({ Invoke-MenuAction $actionPushFolder }) }
    $fileExplorerPanel.Add_PreviewDragOver({
        $e = $args[1]
        if ($e.Data.GetDataPresent([System.Windows.DataFormats]::FileDrop)) {
            $e.Effects = [System.Windows.DragDropEffects]::Copy
        } else {
            $e.Effects = [System.Windows.DragDropEffects]::None
        }
        $e.Handled = $true
    })
    $fileExplorerPanel.Add_PreviewDrop({
        $e = $args[1]
        if ($e.Data.GetDataPresent([System.Windows.DataFormats]::FileDrop)) {
            $droppedFiles = $e.Data.GetData([System.Windows.DataFormats]::FileDrop)
            if ($droppedFiles -and $droppedFiles.Count -gt 0) {
                $targetIp = (Get-ItemProperty "HKCU:\Software\DeX" -Name "LastIp" -ErrorAction SilentlyContinue).LastIp
                if ([string]::IsNullOrEmpty($targetIp)) { $targetIp = "127.0.0.1" }
                
                $allFiles = @()
                foreach ($path in $droppedFiles) {
                    if (Test-Path $path -PathType Container) {
                        $allFiles += (Get-ChildItem -Path $path -File -Recurse | Select-Object -ExpandProperty FullName)
                    } else {
                        $allFiles += $path
                    }
                }
                
                if ($allFiles.Count -gt 0) {
                    $exePath = Join-Path $PSScriptRoot "..\DeXShareTarget.exe"
                    $argsList = @("-IP", $targetIp) + $allFiles
                    Start-Process $exePath -ArgumentList $argsList
                }
            }
        }
        $e.Handled = $true
    })
