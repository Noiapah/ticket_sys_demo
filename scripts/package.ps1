param(
  [switch]$SkipTests,
  [ValidateSet('exe', 'app-image')]
  [string]$Type = 'exe'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path

if (-not $env:JAVA_HOME) { throw 'JAVA_HOME må peke til JDK 25.' }
$jpackage = Join-Path $env:JAVA_HOME 'bin/jpackage.exe'
if (-not (Test-Path -LiteralPath $jpackage)) { throw 'Fant ikke jpackage.exe i JAVA_HOME.' }
if ($Type -eq 'exe' -and (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -or -not (Get-Command light.exe -ErrorAction SilentlyContinue))) {
  throw 'EXE-bygging krever WiX 3 på PATH.'
}
$maven = Join-Path $projectRoot 'mvnw.cmd'
[xml]$project = Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw
$appVersion = $project.project.version
$appJar = "$($project.project.artifactId)-$appVersion.jar"

Push-Location $projectRoot
try {
  & (Join-Path $PSScriptRoot 'clean.ps1')

  $buildArguments = @('package')
  if ($SkipTests) { $buildArguments += '-DskipTests' }
  & $maven @buildArguments
  if ($LASTEXITCODE -ne 0) { throw 'Maven-byggingen feilet.' }
  & $maven dependency:copy-dependencies '-DincludeScope=runtime' '-DoutputDirectory=target/package-input'
  if ($LASTEXITCODE -ne 0) { throw 'Kopiering av runtime-avhengigheter feilet.' }
  Copy-Item -LiteralPath "target/$appJar" -Destination 'target/package-input' -Force

  $destination = Join-Path $projectRoot 'target/jpackage'
  New-Item -ItemType Directory -Force -Path $destination | Out-Null
  $arguments = @(
    '--type', $Type,
    '--name', 'Telefonhjelp',
    '--app-version', $appVersion,
    '--vendor', 'Telefonhjelp',
    '--description', 'Lokalt supportsystem for telefonbutikk',
    '--input', (Join-Path $projectRoot 'target/package-input'),
    '--main-jar', $appJar,
    '--main-class', 'no.telefonhjelp.DesktopLauncher',
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--dest', $destination
  )
  if ($Type -eq 'exe') {
    $arguments += @(
      '--win-menu',
      '--win-shortcut',
      '--win-dir-chooser',
      '--win-per-user-install',
      '--win-upgrade-uuid', '6144505c-7492-47d5-b0b1-bb64b8a16cd6'
    )
  }
  & $jpackage @arguments
  if ($LASTEXITCODE -ne 0) { throw 'jpackage feilet. EXE-bygging krever WiX Toolset på PATH.' }
  if ($Type -eq 'exe') {
    # Keep the previous installer until its replacement has built successfully.
    $releaseDirectory = Join-Path $projectRoot 'releases'
    New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
    Move-Item -LiteralPath (Join-Path $destination "Telefonhjelp-$appVersion.exe") -Destination $releaseDirectory -Force
    $destination = $releaseDirectory
  }
  Write-Output "Ferdig: $destination"
} finally {
  Pop-Location
}
