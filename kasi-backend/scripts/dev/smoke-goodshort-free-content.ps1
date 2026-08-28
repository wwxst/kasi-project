$ErrorActionPreference = 'Stop'

$required = @(
    'GOODSHORT_BASE_URL',
    'GOODSHORT_PARTNER_ID',
    'GOODSHORT_API_KEY',
    'DRAMA_EXTERNAL_ID'
)

$missing = $required | Where-Object {
    [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_))
}
if ($missing.Count -gt 0) {
    throw "Missing required environment variables: $($missing -join ', ')"
}

$ffmpegCommand = if ([string]::IsNullOrWhiteSpace($env:APP_FFMPEG_PATH)) {
    'ffmpeg'
} else {
    $env:APP_FFMPEG_PATH
}
if (-not (Get-Command $ffmpegCommand -ErrorAction SilentlyContinue)) {
    throw 'FFmpeg executable was not found. Set APP_FFMPEG_PATH or add ffmpeg to PATH.'
}

$jdk = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
if (Test-Path (Join-Path $jdk 'bin\java.exe')) {
    $env:JAVA_HOME = $jdk
    $env:Path = "$jdk\bin;$env:Path"
}

Write-Host 'GoodShort smoke prerequisites are present. Running the real free-content integration test.'
& .\mvnw.cmd '-Dgoodshort.integration=true' '-Dtest=GoodShortFreeContentIntegrationTest' test
if ($LASTEXITCODE -ne 0) {
    throw "GoodShort integration test failed with exit code $LASTEXITCODE"
}
