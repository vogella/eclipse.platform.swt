# VirtualBox VM for Windows SWT tests

SWT UI tests need a real, unlocked interactive desktop and they grab focus. This kit
runs them inside a Windows 11 VirtualBox VM that has its own auto-logged-on desktop,
so the host desktop is never touched. Works on Windows Home (no Hyper-V or Sandbox needed).

```
host\New-SwtTestVm.ps1        install VirtualBox, create + unattended-install the VM
guest\Setup-SwtGuest.ps1      one-time setup in the VM: JDK 21, Git+LFS, Maven, MSVC, auto-logon
guest\Run-SwtTests.ps1        runs Maven tests in the VM's desktop session
host\Invoke-SwtTestsInVm.ps1  starts the VM headless, triggers a run, waits for the result
results\<run-id>\             build.log, surefire reports, exitcode.txt (written by the VM)
```

## 1. Get a Windows 11 ISO

Download the free 90-day **Windows 11 Enterprise Evaluation** ISO from the
Microsoft Evaluation Center (needs a short form), or the regular Windows 11 ISO from
microsoft.com/software-download/windows11 (runs unactivated with a watermark, fine
for tests). For the multi-edition consumer ISO pass `-ImageIndex` (e.g. 6 for Pro;
check with `dism /Get-WimInfo /WimFile:E:\sources\install.wim`).

## 2. Create the VM (host, elevated PowerShell)

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
cd <this folder>
.\host\New-SwtTestVm.ps1 -IsoPath C:\ISO\Win11_Eval.iso -SwtCheckout C:\git\eclipse.platform.swt
```

Defaults: 8 GB RAM, 4 CPUs, 100 GB dynamic disk, user `swt` / password `swt-test-1`
(change with `-GuestPassword`; the VM is NAT-only). Windows installs unattended with
Guest Additions in 20 to 40 minutes.

## 3. Set up the guest (inside the VM, once)

Log in (usually automatic), open PowerShell **as Administrator** and run:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
& \\VBOXSVR\swt-vm\guest\Setup-SwtGuest.ps1 -Password 'swt-test-1'
```

Add `-SkipNativeToolchain` to skip Visual Studio Build Tools (about 6 GB). They are
only needed for `-BuildNatives`; without it the build uses the committed `swt-*.dll`
files from Git LFS.

Reboot the VM once to confirm it logs in by itself, then on the host:

```powershell
VBoxManage snapshot swt-win11 take clean-setup
```

## 4. Run tests (host)

```powershell
.\host\Invoke-SwtTestsInVm.ps1                                           # all tests, host HEAD
.\host\Invoke-SwtTestsInVm.ps1 -Ref my-branch -Test Test_org_eclipse_swt_widgets_Button
.\host\Invoke-SwtTestsInVm.ps1 -Test 'Test_org_eclipse_swt_widgets_Text#test_setTextLimit'
.\host\Invoke-SwtTestsInVm.ps1 -BuildNatives                             # rebuild DLLs like CI
```

The VM starts headless, so no window appears. Only **committed** state of the host
checkout is tested (the VM clones `\\VBOXSVR\swt` and builds on its own disk, because
Maven on a shared folder is slow and file locking breaks). Commit a WIP commit first if needed.

To watch the tests, open the VM in the VirtualBox Manager ("Show"). Minimizing or
closing the window (choose "continue running in background") does not stop the tests.

### Why a scheduled task

Processes started via `VBoxManage guestcontrol`, SSH or a service run in a
non-interactive session, where SWT shells never get a real desktop and focus tests
fail. The `SWT-Tests` task is registered with the *Interactive* logon type, so it runs
on the auto-logged-on user's desktop. The host only uses guestcontrol to call
`schtasks /Run`.

## Performance: Hyper-V / VBS

If Windows' own hypervisor is active, VirtualBox runs on top of it (green turtle icon in
the VM status bar) and is several times slower. Windows Home can still have it on through
**Memory Integrity** (Core Isolation), **Virtual Machine Platform** or **WSL2**. The
create script warns if it detects this. To turn it off (then reboot):

```powershell
bcdedit /set hypervisorlaunchtype off
# Windows Security > Device security > Core isolation > Memory integrity: Off
# optional features: Virtual Machine Platform, Windows Hypervisor Platform: off
```

This disables WSL2 and Docker Desktop's WSL backend until you set it back to `auto`.
Also enable VT-x/AMD-V in the BIOS if VirtualBox complains about it.

## Housekeeping

* Roll back to a clean VM: `VBoxManage snapshot swt-win11 restore clean-setup`
* Stop: `VBoxManage controlvm swt-win11 acpipowerbutton`
* Delete: `VBoxManage unregistervm swt-win11 --delete`
* An evaluation VM expires after 90 days; restore the snapshot and run `slmgr /rearm`
  or recreate the VM.
