# SWT Visual Oracle: Tracking and Steering

This is the live coordination document for the project described in `PLAN.md`.

It has two readers.
The **orchestrator** is a Claude session that plans, dispatches, reviews and integrates.
The **workers** are opencode agents that each own exactly one task at a time and never talk to each other.

All coordination happens through this file.
If something is not written here, it did not happen.

## Roles

### Orchestrator (Claude)

Owns the board, the SPI, and the integration branch.
Decides what is ready to dispatch, writes the task brief, reviews returned work, merges it, and updates this document.

The orchestrator writes code in exactly two situations: SPI changes, and conflict resolution during integration.
Everything else is delegated.

### Worker (opencode agent)

Owns one task from claim to handoff.
Works in its own git worktree on its own branch.
Never edits SPI files, never edits this document except to append its handoff record, never merges anything.

A worker that believes the SPI must change stops and files a change request instead of editing it.

## Work protocol

### One task, one worktree, one branch

```bash
git worktree add ../oracle-<TASK_ID> -b oracle/<TASK_ID> oracle/integration
```

Branch names are `oracle/T04`, `oracle/T13` and so on.
The integration branch is `oracle/integration` and only the orchestrator pushes to it.

### Commit convention

One functional commit per task, amended as the worker iterates.
Subject line starts with the task ID: `T13: catalog specimens for buttons and labels`.

This matches the Eclipse convention of a clean single commit per change, and it makes the orchestrator's review a single diff rather than a history walk.

### Verification gate

No task is handed off until `oracle selftest` passes in the worker's worktree.
Once T20 exists, tasks that touch rendering must also show a passing filtered run, and the worker pastes the command and its output into the handoff record.

A handoff without evidence is rejected without review.
This rule exists because the entire point of the project is that verification is cheap; a worker that skips it is not saving time, it is moving the cost onto the orchestrator.

### Headless run requirement

Every run is headless and Wayland-safe:

```bash
env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 xvfb-run -a <command>
```

Without unsetting the Wayland variables, `xvfb-run` renders on the real compositor and the run is not headless, which silently invalidates any comparison.

## Task board

States: `BLOCKED` waiting on a dependency, `READY` dispatchable now, `CLAIMED` an agent is working, `REVIEW` handed off and awaiting orchestrator review, `MERGED` on `oracle/integration`, `REJECTED` sent back with notes.

| ID | Task | Group | State | Agent | Depends on | Notes |
|---|---|---|---|---|---|---|
| T01 | Capture strategy spike and ADR | FOUNDATION | MERGED | opencode + coordinator | none | copyarea chosen, print disqualified |
| T02 | Target selection and build recipe | FOUNDATION | MERGED | opencode bqd2a78sf | none | Verified by coordinator, see integration log |
| T03 | Harness skeleton and SPI freeze | FOUNDATION | MERGED | opencode (7 attempts) | T02 | SPI frozen in SPI.md, selftest 8/8 verified by coordinator |
| T04 | Capture runtime | CORE | READY | | T01, T03 | Implements copyarea primary, xgrab fallback; must fix xgrab crop origin at zoom>100 |
| T05 | Environment control | CORE | READY | | T03 | |
| T06 | Diff engine | CORE | READY | | T03 | |
| T07 | Defect classification | CORE | BLOCKED | | T06 | |
| T08 | Result model and JSON schema | CORE | READY | | T03 | |
| T09 | Backend adapter: native | BACKENDS | READY | | T03 | |
| T10 | Backend adapter: prototype-skija | BACKENDS | READY | | T02, T03 | T02 proved it builds and activates |
| T11 | Backend adapter: SWT.SKIA canvas | BACKENDS | READY | | T02, T03 | |
| T12 | Determinism lint | QUALITY | BLOCKED | | T04 | |
| T13 | Catalog: buttons and labels | CATALOG | READY | | T03 | |
| T14 | Catalog: text entry | CATALOG | READY | | T03 | |
| T15 | Catalog: item widgets | CATALOG | READY | | T03 | XL, split on claim |
| T16 | Catalog: containers | CATALOG | READY | | T03 | |
| T17 | Catalog: range widgets | CATALOG | READY | | T03 | Freeze animation |
| T18 | Catalog: state coverage generator | CATALOG | BLOCKED | | T13 | |
| T19 | HTML report | OUTPUT | BLOCKED | | T08 | |
| T20 | Agent CLI | OUTPUT | BLOCKED | | T04, T08 | |
| T21 | Linux CI | CI | BLOCKED | | T04, T09 | |
| T22 | Windows CI | CI | BLOCKED | | T21 | |
| T23 | Gate: one family end to end | GATE | BLOCKED | | T04, T06, T09, T13 | Expect SPI gaps here |
| T24 | Gate: full catalog run | GATE | BLOCKED | | T23, all CATALOG | |
| T25 | Agent loop documentation | OUTPUT | BLOCKED | | T20, T24 | |

## Dispatch template

The orchestrator fills this in and hands it to an opencode agent as its entire brief.
A worker receives no other context, so the brief must stand alone.

```
TASK: <ID> <title>
BRANCH: oracle/<ID>, created from oracle/integration
WORKTREE: ../oracle-<ID>

READ FIRST:
  docs/visual-oracle/PLAN.md, sections "Architecture" and "SPI sketch"
  docs/visual-oracle/TRACKING.md, sections "Work protocol" and "Quality bar"

SCOPE:
  <exactly what to build, in two or three sentences>

SCRATCH:
  write all build output, clones and downloaded jars under /tmp/opencode/<ID>/
  any other path outside the worktree is auto-rejected by the permission layer,
  non-interactively and without an obvious error, and your run will stall

DO NOT:
  edit any file under <spi package>
  edit any file owned by another task
  merge, rebase onto, or push to oracle/integration
  add dependencies without asking

ACCEPTANCE:
  <verifiable conditions, each one a command that exits zero>

VERIFY WITH:
  env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 xvfb-run -a <command>

WHEN DONE:
  append a handoff record to docs/visual-oracle/TRACKING.md using the template
  in "Handoff records", commit it with your functional commit, and stop
```

## Quality bar

Work is rejected and sent back when any of these is true.

The acceptance commands were not run, or their output was not pasted into the handoff record.
Files outside the task's scope were modified.
An SPI file was modified without an approved change request.
A test was weakened or disabled to make a run pass.
A specimen was added that is not deterministic under the T12 lint.
The commit history has more than one functional commit.

Rejection is cheap and carries no judgment; it is a routing decision, not a verdict on the work.

## SPI change requests

A worker that hits an SPI limitation stops and appends an entry here rather than editing the interface.

### SCR-1: capture runtime samples GTK theme state transitions mid-flight [RESOLVED]
Task: T13
Interface: `impl/CaptureRuntime.java` (settling); no frozen SPI file touched
Problem: GTK3 themes render widget state changes with a CSS transition; Adwaita declares `transition: all` (~200 ms) on the `button` node, so a push button whose state differs from the default at map time was captured mid-transition. The skeleton runtime's fixed 150 ms settle sampled the animation; measured 2-4 distinct frames per process below 250 ms settle, byte-identical across processes only from roughly 400 ms. This cost the catalog three specimens: `button.push.disabled`, `button.check.selected`, `button.radio.selected`. After the T04 merge the same failure resurfaced for `button.toggle.selected`, because T04's event-driven drain cannot see frame-clock redraws: the queue goes quiet while the pixels are still interpolating.
Resolution: fixed in `impl/CaptureRuntime` (change authorized by the orchestrator's T13 follow-up brief after T04's agent finished): a capture is now returned only once one rendering persists byte-identical across a pumped 250 ms observation window, regrabbing every frame period and resetting on any change, bounded at 60 grabs / 5 s with a loud failure if a rendering never settles. Stability is proven by sustained observation, not timed by a fixed settle delay; quiet widgets pay only the observation window. All three dropped specimens are restored and pass the triple-render check, measurably distinct from their defaults (ImageMagick AE against the base variant: push disabled 5240, check selected 256, radio selected 212).
State: CLOSED, superseded by the settling fix in oracle/T13; the T05 animation-disabling recipe stays unnecessary so far

Template:

```
### SCR-<n>: <one line summary>
Task: <ID>
Interface: <file and method>
Problem: <what cannot be expressed today, concretely>
Proposed: <smallest change that unblocks>
Impact: <which other tasks touch this interface>
State: OPEN | APPROVED | REJECTED
```

The orchestrator implements approved changes on `oracle/integration` itself and then rebases the affected branches.
Workers never coordinate SPI changes among themselves, because two agents editing the same interface in parallel is the failure mode this whole protocol exists to prevent.

## Handoff records

Appended by the worker as the last action before stopping.
Newest at the bottom.

Template:

```
### <ID> handoff, <date>
Branch: oracle/<ID> at <short sha>
Scope delivered: <what exists now that did not before>
Out of scope, deliberately: <what was left, and why>
Verified with:
  <command>
  <pasted output, trimmed to the verdict lines>
Known gaps: <anything the orchestrator must know before merging>
Open questions: <or "none">
```

## Integration log

Appended by the orchestrator on every merge into `oracle/integration`.

```
### <date> merged <ID>
Conflicts: <none, or how resolved>
Board updates: <state changes made as a result>
Newly unblocked: <task IDs moved to READY>
```

## Orchestrator loop

Run this loop once per working session.

0. After every merge, update `PLAN.md` so it states decisions rather than open questions.
   A worker dispatched next week reads `PLAN.md` as its specification and cannot ask what changed, so a stale design document sends it down a path the project has already abandoned.
   Replace superseded decisions in place, name the ADR that settled them, and record any defect the merged task left open together with the task that now owns it.
1. Read the board and the handoff records added since the last session.
2. Review every task in `REVIEW`: read the diff, run the acceptance commands yourself, and either merge or reject with specific notes.
3. Update the board states, and move every task whose dependencies are now `MERGED` to `READY`.
4. Check the SCR list and resolve anything `OPEN` before dispatching new work, because unresolved interface questions block parallel agents silently.
5. Dispatch: fill the template for each `READY` task up to the number of agents you can review in one session.
6. Do not dispatch more work than you can review.
   A queue of unreviewed branches is worse than an idle agent, because branches drift from `oracle/integration` while they wait.
7. Append an integration log entry for each merge.

### Dispatch capacity guidance

Phase 2 is the only phase with meaningful parallelism and it comfortably supports five to eight concurrent workers, because CORE, BACKENDS and CATALOG touch disjoint files.

Phases 0, 1 and 3 are effectively serial.
Adding agents there produces conflicts and rework, not throughput.
The temptation to parallelize the SPI freeze in Phase 1 should be resisted; it is the one task where a second opinion is worth more than a second worker.

## Current status

Phases 0 and 1 are complete: T01, T02 and T03 are merged, the SPI is frozen in `SPI.md`, and `tools/oracle/oracle selftest` passes 8 of 8 checks from a clean checkout.

Phase 2 is open. CORE (T04, T05, T06, T08), BACKENDS (T09, T10, T11) and CATALOG (T13 to T17) are all `READY` and touch disjoint files.

Third operational finding, from T03. The configured opencode model is a free alpha endpoint that drops streams unpredictably: T03 took 7 attempts and 5 drops to finish, T01 lost 6 sessions to the same cause, T02 was untouched.
The user chose to keep that endpoint, so every dispatch now runs under a supervising driver that resumes the same session with `--continue` after each drop and stops only when HEAD has moved and the tree is clean.
Drops occur at stream start and cost little when resumed, so combined with commit-early briefs the throughput cost is tolerable, but it roughly doubles wall-clock per task.

Two operational findings from the first dispatches, both now folded into the rules above.

Scratch paths outside the worktree are auto-rejected by opencode's permission layer in non-interactive runs.
The agent then exits with code zero having done nothing, so a zero exit proves nothing and only the worktree diff does.
`/tmp/opencode/` is permitted, arbitrary `/tmp/` paths are not.

Concurrent opencode sessions appear to be unreliable: T01 took four provider stream errors at stream start while T02 ran, and was dispatched successfully only once T02 had finished.
Until this is understood, dispatch one agent at a time, which costs nothing in Phases 0, 1 and 3 and will need revisiting before the wide parallel Phase 2.

### T02 handoff, 2026-08-23
Branch: oracle/T02 at ac7283c8f1 (parent of this commit; this record ships inside the single T02 commit)
Scope delivered:
`tools/oracle/build.sh`: builds each backend with plain javac in seconds, idempotent via source fingerprints, classpath only on stdout, all diagnostics on stderr.
Backends: `native` (this worktree, GTK Linux x86_64 fragment), `skia-canvas` (same host bundle plus the `org.eclipse.swt.skia` fragment from PR 3231, pinned skija-shared/skija-linux-x64 0.143.17 + types 0.2.0 from Maven Central, checksum-pinned, cached outside the worktree), `skija-proto` (clone of swt-initiative31/prototype-skija@master into the cache; the fork declares its Skija dependency as committed jars 0.116.3 in `binaries/.../lib`, referenced by `jars.extra.classpath`, so no download is needed and plain javac works for it unchanged).
Non-Java resources are copied per source folder into each class output directory; this is what makes both the GTK theming CSS and the ServiceLoader registration (`META-INF/services/org.eclipse.swt.internal.canvasext.IExternalCanvasFactory`) work.
`tools/oracle/verify-backend.sh <id>` runs a probe program headless under Xvfb and asserts genuine backend activation: paint events plus natives loaded for native; the `External canvas activated.` log line via `-Dorg.eclipse.swt.external.canvas:logActivation=true` for skia-canvas; `Drawing.createGraphicsContext` returning a real `SkijaGC` (raster surface created) for skija-proto. It detects and rejects the silent fallback-to-native case.
`docs/visual-oracle/adr/ADR-002-build-target.md`: sources, pins, refresh policy, unavailability behavior.
Out of scope, deliberately: capture strategy (T01), harness/SPI (T03), Windows/macOS builds of any backend, pinning the moving fork `master` to a fixed SHA (refresh is explicit via `ORACLE_REFRESH=1`; resolved SHA printed on every build).
Verified with:

```
$ tools/oracle/build.sh native
/home/vogella/.cache/swt-visual-oracle/build/50392c04a96b/native/classes
(cold build 16.2 s)

$ time tools/oracle/build.sh native   # second consecutive run
0.27 s total, stdout as above

$ tools/oracle/build.sh --all         # exit 0, three lines
native /home/vogella/.cache/swt-visual-oracle/build/50392c04a96b/native/classes
skia-canvas /home/vogella/.cache/swt-visual-oracle/build/50392c04a96b/skia-canvas/classes-main:...
skija-proto /home/vogella/.cache/swt-visual-oracle/build/50392c04a96b/skija-proto/classes:...

$ tools/oracle/verify-backend.sh native
NATIVE-ACTIVE=true
VERIFY-OK: backend 'native' is genuinely active

$ tools/oracle/verify-backend.sh skia-canvas
External canvas activated.
PAINTS=1
VERIFY-OK: backend 'skia-canvas' is genuinely active

$ tools/oracle/verify-backend.sh skija-proto
PROBE-GC=org.eclipse.swt.graphics.SkijaGC
SKIJA-PROTO-ACTIVE=true
VERIFY-OK: backend 'skija-proto' is genuinely active
```

All verify runs used `env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1024x768x24"` internally.
Known gaps: builds and runs must use the same JDK (21+, developed against Temurin 25); `--enable-native-access=ALL-UNNAMED` is passed by probes because SWT loads natives via `System.loadLibrary`. All three backends currently require Linux x86_64 because they run prebuilt GTK binaries; other machines get a clean non-zero exit with a reason. A missing or corrupt cache jar, an unresolved git LFS pointer, or a failed clone/download each fail cleanly with a specific message.
The verifier caught one real silent-fallback during development (service file copied under `resources/` instead of classpath root) and rejected it, which is exactly its job.
Open questions: none.


## Integration log

### 2026-08-23 merged T02
Conflicts: none, fast-forward.
Coordinator verification, run independently of the agent's own claims:
- cold `build.sh native` after deleting the build cache: 35.3s, target was 60s
- warm rebuild: 0.685s, target was 2s, classpath byte-identical
- `build.sh --all`: 52.4s, all three backends, stdout carries only classpaths
- `verify-backend.sh` for `native`, `skia-canvas` and `skija-proto`: all three report genuine activation
- incremental correctness spot check: appending a comment to `Point.java` triggered a recompile, so the fingerprint is not stale-safe by accident
Board updates: T02 to MERGED, T03 to READY.
Newly unblocked: T03.
Not verified, deliberately: the negative case for `verify-backend.sh`, that it fails when a backend silently falls back to native. Worth a follow-up, because a false "active" verdict would make the whole harness compare native against native and report perfect agreement.

### T01 handoff, 2026-08-23
Branch: oracle/T01
Scope delivered: capture strategy decided and recorded in ADR-001, with a reproducible spike under `tools/oracle-spike/` producing 15 measurements and 14 evidence PNGs.
Ownership note: dispatched to an opencode agent, which built the spike harness (`CaptureSpike.java`, `run-spike.sh`) and established the decisive GL finding, then lost its session to repeated provider stream errors before committing. The coordinator took the task over, fixed three defects in the spike script, ran it to green, and wrote the ADR.
Defects fixed during takeover:
  - `scenario`/`det_run` passed `--skia` as a JVM option, so the Skia scenario never started
  - determinism runs passed an absolute path as the filename tag, producing a doubled path
  - the evidence copy list expected Skia canvas images from the native scenarios, which do not create one
Out of scope, deliberately: Win32 measurement, no Windows machine available; the `xgrab` crop origin bug at zoom 200 is documented for T04 rather than fixed here.
Verified with:
  bash tools/oracle-spike/run-spike.sh
  SPIKE-OK: 15 RESULT lines, 14 evidence PNGs in docs/visual-oracle/adr/evidence/
  print glMilli=0, copyarea glMilli=580, xgrab glMilli=580 on the SWT.SKIA canvas
  copyarea 0.52ms, print 0.97ms, xgrab 154.57ms per capture at zoom 100
  DET-CROSS print/copyarea/xgrab all IDENTICAL over 5 processes
Known gaps: `xgrab` crop origin is offset by 24px at zoom 200 and must be fixed before the fallback is used at non-unit zoom.
Open questions: none.


### T03 handoff, 2026-08-24
Branch: oracle/T03 at 3d09f01b59
Scope delivered:
`tools/oracle/harness/src` (plain javac, package root `org.eclipse.swt.visualoracle`, subpackages `spi`, `json`, `result`, `impl`, `tools`): the five SPI interfaces plus supporting types frozen and documented in `docs/visual-oracle/SPI.md`; result JSON schema v1 documented in `docs/visual-oracle/RESULT-SCHEMA.md` and enforced in both directions by a strict parser + strict validator (valid accepted, six mutation classes rejected); minimal reference implementations (ButtonPushSpecimen, NativeBackend, CopyAreaCapture per ADR-001, ExactDiffer with changed-pixel count); CLI `tools/oracle/oracle` with `selftest` implemented and `run`/`triage` attach points defined (exit 3, dispatch cases ready for T20); `build-harness.sh` compiling the harness against the T02 native classpath; the wrapper applies the Wayland-safe Xvfb environment itself. The T02 gap is closed: selftest proves `verify-backend.sh skia-canvas` fails when the canvas is disabled (`-Dorg.eclipse.swt.external.canvas:disabled=true` injected via `JDK_JAVA_OPTIONS`, verify-backend.sh untouched) AND still passes when enabled (positive control against an unconditionally-failing script).
Deviations from the PLAN.md SPI sketch, all documented in SPI.md: multi-process merging replaces same-process dual rendering (ADR-002: one classpath/natives per backend; SWT pins env at Display creation); Capture keeps the sketch signature but must fail with UnsupportedEnvironmentException on process/env mismatch; DiffResult gains changedPixels; result status model CAPTURED/UNSUPPORTED/FAILED added so supports() gaps and failures are data.
Out of scope, deliberately: production capture runtime incl. xgrab fallback (T04), environment control processes (T05), real diff engine and clusters (T06/T07), schema hardening beyond v1 (T08), skija-canvas/skija-proto adapters (T10/T11), run/triage implementation (T20).
Verified with:

```
$ tools/oracle/build-harness.sh && tools/oracle/oracle selftest
CHECK 0 backend-classpath: PASS
CHECK 1 specimen-created-and-captured: PASS
CHECK 2 capture-is-deterministic: PASS
CHECK 3 differ-reports-equality: PASS
CHECK 4 differ-detects-altered-image: PASS
CHECK 5 result-json-conforms-to-schema: PASS
CHECK 6 verify-backend-fails-on-disabled-canvas: PASS
CHECK 7 verify-backend-passes-on-enabled-canvas: PASS
SELFTEST-OK: 8/8 checks passed (9.6 s)
EXIT=0
```

Four consecutive full runs green (7.5-9.6 s each). All runs headless via the wrapper (Wayland vars unset, GDK_BACKEND=x11, LIBGL_ALWAYS_SOFTWARE=1, Xvfb 1600x1200x24).
Known gaps: ExactDiffer writes at most one bounding-box placeholder cluster (flagged as provisional for consumers until T06 in RESULT-SCHEMA.md); CopyAreaCapture is skeleton-grade, it owns shell+settle inline using the T01-proven paint-event wait and is meant to be replaced wholesale by T04 behind the same interface; the two verify-backend checks build skia-canvas, so a cold skija jar cache makes them need network once.
Open questions: PLAN.md's architecture paragraph still says "renders the widget twice in the same process"; SPI.md documents the multi-process reality that follows from ADR-002, but only the orchestrator can decide whether to update PLAN.md itself.

### 2026-08-24 merged T03
Conflicts: none, rebased onto the plan update commit first, then fast-forward.
Coordinator verification, run independently of the agent's claims:
- `build-harness.sh` then `oracle selftest` in a detached checkout at the commit: 8/8 checks, 12 s
- re-run after rebase in the task worktree: 8/8 checks, 7 s
- the negative test the coordinator required is present as CHECK 6, and the agent added CHECK 7 as a positive control on its own initiative, so the negative test cannot pass for the wrong reason
Design correction accepted: the agent found that the PLAN.md sketch assumed both backends render in one process, which ADR-002 and SWT's Display-time environment binding make impossible. Recorded as D6 in PLAN.md; T05 and T20 change shape as a result.
Board updates: T03 to MERGED. Phase 2 opened.
Newly unblocked: T04, T05, T06, T08, T09, T10, T11, T13, T14, T15, T16, T17.

### T04 handoff, 2026-08-24
Branch: oracle/T04, one commit on top of bbb03639e9; this record ships inside that commit
Scope delivered:
`impl/CaptureRuntime` replaces `impl/CopyAreaCapture` behind the unchanged frozen `Capture` interface. One shell per display, reused across specimens: children disposed, bounds reset, client area invalidated between specimens (cross-talk proven absent by CHECK 8: button/label/button interleaving plus fresh-shell equivalence, all byte-identical). Settling waits for the control's first paint, then forces pending damage out with `Control.update()` and drains until two consecutive quiet cycles show zero paint/resize/move activity; why that condition is sufficient is documented inline (event-driven drawing means nothing can change pixels once a full drain produces no activity; animations stay T12's concern). Failure handling: specimen creation throwables surface as `CaptureFailedException` with the specimen id and original cause, the shell stays clean and the run continues (CHECK 9).
Both ADR-001 strategies live behind one interface: `COPY_AREA` primary, `X11_GRAB` fallback. The fallback grabs the control's own X window **by id** (`import -window 0x…`), so the X server does the cropping and no coordinate arithmetic exists to get wrong; the grabbed data's true zoom basis is derived from its physical/logical width ratio and normalized through SWT's own DPI machinery (`ImageDataProvider`), which makes it byte-identical to `copyArea`. Root cause of the ADR-001 defect, established by measurement: under `-Dswt.autoScale=200` on a 96 dpi X server the widget windows are *not* scaled on screen, `toDisplay` already returns true device coordinates, and multiplying by `zoom/100` overshoots the origin by exactly the scale factor. That reproduces every spike number analytically (predicted marker intersection 1044 px, bbox 0,0..168,22, hence markerTop/markerLeft=24 and uniqColors=15).
Captures now carry device-zoom pixels (`getImageData(deviceZoom)`), not the skeleton's implicit zoom-100 downscale. `tools/CaptureProbe` captures the reference specimen in a child process and prints a CAPTURE line. Selftest grows 8 -> 13 checks (shell-reuse isolation, throwing-specimen-as-data, cross-process determinism tied to the in-process capture, strategy agreement at zoom 100 and at zoom 200). Harness scratch moved from `/tmp/opencode/oracle-t03` to `/tmp/opencode/oracle-t04`.
Out of scope, deliberately: diff engine (T06), specimen catalog (T13+), determinism lint (T12), environment processes (T05), `run`/`triage` (T20). Untouched: `build.sh`, `verify-backend.sh`, `build-harness.sh`, the `oracle` wrapper, `spi/`, and the T01 spike.
Verified with:

```
$ tools/oracle/build-harness.sh && tools/oracle/oracle selftest
CHECK 0 backend-classpath: PASS
CHECK 1 specimen-created-and-captured: PASS
CHECK 2 capture-is-deterministic: PASS
CHECK 3 differ-reports-equality: PASS
CHECK 4 differ-detects-altered-image: PASS
CHECK 5 result-json-conforms-to-schema: PASS
CHECK 6 verify-backend-fails-on-disabled-canvas: PASS
CHECK 7 verify-backend-passes-on-enabled-canvas: PASS
CHECK 8 shell-reuse-prevents-cross-talk: PASS
CHECK 9 throwing-specimen-reported-as-failure: PASS
CHECK 10 capture-deterministic-across-processes: PASS
CHECK 11 xgrab-agrees-with-copyarea-at-zoom100: PASS
CHECK 12 xgrab-agrees-with-copyarea-at-zoom200: PASS
SELFTEST-OK: 13/13 checks passed (9.3 s)
EXIT=0
```

Five consecutive green runs (9.3, 22.2, 24.5, 22.5, 9.3 s), all headless through the wrapper.
Crop-fix counterfactual (scratch probe, `-Dswt.autoScale=200`, same button): pre-fix formula computes crop origin 24,24 where the true device rect starts at 12,12; copyAreaSha=`44983467…`, prefixFormulaSha=`5c1780b7…`, `agree=false`, so CHECK 12 would have failed before the fix. At zoom 100 both coincide, `agree=true`, matching the spike's own history.
Known gaps: under genuine GDK scale factors (`GDK_SCALE=2`) the X window pixmap and the application-side cairo surface are two different renderings; measured residual 2045 of 56320 pixels differing, maxChannelDelta 138, best shift alignment no better than identity, i.e. antialiasing resampling, not offset. Byte agreement between the strategies there is impossible by construction; extents remain exact. The canonical zoom-200 environment stays `-Dswt.autoScale=200` (ADR-001, run-spike.sh); T05/T21 should pin that mechanism whenever strategies are compared across zooms. The fallback needs ImageMagick `import` and gtk/X11 and reports their absence as data (`CaptureFailedException`/`UnsupportedEnvironmentException`). Fractional zooms (150) follow the same code path but are only proven at 100 and 200. Note: SPI.md enumerates package `tools` as "SelfTest and OracleCli"; `CaptureProbe` joins them as selftest infrastructure, no frozen signature touched.
Open questions: none.

### T13 handoff, 2026-08-24
Branch: oracle/T13, one commit amended onto the merged T04 capture runtime; this record ships inside that commit
Scope delivered:
First catalog families, contributed as four `SpecimenModule` classes in `org.eclipse.swt.visualoracle.catalog` (discovered automatically, no shared registration file touched): `ButtonModule` (23 specimens: PUSH default/disabled/image/image+text/left/right/border/flat/wrap, CHECK default/selected/disabled/image/right-aligned, RADIO default/selected/disabled, TOGGLE default/selected, ARROW up/down/left/right), `LabelModule` (9: text left/center/right, image, image+text, wrap+border, disabled, separators horizontal and vertical), `CLabelModule` (6: text, image right/center, SHADOW_IN, SHADOW_OUT, truncated long text), `LinkModule` (4: markup, plain, multiline, disabled). 42 specimens total. `impl/ButtonPushSpecimen` moved into `ButtonModule.Push`, id `button.push.default` unchanged; the one SelfTest reference updated. New `tools/CatalogCheck` wired into `SelfTest` as CHECK 13 (`catalog-discovered-and-wellformed`: module presence in discovery, unique lowercase dot-separated ids, positive preferred sizes) and CHECK 14 (`catalog-triple-render-deterministic`: every discovered specimen captured three times in one process, byte-identical PNGs required at exactly `preferredSize()`). CHECK 14 iterates whatever `SpecimenCatalog.discover()` finds, so it already lints future families from T14-T17 for free.
Follow-up settling fix, in `impl/CaptureRuntime` (change authorized by the orchestrator's follow-up brief; T04's agent is finished): a capture is returned only once one rendering persists byte-identical across a 250 ms observation window during which the event loop stays live and pixels are regrabbed every frame period, resetting on any change; bounded at 60 grabs / 5 s with a loud `CaptureFailedException` if a rendering never settles. Why that condition is sufficient is documented inline where T04 documented its own: every late pixel change on GTK, theme CSS transitions included, must reach the captured buffer through frames, and no transient value survives sustained observation longer than any measured transition start latency (below 100 ms) plus animation span (around 200 ms). The first design, returning after two consecutive identical grabs, was measured insufficient and is not what ships: instrumented timelines of `button.push.disabled` showed captures returning during the quiet gap before a transition starts (identical grabs at t=17/38 ms, wrong state) and on a two-frame mid-animation plateau. Deterministic by construction instead of timed by construction; quiet widgets pay only the observation window, which is why CHECK 14 went from failing on `button.toggle.selected` to passing all 42 specimens without weakening anything, at the cost of a slower selftest (~60-82 s, was ~20 s). The three specimens dropped under SCR-1 (`button.push.disabled`, `button.check.selected`, `button.radio.selected`) are restored and pass; measured distinct from their defaults (ImageMagick AE: 5240 / 256 / 212). The X11_GRAB fallback deliberately keeps T04's single grab directly after the drain: measured on this stack, once extra frame cycles elapse the imported window content diverges from what copyArea reports (44 of 5600 corner pixels on the reference button) in a history-dependent way rather than converging, so delaying imports amplifies variance there; strategy byte-agreement stays enforced by CHECKs 11 and 12.
Out of scope, deliberately:
No focus-state specimen: GTK auto-focuses the lone child on shell open regardless of specimen intent (probed: `FOCUS-CONTROL=Button`; arrow buttons and labels never take focus), and focus is pixel-neutral under GTK's focus-visible heuristic for every style here (`focusChangesPixels=false` for all ten widget styles probed), so a focused variant would render identically to these specimens anyway.
Still dropped: `clabel.disabled`, pixel-identical to `clabel.default` (AE=0): CLabel is custom-drawn and never consults its enabled state, verified in source, so that variant can never show anything. This reason is unrelated to settling and untouched by the fix.
Known gaps: sibling-variant distinctness verified by ImageMagick AE metrics only; no human has eyeballed any image, per project rules. On this Xvfb's icon theme, ARROW LEFT and RIGHT resolve to identical glyphs (AE=0 between them; vertical pairs differ ~53-65 px); the two styles stay covered since they are distinct SWT styles and differ on real desktop themes, but cross-backend diffs of those two will be trivially EQUAL in this environment. Specimen images are drawn programmatically (system colors, fixed geometry) and disposed via DisposeListener; no file assets. CatalogCheck hardcodes its four module classes in FAMILY_MODULES; a T14-T17 agent adds theirs by one list entry (or the orchestrator generalizes to auto-discovery later). The X11 fallback's determinism rests on T04's drain timing alone, not on the stability observation; if a future specimen class needs the fallback against animated popups, that combination is unproven territory.
Verified with:

```
$ tools/oracle/build-harness.sh && tools/oracle/oracle selftest
CHECK 0 backend-classpath: PASS
CHECK 1 specimen-created-and-captured: PASS
CHECK 2 capture-is-deterministic: PASS
CHECK 3 differ-reports-equality: PASS
CHECK 4 differ-detects-altered-image: PASS
CHECK 5 result-json-conforms-to-schema: PASS
CHECK 6 verify-backend-fails-on-disabled-canvas: PASS
CHECK 7 verify-backend-passes-on-enabled-canvas: PASS
CHECK 8 shell-reuse-prevents-cross-talk: PASS
CHECK 9 throwing-specimen-reported-as-failure: PASS
CHECK 10 capture-deterministic-across-processes: PASS
CHECK 11 xgrab-agrees-with-copyarea-at-zoom100: PASS
CHECK 12 xgrab-agrees-with-copyarea-at-zoom200: PASS
CHECK 13 catalog-discovered-and-wellformed: PASS
CHECK 14 catalog-triple-render-deterministic: PASS
SELFTEST-OK: 15/15 checks passed (81.9 s)
EXIT=0
```

Three consecutive green runs (81.9, 76.0, 60.4 s), each line of CHECK 14 printing `deterministic (<w>x<h>)` for all 42 specimens, restored ones included. All runs headless via the wrapper (Wayland vars unset, GDK_BACKEND=x11, LIBGL_ALWAYS_SOFTWARE=1, Xvfb 1600x1200x24).
Targeted flake hunt on the restored troublemaker: 40 rounds of triple-rendering `button.push.disabled` through the real runtime, first settling design 6 rounds mismatched, shipped persistence-based design 0.
Open questions: none.
