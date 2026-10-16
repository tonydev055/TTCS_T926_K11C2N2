# Run with Windows PowerShell 5.1: no server is started and no email is sent.
$ErrorActionPreference='Stop'
$scriptPath=Join-Path $PSScriptRoot '../../scripts/start-gmail.ps1'
$tokens=$null
$parseErrors=$null
[System.Management.Automation.Language.Parser]::ParseFile(
  (Resolve-Path $scriptPath).Path,[ref]$tokens,[ref]$parseErrors
) | Out-Null
if($parseErrors.Count){throw ($parseErrors.Message -join '; ')}

$before=@{}
foreach($name in @('MAIL_MODE','SMTP_USERNAME','SMTP_APP_PASSWORD','APP_URL','JAVA_HOME','CATALINA_HOME','CATALINA_BASE')) {
  $before[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
}
$launchState=@{Count=0}
function Read-Host {
  param($Prompt,[switch]$AsSecureString)
  if($AsSecureString){return ConvertTo-SecureString 'test fake app pass' -AsPlainText -Force}
  return 'qa@example.test'
}
function Test-Path {param($Path) return $true}
function Start-Process {
  param($FilePath,$ArgumentList,$WorkingDirectory,$WindowStyle)
  if($env:MAIL_MODE -ne 'smtp'){throw 'SMTP mode missing'}
  if($env:SMTP_USERNAME -ne 'qa@example.test'){throw 'Sender missing'}
  if($env:SMTP_APP_PASSWORD -ne 'testfakeapppass'){throw 'App password parsing failed'}
  if($ArgumentList -ne 'run' -or $WindowStyle -ne 'Hidden'){throw 'Unexpected launch options'}
  $launchState.Count++
}
& $scriptPath | Out-Null
if($launchState.Count -ne 1){throw 'Expected one simulated launch'}
foreach($name in $before.Keys) {
  if([Environment]::GetEnvironmentVariable($name,'Process') -cne $before[$name]) {
    throw "Environment not restored: $name"
  }
}
Write-Output 'PASS: Windows PowerShell parsing, SMTP environment, simulated launch and environment cleanup.'
