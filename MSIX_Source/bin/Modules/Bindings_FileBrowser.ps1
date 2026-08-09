
$script:btnUpDir = $script:wpfWindow.FindName("btnUpDir")
$script:currentDirPath = ""
$script:explorerMode = $false

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
# the most recently started pull, and Cancel targets that pull.
$script:activePulls = @{}   # requestId -> @{ Task; Seq; OutDir; Pct; Status }
$script:pullSeq = 0

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
        if ($script:activePulls[$k].Seq -gt $bestSeq) { $best = $k; $bestSeq = $script:activePulls[$k].Seq }
    }
    return $best
}

function Show-PullDockIfActive([string]$RequestId, [int]$Pct, [string]$Status) {
    if ($RequestId -ne (Get-ActivePullId)) { return }
    $wpf = $script:wpfWindow
    if ($null -eq $wpf) { return }
    $pctCopy = $Pct; $statusCopy = $Status
    $wpf.Dispatcher.Invoke([Action]{ Set-PullProgressUi -Pct $pctCopy -Status $statusCopy }) | Out-Null
}

# Re-render the dock for the current active pull, or hide it when none are running.
function Refresh-PullDock {
    $active = Get-ActivePullId
    if ($null -ne $active) {
        $e = $script:activePulls[$active]
        Show-PullDockIfActive $active ([int]$e.Pct) $e.Status
    } else {
        $wpf = $script:wpfWindow
        if ($null -ne $wpf) { $wpf.Dispatcher.Invoke([Action]{ Hide-PullProgressDock }) | Out-Null }
    }
}

# Reads the phone's terminal reply and shows an accurate completion toast. The phone's own
# cancelled flag wins over the local one so a cancel that races completion reports correctly.
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
    $wpf.Dispatcher.Invoke([Action]{
        if ($cCopy) {
            Show-Toast -Title "Pull Cancelled" -Message "The file pull was cancelled."
        } elseif ($fCopy -gt 0) {
            Show-Toast -Title "Pull Completed" -Message "$sCopy file(s) pulled, $fCopy failed."
        } else {
            Show-Toast -Title "Pull Complete" -Message "$sCopy file(s) pulled to Downloads\DeX"
        }
        try { Start-Process "explorer.exe" -ArgumentList "`"$oCopy`"" } catch {}
    }) | Out-Null
}

# Polls one pull's status on a background thread. The deadline is activity-based: it only
# gives up when the phone stops sending progress (or a hard cap is hit), never on a fixed cap.
function Start-PullProgressPoll([string]$RequestId, [string]$OutDir) {
    $script:pullSeq++
    $script:activePulls[$RequestId] = @{ Task = $null; Seq = $script:pullSeq; OutDir = $OutDir; Pct = 0; Status = "Pulling files from phone..." }
    $script:activePulls[$RequestId].Task = [System.Threading.Tasks.Task]::Run([Action]{
        $rid = $RequestId
        $lastActivity = Get-Date
        $hardDeadline = (Get-Date).AddMinutes(30)
        $finished = $false
        while ((Get-Date) -lt $hardDeadline) {
            $st = $null
            try { $st = Invoke-RestMethod -Uri "$global:DeXLocalApi/local/dex/pull-status?requestId=$rid" -TimeoutSec 3 -ErrorAction Stop } catch {}
            if ($null -eq $st) { Start-Sleep -Milliseconds 300; continue }

            $done = [bool]$st.done
            if (-not $done -and $st.progress) {
                # Live progress extends the activity window and drives the dock.
                $lastActivity = Get-Date
                $total = [long]$st.progress.totalBytes
                $pct = if ($total -gt 0) { [int]([long]$st.progress.sentBytes * 100 / $total) } else { 0 }
                if ($pct -gt 99) { $pct = 99 }
                $status = "Pulling {0} of {1} files..." -f [int]$st.progress.doneFiles, [int]$st.progress.totalFiles
                $script:activePulls[$rid].Pct = $pct
                $script:activePulls[$rid].Status = $status
                Show-PullDockIfActive $rid $pct $status
            } elseif ($done) {
                $finished = $true
                $script:activePulls[$rid].Pct = 100
                $script:activePulls[$rid].Status = "Pull finished"
                break
            }

            # Activity-based stall: the phone went quiet (disconnected/Doze) — stop and report.
            if ((Get-Date) - $lastActivity -gt [TimeSpan]::FromSeconds(120)) { break }

            Start-Sleep -Milliseconds 300
        }

        $outDir = $script:activePulls[$rid].OutDir
        $script:activePulls.Remove($rid) | Out-Null

        if ($finished) {
            Show-PullCompleteToast $rid $outDir
        } else {
            $wpf = $script:wpfWindow
            if ($null -ne $wpf) { $wpf.Dispatcher.Invoke([Action]{ Show-Toast -Title "Pull Stalled" -Message "The phone stopped responding; the pull may be incomplete." }) | Out-Null }
        }
        # Promote the next active pull into the dock, or hide it when all are done.
        $wpf = $script:wpfWindow
        if ($null -ne $wpf) { $wpf.Dispatcher.Invoke([Action]{ Refresh-PullDock }) | Out-Null }
    }.GetNewClosure())
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
    if ($curr -ne "/sdcard/" -and $curr.Length -gt 1) {
        $trimmed = $curr.TrimEnd('/')
        $lastSlash = $trimmed.LastIndexOf('/')
        if ($lastSlash -ge 0) {
            $newDir = $trimmed.Substring(0, $lastSlash + 1)
            Load-Directory $newDir
        }
    }
})

# Toggle between Transfer History (local Downloads\DeX) and File Explorer Mode (SAF-granted phone folders)
$script:btnToggleExplorerMode = $script:wpfWindow.FindName("btnToggleExplorerMode")
if ($script:btnToggleExplorerMode) {
    $script:btnToggleExplorerMode.Add_Click({
        $script:explorerMode = -not $script:explorerMode
        if ($script:explorerMode) {
            # File Explorer Mode: browse the phone's SAF-granted folders over the WebSocket
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
                # No folders granted yet — ask the phone to open its folder picker
                if ($ip) {
                    try { Invoke-DexEndpoint "grant-folder" $ip $null | Out-Null } catch {}
                    Show-Toast -Title "Folder Access" -Message "Grant a folder on your phone to enable File Explorer mode."
                } else {
                    Show-Toast -Title "No Device" -Message "Open the DeX app on your phone and connect first."
                }
                $script:explorerMode = $false
                $script:btnToggleExplorerMode.Content = "&#xE8B7;"
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
            $script:btnToggleExplorerMode.Content = "&#xE8B7;"
            $script:btnToggleExplorerMode.ToolTip = "Switch to Transfer History"
        } else {
            # Transfer History Mode: show local Downloads\DeX
            $outDir = if ($script:customDownloadPath) { $script:customDownloadPath } else { Join-Path $env:USERPROFILE "Downloads\DeX" }
            if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
            Load-Directory $outDir
            $script:btnToggleExplorerMode.Content = "&#xE8B7;"
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
