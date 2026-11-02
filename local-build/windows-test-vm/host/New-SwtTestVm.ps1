<#
.SYNOPSIS
  Creates a Windows 11 VirtualBox VM for running SWT tests in an isolated
  interactive desktop (so tests never steal focus on the host).

.DESCRIPTION
  Run on the Windows host in an elevated PowerShell:
    Set-ExecutionPolicy -Scope Process Bypass
    .\New-SwtTestVm.ps1 -IsoPath C:\ISO\Win11_Eval.iso -SwtCheckout C:\git\eclipse.platform.swt

  Steps:
    1. Installs VirtualBox via winget (skipped if VBoxManage is found).
    2. Creates a VM with EFI, Secure Boot, TPM 2.0 (Windows 11 requirements).
    3. Adds two shared folders: the SWT checkout (read-only, used as a git
       remote) and the "guest" scripts folder plus a results folder.
    4. Starts a VirtualBox unattended install with Guest Additions.

  After Windows finishes installing, log in once and run
    \\VBOXSVR\swt-vm\guest\Setup-SwtGuest.ps1
  as described in README.md.
#>
[CmdletBinding()]
param(
	[Parameter(Mandatory)] [string] $IsoPath,
	[Parameter(Mandatory)] [string] $SwtCheckout,
	[string] $VmName      = 'swt-win11',
	[string] $VmFolder    = "$env:USERPROFILE\VirtualBox VMs",
	[int]    $MemoryMB    = 8192,
	[int]    $Cpus        = 4,
	[int]    $DiskGB      = 100,
	[string] $GuestUser   = 'swt',
	[string] $GuestPassword = 'swt-test-1',
	# Windows edition inside the ISO. Eval Enterprise ISO has one image (1).
	# For a consumer multi-edition ISO run:  dism /Get-WimInfo /WimFile:<iso>\sources\install.wim
	[int]    $ImageIndex  = 1,
	[string] $Locale      = 'en_US',
	[string] $Country     = 'DE',
	[string] $TimeZone    = 'W. Europe Standard Time',
	# Folder holding this kit (parent of host\ and guest\). Defaults to this script's parent.
	[string] $KitFolder   = (Split-Path -Parent $PSScriptRoot),
	[switch] $Headless
)

$ErrorActionPreference = 'Stop'

function Find-VBoxManage {
	$candidates = @(
		"$env:ProgramFiles\Oracle\VirtualBox\VBoxManage.exe",
		"${env:ProgramFiles(x86)}\Oracle\VirtualBox\VBoxManage.exe"
	)
	if ($env:VBOX_MSI_INSTALL_PATH) { $candidates = @(Join-Path $env:VBOX_MSI_INSTALL_PATH 'VBoxManage.exe') + $candidates }
	$candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
}

function VBox { & $script:VBoxManage @args; if ($LASTEXITCODE -ne 0) { throw "VBoxManage $($args -join ' ') failed ($LASTEXITCODE)" } }

# --- 0. Sanity checks --------------------------------------------------------
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
	throw 'Run this script from an elevated (Administrator) PowerShell.'
}
if (-not (Test-Path $IsoPath))     { throw "ISO not found: $IsoPath" }
if (-not (Test-Path $SwtCheckout)) { throw "SWT checkout not found: $SwtCheckout" }
$IsoPath     = (Resolve-Path $IsoPath).Path
$SwtCheckout = (Resolve-Path $SwtCheckout).Path
$KitFolder   = (Resolve-Path $KitFolder).Path

# Hyper-V / VBS make VirtualBox fall back to the slow "NEM" mode (green turtle icon).
$hv = Get-CimInstance -ClassName Win32_ComputerSystem
$dg = Get-CimInstance -Namespace root\Microsoft\Windows\DeviceGuard -ClassName Win32_DeviceGuard -ErrorAction SilentlyContinue
if ($hv.HypervisorPresent -or ($dg -and $dg.VirtualizationBasedSecurityStatus -ne 0)) {
	Write-Warning ('A Windows hypervisor is active (Hyper-V, VBS / Memory Integrity, WSL2 or similar). ' +
		'VirtualBox will run, but much slower. See README.md, section "Performance", to turn it off.')
}

# --- 1. Install VirtualBox ---------------------------------------------------
$script:VBoxManage = Find-VBoxManage
if (-not $script:VBoxManage) {
	Write-Host '==> Installing VirtualBox via winget'
	winget install --id Oracle.VirtualBox -e --accept-package-agreements --accept-source-agreements
	$script:VBoxManage = Find-VBoxManage
	if (-not $script:VBoxManage) { throw 'VirtualBox installed but VBoxManage.exe not found. Open a new shell and rerun.' }
}
Write-Host "==> Using $script:VBoxManage"
& $script:VBoxManage --version

# --- 2. Create the VM --------------------------------------------------------
$existing = & $script:VBoxManage list vms | Select-String -SimpleMatch "`"$VmName`""
if ($existing) { throw "A VM named '$VmName' already exists. Remove it (VBoxManage unregistervm $VmName --delete) or pick -VmName." }

$results = Join-Path $KitFolder 'results'
New-Item -ItemType Directory -Force -Path $results | Out-Null

Write-Host "==> Creating VM $VmName"
VBox createvm --name $VmName --ostype Windows11_64 --basefolder $VmFolder --register
VBox modifyvm $VmName `
	--memory $MemoryMB --cpus $Cpus `
	--firmware efi --tpm-type 2.0 `
	--graphicscontroller vboxsvga --vram 128 `
	--nic1 nat `
	--clipboard-mode bidirectional --drag-and-drop disabled `
	--audio-enabled off `
	--usb-xhci on `
	--mouse usbtablet

# Secure Boot with the Microsoft keys (Windows 11 setup checks this).
VBox modifynvram $VmName inituefivarstore
VBox modifynvram $VmName enrollmssignatures
VBox modifynvram $VmName enrollorclpk

$disk = Join-Path (Join-Path $VmFolder $VmName) "$VmName.vdi"
VBox createmedium disk --filename $disk --size ($DiskGB * 1024) --format VDI --variant Standard
VBox storagectl $VmName --name SATA --add sata --controller IntelAhci --portcount 4 --hostiocache on
VBox storageattach $VmName --storagectl SATA --port 0 --device 0 --type hdd --medium $disk --nonrotational on --discard on

# --- 3. Shared folders --------------------------------------------------------
#   \\VBOXSVR\swt     -> SWT checkout (read-only; the guest clones/fetches from it)
#   \\VBOXSVR\swt-vm  -> this kit (guest scripts + results\ written by the guest)
VBox sharedfolder add $VmName --name swt    --hostpath $SwtCheckout --readonly --automount --auto-mount-point S:
VBox sharedfolder add $VmName --name swt-vm --hostpath $KitFolder             --automount --auto-mount-point V:

# --- 4. Unattended install ---------------------------------------------------
Write-Host '==> Starting unattended Windows install (takes 20-40 minutes)'
$startType = if ($Headless) { 'headless' } else { 'gui' }
VBox unattended install $VmName `
	--iso=$IsoPath `
	--user=$GuestUser --password=$GuestPassword --full-user-name="SWT Tests" `
	--image-index=$ImageIndex `
	--locale=$Locale --country=$Country --time-zone=$TimeZone `
	--hostname="$VmName.local" `
	--install-additions `
	--start-vm=$startType

Write-Host @"

Done. Windows is installing inside '$VmName'.
When it shows the desktop (user '$GuestUser', password '$GuestPassword'):
  open PowerShell as Administrator in the VM and run
    Set-ExecutionPolicy -Scope Process Bypass -Force
    & \\VBOXSVR\swt-vm\guest\Setup-SwtGuest.ps1 -Password '$GuestPassword'
Then take a snapshot:  VBoxManage snapshot $VmName take clean-setup
"@
