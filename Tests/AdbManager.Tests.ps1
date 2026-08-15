$ErrorActionPreference = 'Stop'

Describe "AdbManager" {
    BeforeAll {
        $modulePath = "w:\CodeDeX\DeX\MSIX_Source\bin\Modules\AdbManager.psm1"
        Import-Module $modulePath -Force
        function global:Write-Trace { param($msg) }
    }

    Context "Get-OrDownloadAdbBinary" {
        It "Returns global path if it exists" {
            $global:AdbExePath = "C:\dummy_adb\adb.exe"
            Mock Test-Path { return $true } -ModuleName AdbManager
            Mock Get-Command { return $null } -ModuleName AdbManager
            
            $result = Get-OrDownloadAdbBinary
            $result | Should Be "C:\dummy_adb\adb.exe"
        }

        It "Checks system path if global path is invalid" {
            $global:AdbExePath = $null
            Mock Test-Path { return $false } -ModuleName AdbManager
            Mock Get-Command { return [PSCustomObject]@{ Source = "C:\sys\adb.exe" } } -ModuleName AdbManager
            
            $result = Get-OrDownloadAdbBinary
            $result | Should Be "C:\sys\adb.exe"
        }
    }

    Context "Invoke-AdbConnect" {
        It "Returns failure if ADB binary not available" {
            Mock Get-OrDownloadAdbBinary { return "C:\missing\adb.exe" } -ModuleName AdbManager
            Mock Test-Path { return $false } -ModuleName AdbManager
            
            $result = Invoke-AdbConnect -Target "127.0.0.1"
            $result.Success | Should Be $false
            $result.Message | Should Be "ADB binary not available."
        }
        
        It "Fails if TCP ping times out" {
            Mock Get-OrDownloadAdbBinary { return "C:\dummy\adb.exe" } -ModuleName AdbManager
            Mock Test-Path { return $true } -ModuleName AdbManager
            Mock adb { return "" } -ModuleName AdbManager
            
            $result = Invoke-AdbConnect -Target "192.0.2.1:5555"
            $result.Success | Should Be $false
        }
    }

    Context "Invoke-AdbPair" {
        It "Returns failure if ADB binary not available" {
            Mock Get-OrDownloadAdbBinary { return "C:\missing\adb.exe" } -ModuleName AdbManager
            Mock Test-Path { return $false } -ModuleName AdbManager
            
            $result = Invoke-AdbPair -Target "127.0.0.1" -Pin "123456"
            $result | Should Be $false
        }
        
        It "Fails if TCP ping times out" {
            Mock Get-OrDownloadAdbBinary { return "C:\dummy\adb.exe" } -ModuleName AdbManager
            Mock Test-Path { return $true } -ModuleName AdbManager
            
            $result = Invoke-AdbPair -Target "192.0.2.1:5555" -Pin "123456"
            $result | Should Be $false
        }
    }
}
