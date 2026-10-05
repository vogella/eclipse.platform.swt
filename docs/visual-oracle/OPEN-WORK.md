# Visual oracle: open work

State of the cross-platform port and what is left to do.
Delete items as they are done, and this file once it is empty.

## Where things stand

| Platform | Selftest | Same-ref run (strict) | Verified on |
|---|---|---|---|
| macOS arm64 (Cocoa) | 66/66 | 218/218 equal in 4 consecutive runs, every image byte-identical across the runs | developer Mac |
| Windows x86_64 (Win32) | 66/66 | 218/218 equal (copyArea), 52/52 equal (print, measurement subset) | one GitHub Actions run on `windows-latest` |
| Linux x86_64 (GTK3) | not run since the port | not run since the port | compile check only |

The catalog has 218 specimens, 49 of them in the new `styledtext` family.
"Strict" means `--max-channel-delta 0 --max-changed-fraction 0`.

## Windows, on a real desktop

CI proves the harness on a clean runner at 100 percent scaling; a developer machine is noisier.

1. Check out `oracle/native-baseline` in Git Bash, with a JDK 21 or newer on `PATH` and Git LFS installed:
   ```bash
   git lfs pull --include='binaries/org.eclipse.swt.win32.win32.x86_64/*'
   tools/oracle/oracle selftest 2>&1 | tee selftest.log
   for i in 1 2 3 4; do
     ORACLE_BASELINE=HEAD ORACLE_CANDIDATE=HEAD tools/oracle/oracle run \
       --max-channel-delta 0 --max-changed-fraction 0 --out oracle-run-$i
   done
   ```
2. Set display scaling to 100 percent first; zoom 100 runs do not change the monitor scale, and a mismatch is refused as an unsupported environment.
3. Keep the machine idle while it runs: children open real windows on the desktop, one at a time.
4. Compare the four runs across each other, not only each run's own verdict: the same specimen must give the same image hash in every run.
   The macOS check for that is a short script over `result.json` (`captures[].image` of the `native-baseline` side).
5. Repeat with dark mode on and with 125 or 150 percent scaling, to learn what is refused cleanly and what silently differs.
6. Zoom 200 is only proven through `swt.autoScale=200` on a 100 percent screen; check it on a real high-DPI monitor.

Known gaps on Windows:
- Only the platform default theme is supported; a named theme is refused, and the selftest skips the alternate-theme checks.
  SWT's dark mode (`OS.setTheme`) needs a live Display, so pinning it per child is not implemented.
- ClearType makes text pixels machine dependent, so baseline and candidate must always run on the same machine in one `oracle run`.
- The Windows fix commit has been through one CI run only; run the workflow a few more times (`workflow_dispatch`) to see whether the focus sink (`Win32PlatformSupport.parkFocus`) holds.

## macOS

- Compare `origin/master` against `framework-fixes` (the Cocoa clean-up branch): `ORACLE_BASELINE=origin/master ORACLE_CANDIDATE=framework-fixes tools/oracle/oracle run`.
  Not done yet, and it is the comparison the port was built for.
- SWT's Cocoa `Display` sets `launched = true` before it calls `finishLaunching()`, so its own override skips the real launch, which then happens on the first event loop pass and activates the app at an unpredictable moment.
  The harness works around it (`CocoaPlatformSupport.prepareDisplay`); it may deserve an SWT issue.
- Children run one at a time; with 2 or 4 concurrent children 16 to 21 specimens differed (see ADR-003).

## Linux (GTK)

- Run `tools/oracle/oracle selftest` and a strict same-ref run on Linux x86_64.
  The port moved all GTK code behind `PlatformSupport` and changed shared code (the synthetic defects in `DiffCheck`, the launch paths in `SelfTest`, child parallelism in `RunVerb`), and none of it has run on GTK since.

## StyledText

- `script.cjk` and `script.emoji` depend on platform font fallback: stable on one machine, possibly different between machines.
- On macOS, offsets inside an emoji with a skin-tone modifier report x positions beyond the end of the text; found by the StyledText characterization tests on `test-improvements-styledtext`, not caused by the clean-up.
