$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $workspacePath
if (-not $env:APP_TOKEN -or $env:APP_TOKEN.Length -lt 24) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $env:APP_TOKEN = [Convert]::ToBase64String($bytes)
    Write-Host 'Generated a token for this session. Copy it into the local login screen:'
    Write-Host $env:APP_TOKEN
}
Push-Location -LiteralPath (Join-Path $workspacePath 'frontend')
try {
    npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
} finally { Pop-Location }
$staticPath = Join-Path $workspacePath 'backend/src/main/resources/static'
New-Item -ItemType Directory -Force -Path $staticPath | Out-Null
Copy-Item -Path (Join-Path $workspacePath 'frontend/dist/*') -Destination $staticPath -Recurse -Force
mvn -f backend/pom.xml clean package
if ($LASTEXITCODE -ne 0) { throw 'Backend tests or packaging failed' }
$javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
& $javaExecutable -jar backend/target/invoice-manager-1.0.0.jar --debug=false
