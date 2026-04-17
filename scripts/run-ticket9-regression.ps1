[CmdletBinding()]
param(
    [switch]$IncludeExtended
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location $repoRoot
$env:GRADLE_USER_HOME = Join-Path $repoRoot ".gradle-home"

$tests = @(
    "com.example.mdmbackend.integration.AuthIntegrationTest",
    "com.example.mdmbackend.integration.DeviceSessionGuardIntegrationTest",
    "com.example.mdmbackend.integration.CommandIntegrationTest",
    "com.example.mdmbackend.integration.CommandDeliveryRefactorIntegrationTest",
    "com.example.mdmbackend.integration.ConfigCurrentIntegrationTest",
    "com.example.mdmbackend.integration.FcmWakeupIntegrationTest",
    "com.example.mdmbackend.integration.DeviceAppInventoryIntegrationTest",
    "com.example.mdmbackend.integration.TelemetryAdminReadIntegrationTest",
    "com.example.mdmbackend.integration.AuditIntegrationTest"
)

if ($IncludeExtended) {
    $tests += @(
        "com.example.mdmbackend.integration.EventBusAuditIntegrationTest",
        "com.example.mdmbackend.integration.UsageIntegrationTest",
        "com.example.mdmbackend.integration.UnlockPasswordConfigIntegrationTest"
    )
}

$gradleArgs = @("test", "--no-daemon")
foreach ($testClass in $tests) {
    $gradleArgs += @("--tests", $testClass)
}

Write-Host "Running backend regression suite:"
$tests | ForEach-Object { Write-Host " - $_" }

& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
