param(
    [switch]$Required
)

$ErrorActionPreference = 'Stop'

$requiredVariables = @(
    'GOODSHORT_BASE_URL',
    'GOODSHORT_PARTNER_ID',
    'GOODSHORT_API_KEY',
    'DRAMA_EXTERNAL_ID'
)

$missing = $requiredVariables | Where-Object {
    [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_))
}
if ($missing.Count -gt 0) {
    $message = "Missing required environment variables: $($missing -join ', ')"
    if ($Required) {
        throw "GoodShort real smoke is required. $message"
    }
    Write-Host "SKIP GoodShort real smoke. $message"
    exit 0
}

$isWindowsPlatform = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
    [System.Runtime.InteropServices.OSPlatform]::Windows
)
$jdk = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
if ($isWindowsPlatform -and (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
    $env:JAVA_HOME = $jdk
    $env:Path = "$jdk\bin;$env:Path"
}

Write-Host 'GoodShort real smoke configuration is complete. Running the real free-content integration test.'
$mavenWrapper = if ($isWindowsPlatform) { '.\mvnw.cmd' } else { './mvnw' }
& $mavenWrapper '-Preal-smoke-tests' '-Dtest=GoodShortFreeContentIntegrationTest' test
if ($LASTEXITCODE -ne 0) {
    throw "GoodShort integration test failed with exit code $LASTEXITCODE"
}
