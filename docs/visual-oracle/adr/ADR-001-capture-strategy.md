# ADR-001: Capture strategy for the visual oracle

Date: 2026-08-23
Status: accepted
Task: T01

## Context

The harness renders each widget through a native backend and a Skija backend and compares the images.
How those images are obtained decides whether the comparison is meaningful at all.

Three strategies were implemented and measured, on Linux with GTK3 under Xvfb with llvmpipe:

1. `print`: `Control.print(GC)` into an `Image`.
2. `copyarea`: `new GC(control)` plus `GC.copyArea(Image, 0, 0)`.
3. `xgrab`: full screen grab with ImageMagick `import -window root`, cropped to widget bounds. Measured both as a full grab plus in-process crop (`xgrab`) and as a grab with `import`'s own `-crop` (`xgrab-crop`).

The spike is `tools/oracle-spike/`, reproducible with `bash tools/oracle-spike/run-spike.sh`.
It runs three scenarios (native at zoom 100, native at zoom 200, Skia canvas at zoom 100), captures a `Button` and, where applicable, a `Canvas` created with `SWT.SKIA`, and writes 14 evidence PNGs plus 15 `RESULT` lines.

The decisive question was set before any measurement: the Skia canvas renders into a GLX child window created with `gdk_window_new`, not into the widget's cairo surface, so a strategy that reads back the cairo surface may capture nothing for exactly the widgets this harness exists to compare.

## Measurements

`glMilli` is the fraction of captured pixels in thousandths matching one of the three colors the Skia canvas paints.
The canvas is painted entirely with those colors, so a strategy that sees the GL content reports several hundred; one that is blind to it reports zero.

Capture of the `SWT.SKIA` canvas, scenario `skia-z100`:

| strategy | glMilli | unique colors | dominant color |
|---|---|---|---|
| `print` | **0** | 8 | 963 |
| `copyarea` | **580** | 5 | 403 |
| `xgrab` | **580** | 5 | 410 |

Speed, mean milliseconds per capture of a `Button`, 50 captures:

| strategy | zoom 100 | zoom 200 |
|---|---|---|
| `print` | 0.97 | 1.66 |
| `copyarea` | **0.52** | **1.28** |
| `xgrab` | 154.57 | 157.08 |
| `xgrab-crop` | 71.63 | 68.31 |

Determinism, sha256 of the capture across five separate processes:

| strategy | verdict | hash |
|---|---|---|
| `print` | IDENTICAL | `4f14dbfd…` |
| `copyarea` | IDENTICAL | `87c97ee7…` |
| `xgrab` | IDENTICAL | `87c97ee7…` |

Crop precision. The scenario paints a magenta surround outside the widget bounds, so magenta pixels inside an edge band mean the crop leaked outside the widget:

| strategy | zoom 100 | zoom 200 |
|---|---|---|
| `print` | clean, 148 colors | clean, 147 colors |
| `copyarea` | clean, 148 colors | clean, 148 colors |
| `xgrab` | clean, 148 colors | **24 marker pixels top and left, only 15 colors** |

## Decision

**Use `copyarea`, that is `new GC(control)` plus `GC.copyArea`, as the capture strategy. Keep `xgrab` as a fallback for cases it cannot serve. Do not use `print`.**

`print` is disqualified twice over.
It is blind to GL content, reporting `glMilli=0` where the canvas is 58 percent GL-painted, so it would capture an empty image for the Skia canvas and the harness would silently compare native against nothing.
Independently, its capture of a plain native `Button` hashes differently from both other strategies, which agree with each other byte for byte.
`Control.print` reproduces a re-rendering of the widget rather than what is on screen, and the oracle needs what is on screen.

`copyarea` wins every remaining criterion.
It sees GL content, it is the fastest by a wide margin at 0.52 ms against 154 ms for a full grab, it is deterministic across processes, and its crop stays exact at zoom 200 where the X11 grab does not.

`xgrab` is correct but expensive and fragile.
At 154 ms per capture it is roughly 300 times slower, which for a catalog of a few thousand specimens across a DPI and theme matrix is the difference between a run of minutes and a run of hours.
Its crop origin is also wrong under scaling: at zoom 200 it picked up 24 marker pixels on the top and left edges and captured only 15 distinct colors instead of 148, meaning it captured a shifted region.
That is fixable by scaling the crop origin, but it is a defect that must be fixed before the fallback is used at any zoom other than 100.

## Consequences

The harness captures per widget, not per shell, which suits the specimen model in `PLAN.md`: one specimen creates one control, and capture asks that control for its pixels.

T04 implements `copyarea` as the primary `Capture` implementation and `xgrab` behind the same interface.
Both stay available, because the fallback matters for anything `copyarea` cannot reach, for example a widget that owns a native popup window outside its own bounds.

Before `xgrab` is used at zoom other than 100, its crop origin must be scaled.
This is recorded as an open item for T04 rather than fixed here, because the spike only needed to establish which strategy to build on.

Screen grabbing needs no X server privileges beyond the Xvfb display the harness already creates, so the fallback stays usable in CI.

## Limitations

Measured on Linux with GTK3 only, under Xvfb with `LIBGL_ALWAYS_SOFTWARE=1`, on one machine.

**Win32 is not verified.** No Windows machine was available.
Reading the sources, `Control.print` on Win32 goes through `WM_PRINT` and `PrintWindow`, which is a re-render for the same reason it is on GTK, so the GL blindness very likely reproduces there.
`GC.copyArea` on Win32 is a `BitBlt` from the widget device context and should see whatever the window actually contains.
Both statements are inference, not measurement, and T22 must confirm them before the Windows matrix is trusted.

Absolute timings are from one machine under background load, so treat the ratios as the finding and the absolute numbers as indicative.
