# .env를 프로세스 환경변수로 로드한 뒤 전달받은 명령을 실행한다.
$ErrorActionPreference = "Stop"

$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) ".env"
if (-not (Test-Path $envFile)) {
    Write-Error "env file not found: $envFile"
    exit 1
}

foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*$' -or $line -match '^\s*#') { continue }
    $idx = $line.IndexOf('=')
    if ($idx -lt 0) { continue }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim().Trim('"')
    if ($key) { [Environment]::SetEnvironmentVariable($key, $value, "Process") }
}

if ($env:SARAMQUANT_IAM_KEY_ACCESS) { $env:AWS_ACCESS_KEY_ID = $env:SARAMQUANT_IAM_KEY_ACCESS }
if ($env:SARAMQUANT_IAM_KEY_SECRET) { $env:AWS_SECRET_ACCESS_KEY = $env:SARAMQUANT_IAM_KEY_SECRET }
if (-not $env:AWS_REGION) { $env:AWS_REGION = "ap-northeast-2" }

# PATH에 java가 없는 로컬 환경을 위해 IDE가 설치한 JDK를 자동 탐색한다.
if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}

if ($args.Count -eq 0) {
    Write-Error "usage: run-with-env.ps1 <command> [args...]"
    exit 1
}

$rest = @()
if ($args.Count -gt 1) { $rest = $args[1..($args.Count - 1)] }
& $args[0] @rest
exit $LASTEXITCODE
