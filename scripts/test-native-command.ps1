$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'native-command.ps1')

$output = @(Invoke-CheckedNative -FilePath 'node.exe' -ArgumentList @('-e', "process.stdout.write(JSON.stringify({ok:true})); process.stderr.write('synthetic warning\n');"))
if (($output -join '') -ne '{"ok":true}') { throw 'Native stderr contaminated captured stdout.' }

$rejected = $false
try {
  Invoke-CheckedNative -FilePath 'node.exe' -ArgumentList @('-e', "process.stderr.write('synthetic failure\n'); process.exit(7);") | Out-Null
} catch {
  if ($_.Exception.Message -notmatch 'exit code 7') { throw }
  $rejected = $true
}
if (-not $rejected) { throw 'A failed native command was accepted.' }

$rejected = $false
try {
  Invoke-CheckedNative -FilePath 'phone-support-command-that-does-not-exist.exe' | Out-Null
} catch { $rejected = $true }
if (-not $rejected) { throw 'A missing native command was accepted.' }

Write-Output 'Native command checks passed: stderr separation, nonzero exit, missing executable.'
