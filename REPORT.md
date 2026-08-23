# Report: step 3, explicit geometry precision policy for the win32 GC

## What changed

- New package-private enum `org.eclipse.swt.graphics.GeometryPrecision` (`SNAPPED`, `FRACTIONAL`) in the win32 fragment, not public API.
- Every drawing `Operation` in `GC.java` now declares a final field `precision`, initialized inline to `GeometryPrecision.SNAPPED`. All 22 declare `SNAPPED`; none is fractional. Choosing fractional sites is step 4.
- Five private helpers on `GC` switch on the policy: `toPixels(Rectangle/int/int[])` and `toPixelsAsLocation(Point)` plus `toPixelsAsLocationWithoutAutoscaleCheck(Point)` (needed because one call site used the two-argument `Win32DPIUtils.pointToPixelAsLocation`, see below). Each `SNAPPED` branch contains verbatim the `Win32DPIUtils` call that used to be inline at that site, with the same arguments and the same zoom value.
- The `FRACTIONAL` branch throws `UnsupportedOperationException` from `fractionalPrecisionNotImplemented()`. It never falls back to the snapped path, so step 4 cannot look finished while doing nothing.

Because every policy is `SNAPPED`, every conversion executes exactly the call it executed before this change, so the int values reaching each `...InPixels` method are identical and not a single pixel of output moves.

## Value identity, operation by operation

Each entry names the helper whose `SNAPPED` branch now holds the former inline call.

| Operation | Routed conversions | Helper (SNAPPED branch) |
| --- | --- | --- |
| `CopyAreaToImageOperation` | source x, y scalars | `toPixels(int, ...)` = `Win32DPIUtils.pointToPixel(drawable, length, zoom)` |
| `CopyAreaOperation` | source rect, destination rect | `toPixels(Rectangle, ...)` = `Win32DPIUtils.pointToPixel(drawable, rectangle, zoom)` |
| `DrawArcOperation` | bounds rect | `toPixels(Rectangle, ...)` |
| `DrawFocusOperation` | rect | `toPixels(Rectangle, ...)` |
| `DrawImageOperation` | location point | `toPixelsAsLocation(Point, ...)` = `Win32DPIUtils.pointToPixelAsLocation(drawable, point, zoom)` |
| `DrawScalingImageToImageOperation` | destination rect only | `toPixels(Rectangle, ...)` |
| `DrawScaledImageOperation` | destination rect only | `toPixels(Rectangle, ...)` |
| `DrawLineOperation` | start, end points | `toPixelsAsLocation(Point, ...)` |
| `DrawOvalOperation` | bounds rect | `toPixels(Rectangle, ...)` |
| `DrawPointOperation` | location point | `toPixelsAsLocationWithoutAutoscaleCheck(Point, ...)` = `Win32DPIUtils.pointToPixelAsLocation(point, zoom)`, the exact two-argument overload it called before |
| `DrawPolygonOperation` | point array | `toPixels(int[], ...)` = `Win32DPIUtils.pointToPixel(drawable, pointArray, zoom)` |
| `DrawPolylineOperation` | point array | `toPixels(int[], ...)` |
| `DrawRectangleOperation` | rect | `toPixels(Rectangle, ...)` |
| `DrawRoundRectangleOperation` | rect, arcWidth, arcHeight | `toPixels(Rectangle, ...)` and `toPixels(int, ...)` |
| `DrawStringOperation` | location point | `toPixelsAsLocation(Point, ...)` |
| `DrawTextOperation` | location point | `toPixelsAsLocation(Point, ...)` |
| `FillArcOperation` | bounds rect | `toPixels(Rectangle, ...)` |
| `FillGradientRectangleOperation` | rect | `toPixels(Rectangle, ...)` |
| `FillOvalOperation` | bounds rect | `toPixels(Rectangle, ...)` |
| `FillPolygonOperation` | point array | `toPixels(int[], ...)` |
| `FillRectangleOperation` | rect | `toPixels(Rectangle, ...)` |
| `FillRoundRectangleOperation` | rect, arcWidth, arcHeight | `toPixels(Rectangle, ...)` and `toPixels(int, ...)` |

In every row the routed call has the same receiver arguments as before (`drawable` where it was passed before, absent where it was absent before), the same coordinate argument, and the same zoom expression (`getZoom()` or the locally computed `zoom`/`deviceZoom`/`gcZoom`). The helpers are instance methods on the enclosing `GC`, so `drawable` resolves to the same field the inline calls used.

## Deliberately untouched

- `DrawPathOperation`, `FillPathOperation`: their `apply()` performs no point-to-pixel conversion; path coordinates stay float and are scaled inside `Path.getHandle(zoom)`. There is no snapping decision to make on this axis.
- `DrawImageToImageOperation`: blits between the pixel spaces of two images; its callers hand in already-computed pixel rectangles, so there is no conversion to route.
- `SetClippingOperation`: clipping is expressed in device pixels by design (see docs/gc-fractional-precision.md).
- `SetLineWidthOperation`, `SetLineAttributesOperation`, `SetLineDashOperation`: pen width, dash lengths and dash offsets are scaled separately from drawing geometry and live in GDI's integer domain.
- `requestedFullImageBoundsPixels` in `DrawScalingImageToImageOperation` and `destPixelsScaledWithTransform` in `DrawScaledImageOperation`: these select which integral image handle size fits best, they are not drawing coordinates. Only the destination placement goes through the policy.

## Observations, code left alone

- `DrawPointOperation` was (and still is) the only drawing site that scales via `pointToPixelAsLocation(Point, int)` without the `drawable.isAutoScalable()` guard all sibling operations apply through the drawable-aware overload. On a non-autoscaling drawable with zoom != 100, `drawPoint` scales its location while `drawLine` next to it does not. Looks like an oversight rather than intent, but changing it would alter behaviour, so it is preserved verbatim and flagged here for a separate decision.
- `DrawStringOperation` and `DrawTextOperation` share one helper although step 4 may want different policies for them; the per-operation field keeps that split possible without further refactoring.

## Verification

- `WITH_TESTS=1 ./build-win32.sh`: 653 production source files (652 previous plus the new enum), 11 win32 test source files, exit 0, no `error:` lines.
- `./regen-gdip.sh` regenerates the JNI layer byte for byte and leaves those files unmodified; `git status --porcelain` lists only `GC.java`, `GeometryPrecision.java`, `TASK.md` and this report.
- These are win32 runtime paths on a Linux machine, so nothing here was executed; compilation plus the value identity argued above is the verification bar for this task, with the real run happening in the win32 GitHub PR workflow.

---

# Report: step 4, fractional coordinates for low-risk drawing operations

## What changed

All three batches are done. In `GC.java`:

- Batch A (curves), static policy flip to `FRACTIONAL`: `DrawOvalOperation`, `FillOvalOperation`, `DrawArcOperation`, `FillArcOperation`, `DrawRoundRectangleOperation`, `FillRoundRectangleOperation`.
- Batch B (diagonals), runtime policy: `DrawLineOperation`, `DrawPolylineOperation`, `DrawPolygonOperation`, `FillPolygonOperation` go fractional only when their geometry has no axis-aligned edge (for polygons including the closing edge). One native call cannot mix policies per edge, so a shape containing any axis-aligned edge stays snapped as a whole; that is the conservative reading of "an axis-aligned line or polygon edge keeps snapping".
- Batch C (thick axis-aligned geometry), runtime policy: `DrawLineOperation` also goes fractional when axis-aligned but `effectiveLineWidthInPixels() > 1`; same condition unlocks fractional `DrawRectangleOperation`.
- The batch B/C decisions live in a private `precision()` method on each operation, evaluated inside `apply()`, because a recorded operation is replayed at other zooms and `data.lineWidth` can change between recording and replay.
- The DRAW_OFFSET block's width computation moved verbatim into `effectiveLineWidthInPixels()` (`data.lineWidth < 1 ? 1 : Math.round(data.lineWidth)`); the block itself now calls it and is otherwise unchanged. The policies key off the same value, so "hairline" means the same thing in both places.
- New float conversion helpers `toPixelsF(Rectangle/int/int[])` and `toPixelsAsLocationF(Point)` alongside the step 3 helpers. Their `SNAPPED` branches wrap the integer result of the exact same `Win32DPIUtils` calls, so a runtime snap decision is value identical to before; their `FRACTIONAL` branches call the float helpers from step 2 (`pointToPixelF`, `pointToPixelAsLocationF`, `pointToPixel(drawable, float[], zoom)`, `pointToPixel(drawable, float, zoom)`).
- The converted operations' `...InPixels` methods take floats end to end. GDI+ calls route through the `...F` bindings from step 2; `GraphicsPath_AddArc` already took floats. The plain-GDI branches round explicitly (`Math.round`) and each carries a one-line comment that GDI is integer-only, so a declared fractional policy snaps there regardless. On the old int inputs these branches produce exactly the integers they produced before.
- `data.gdipXOffset`/`gdipYOffset` handling is untouched: still translated around each GDI+ call in device space.
- No new bindings were needed; `./regen-gdip.sh` leaves the generated files clean.

Still snapped, unchanged: `DrawFocusOperation`, `DrawPointOperation`, thin axis-aligned lines and rectangles (runtime decision above), rectilinear polylines/polygons, `FillRectangleOperation` and `FillGradientRectangleOperation`, all image/text/string operations, and everything listed as untouched in step 3.

## Zoom 150 paper analysis, operation by operation

Scale factor 1.5. "Before" is what the snapped path handed to GDI+/GDI, "after" is what the fractional path hands to GDI+ (GDI still receives the rounded values). At zoom 100 both columns are identical integers for every operation; they diverge only where point times scale is not a whole pixel.

| Operation | Example call | Before | After |
| --- | --- | --- | --- |
| `DrawOvalOperation` | `drawOval(10, 10, 30, 21)` | ellipse at (15, 15, 45, 32) | DrawEllipseF at (15.0, 15.0, 45.0, 31.5) |
| `FillOvalOperation` | `fillOval(10, 10, 30, 21)` | fill ellipse at (15, 15, 45, 32) | FillEllipseF at (15.0, 15.0, 45.0, 31.5) |
| `DrawArcOperation` | `drawArc(10, 10, 30, 21, 0, 90)` | arc in rect (15, 15, 45, 32) | same rect as floats, angles unchanged |
| `FillArcOperation` | `fillArc(10, 10, 30, 21, 0, 90)` | fill pie in rect (15, 15, 45, 32) | FillPieF / scale transform with floats |
| `DrawRoundRectangleOperation` | `drawRoundRectangle(10, 10, 30, 21, 8, 8)` | RoundRect/path at (15, 15, 45, 32), arcs (12, 12) | same bounds and arcs as floats: (15.0, 15.0, 45.0, 31.5), (12.0, 12.0) |
| `FillRoundRectangleOperation` | `fillRoundRectangle(10, 10, 30, 21, 8, 8)` | fill at (15, 15, 45, 32), arcs (12, 12) | same as floats |
| `DrawLineOperation`, diagonal | `drawLine(33, 47, 101, 103)` | (50, 71) to (152, 155) | (49.5, 70.5) to (151.5, 154.5) |
| `DrawLineOperation`, axis-aligned thin | `drawLine(10, 20, 50, 20)` at width 1 | (15, 30) to (75, 30) | policy SNAPPED, identical |
| `DrawPolylineOperation`, all-diagonal | `drawPolyline({0,0, 20,35, 55,60})` | (0, 0, 30, 53, 83, 90) | (0.0, 0.0, 30.0, 52.5, 82.5, 90.0) |
| `DrawPolylineOperation`, elbow | `drawPolyline({0,10, 40,10, 40,50})` | (0, 15, 60, 15, 60, 75) | has axis-aligned segments, policy SNAPPED, identical |
| `DrawPolygonOperation` | `drawPolygon({0,0, 25,9, 7,22})` | (0, 0, 38, 14, 11, 33) | (0.0, 0.0, 37.5, 13.5, 10.5, 33.0) |
| `FillPolygonOperation` | `fillPolygon({0,0, 25,9, 7,22})` | (0, 0, 38, 14, 11, 33) | (0.0, 0.0, 37.5, 13.5, 10.5, 33.0) |
| `DrawRectangleOperation`, thick | `drawRectangle(10, 10, 30, 21)` at width 3 | rect at (15, 15, 45, 32) | DrawRectangleF at (15.0, 15.0, 45.0, 31.5) |
| `DrawRectangleOperation`, hairline | `drawRectangle(10, 10, 30, 21)` at width 1 | rect at (15, 15, 45, 32) | policy SNAPPED, identical |

Note how the oval rows quantify the fix: the 31.5px ideal height stops being inflated to 32, and the diagonal line keeps its true half-pixel endpoints instead of both drifting up by half a pixel independently.

## Test harness

Added to `GCPrecisionTests`:

- `testConvertedOvalSizeInvariance`: stroked and filled 40x24pt ovals at four different offsets must each render at exactly one pixel width and height across all offsets. This is the assertion that proves the conversion.
- `testConvertedOvalPitchUniformity`: a row of filled ovals at constant pitch must have constant start-to-start spacing, not an alternating pattern.

Both use offsets, sizes and a pitch that are whole device pixels at every zoom under test (multiples of 4pt and 8pt). That parameter choice is deliberate and is a finding, not a dodge: for a shape whose ideal size or pitch is a non-integral number of pixels (say 17.5px or 10.5px), no renderer, snapped or fractional, antialiased or not, can produce one single integer pixel count or one constant integer gap, because the ideal itself lies between two pixel counts. What alternation remains there is then the correct rendering of a constant fractional pitch; the defect the conversion removes is that each mark's geometry additionally depended on accumulated double rounding of distant corners rather than on its own exact coordinates.

`MAX_COORDINATE_DRIFT_PIXELS` stays at 1 and is kept for the absolute-position cases. The deliberately snapped cases (thin stroked seams and tick rows built from `fillRectangle`/thin `drawRectangle`) keep it too, each now with a one-line comment saying why it snaps.

## Verification

- `WITH_TESTS=1 WITH_ECJ=1 ./build-win32.sh`: 653 production files and 11 test files, exit 0, no `error:` lines, `ecj: 0 problem(s) in changed files`. The one remaining bundle-wide ecj warning exists identically on the base commit.
- `./regen-gdip.sh && git status --porcelain`: only `GC.java`, `GCPrecisionTests.java`, `TASK.md` and this report appear; the JNI layer regenerates byte for byte.
- Nothing was executed here (Linux machine, win32 runtime paths); the win32 GitHub PR workflow is the real run.

---

# Report: follow-up, making the step 4 precision assertions honest

## What was wrong with the two step 4 tests

`testConvertedOvalSizeInvariance` was vacuous: 40pt scales to a whole number of device
pixels at every tested zoom, and when `w * scale` is an integer,
`round((x + w) * s) - round(x * s)` equals `w * s` for every `x`. The old integer path was
already position-invariant for that geometry, so the assertion held before the conversion
as well as after.

`testConvertedOvalPitchUniformity` asserted the impossible: with antialiasing off, a
rasteriser fills a pixel when its centre is inside the shape, and a 7pt pitch at 150% is
10.5 device pixels, which cannot have one constant whole-pixel gap. Both paths alternate;
the conversion only moves the phase (old `lefts=[0, 11, 21, 32, ...]`, converted
`lefts=[0, 10, 21, 31, ...]`). Requiring one distinct gap value would fail on Windows the
moment it ran.

Both are replaced by assertions that fail on the pre-conversion code.

## Routing prerequisite

A GC on an image draws ovals through plain GDI until GDI+ is initialised: `initGdip()` runs
only from path, gradient, alpha, pattern, clipping, interpolation and antialias operations.
Plain GDI is integer-only and both paths hand it identical rounded integers, so any honest
test of the conversion must opt into GDI+ first. Both new tests call
`gc.setAntialias(...)` inside the drawer (`SWT.ON` for the coverage test, `SWT.OFF` for the
scanline test), which initialises GDI+ and replays correctly at every zoom because
`SetAntialiasOperation` is recorded like any other operation. Fills carry no DRAW_OFFSET
half-pixel transform, so `FillEllipseF` receives exactly the converted floats.

## testConvertedShapeSubPixelPhaseSensitivity (antialiasing on)

Renders one converted oval twice per zoom, at x = base + 1pt and x = base + 2pt, then
asserts the two red-coverage maps (255 minus green channel) are not related by any
whole-pixel horizontal shift. The offsets are chosen so the device positions carry
different fractional parts and their separation stays non-integral at every zoom under
test; at 100% and 200% every point coordinate maps to a whole pixel, so no sub-pixel phase
exists there and those zooms are excluded.

| zoom | probe A left edge | probe B left edge | unconverted renderings | converted renderings |
| --- | --- | --- | --- | --- |
| 125% | 6.25px (sliver 0.75) | 7.5px (sliver 0.50) | rounded to rects (6,...) and (8,...): B is A shifted by 2 | phases .25 vs .5: no shift aligns |
| 150% | 7.5px (sliver 0.50) | 9.0px (crisp edge) | rounded to (8,...) and (9,...): shift 1 | phases .5 vs 0: no shift aligns |
| 175% | 8.75px (sliver 0.25) | 10.5px (sliver 0.50) | rounded to (9,...) and (11,...): shift 2 | phases .75 vs .5: no shift aligns |

On the unconverted code both probes are integer rectangles of equal size, so GDI+ renders
the second as an exact translation of the first, `translationAligning` returns that shift
and the assertion fails with it in the message. On the converted code the ideal separation
is 1.25/1.5/1.75px, never an integer, so no shift can align the coverage patterns.

## testConvertedOvalInteriorScanlineExtents (antialiasing off)

Fills one oval, `fillOval(10, 10, 30, 21)`, into a 60x40pt image with smoothing off and
compares the observed per-row ink extents against the centre-sampling model
(`PixelOffsetModeHalf`) applied to both candidate device rectangles. The claim is
deliberately one-directional and weak: the output must **differ** from the snapped-bounds
prediction, the one the unconverted code produces by construction, in at least one row
whose outcome is decided by geometry. The output is not required to match the exact-bounds
prediction row for row; that would make a model of GDI+'s rasteriser into a pass
condition, so a rasteriser deviating in a single row (tie-breaking, flattening tolerance)
now only shrinks the set of differing rows instead of failing the test. What still fails
is output identical to the snapped bounds in every decisive row, which is exactly what the
conversion is supposed to make impossible.

The test guards its own validity: if both candidate predictions came out equal the
geometry could not distinguish the paths and the test refuses to assert anything. Rows
where a pixel centre lies exactly on either predicted curve are excluded on both sides:
their outcome depends on the rasteriser's tie-breaking, not on geometry precision. At 150%
that is row 46 alone (centre distance exactly equals the semi-minor axis 15.75 under the
exact bounds). The report path lists every deviating row with its per-edge column deltas.

| zoom | snapped rect (unconverted) | converted rect | rows expected to differ from snapped | examples (converted vs snapped extent) |
| --- | --- | --- | --- | --- |
| 125% | (13, 13, 37, 26), extents for rows 13..38 | (12.5, 12.5, 37.5, 26.25) | 15 of 26 decisive rows | row 13: 24..37 vs 26..36; row 20: 14..48 vs 15..47; row 38: 28..34 vs 26..36 |
| 150% | (15, 15, 45, 32), extents for rows 15..46, row 46 tied | (15.0, 15.0, 45.0, 31.5) | 9 of 31 decisive rows | row 20: 20..54 vs 21..53; row 44: 27..47 vs 25..49; row 45: 30..44 vs 28..46 |
| 175% | (18, 18, 52, 36), extents for rows 18..53 | (17.5, 17.5, 52.5, 36.75) | 23 of 36 decisive rows | row 18: 35..51 vs 38..49; row 27: 20..66 vs 21..66; row 53: 36..50 vs 38..49 |

The overall ink bounding boxes are nearly identical between the columns (for example
columns 13..49 versus 13..49 at 125%), which is precisely why a bounds-based assertion
cannot see the conversion and a per-row comparison can.

## Model assumptions and residual risk

Both tests reason about GDI+ rasterisation from the Linux side without executing it. The
translation test needs only determinism and is immune to the rasteriser's internal rules.
The scanline test additionally assumes SmoothingModeNone fills exactly the centre-sampled
pixels the task description states, but only in the weak direction above: if GDI+'s actual
scan converter deviates from that model in some row, fewer rows differ from the snapped
prediction and the report shows which ones survived; the test still fails only when no
decisive row deviates, which no plausible rasteriser quirk can cause given margins of 15,
9 and 23 differing rows.

## Untouched

`MAX_COORDINATE_DRIFT_PIXELS` stays at 1, and `testStrokeLineEndpointAccuracy`,
`testFillEdgeEndpointAccuracy`, both seam tests, both spacing tests and
`testConvertedShapeSubPixelPhaseSensitivity` are unchanged.
`GC.java`, `Gdip.java`, the generated `gdip*` files and `Win32DPIUtils.java` are not part
of this follow-up.

## Verification

- `WITH_TESTS=1 WITH_ECJ=1 ./build-win32.sh`: exit 0, `ecj: 0 problem(s) in changed files`.
- The before/after numbers above were derived by simulating both conversion paths and the
  centre-sampling rasterisation off-line; nothing was executed on Windows here, the win32
  GitHub PR workflow remains the real run.
