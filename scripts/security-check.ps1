param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'native-command.ps1')
Push-Location $projectRoot
try {
  $arguments = @('--no-transfer-progress', 'verify')
  if ($SkipTests) { $arguments += '-DskipTests' }
  if (-not $SkipTests) {
    & (Join-Path $PSScriptRoot 'test-native-command.ps1')
    Invoke-CheckedNative -FilePath 'npm.cmd' -ArgumentList @('test')
  }
  Invoke-CheckedNative -FilePath '.\mvnw.cmd' -ArgumentList $arguments
  Invoke-CheckedNative -FilePath 'node.exe' -ArgumentList @('scripts/audit-java.mjs')
  Invoke-CheckedNative -FilePath 'npm.cmd' -ArgumentList @('audit', '--audit-level=high')
  $sbom = Invoke-CheckedNative -FilePath 'npm.cmd' -ArgumentList @('sbom', '--sbom-format=cyclonedx', '--omit=dev')
  [IO.File]::WriteAllText((Join-Path $projectRoot 'target/sbom-frontend.json'), ($sbom -join "`n"), (New-Object Text.UTF8Encoding($false)))
} finally { Pop-Location }
