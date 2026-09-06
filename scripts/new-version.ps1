$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$javaDirectory = Join-Path $projectRoot '.tools\liberica-full\jdk-25.0.4.1'
$nodeDirectory = Join-Path $projectRoot '.tools\node-v24.19.0-win-x64'

if (-not (Test-Path -LiteralPath (Join-Path $javaDirectory 'bin\java.exe'))) {
  throw "Fant ikke prosjektets JDK i $javaDirectory"
}
if (-not (Test-Path -LiteralPath (Join-Path $nodeDirectory 'node.exe'))) {
  throw "Fant ikke prosjektets Node.js i $nodeDirectory"
}

$env:JAVA_HOME = (Resolve-Path -LiteralPath $javaDirectory).Path
$env:Path = "$env:JAVA_HOME\bin;$nodeDirectory;$env:Path"

Push-Location $projectRoot
try {
  & (Join-Path $projectRoot 'mvnw.cmd') compile javafx:run
  if ($LASTEXITCODE -ne 0) { throw 'Kunne ikke bygge og starte Telefonhjelp.' }
} finally {
  Pop-Location
}
