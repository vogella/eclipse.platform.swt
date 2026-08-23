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
