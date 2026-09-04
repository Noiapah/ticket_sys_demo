param(
  [switch]$SkipTests,
  [ValidateSet('exe', 'app-image')]
  [string]$Type = 'exe'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not $env:JAVA_HOME) { throw 'JAVA_HOME må peke til JDK 25.' }
$oldPackage = Join-Path $projectRoot 'target\jpackage'
if (Test-Path -LiteralPath $oldPackage) {
  Get-ChildItem -LiteralPath $oldPackage -Recurse -File | ForEach-Object { $_.IsReadOnly = $false }
  Remove-Item -LiteralPath $oldPackage -Recurse -Force
}
$maven = Join-Path $projectRoot 'mvnw.cmd'
if (-not (Test-Path $maven)) { $maven = 'mvn.cmd' }

$buildArguments = @('clean', 'package')
if ($SkipTests) { $buildArguments += '-DskipTests' }
& $maven @buildArguments
if ($LASTEXITCODE -ne 0) { throw 'Maven-byggingen feilet.' }
& $maven dependency:copy-dependencies '-DincludeScope=runtime' '-DoutputDirectory=target/package-input'
if ($LASTEXITCODE -ne 0) { throw 'Kopiering av runtime-avhengigheter feilet.' }
Copy-Item -LiteralPath 'target/phone-support-1.0.0.jar' -Destination 'target/package-input' -Force

$destination = Join-Path $projectRoot 'target/jpackage'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$arguments = @(
  '--type', $Type,
  '--name', 'Telefonhjelp',
  '--app-version', '1.0.0',
  '--vendor', 'Telefonhjelp',
  '--description', 'Lokalt supportsystem for telefonbutikk',
  '--input', (Join-Path $projectRoot 'target/package-input'),
  '--main-jar', 'phone-support-1.0.0.jar',
  '--main-class', 'no.telefonhjelp.DesktopLauncher',
  '--java-options', '--enable-native-access=ALL-UNNAMED',
  '--dest', $destination
)
if ($Type -eq 'exe') { $arguments += @('--win-menu', '--win-shortcut', '--win-dir-chooser', '--win-per-user-install') }
& (Join-Path $env:JAVA_HOME 'bin/jpackage.exe') @arguments
if ($LASTEXITCODE -ne 0) { throw 'jpackage feilet. EXE-bygging krever WiX Toolset på PATH.' }
