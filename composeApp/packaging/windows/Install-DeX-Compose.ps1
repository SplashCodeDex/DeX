[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$HasErrors = $false

# Requires Admin privileges to install to LocalMachine Root (for certificate)
if (-Not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Restarting script with Administrator privileges to install the certificate..." -ForegroundColor Yellow
    $Proc = Start-Process pwsh -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs -PassThru -Wait
    exit $Proc.ExitCode
}

$Root = (Get-Item $PSScriptRoot).Parent.Parent.Parent.FullName
$CertPath = Join-Path $PSScriptRoot "CodeDeX.cer"
$AppPath = Join-Path $Root "DeX-Compose.msix"

try {
    if (-Not (Test-Path $CertPath)) {
        throw "Could not find CodeDeX.cer in $PSScriptRoot"
    }
    
    if (-Not (Test-Path $AppPath)) {
        throw "Could not find DeX-Compose.msix in $Root"
    }

    Write-Host "Checking CodeDeX Dev Certificate..." -ForegroundColor Cyan
    $Cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($CertPath)
    
    $CertStore = New-Object System.Security.Cryptography.X509Certificates.X509Store("Root", "LocalMachine")
    $CertStore.Open("ReadOnly")
    $Exists = $CertStore.Certificates.Find("FindByThumbprint", $Cert.Thumbprint, $false)
    $CertStore.Close()

    if ($Exists.Count -gt 0) {
        Write-Host "Certificate already installed in Trusted Root." -ForegroundColor Green
    } else {
        Write-Host "Installing Certificate to Trusted Root..." -ForegroundColor Cyan
        Import-Certificate -FilePath $CertPath -CertStoreLocation "Cert:\LocalMachine\Root" | Out-Null
        Write-Host "Certificate installed successfully." -ForegroundColor Green
    }

    Write-Host "Installing/Updating DeX Compose MSIX package..." -ForegroundColor Cyan
    Add-AppxPackage -Path $AppPath -ForceUpdateFromAnyVersion -ForceApplicationShutdown
    Write-Host "DeX Compose installed/updated successfully." -ForegroundColor Green
}
catch {
    $HasErrors = $true
    Write-Host "An error occurred during installation:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
}

if ($HasErrors) {
    Write-Host "`nInstallation completed with errors. Press any key to exit..." -ForegroundColor Yellow
    $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
    exit 1
} else {
    Write-Host "`nInstallation completed successfully. Exiting in 3 seconds..." -ForegroundColor Green
    Start-Sleep -Seconds 3
}
