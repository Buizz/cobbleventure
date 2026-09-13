$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $projectRoot ".local-server.pid"

if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Host "기록된 로컬 서버가 없습니다. 이미 종료된 상태입니다."
    exit 0
}

$serverProcessId = [int](Get-Content -LiteralPath $pidFile -Raw)
$serverProcess = Get-Process -Id $serverProcessId -ErrorAction SilentlyContinue

if ($serverProcess) {
    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $serverProcessId" -ErrorAction SilentlyContinue
    $commandLine = [string]$processInfo.CommandLine
    if (-not $commandLine -or $commandLine.IndexOf($projectRoot, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
        Write-Error "기록된 PID $serverProcessId 프로세스가 Battle Lab 소유인지 확인할 수 없어 종료하지 않았습니다."
        exit 1
    }
    & taskkill.exe /PID $serverProcessId /T /F | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "프로세스 $serverProcessId 종료에 실패했습니다."
        exit 1
    }
}

Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
Write-Host "Cobbleventure Battle Lab 로컬 서버를 종료했습니다."
