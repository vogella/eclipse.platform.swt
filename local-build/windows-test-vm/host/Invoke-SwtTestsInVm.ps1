<#
.SYNOPSIS
  Runs SWT tests in the VM's desktop without touching the host desktop.

    .\Invoke-SwtTestsInVm.ps1                                   # all tests, host HEAD
    .\Invoke-SwtTestsInVm.ps1 -Ref my-branch -Test Test_org_eclipse_swt_widgets_Button
    .\Invoke-SwtTestsInVm.ps1 -BuildNatives                     # also rebuild the DLLs, like CI

  Starts the VM headless if needed, waits for auto-logon, writes a request
  file to the shared folder, triggers the "SWT-Tests" scheduled task (which
  runs in the interactive session, not session 0) and waits for the result.
  Only committed state of the host checkout is tested.
#>
[CmdletBinding()]
param(
	[string] $Ref = 'HEAD',
	[string] $Test = '',
	[switch] $BuildNatives,
	[string] $VmName = 'swt-win11',
	[string] $GuestUser = 'swt',
	[string] $GuestPassword = 'swt-test-1',
	[string] $KitFolder = (Split-Path -Parent $PSScriptRoot),
	[int]    $TimeoutMinutes = 240
)
$ErrorActionPreference = 'Stop'
$vbm = @("$env:ProgramFiles\Oracle\VirtualBox\VBoxManage.exe", (Join-Path "$env:VBOX_MSI_INSTALL_PATH" 'VBoxManage.exe')) |
	Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $vbm) { throw 'VBoxManage.exe not found' }

# Start headless: the VM has its own desktop, nothing appears on the host.
$state = (& $vbm showvminfo $VmName --machinereadable | Select-String '^VMState=').ToString()
if ($state -notmatch '"running"') {
	Write-Host "==> Starting $VmName headless"
	& $vbm startvm $VmName --type headless | Out-Null
}

# Wait until the user is logged on (Guest Additions report it).
Write-Host '==> Waiting for guest logon'
$deadline = (Get-Date).AddMinutes(10)
do {
	Start-Sleep 5
	$users = & $vbm guestproperty get $VmName '/VirtualBox/GuestInfo/OS/LoggedInUsers' 2>$null
} until (($users -match 'Value: [1-9]') -or (Get-Date) -gt $deadline)
if ((Get-Date) -gt $deadline) { throw 'Guest did not log on in 10 minutes (check auto-logon).' }

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$results = Join-Path $KitFolder 'results'
New-Item -ItemType Directory -Force $results | Out-Null
@{ ref = $Ref; test = $Test; buildNatives = [bool]$BuildNatives; runId = $runId } |
	ConvertTo-Json | Set-Content (Join-Path $results 'request.json')

Write-Host "==> Triggering SWT-Tests in the guest (run $runId)"
& $vbm guestcontrol $VmName run --username $GuestUser --password $GuestPassword `
	--exe 'C:\Windows\System32\schtasks.exe' -- schtasks.exe /Run /TN SWT-Tests
if ($LASTEXITCODE -ne 0) { throw 'Could not start the scheduled task in the guest' }

$done = Join-Path $results "$runId\exitcode.txt"
$log  = Join-Path $results "$runId\build.log"
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
while (-not (Test-Path $done)) {
	if ((Get-Date) -gt $deadline) { throw "Timed out; see $log" }
	Start-Sleep 15
	if (Test-Path $log) { Get-Content $log -Tail 1 -ErrorAction SilentlyContinue | Write-Host }
}
$code = [int](Get-Content $done)
Write-Host "==> Finished with exit code $code. Logs and surefire reports: $(Split-Path $done)"
exit $code
