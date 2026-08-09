$script:txtSearch = $script:wpfWindow.FindName("txtSearch")
if ($script:txtSearch) {
    # The placeholder text varies by mode ("Search transfers..." / "Search files...").
    function Test-SearchPlaceholder([string]$Text) {
        return $Text -eq "Search transfers..." -or $Text -eq "Search files..."
    }
    $script:txtSearch.Add_GotFocus({
        if (Test-SearchPlaceholder $script:txtSearch.Text) {
            $script:txtSearch.Text = ""
            $script:txtSearch.Foreground = $script:wpfWindow.FindResource("PrimaryTextBrush")
        }
    })
    $script:txtSearch.Add_LostFocus({
        if ([string]::IsNullOrWhiteSpace($script:txtSearch.Text)) {
            # Restore the mode-appropriate placeholder.
            $isSaf = $script:currentDirPath -like 'content://*'
            $script:txtSearch.Text = if ($isSaf) { "Search files..." } else { "Search transfers..." }
            $script:txtSearch.Foreground = $script:wpfWindow.FindResource("SecondaryTextBrush")
        }
    })
    $script:searchTimer = New-Object System.Windows.Threading.DispatcherTimer
    $script:searchTimer.Interval = [TimeSpan]::FromMilliseconds(150)
    $script:searchTimer.Add_Tick({
        $script:searchTimer.Stop()
        $query = $script:txtSearch.Text.ToLower()
        if (Test-SearchPlaceholder $script:txtSearch.Text) { $query = "" }
        foreach ($item in $script:lbFiles.Items) {
            $name = if ($item.Content -and $item.Content.Name) { $item.Content.Name.ToLower() } else { "" }
            if ([string]::IsNullOrWhiteSpace($query) -or $name.Contains($query)) {
                $item.Visibility = 'Visible'
            } else {
                $item.Visibility = 'Collapsed'
            }
        }
    })

    $script:txtSearch.Add_TextChanged({
        if ($null -ne $script:searchTimer) {
            $script:searchTimer.Stop()
            $script:searchTimer.Start()
        }
    })
}
