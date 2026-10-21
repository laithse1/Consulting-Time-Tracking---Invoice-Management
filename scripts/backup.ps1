param([string]$Database = './data/consulting.db', [string]$Destination = './backups')
$ErrorActionPreference = 'Stop'
$dbFile = (Resolve-Path -LiteralPath $Database).Path
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$backupFile = Join-Path (Resolve-Path -LiteralPath $Destination).Path ('consulting-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.db')
# Python's SQLite backup API produces a consistent copy even while the source is open.
python (Join-Path $PSScriptRoot 'backup.py') $dbFile $backupFile
if ($LASTEXITCODE -ne 0) { throw 'Backup or integrity verification failed' }
Write-Host "Verified backup: $backupFile"
