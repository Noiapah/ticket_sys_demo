[CmdletBinding(SupportsShouldProcess)]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path

# Explicit generated paths only. Source, releases, dependencies and local tools stay intact.
$generatedPaths = @('target', 'build', 'tsconfig.app.tsbuildinfo', 'tsconfig.node.tsbuildinfo')
foreach ($relativePath in $generatedPaths) {
  $candidate = Join-Path $projectRoot $relativePath
  if (-not (Test-Path -LiteralPath $candidate)) { continue }
  $resolved = (Resolve-Path -LiteralPath $candidate).Path
  if (-not $resolved.StartsWith($projectRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean a path outside the project: $resolved"
  }
  $entries = @(Get-Item -LiteralPath $resolved -Force)
  if ($entries[0].PSIsContainer) { $entries += @(Get-ChildItem -LiteralPath $resolved -Recurse -Force) }
  if ($entries | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) {
    throw "Refusing to clean a path containing a junction or symbolic link: $resolved"
  }
  if ($PSCmdlet.ShouldProcess($resolved, 'Remove generated files')) {
    # jpackage can mark runtime files read-only, which also prevents Maven clean.
    $entries | Where-Object { -not $_.PSIsContainer } | ForEach-Object { $_.IsReadOnly = $false }
    Remove-Item -LiteralPath $resolved -Recurse -Force
  }
}
