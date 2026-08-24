# Visual Oracle SPI

This document freezes the programming interfaces of the SWT Visual Oracle harness.
Every parallel task builds against these shapes, so they change only through the
SPI change request process in `TRACKING.md`, never by direct edits on worker branches.

Code lives in `tools/oracle/harness/src`, package root `org.eclipse.swt.visualoracle`.
It is plain Java compiled with `javac` by `tools/oracle/build-harness.sh`; no Maven, no Tycho.

## Package layout and stability promises

| Package | Stability |
|---|---|
| `org.eclipse.swt.visualoracle.spi` | Frozen. Signatures, semantics and enum constants are stable within this phase of the project. |
| `org.eclipse.swt.visualoracle.json` | Stable behaviour, not frozen API. The strict parser and the schema validator may gain capability but never loosen silently. |
| `org.eclipse.swt.visualoracle.result` | Convenience model behind result schema v1. The JSON serialisation is the contract; this package only mirrors it. |
| `org.eclipse.swt.visualoracle.impl` | Reference implementations, explicitly replaceable by later tasks (T04, T06, T09). Nothing outside the harness may rely on them. |
| `org.eclipse.swt.visualoracle.tools` | `SelfTest` and the `OracleCli` entry point. |

## Process model

Two constraints from the merged foundation tasks shape everything:

* Each backend links its own natives and class output (ADR-002), so at most one
  backend is active per process.
* SWT reads zoom, theme and text direction once at `Display` creation, so one
  process serves exactly one `RenderEnv`.

A full comparison therefore runs several single-purpose processes (one per
backend and environment combination) and merges their results at the JSON level.
Later tasks build on this: T05 automates the per-environment processes and T20
merges and compares across them.

## Interfaces

### `Specimen`

Declares what to render: one stable id (`"button.push.default"`), one
`preferredSize()`, one factory `create(Composite, SpecimenContext)` returning
exactly one control.

Implementers may assume: the parent composite belongs to an opened shell owned by
the capture runtime; `create` is called on the display thread; the returned control
is laid out, settled and disposed by the runtime, never by the specimen.

The specimen must be deterministic under identical inputs: no wall-clock time,
no animation phase, no hover or focus stealing inside `create`.
Specimens never open shells, never pump the event loop and never capture pixels.
They must call `SpecimenContext.configure(Control)` on the control they return.

### `SpecimenContext`

Carries the process' `RenderEnv` (`env()`) and applies it to a fresh control via
`configure(Control)` (text orientation and base font).
Calling `configure` exactly once per created control is part of the specimen contract;
it is how the harness guarantees all backends render under identical settings.

### `SpecimenModule` and `SpecimenCatalog`

One widget family contributes one `SpecimenModule` in
`org.eclipse.swt.visualoracle.catalog`, with a class name ending in `Module` and a
public no-argument constructor: `family()` returns the lowercase family name,
`specimens()` returns that family's specimens without touching a Display.

`SpecimenCatalog.discover()` finds every such class by scanning the compiled
catalog package, then fails loudly on a duplicate specimen id.

There is deliberately **no central registration file**. Widget families are added by
several agents working in parallel, and a shared list would make every one of them
conflict with the others. Adding a family means adding files, never editing them.

### `Tag`

Filterable specimen properties: `TEXT_HEAVY`, `ANIMATED`, `FOCUS_SENSITIVE`,
`NATIVE_POPUP`. Enum constants are frozen because they appear in reports and filters.

### `Backend`

Adapts one rendering stack. `id()` values for the built backends are fixed:
`native`, `skia-canvas`, `skija-proto`, matching `tools/oracle/build.sh`.

`configure(Display)` must prove the backend is genuinely active before any
specimen is created, throwing `BackendUnavailableException` otherwise.
A silent fall-back to native rendering would make comparisons report perfect
agreement between two copies of the same renderer; that failure mode is the reason
`configure` exists and why `tools/oracle/verify-backend.sh` must stay able to say no.

`supports(Specimen)` reports coverage: a backend does not have to render every widget.
Unsupported specimens surface as status `UNSUPPORTED` in results and are never
counted as rendering defects.

### `RenderEnv`, `Theme`, `Direction`

`RenderEnv(zoomPercent, theme, direction, fontFamily, fontSize)` pins one rendering
environment. An empty theme id means platform default; an empty font family means
use the system font unchanged; `fontSize` follows the `FontData` convention
(positive points, negative pixels).

An environment is bound to a process, not switchable (see process model above).

### `Capture` and `CapturedImage`

`capture(Specimen, Backend, RenderEnv)` renders the specimen through the backend
and returns the on-screen pixels of exactly the control bounds, as decided by ADR-001
(`GC.copyArea` primary strategy).

Contract:

* Deterministic: identical inputs produce identical images.
* Environment-bound: throws `UnsupportedEnvironmentException` when asked for an
  environment other than the one the process was started with.
* Coverage-aware: callers check `Backend.supports` first; `Capture` throws
  `UnsupportedSpecimenException` if invoked anyway.
* Failure-as-data: any other problem throws `CaptureFailedException`; the run records
  status `FAILED` with the message and continues.

`CapturedImage` holds plain image data that survives disposal of shells and displays,
plus `pngBytes()`, which must be byte-identical for identical pixels because results
reference PNGs as evidence.

### `Differ`, `Tolerance`, `DiffResult`, `DiffCluster`, `Verdict`, `DefectClass`

`compare(reference, candidate, tolerance)` compares two captures of the same specimen.
The oracle side is named `reference` (normally native), the side under test `candidate`.

A pixel counts as changed when any channel differs by more than
`Tolerance.maxChannelDelta`; `maxChangedFraction` separates WITHIN_TOLERANCE from
DIFFERENT. Verdicts: `EQUAL` (bit-identical), `WITHIN_TOLERANCE`, `DIFFERENT`.

`DiffResult` carries verdict, changed-pixel count, changed fraction, maximum observed
channel delta, connected `clusters` and a probable `DefectClass`.
Size mismatches are DIFFERENT over the whole area, not exceptions.
The minimal `ExactDiffer` produces one bounding-box cluster; real cluster detection
is T06, classification mapping is T07. Both replace the engine behind this interface.

### Exceptions

`BackendUnavailableException` (activation cannot be proven), `CaptureFailedException`
(capture failed, run continues), `UnsupportedSpecimenException` and
`UnsupportedEnvironmentException` (both extend `CaptureFailedException`).
All are unchecked; the harness never swallows `BackendUnavailableException`.

## Result JSON

The machine-readable outcome of a run is documented in `RESULT-SCHEMA.md`
and enforced by `ResultSchemaValidator`, which the selftest exercises in both
directions (valid documents accepted, mutated documents rejected).
Field names, enum values and the strict no-unknown-properties rule are frozen per
schema version; changes require bumping `schemaVersion`.

## CLI

Entry point: `tools/oracle/oracle <verb>`, implemented by `OracleCli`.
The wrapper compiles the harness and runs graphical verbs headless under Xvfb with
the Wayland variables unset, so callers never apply that incantation themselves.

| Verb | State | Behaviour |
|---|---|---|
| `selftest` | implemented | End-to-end pipeline proof, non-zero exit on any failure. |
| `version` | implemented | Tool and schema version. |
| `help` | implemented | Usage. |
| `run` | reserved for T20 | Attach point: dispatch case in `OracleCli`, exits 3 until delivered. |
| `triage` | reserved for T20 | Attach point: dispatch case in `OracleCli`, exits 3 until delivered. |

Exit codes: 0 success, 1 selftest failure or runtime error, 2 usage error,
3 verb reserved for a later task.

## Deviations from the PLAN.md SPI sketch

The sketch predates T01 and T02; where their findings contradict it, the findings win.

* **Same-process dual rendering replaced by multi-process merging.**
  The plan sketched both backends rendering in one process; ADR-002 showed each
  backend needs its own classpath and natives, and SWT pins environment at Display
  creation. Results are therefore produced per process and compared at the JSON level.
* **`Capture` keeps the sketch signature but gains environment semantics.**
  Passing `RenderEnv` per call would suggest in-process switching; instead the
  implementation must verify the request matches the process environment and fail
  with `UnsupportedEnvironmentException` otherwise.
* **`DiffResult.changedPixels` added** because the task requires a changed-pixel count;
  the sketch had only the fraction.
* **Parameter naming**: `native_` became `reference`/`candidate` (`native` is a Java
  keyword and the underscore spelling adds nothing).
* **Result status model added** (`CAPTURED` / `UNSUPPORTED` / `FAILED`) so that
  `Backend.supports` gaps and capture failures are data consumers can rely on;
  the sketch had no place for either.
