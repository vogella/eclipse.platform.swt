<#
.SYNOPSIS
  One-time setup inside the Windows test VM. Run as Administrator in the VM:
    Set-ExecutionPolicy -Scope Process Bypass -Force
    & \\VBOXSVR\swt-vm\guest\Setup-SwtGuest.ps1 -Password 'swt-test-1'

  Installs JDK 21, Git (+LFS), Maven, optionally VS 2022 Build Tools (needed
  only to rebuild SWT natives), enables auto-logon, keeps the desktop unlocked
  and registers the "SWT-Tests" scheduled task that runs tests in the
  interactive session.
#>
[CmdletBinding()]
param(
	[Parameter(Mandatory)] [string] $Password,
	[string] $User = $env:USERNAME,
	[string] $MavenVersion = '3.9.11',
	[switch] $SkipNativeToolchain
)
$ErrorActionPreference = 'Stop'

function Winget([string]$id, [string[]]$extra = @()) {
	Write-Host "==> winget install $id"
	winget install --id $id -e --silent --accept-package-agreements --accept-source-agreements @extra
	# 0 = ok, -1978335189 = already installed
	if ($LASTEXITCODE -ne 0 -and $LASTEXITCODE -ne -1978335189) { throw "winget $id failed ($LASTEXITCODE)" }
}

# --- Tools -------------------------------------------------------------------
Winget 'EclipseAdoptium.Temurin.21.JDK'
Winget 'Git.Git'
Winget 'GitHub.GitLFS'
Winget 'Microsoft.Sysinternals.Autologon'
if (-not $SkipNativeToolchain) {
	# MSVC + Windows SDK, used by build.bat when -BuildNatives is passed to the runner.
	Winget 'Microsoft.VisualStudio.2022.BuildTools' @('--override',
		'--quiet --wait --norestart --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended')
}

# Maven (no reliable winget id): unpack to C:\tools\maven
$mvnHome = "C:\tools\apache-maven-$MavenVersion"
if (-not (Test-Path "$mvnHome\bin\mvn.cmd")) {
	Write-Host "==> Installing Maven $MavenVersion"
	New-Item -ItemType Directory -Force C:\tools | Out-Null
	$zip = "$env:TEMP\maven.zip"
	Invoke-WebRequest "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip" -OutFile $zip
	Expand-Archive $zip -DestinationPath C:\tools -Force
}
$jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' | Sort-Object Name | Select-Object -Last 1
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk.FullName, 'Machine')
[Environment]::SetEnvironmentVariable('MAVEN_HOME', $mvnHome, 'Machine')
$path = [Environment]::GetEnvironmentVariable('Path', 'Machine')
foreach ($p in @("$($jdk.FullName)\bin", "$mvnHome\bin")) { if ($path -notlike "*$p*") { $path = "$p;$path" } }
[Environment]::SetEnvironmentVariable('Path', $path, 'Machine')

# --- Interactive desktop that stays usable ------------------------------------
Write-Host '==> Auto-logon, no lock screen, no sleep'
$autologon = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Links\autologon*.exe", 'C:\Program Files\WinGet\Links\autologon*.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($autologon) {
	& $autologon.FullName $User $env:COMPUTERNAME $Password /accepteula   # stores the password as an LSA secret
} else {
	$wl = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon'
	Set-ItemProperty $wl AutoAdminLogon '1'
	Set-ItemProperty $wl DefaultUserName $User
	Set-ItemProperty $wl DefaultDomainName $env:COMPUTERNAME
	Set-ItemProperty $wl DefaultPassword $Password   # plain text; acceptable for a throwaway test VM
}
powercfg /change monitor-timeout-ac 0
powercfg /change standby-timeout-ac 0
powercfg /change hibernate-timeout-ac 0
New-Item -Force 'HKLM:\SOFTWARE\Policies\Microsoft\Windows\Personalization' | Out-Null
Set-ItemProperty 'HKLM:\SOFTWARE\Policies\Microsoft\Windows\Personalization' NoLockScreen 1 -Type DWord
Set-ItemProperty 'HKCU:\Control Panel\Desktop' ScreenSaveActive '0'
# Don't let Windows Update reboot the VM in the middle of a test run.
New-Item -Force 'HKLM:\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate\AU' | Out-Null
Set-ItemProperty 'HKLM:\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate\AU' NoAutoRebootWithLoggedOnUsers 1 -Type DWord

# --- Local working copy -------------------------------------------------------
# Builds run on the VM's own disk (shared folders are slow and break file locking).
$work = 'C:\work\eclipse.platform.swt'
$git = 'C:\Program Files\Git\cmd\git.exe'
if (-not (Test-Path $work)) {
	Write-Host "==> Cloning \\VBOXSVR\swt to $work"
	New-Item -ItemType Directory -Force C:\work | Out-Null
	& $git config --global --add safe.directory '*'
	& $git clone --origin host '\\VBOXSVR\swt' $work
	if ($LASTEXITCODE -ne 0) { throw 'git clone from the shared folder failed' }
}

# --- Runner + scheduled task in the interactive session -----------------------
New-Item -ItemType Directory -Force C:\swt-vm | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'Run-SwtTests.ps1') C:\swt-vm\ -Force

$action  = New-ScheduledTaskAction -Execute 'powershell.exe' `
	-Argument '-NoProfile -ExecutionPolicy Bypass -File C:\swt-vm\Run-SwtTests.ps1 -FromTask'
# Interactive logon type = runs on the logged-on user's desktop (not session 0).
$principal = New-ScheduledTaskPrincipal -UserId "$env:COMPUTERNAME\$User" -LogonType Interactive -RunLevel Limited
$settings  = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 4) -MultipleInstances IgnoreNew -AllowStartIfOnBatteries
Register-ScheduledTask -TaskName 'SWT-Tests' -Action $action -Principal $principal -Settings $settings -Force | Out-Null
# Let the host trigger it via "schtasks /Run" from a guestcontrol session of the same user.

Write-Host @"

Guest setup done. Reboot the VM once to check auto-logon, then on the host:
  VBoxManage snapshot swt-win11 take clean-setup
  .\host\Invoke-SwtTestsInVm.ps1 -Test Test_org_eclipse_swt_widgets_Button
"@
