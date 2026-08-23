# Fractional drawing precision for the win32 GC

## Problem

`GC` rounds every drawing coordinate to a whole device pixel before the backend sees it.
At fractional zoom (125%, 150%, 175%) that produces visible artifacts: uneven gaps between
evenly spaced elements, one-pixel seams between adjacent shapes, and geometry that drifts
from its mathematically correct position by up to half a point.

## This is win32 only

GTK's `GC` never converts points to pixels.
Coordinates go straight to Cairo as doubles and the DPI scale is applied by GTK through the
GdkSurface device scale.
Cocoa does the same through the NSView backing scale.
Neither needs any change.

On win32 every drawing call is wrapped in an `Operation` whose `apply()` calls
`Win32DPIUtils.pointToPixel(...)`, which returns an **int** `Point` or `Rectangle`.
See `Eclipse SWT/win32/org/eclipse/swt/graphics/GC.java`, for example `DrawLineOperation`,
`DrawOvalOperation` and `DrawRectangleOperation`.

The GDI+ bindings compound it: every drawing entry point bound in
`Eclipse SWT PI/win32/org/eclipse/swt/internal/gdip/Gdip.java` is the integer `...I` variant
of the GDI+ API (`Graphics_DrawLine`, `Graphics_DrawEllipse`, `Graphics_DrawRectangle`,
`Graphics_FillEllipse`, `Graphics_FillRectangle`, `Graphics_DrawPolygon`,
`Graphics_FillPolygon`, `Graphics_DrawArc`, `Graphics_FillPie` are all `int`).
So even with advanced mode on, where GDI+ could do float math, SWT feeds it pre-rounded
integers.

`Graphics_DrawString` already takes a float `PointF` origin, so text positioning needs no
new binding.

## Not in scope

Public float overloads on `GC` are a separate, later decision.
Adding public float API on top of a pipeline that rounds internally buys the caller nothing,
so the internal pipeline has to be precise first.
Clients who need precise geometry today already have `Path` and `Transform`, both of which
take floats.

Float `Point` and `Rectangle` types are not going to happen.
They are structurally embedded in the API and in JFace, Draw2D, GEF, and every RCP
application.

## Approach

Two routes were considered.

**World transform.** `Graphics_ScaleTransform` is already bound, so the DPI scale could be
set as a GDI+ world transform and unscaled point coordinates passed through, letting GDI+ do
the fractional math.
No native change at all.
Rejected: `setLineWidth` already scales to int pixels separately, so pen widths would be
scaled twice; `data.gdipXOffset`/`gdipYOffset` are expressed in device space; clipping is set
in pixels; and a client `setTransform` would have to compose with the DPI matrix rather than
replace it.
Four separate places to get subtly wrong.

**Float bindings (chosen).** Add float overloads to `Gdip.java` and regenerate the JNI layer.
Each call site then becomes a one-to-one substitution with no semantic entanglement.

Java cannot overload on the native name alone, so float variants are named with an `F`
suffix (`Graphics_DrawLineF`) and carry `@method flags=cpp accessor=DrawLine`, which makes
the generator emit a call to the C++ `DrawLine` overload.
The `accessor` attribute is honoured by `NativesGenerator`.

## Hairline snapping

The regression that will actually bite is one-pixel decorations: focus rectangles, `Table`
and `Tree` gridlines, group borders, separators.
Today they round to a whole pixel and render as a crisp 1px line.
Made fractional, they antialias into two grey rows and read as "SWT got blurry".

So snapping is not something to remove, it is something to make explicit.
Fills, curves, diagonals and text positions become fractional.
Hairline primitives keep snapping to whole device pixels.

This risk is smaller than it first looks, and the reason matters for scoping.
SWT leaves GDI+ smoothing at `SmoothingModeDefault`, which is the GDI+ quality default and
means no antialiasing (`Gdip.java:114`; `SmoothingModeNone` is the separate value 3).
So with default settings a fractional coordinate improves *where* GDI+ decides the pixel
boundary falls without softening the edge.
Blurring only becomes possible once a client has explicitly called `setAntialias(SWT.ON)`,
and in that mode a fractional coordinate is what the client asked for.
Snapping therefore protects a narrower case than feared, but it still protects it, so the
hairline call sites keep it.
`RoundingMode` in `Eclipse SWT/common/org/eclipse/swt/graphics/RoundingMode.java` already
exists as an internal enum and is the natural place to express this per call site.

The plain-GDI (non-advanced) path is unchanged.
GDI is integer-only; there is no fix there, only a documented split in behaviour by advanced
mode.

## What fractional coordinates actually buy

Worth being precise about, because it decides what the harness should assert.

With smoothing off, which is the default, passing an exact fractional coordinate for a
single axis-aligned edge changes nothing. GDI+ rounds it to the same device pixel that the
pre-rounded integer already produced. An ink-column measurement of such an edge will show
no improvement, and a test that demands zero drift there cannot pass.

The defect that fractional coordinates do fix is **size and spacing instability**. Today a
rectangle's pixel width is derived as `round((x + w) * s) - round(x * s)`, so the same
point-size shape gets a different pixel size depending on where it sits:

    zoom=125  w=10pt  ideal 12.50px   today 12 or 13, depending on x
    zoom=175  w=10pt  ideal 17.50px   today 17 or 18, depending on x
    zoom=175  w=37pt  ideal 64.75px   today 64 or 65, depending on x

That is what a user sees as uneven tick spacing, columns of different widths, and jitter in
repeated elements. Measured as a pitch, an evenly spaced row at 150% comes out
11/10/11/10/11/10/11 instead of a constant 10.5.

So the payoff is:

1. Size and spacing invariance, regardless of antialiasing.
2. Correct curve and diagonal geometry, because the rasteriser derives every interior
   scanline from exact endpoints rather than from pre-rounded ones.
3. True sub-pixel placement, but only once a client has enabled antialiasing.

The harness should therefore assert invariance (same point size gives the same pixel size,
constant pitch gives constant spacing), not zero absolute drift.

## Local build harness

The win32 Java sources are pure Java, so they compile on Linux.

    ./build-win32.sh              # 652 files, ~8s
    WITH_TESTS=1 ./build-win32.sh # also compiles the win32 test fragment

    ./regen-gdip.sh               # regenerates gdip.cpp, gdip_stats.*, gdip_structs.*

`regen-gdip.sh` runs the repo's own JNI generator from `bundles/org.eclipse.swt.tools`
headlessly.
It reproduces the committed generated files byte for byte, so a run with no `Gdip.java`
change leaves the tree clean.
The generator does not emit the EPL copyright header, so the script preserves each file's
existing first 13 lines and splices the generated body underneath.

Tests cannot be *run* here.
They are win32 runtime tests; only compilation is verifiable locally, and the real run
happens in the win32 PR workflow.

## Sequence

1. Precision test harness in the win32 test fragment, documenting the current drift.
   No behaviour change.
2. `Gdip.java` float bindings plus regeneration. No call sites yet.
3. `RoundingMode` plumbed into the win32 `Operation` layer, all sites still snapping.
   Behaviour-neutral, sets up the switch.
4. Convert drawing operations in small batches, tightening the harness assertions per batch:
   lines and rectangles, then ovals and arcs, then polygons, then images and text.
5. Enable the tightened assertions in `AllNonBrowserTests_AutoscaleOsNonDefaults`, which
   already runs the suite at `swt.autoScale=quarter`.
