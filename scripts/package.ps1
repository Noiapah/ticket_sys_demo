param(
  [switch]$SkipTests,
  [ValidateSet('exe', 'app-image')]
  [string]$Type = 'exe',
  [string]$SigningThumbprint = $env:PHONE_SUPPORT_SIGNING_THUMBPRINT,
  [string]$TimestampUrl = $env:PHONE_SUPPORT_TIMESTAMP_URL,
  [switch]$Release
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'native-command.ps1')

if (-not $env:JAVA_HOME) { throw 'JAVA_HOME må peke til JDK 25.' }
$jpackage = Join-Path $env:JAVA_HOME 'bin/jpackage.exe'
if (-not (Test-Path -LiteralPath $jpackage)) { throw 'Fant ikke jpackage.exe i JAVA_HOME.' }
if ($Release -and ($SkipTests -or -not $SigningThumbprint -or -not $TimestampUrl)) { throw 'Release builds require tests, a signing certificate thumbprint and a timestamp URL.' }
if ($SigningThumbprint -and ($SigningThumbprint -notmatch '^[0-9A-Fa-f]{40}$' -or -not $TimestampUrl -or -not (Get-Command signtool.exe -ErrorAction SilentlyContinue))) { throw 'Signing requires a certificate thumbprint, timestamp URL and signtool.exe on PATH.' }

function Sign-ReleaseFile([string]$File) {
  Invoke-CheckedNative -FilePath 'signtool.exe' -ArgumentList @('sign', '/sha1', $SigningThumbprint, '/fd', 'SHA256', '/tr', $TimestampUrl, '/td', 'SHA256', $File)
  Invoke-CheckedNative -FilePath 'signtool.exe' -ArgumentList @('verify', '/pa', $File)
}
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

  $buildArguments = @('--no-transfer-progress', 'package')
  if ($Release) { $buildArguments = @('--no-transfer-progress', 'verify') }
  if ($SkipTests) { $buildArguments += '-DskipTests' }
  if (-not $SkipTests) {
    & (Join-Path $PSScriptRoot 'test-native-command.ps1')
    Invoke-CheckedNative -FilePath 'npm.cmd' -ArgumentList @('test')
  }
  Invoke-CheckedNative -FilePath $maven -ArgumentList $buildArguments
  if ($Release) {
    Invoke-CheckedNative -FilePath 'node.exe' -ArgumentList @('scripts/audit-java.mjs')
    Invoke-CheckedNative -FilePath 'npm.cmd' -ArgumentList @('audit', '--audit-level=high')
  }
  $frontendSbom = Invoke-CheckedNative -FilePath 'npm.cmd' -ArgumentList @('sbom', '--sbom-format=cyclonedx', '--omit=dev')
  [IO.File]::WriteAllText((Join-Path $projectRoot 'target/sbom-frontend.json'), ($frontendSbom -join "`n"), (New-Object Text.UTF8Encoding($false)))
  Invoke-CheckedNative -FilePath $maven -ArgumentList @('--no-transfer-progress', 'dependency:copy-dependencies', '-DincludeScope=runtime', '-DoutputDirectory=target/package-input')
  Copy-Item -LiteralPath "target/$appJar" -Destination 'target/package-input' -Force

  $destination = Join-Path $projectRoot 'target/jpackage'
  New-Item -ItemType Directory -Force -Path $destination | Out-Null
  $arguments = @(
    '--type', 'app-image',
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
  Invoke-CheckedNative -FilePath $jpackage -ArgumentList $arguments
  $appImage = Join-Path $destination 'Telefonhjelp'
  if ($SigningThumbprint) { Sign-ReleaseFile (Join-Path $appImage 'Telefonhjelp.exe') }
  Copy-Item -LiteralPath 'target/sbom-java.json', 'target/sbom-frontend.json' -Destination $appImage
  if ($Type -eq 'exe') {
    $installerArguments = @('--type', 'exe', '--app-image', $appImage, '--name', 'Telefonhjelp', '--app-version', $appVersion, '--dest', $destination,
      '--win-menu',
      '--win-shortcut',
      '--win-dir-chooser',
      '--win-per-user-install',
      '--win-upgrade-uuid', '6144505c-7492-47d5-b0b1-bb64b8a16cd6'
    )
    Invoke-CheckedNative -FilePath $jpackage -ArgumentList $installerArguments
    if ($SigningThumbprint) { Sign-ReleaseFile (Join-Path $destination "Telefonhjelp-$appVersion.exe") }
  }
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
