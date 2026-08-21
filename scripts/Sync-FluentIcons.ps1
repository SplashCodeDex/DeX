$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$configPath = Join-Path $scriptDir "fluent_icons.json"

# Resolve-Path fails if the path doesn't exist, so we build the path string directly
$destDir = [System.IO.Path]::GetFullPath((Join-Path $scriptDir "..\core\designsystem\src\commonMain\composeResources\drawable"))

if (-not (Test-Path $destDir)) {
    New-Item -ItemType Directory -Path $destDir | Out-Null
}

if (-not (Test-Path $configPath)) {
    Write-Error "Config file not found: $configPath"
    exit 1
}

$config = Get-Content $configPath | ConvertFrom-Json

Write-Host "Fetching Fluent UI System Icons GitHub tree..."
try {
    $treeData = Invoke-RestMethod -Uri "https://api.github.com/repos/microsoft/fluentui-system-icons/git/trees/master?recursive=1"
} catch {
    Write-Error "Failed to fetch GitHub tree. $_"
    exit 1
}

$svgPaths = $treeData.tree | Where-Object { $_.path -match "^assets/.*\.svg$" } | Select-Object -ExpandProperty path

$downloadCount = 0
$missing = @()

foreach ($key in $config.psobject.properties) {
    $localBaseName = $key.Name
    $fluentName = $key.Value

    $fluentFileName = "ic_fluent_${fluentName}.svg"
    $match = $svgPaths | Where-Object { $_ -match "(/|^)$fluentFileName$" } | Select-Object -First 1

    if (-not $match) {
        Write-Warning "Could not find $fluentFileName in the Fluent UI repository!"
        $missing += $fluentName
        continue
    }

    $destFile = "ic_fluent_${localBaseName}.svg"
    $destPath = Join-Path $destDir $destFile

    # URL encode path segments
    $encodedPath = ($match -split '/' | ForEach-Object { [uri]::EscapeDataString($_) }) -join '/'
    $rawUrl = "https://raw.githubusercontent.com/microsoft/fluentui-system-icons/master/$encodedPath"

    try {
        Invoke-WebRequest -Uri $rawUrl -OutFile $destPath -UseBasicParsing
        Write-Host "Downloaded: $destFile"
        $downloadCount++
    } catch {
        Write-Error "Failed to download $rawUrl : $_"
    }
}

Write-Host ""
Write-Host "Successfully synced $downloadCount icons."
if ($missing.Count -gt 0) {
    Write-Host "Missing/Unresolved icons:" -ForegroundColor Yellow
    $missing | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
}
Write-Host "Done!"
