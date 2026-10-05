# ADR-003: Cocoa capture strategy and child parallelism

Date: 2026-10-05
Status: accepted

## Context

The Cocoa backend of the oracle needs a capture path that is byte-identical across processes and works unattended on a developer Mac.
`Screen Recording` permission cannot be granted unattended, and window ordering on the shared WindowServer must not reach the captures.

## Decision

### Capture strategy

`GC.copyArea` on the control is the only strategy.
It renders the view hierarchy (`cacheDisplayInRect`), so it needs no screen grab, no Screen Recording permission and is independent of window ordering.
Two processes render a push button to identical bytes.
`Control.print(GC)` renders the same button without its label and was rejected.
There is no grab fallback on Cocoa.

### Process setup

Every JVM starts with `-XstartOnFirstThread`, supplied through `PlatformSupport.jvmArguments()` for children, probes and raw test children, and through `ORACLE_PLATFORM_JAVA_OPTS` for the command line JVM.
`prepareDisplay` sets the activation policy to prohibited, so a child never becomes the active application and every window renders inactive.
Light and Dark are pinned with `Display.setDarkThemePreferred`.
Controls such as sliders and separators are fully native and may never send `SWT.Paint`, so settling waits 500 ms for them instead of requiring a paint event.

### Zoom

`-Dswt.autoScale=200` yields true 2x rendering: the 140x40 reference specimen captures as 280x80 and the child confirms the device zoom before capturing.

### Child parallelism

Same-ref runs (`ORACLE_BASELINE=HEAD ORACLE_CANDIDATE=HEAD`, zero tolerance, 218 specimens) on the real WindowServer:

| children | wall time | different specimens |
|---|---|---|
| 1 | 177 s | 0 |
| 2 | 89 s | 21 |
| 4 (old default) | 51 s | 16 |

The differing specimens are native combos, progress bars, scales, password and read-only texts and checked trees, so concurrent windows reach the rendering of native controls even though copyArea does not read the screen.
The default is therefore one child at a time, set by `platform.sh`.

## Consequences

The full catalog takes about three minutes per side-by-side run on an Apple Silicon Mac.
The selftest takes about 450 seconds.
