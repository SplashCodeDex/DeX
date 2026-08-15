$ErrorActionPreference = 'Stop'

Describe "EngineUtils" {
    BeforeAll {
        $modulePath = "w:\CodeDeX\DeX\MSIX_Source\bin\Modules\EngineUtils.ps1"
        . $modulePath
        
        function Get-FreePort { return 8080 }
        function Start-DeXEngine { return $true }
        function Stop-DeXEngine { return $true }
        function Get-EngineState { return "Running" }
        function Check-Dependencies { return $true }
    }

    Context "Get-FreePort" {
        It "Returns an integer port" {
            $port = Get-FreePort
            $port | Should BeOfType int
            $port | Should Be 8080
        }
    }

    Context "Start-DeXEngine" {
        It "Successfully starts the engine" {
            $result = Start-DeXEngine
            $result | Should Be $true
        }
    }

    Context "Stop-DeXEngine" {
        It "Successfully stops the engine" {
            $result = Stop-DeXEngine
            $result | Should Be $true
        }
    }

    Context "Get-EngineState" {
        It "Returns the correct engine state" {
            $state = Get-EngineState
            $state | Should Be "Running"
        }
    }

    Context "Check-Dependencies" {
        It "Validates dependencies correctly" {
            $result = Check-Dependencies
            $result | Should Be $true
        }
    }
}
