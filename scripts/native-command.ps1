function Invoke-CheckedNative {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory = $true)][string]$FilePath,
    [string[]]$ArgumentList = @()
  )

  $command = @(Get-Command -Name $FilePath -CommandType Application -ErrorAction Stop)[0]
  # Keep stderr separate even when PowerShell represents native stderr as plain
  # strings rather than ErrorRecord objects. Only the exit code decides success.
  $stderrFile = [IO.Path]::GetTempFileName()
  try {
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    $global:LASTEXITCODE = $null
    & $command.Source @ArgumentList 2> $stderrFile
    $nativeExitCode = $LASTEXITCODE
  } finally {
    try {
      Get-Content -LiteralPath $stderrFile -ErrorAction Stop | ForEach-Object {
        Write-Information -MessageData $_ -InformationAction Continue
      }
    } finally {
      Remove-Item -LiteralPath $stderrFile -Force -ErrorAction Stop
    }
  }
  if ($nativeExitCode -ne 0) {
    throw "Native command failed: $($command.Name) (exit code $nativeExitCode)."
  }
}
