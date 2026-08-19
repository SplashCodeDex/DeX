[CmdletBinding()]
param([string]$Configuration = "Release")

$ErrorActionPreference = 'Stop'

try {
    $Root = $PSScriptRoot
    $PayloadDir = Join-Path $Root "MSIX_Compose_Payload"
    $MsixPath = Join-Path $Root "DeX-Compose.msix"

    Write-Host "Cleaning payload directory..." -ForegroundColor Cyan
    if (Test-Path $PayloadDir) {
        Remove-Item -Path $PayloadDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $PayloadDir | Out-Null

    # 1. Build Compose Multiplatform Distributable
    Write-Host "Building Compose Desktop Distributable..." -ForegroundColor Cyan
    Set-Location $Root
    .\gradlew.bat :composeApp:createDistributable
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle createDistributable failed."
    }

    $ComposeOutDir = Join-Path $Root "composeApp\build\compose\binaries\main\app\DeX"
    if (-not (Test-Path $ComposeOutDir)) {
        throw "Compose output directory not found: $ComposeOutDir"
    }

    Write-Host "Copying Compose payload..." -ForegroundColor Cyan
    Copy-Item -Path "$ComposeOutDir\*" -Destination $PayloadDir -Recurse -Force

    # 2. Build C# Share Target
    $ProjDir = Join-Path $Root "DeXShareTarget"
    Write-Host "Building C# Project ($Configuration)..." -ForegroundColor Cyan
    Set-Location $ProjDir
    dotnet build -c $Configuration
    if ($LASTEXITCODE -ne 0) {
        throw "dotnet build failed (exit code $LASTEXITCODE)."
    }

    $CsSourceDir = Join-Path $ProjDir "bin\$Configuration\net10.0-windows10.0.22000.0"
    if (-not (Test-Path $CsSourceDir)) {
        throw "Build output directory not found: $CsSourceDir"
    }

    Write-Host "Copying C# Share Target bridge payload..." -ForegroundColor Cyan
    Copy-Item -Path "$CsSourceDir\*" -Destination $PayloadDir -Recurse -Force

    # 3. Copy Assets
    Write-Host "Copying Assets from legacy MSIX_Source..." -ForegroundColor Cyan
    $AssetsSource = Join-Path $Root "MSIX_Source\Assets"
    $AssetsDest = Join-Path $PayloadDir "Assets"
    Copy-Item -Path $AssetsSource -Destination $AssetsDest -Recurse -Force

    # 4. Generate Dual-Executable AppxManifest.xml
    Write-Host "Generating AppxManifest.xml..." -ForegroundColor Cyan
    $ManifestContent = @"
<?xml version="1.0" encoding="utf-8"?>
<Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10" 
         xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10" 
         xmlns:desktop="http://schemas.microsoft.com/appx/manifest/desktop/windows10"
         xmlns:desktop2="http://schemas.microsoft.com/appx/manifest/desktop/windows10/2"
         xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
         IgnorableNamespaces="uap desktop desktop2 rescap">
  <Identity Name="CodeDeX.DeX" Publisher="CN=CodeDeX" Version="10.1.2.0" ProcessorArchitecture="x64" />
  <Properties>
    <DisplayName>DeX - Next Gen Local Send</DisplayName>
    <PublisherDisplayName>CodeDeX</PublisherDisplayName>
    <Logo>Assets\StoreLogo.png</Logo>
  </Properties>
  <Dependencies>
    <TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.22000.0" MaxVersionTested="10.0.22621.0" />
  </Dependencies>
  <Resources>
    <Resource Language="en-us" />
  </Resources>
  <Applications>
    <!-- Main Application routes to Compose DeX.exe -->
    <Application Id="App" Executable="DeX.exe" EntryPoint="Windows.FullTrustApplication">
      <uap:VisualElements DisplayName="DeX" Description="Send files directly between your devices" BackgroundColor="black" Square150x150Logo="Assets\Square150x150Logo.png" Square44x44Logo="Assets\Square44x44Logo.png">
        <uap:DefaultTile Wide310x150Logo="Assets\Wide310x150Logo.png" />
      </uap:VisualElements>
      <Extensions>
        <!-- Share Target Definition routes specifically to DeXShareTarget.exe -->
        <uap:Extension Category="windows.shareTarget" Executable="DeXShareTarget.exe" EntryPoint="Windows.FullTrustApplication">
          <uap:ShareTarget>
            <uap:SupportedFileTypes>
              <uap:SupportsAnyFileType />
            </uap:SupportedFileTypes>
            <uap:DataFormat>StorageItems</uap:DataFormat>
          </uap:ShareTarget>
        </uap:Extension>
        
        <!-- Startup Task routes to Compose DeX.exe -->
        <desktop:Extension Category="windows.startupTask" Executable="DeX.exe" EntryPoint="Windows.FullTrustApplication">
          <desktop:StartupTask TaskId="DeXStartup" Enabled="true" DisplayName="DeX Engine" />
        </desktop:Extension>
      </Extensions>
    </Application>
  </Applications>
  <Extensions>
    <desktop2:Extension Category="windows.firewallRules">
      <desktop2:FirewallRules Executable="DeX.exe">
        <desktop2:Rule Direction="in" IPProtocol="UDP" Profile="all" />
        <desktop2:Rule Direction="in" IPProtocol="TCP" Profile="all" />
        <desktop2:Rule Direction="out" IPProtocol="UDP" Profile="all" />
        <desktop2:Rule Direction="out" IPProtocol="TCP" Profile="all" />
      </desktop2:FirewallRules>
    </desktop2:Extension>
    <desktop2:Extension Category="windows.firewallRules">
      <desktop2:FirewallRules Executable="DeXShareTarget.exe">
        <desktop2:Rule Direction="in" IPProtocol="UDP" Profile="all" />
        <desktop2:Rule Direction="in" IPProtocol="TCP" Profile="all" />
        <desktop2:Rule Direction="out" IPProtocol="UDP" Profile="all" />
        <desktop2:Rule Direction="out" IPProtocol="TCP" Profile="all" />
      </desktop2:FirewallRules>
    </desktop2:Extension>
  </Extensions>
  <Capabilities>
    <rescap:Capability Name="runFullTrust" />
    <Capability Name="internetClientServer" />
    <Capability Name="privateNetworkClientServer" />
  </Capabilities>
</Package>
"@
    
    $ManifestPath = Join-Path $PayloadDir "AppxManifest.xml"
    # Ensure UTF-8 encoding without BOM for manifest (UWP standard)
    [System.IO.File]::WriteAllText($ManifestPath, $ManifestContent, [System.Text.Encoding]::UTF8)

    # 5. Pack MSIX Container
    # robustly find makeappx.exe
    $sdkPaths = @(
        "C:\Program Files (x86)\Windows Kits\10\bin\*\x64\makeappx.exe",
        "C:\Program Files\Windows Kits\10\bin\*\x64\makeappx.exe"
    )
    $makeappx = $null
    foreach ($path in $sdkPaths) {
        $makeappx = (Get-ChildItem $path -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
        if ($makeappx) { break }
    }
    
    if (-not $makeappx) { 
        throw "makeappx.exe not found. Please install the Windows 10/11 SDK." 
    }

    Write-Host "Running makeappx pack..." -ForegroundColor Cyan
    
    Set-Location $Root
    & $makeappx pack /d $PayloadDir /p $MsixPath /o
    if ($LASTEXITCODE -ne 0) { 
        throw "makeappx pack failed (exit code $LASTEXITCODE)." 
    }

    Write-Host "Signing DeX-Compose MSIX..." -ForegroundColor Cyan
    & .\SignMSIX.ps1 -TargetMsix "DeX-Compose.msix"
    if ($LASTEXITCODE -ne 0) {
        throw "SignMSIX.ps1 failed (exit code $LASTEXITCODE)."
    }

    Write-Host "Successfully built, packed, and signed DeX Compose MSIX: $MsixPath" -ForegroundColor Green
}
catch {
    Set-Location $PSScriptRoot -ErrorAction SilentlyContinue
    Write-Host "`n[ERROR] PackMSIX-Compose.ps1 failed:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
