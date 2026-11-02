<#
.SYNOPSIS
  Runs SWT tests inside the VM's interactive desktop session.

  Normally started by the "SWT-Tests" scheduled task, which the host triggers
  with host\Invoke-SwtTestsInVm.ps1. With -FromTask the parameters are read
  from \\VBOXSVR\swt-vm\results\request.json (written by the host).
  It can also be run by hand in the VM:
    C:\swt-vm\Run-SwtTests.ps1 -Ref master -Test Test_org_eclipse_swt_widgets_Button

  Output: \\VBOXSVR\swt-vm\results\<run-id>\ (build.log, surefire reports,
  exitcode.txt; exitcode.txt is written last and marks the run as finished).
#>
[CmdletBinding()]
param(
	[string] $Ref = 'HEAD',          # branch, tag or commit of the HOST checkout
	[string] $Test = '',             # e.g. Test_org_eclipse_swt_widgets_Button[#method]; empty = all
	[switch] $BuildNatives,          # rebuild swt-*.dll with MSVC like CI (-Dnative=win32.win32.x86_64)
	[string] $RunId = (Get-Date -Format 'yyyyMMdd-HHmmss'),
	[switch] $FromTask
)
$ErrorActionPreference = 'Stop'
$share = '\\VBOXSVR\swt-vm\results'
$work  = 'C:\work\eclipse.platform.swt'

if ($FromTask) {
	$req = Get-Content "$share\request.json" -Raw | ConvertFrom-Json
	$Ref = $req.ref; $Test = $req.test; $BuildNatives = [bool]$req.buildNatives; $RunId = $req.runId
}
$out = Join-Path $share $RunId
New-Item -ItemType Directory -Force $out | Out-Null
$log = Join-Path $out 'build.log'
$code = 1
try {
	Start-Transcript -Path $log -Force | Out-Null
	$env:JAVA_HOME  = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
	$env:Path       = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + $env:Path
	Set-Location $work

	# Sync with the host checkout (committed state only).
	git fetch host --force --prune '+refs/heads/*:refs/remotes/host/*' '+HEAD:refs/remotes/host/HEAD'
	if ($LASTEXITCODE -ne 0) { throw 'git fetch from host failed' }
	$target = if ($Ref -eq 'HEAD') { 'host/HEAD' } elseif (git rev-parse -q --verify "host/$Ref") { "host/$Ref" } else { $Ref }
	git checkout --force --detach $target
	git clean -fdx -e .m2
	git lfs pull --include='/binaries/org.eclipse.swt.win32.win32.x86_64/*.dll' 2>$null
	Write-Host "==> Testing $(git log -1 --oneline)"

	$mvnArgs = @('--batch-mode', '-V', '-e', '-Dtycho.baseline.replace=none',
		'-DskipNativeTests=false', '-DfailIfNoTests=false', '--fail-at-end')
	if ($BuildNatives) { $mvnArgs += '-Dnative=win32.win32.x86_64' }
	if ($Test) {
		$mvnArgs += @('-pl', ':org.eclipse.swt.tests', '-am', "-Dtest=$Test", '-Dsurefire.failIfNoSpecifiedTests=false')
	}
	$mvnArgs += @('clean', 'verify')
	Write-Host "==> mvn $($mvnArgs -join ' ')"
	& mvn.cmd @mvnArgs
	$code = $LASTEXITCODE
} catch {
	Write-Host "ERROR: $_"
	$code = 2
} finally {
	Get-ChildItem $work -Recurse -Directory -Filter surefire-reports -ErrorAction SilentlyContinue | ForEach-Object {
		$dest = Join-Path $out ($_.Parent.Parent.Name)
		Copy-Item $_.FullName $dest -Recurse -Force
	}
	Stop-Transcript | Out-Null
	Set-Content (Join-Path $out 'exitcode.txt') $code
}
exit $code
