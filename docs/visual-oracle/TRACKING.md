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

### Never use `git stash` in a task worktree

The stash stack is repository-wide, not worktree-local, and this repository has several worktrees with live agents plus the user's own checkout and their existing stashes.
A crash between push and pop strands work on a shared stack, and a concurrent `pop` can take an entry belonging to someone else.
To test whether a failure is pre-existing, use a scratch worktree at the base commit instead: `git worktree add --detach /tmp/opencode/<task>-base <sha>`.

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
| T04 | Capture runtime | CORE | MERGED | opencode (1 attempt) | T01, T03 | Implements copyarea primary, xgrab fallback; must fix xgrab crop origin at zoom>100 |
| T05 | Environment control | CORE | READY | | T03 | |
| T06 | Diff engine | CORE | READY | | T03 | |
| T07 | Defect classification | CORE | BLOCKED | | T06 | |
| T08 | Result model and JSON schema | CORE | READY | | T03 | |
| T09 | Backend adapter: native | BACKENDS | READY | | T03 | |
| T10 | Backend adapter: prototype-skija | BACKENDS | READY | | T02, T03 | T02 proved it builds and activates |
| T11 | Backend adapter: SWT.SKIA canvas | BACKENDS | READY | | T02, T03 | |
| T12 | Determinism lint | QUALITY | BLOCKED | | T04 | |
| T13 | Catalog: buttons and labels | CATALOG | MERGED | opencode (1 attempt + follow-up) | T03 | |
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

### 2026-08-24 merged T04 and T13
Conflicts: `SelfTest.java` and two appended handoff records, both resolved by the coordinator; `CaptureProbe` additionally retargeted from the moved `ButtonPushSpecimen` class to a catalog lookup of `button.push.default`.
Coordinator verification, run independently of the agents' claims:
- T04 alone: 13/13 checks, 8.1 s
- T13 rebased onto T04: initially RED, `button.toggle.selected` non-deterministic under the new settling
- after the follow-up: 15/15 checks, 50.1 s, 42 specimens each proven deterministic
Integration finding worth keeping: both tasks were correct alone and broken together. T13 measured determinism against the skeleton's fixed 150 ms settle; T04 replaced it with an event-driven drain, and GTK theme CSS transitions animate on the frame clock, so the event queue can fall quiet while pixels are still changing. The fix is capture-until-stable, requiring one rendering to persist byte-identical across a pumped 250 ms window, bounded at 60 grabs and 5 s. The agent measured and rejected the weaker "two consecutive identical grabs" design first, which would have passed the check while still capturing mid-animation frames.
Cost: `selftest` grew from about 20 s to 60-80 s. Accepted: determinism by construction is worth more than a fast check that lies.
Board updates: T04 and T13 to MERGED.

### T05 handoff, 2026-08-24
Branch: oracle/T05, one commit; this record ships inside that commit
Scope delivered:
Environment control per decision D6. `impl/LaunchConfig` + `SwtRenderEnvs.launch(RenderEnv)` turn a requested environment into the process settings that realise it on Linux/GTK: `-Dswt.autoScale=<zoom>` (the ADR-001 canonical mechanism), `GTK_THEME` set for a named theme and removed again for the platform default so an inherited value cannot silently win, plus `oracle.env.*` properties carrying the requested environment into the child. Direction and base font are realised per control by `BasicSpecimenContext.configure` (the SPI contract for orientation); GTK's locale-derived default cannot be switched without an installed RTL locale and SWT exposes no Display-wide switch, so the process records its direction in a property and pixels prove effect. `SwtRenderEnvs.current` now measures zoom through `DPIUtil.getDeviceZoom()` instead of deriving it from `Display.getDPI()`, because on GTK the reported DPI does not follow swt.autoScale; the new child-side verification caught that first live (it refused to capture, claiming pinned-to-100 while 200 was requested, instead of silently mislabelling results). `matches()` compares semantically: a system-font request accepts whatever font the machine reports, everything else must match exactly.
`tools/CaptureChild` generalises `CaptureProbe` from one hardcoded specimen to a driven batch: one JVM = one backend x one environment, any number of specimens by catalog id, one schema-v1 `result.json` plus PNG evidence per child. After Display creation it verifies what it really got (measured device zoom, GTK_THEME) against what was requested and refuses to lie: on mismatch every specimen is recorded FAILED with both environments named and exit code 3 says so. Requested non-empty fonts are created eagerly so a bad pin fails before any capture. Exit codes: 0 ok, 2 usage/unknown specimen, 3 environment mismatch, 4 font, 5 backend unavailable, 6 some captures FAILED (document still written).
`impl/ChildProcessLauncher` spawns one child per (backend, environment): each child gets its own Xvfb display (`DISPLAY` stripped, `xvfb-run -a` picks a free number; children never share the parent's server), logs go to files (no pipe deadlocks), a per-child wall-clock timeout kills hung children via destroyForcibly, and every outcome is data: valid documents are parsed and schema-validated, crashes/hangs become synthesized FAILED entries carrying the reason and stderr tail while sibling children continue. Parallelism is bounded by a fixed pool: default 4, measured (below), overridable via `-Doracle.children.parallelism`; timeout via `-Doracle.child.timeoutSeconds`.
`impl/ResultMerger` merges child documents strictly at the JSON level (no second interchange format): requires identical environments (the frozen schema carries exactly one per document, so mixed-environment merges are refused rather than mislabelled), concatenates captures/comparisons and rebases image paths to stay relative to the merged document's directory.
Selftest grows 15 -> 24 checks: launch-config mapping, concurrency bound honored (peak exactly N), zoom proven by captured size in document AND decoded PNG (280x80 for a 140x40 specimen at zoom 200), RTL proven by pixels (label.default moves 644 px vs LTR), HighContrast theme proven by pixels (5472 of 5600 px change), wrong-environment refusal, crashed-child survivability with stderr kept, hung-child timeout at 8 s with sibling completing, and two-children merge validating against the frozen schema with resolving image paths.
Parallelism measurement (idle 8-vCPU machine, software GL, batches of identical children capturing button/label/link):
MAX=1 WALL=6.8s AVG_CHILD=1.12s | MAX=2 WALL=3.4s AVG=1.12s | MAX=4 WALL=2.4s AVG=1.22s | MAX=6 WALL=1.3s AVG=1.29s | 8 children: MAX=6 AVG=1.45s, MAX=8 AVG=1.52-1.78s FAILED=0 everywhere.
Per-child time is flat up to 4 (+0%), degrades past CPU count (+36-59% at 8). Sustainable concurrency: 4; that is the shipped default.
Out of scope, deliberately: cross-backend comparison and verdicts (T20 drives these classes from the reserved `run` verb; no new CLI verb added since SPI.md freezes the verb table), HTML report (T19), diff engine (T06), catalog extensions (T14/T15). Untouched: `build.sh`, `verify-backend.sh`, `build-harness.sh`, the `oracle` wrapper, `spi/`, `catalog/`, `CaptureRuntime`, `ExactDiffer`.
### T06 handoff, 2026-08-24
Branch: oracle/T06, one commit on top of 8f8b54954c; this record ships inside that commit
Scope delivered:
`impl/ClusterDiffer` replaces `impl/ExactDiffer` behind the unchanged frozen `Differ` interface (ExactDiffer deleted; SelfTest CHECKs 3/4 repointed, same check names). Engine: per-pixel peak channel delta, changed = delta > maxChannelDelta; cluster grouping by dilation radius 1 plus 8-connected labelling plus bounding-box-overlap merge (both steps only reunite fragments of one conceptual change: AA halo bands, sliver pairs of small shifts), bounds/pixel counts always from the undilated mask, clusters ordered by significance (changedPixels desc, tighter box first, then position) and capped at 64 with totals preserved in changedPixels. Verdict rule: EQUAL only bit-identical; DIFFERENT when the changed fraction exceeds maxChangedFraction OR any cluster is structural (>= 3 changed pixels with mean peak delta >= MEAN_DELTA_STRUCTURAL = 32); else WITHIN_TOLERANCE. DEFAULT_TOLERANCE = (8, 0.5) proposed for oracle runs. Size mismatch: DIFFERENT, fraction 1.0 over the larger area, changedPixels = larger area, one full-bounds cluster, sentinel maxChannelDelta 255, never an exception. DefectClass populated only where obvious: SHIFTED when a translation within +-2 px reproduces >= 96% of all changed pixels away from image borders while both sides carry comparable content mass (the mass condition keeps a merely-added thin shape, which self-overlaps under translation, from reading as a shift); MISSING_ELEMENT when a single cluster replaces flat near-background pixels with structure or vice versa; otherwise UNKNOWN; NONE when not DIFFERENT. Buffers reused across calls, no allocation per pixel (inline direct-palette decode, indexed palette table); instances not thread-safe like the rest of impl.
Why these thresholds (measured, scratch driver `/tmp/opencode/oracle-t06/Calibrate.java`, two consecutive processes agreeing except where noted): synthetic AA on the real button.push.default capture (edge pixels perturbed by signed magnitude m) stays WITHIN_TOLERANCE for m = 4..32 and flips DIFFERENT at 34: the cliff is exactly MEAN_DELTA_STRUCTURAL, because AA cluster means equal m while ring/square defect means sit far above. AA coverage at those magnitudes reached 822 of 5600 px = 14.7% of the widget (button) and 917 of 12800 px = 7.2% across 2 clusters (link.multiline), which is why default maxChangedFraction is 0.5, above any measured AA coverage, so the fraction trigger remains free to catch whole-canvas drift: darkening tint of delta 9 over everything reads DIFFERENT with all cluster means below 32, proving that trigger stands alone. Removed squares are detected down to 4x4 (14 changed px); a 2x2 mark (2 px) is below MIN_CLUSTER_PIXELS = 3 and deliberately reads WITHIN_TOLERANCE, visible in clusters but not flipping verdicts, so single-pixel grab noise cannot fail a run. A 1px missing ring, thin and edge-hugging like AA but high-contrast, is DIFFERENT with exactly one cluster whose bounds equal the ring (244 of 244 ring pixels changed).
Performance: 97 us per 140x40 pair through BasicCapturedImage's defensive clones (~58 MP/s), ~72 us / ~78 MP/s engine core without clone, 16.3-17.1 ms per 1 MP pair (~59-61 MP/s) in the selftest benchmark. Orders of magnitude under the 1 s/pair failure mode; CHECK 23 asserts a 20 MP/s floor as a gross-regression tripwire, not as a tight number.
Selftest grows 15 -> 24 checks (CHECK 15..23 in `tools/DiffCheck`, selftest infra like CatalogCheck): equality under default tolerance, AA envelope within/beyond, global tint, missing ring bounded, removed square bounded (exact single-cluster bounds), shift classified SHIFTED, two separated defects give two clusters each inside its own region and ordered by significance, size mismatch whole-area, throughput floor. All pairs synthesized in-process from the real CHECK 1 capture by pixel arithmetic; deterministic, no image files, and every assertion is independent of cross-process capture wobble by construction.
RESULT-SCHEMA.md: verdict-semantics and cluster sections rewritten to the implemented reality (no field, type or enum change; schemaVersion untouched); stale ComparisonEntry javadoc line fixed.
Out of scope, deliberately: detailed DefectClass mapping incl. WRONG_COLOR/WRONG_GLYPH (T07; SHIFTED/MISSING_ELEMENT here only where unambiguous), specimen catalog (untouched), environment processes (T05), HTML report (T19), run/triage (T20), spi/, build.sh, verify-backend.sh, build-harness.sh, the oracle wrapper and CaptureRuntime.java all untouched.
### T15 handoff, 2026-08-24
Branch: oracle/T15, single commit on top of 8f8b54954c; this record ships inside that commit.
Scope delivered:
Item-widget catalog families, contributed as three `SpecimenModule` classes in `org.eclipse.swt.visualoracle.catalog` (discovered automatically; no shared file touched): `TableModule` (13 specimens), `TreeModule` (6), `ListModule` (6). Catalog grows 42 -> 67. Coverage per brief: table headers on/off (`table.headers.default`, `table.headers.hidden`), headers+gridlines (`table.headers.gridlines`), single column (`table.column.single`), header image (`table.header.image`), cell images (`table.cell.images`), SWT.CHECK with mixed checked rows (`table.check.rows`), cell-selection row (`table.row.selected`) and FULL_SELECTION row (`table.fullselection.selected`), unselected baseline (`headers.default`, `column.single`), the three column alignments as three comparable specimens (`table.alignment.left|center|right`, identical content and geometry), empty table with headers (`table.empty.headers`); tree collapsed vs expanded over one shared two-level hierarchy (`tree.collapsed.default`, `tree.expanded.default`), SWT.CHECK incl. a grayed node (`tree.expanded.checked`), selected node (`tree.node.selected`), item images (`tree.item.images`), empty (`tree.empty.default`); list single/multi selection each without and with selection (`list.single.default|selected`, `list.multi.default|selected`), disabled (`list.disabled.items`), overflow (`list.overflow.items`). All item text comes from fixed ASCII arrays (NATO-style words); nothing is formatted from counters or time. Column widths are fixed pixels, no `pack()`/`computeSize()` anywhere, so layouts cannot diverge between backends with different font matching. Selection/checks/expansion/images are all applied in `create` before realization, so no GTK state transition runs after map.
Caret decision per family: none of Table, Tree or List owns a caret, so there was nothing to freeze. Focus dependence is handled by explicit state in `create` plus `Tag.FOCUS_SENSITIVE` on the five selection-bearing specimens; the shell's auto-focus on its lone child (T13 finding) lands deterministically and the stability window absorbs any transition it triggers.
Out of scope, deliberately: diff engine (T06), capture runtime changes (two findings belong there, measured and reported below instead of fixed, since `impl/` is outside this task), environment control (T05), run/triage (T20), other widget families.
### T14 handoff, 2026-08-24
Branch: oracle/T14, one commit on top of 8f8b54954c; this record ships inside that commit
Scope delivered:
Two catalog families contributed as `SpecimenModule` classes in `org.eclipse.swt.visualoracle.catalog` (discovered automatically, no shared registration touched), plus one shared helper: `TextModule` (14 specimens: `text.single.empty`, `text.single.content`, `text.single.border.empty`, `text.single.border.content`, `text.single.message.empty` (placeholder), `text.single.readonly.content`, `text.single.disabled.content`, `text.single.password.content`, `text.single.search.empty`, `text.single.search.content` (ICON_CANCEL+ICON_SEARCH), `text.single.right.content`, `text.single.center.content`, `text.multi.border.content` (WRAP|V_SCROLL), `text.multi.plain.content`) and `ComboModule` (6: `combo.readonly.empty`, `combo.readonly.selected`, `combo.editable.empty`, `combo.editable.text`, `combo.editable.disabled`, `combo.narrow.long.item` (110px box, over-long item)). Catalog grows 42 -> 62. `NoCaret` clears GTK can-focus on the specimen control (and on the inner GtkEntry for editable combos) inside the factory, before realization.
Caret decision, per family. Text and Combo: no caret exists, because unfocusability closes the problem at the source; the capture shell focuses a lone focusable child on first open (T13 measured), so whether an entry showed a caret would otherwise depend on its position in the run, and a blink phase cannot reliably survive the 250 ms stability hold. Measured justification: a probe Text left normally focusable captured two different PNG hashes within one process (`6a8a9c38…` then `19c2a679…` twice), exactly the sometimes-passes hazard; with `NoCaret` every capture pins to `19c2a679…`, which equals the earlier probe's caret-hidden phase byte for byte, so zero pixel coverage is lost relative to the common unfocused rendering; only focused-with-caret pixels are excluded. All enabled caret-owning specimens carry `Tag.FOCUS_SENSITIVE`; disabled ones are insensitive and cannot take focus anyway and carry none. Spinner: family dropped entirely, see below; its caret decision would have been the opposite (focused with blink suppressed via `gtk-cursor-blink=false`).
Spinner dropped, with the measurements that justify it. A GtkSpinButton whose value or digits change before mapping keeps a stale pre-restyle frame in its own X window whenever it does not take focus, and the capture faithfully records that stale frame: with `NoCaret`, `spinner.digits.two` and `spinner.minimum` captured byte-identical to `spinner.default` (ImageMagick AE = 0) although the widget model held `0.00` / `25000` (verified via getText/getDigits before and after capture), and `spinner.maximum` rendered glyph-shaped pixels recolored (21 px of `#E1E1E1` where default has 21 px of `#505050`). Simultaneous double capture proved the divergence is in the surface, not the model: same widget, same moment, GC-on-control shows the stale text (20 dark px) while GC-on-shell shows the correct rendering (139). Focus escapes the trap (fresh-shell first-open capture with auto-focus was faithful, 850 dark px), but the mapped-shell `setFocus()` path renders differently again (88 px) and made `digits.two` and `minimum` byte-identical to each other, i.e. cross-run nondeterminism plus unfaithful pixels. Neither re-issuing the direction nor a paint-time value re-nudge changed the captured surface. With the capture runtime frozen for this task and `SpecimenContext.configure` (whose font override triggers the restyle) mandatory per the SPI, no compliant specimen-side fix exists, so the whole family is dropped rather than shipping specimens that pass while showing the wrong state.
Out of scope, deliberately: StyledText (listed for T14 in PLAN.md's task table but absent from this brief's scope definition), Spinner coverage beyond the above (needs a runtime-side fix first), the state coverage generator (T18), run/triage (T20).
Verified with:

```
$ tools/oracle/build-harness.sh && tools/oracle/oracle selftest
CHECK 14 catalog-triple-render-deterministic: PASS
CHECK 15 launch-config-maps-environment-to-process-settings: PASS
CHECK 16 child-parallelism-bound-honored: PASS
CHECK 17 child-zoom-proven-by-captured-size: PASS
      zoom-200 child rendered 280x80 (preferred size doubled)
CHECK 18 child-mirrors-right-to-left: PASS
      RTL mirrors label.default: 644 pixels moved vs LTR
CHECK 19 child-theme-takes-effect: PASS
      HighContrast theme changes 5472 of 140x40 pixels
CHECK 20 child-refuses-wrong-environment: PASS
CHECK 21 crashed-child-recorded-run-continues: PASS
      crash recorded: 'child exited with code 2 and wrote no usable result document', stderr kept (138 chars)
CHECK 22 hung-child-times-out-run-continues: PASS
      hang killed after 8 s, recorded as failure; sibling completed normally
CHECK 23 merged-child-results-validate-against-schema: PASS
      merged 2 children into /tmp/opencode/oracle-t05/selftest-merged-.../result.json, schema-valid, images resolve
SELFTEST-OK: 24/24 checks passed (63.7 s)
EXIT=0
```

Four consecutive green full runs (69.0, 65.6, 64.6, 61.1 s) plus a fifth after the last edit (63.7 s), all headless via the wrapper. Pixel evidence cross-checked with ImageMagick from the shell only (compare -metric AE, identify); no image read into context.
Known gaps: CHECK 11 (xgrab byte-agreement at zoom 100, pre-existing T04/T13 code untouched by this task) flaked once during development and passed in all five full runs around it; its history-dependence is already documented by T13. Font pinning trusts Pango: an unknown family is silently substituted rather than detected (detection needs pango fontset introspection; suggested for T09/T21). Merged documents reference child images across subdirectories, relative to the merged document's own directory, as RESULT-SCHEMA.md prescribes; consumers must resolve paths that way. Parallelism was measured on this machine only; CI hardware should tune `-Doracle.children.parallelism`. Children validate `--backend native` only until the T10/T11 adapters exist.
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
CHECK 15 diff-equality-under-default-tolerance: PASS
CHECK 16 diff-aa-edge-noise-stays-within-tolerance: PASS
CHECK 17 diff-global-tint-is-different: PASS
CHECK 18 diff-missing-ring-is-different-and-bounded: PASS
CHECK 19 diff-removed-square-is-different-and-bounded: PASS
CHECK 20 diff-shifted-content-classified: PASS
CHECK 21 diff-two-defects-two-clusters: PASS
CHECK 22 diff-size-mismatch-whole-area: PASS
CHECK 23 diff-throughput-measured: PASS
SELFTEST-OK: 24/24 checks passed (51.5 s)
EXIT=0
```

Four green full runs this session (51.2, 51.3, 51.5 s among them); one intervening run failed the pre-existing CHECK 7 (skia-canvas positive control, the check T03 flagged as needing network once) and passed on immediate rerun with no cache change, so treated as transient infrastructure, not a harness defect. All runs headless via the wrapper (Wayland vars unset, GDK_BACKEND=x11, LIBGL_ALWAYS_SOFTWARE=1, Xvfb 1600x1200x24).
Known gaps: The spec documents contradict each other and the engine had to pick: SPI.md and Tolerance.java say fraction at or below maxChangedFraction is WITHIN_TOLERANCE, RESULT-SCHEMA.md v1 said any pixel beyond tolerance is DIFFERENT, and ExactDiffer implemented the latter ignoring the fraction entirely. Purely fraction-based separation is also provably insufficient: measured AA covers up to ~15% of a widget while a missing icon covers ~0.1%, so no single fraction separates them; hence the structural-cluster trigger. RESULT-SCHEMA.md now documents the implemented semantics; since that document is consumer-facing, the orchestrator may want to eyeball the rewording. MISSING_ELEMENT fires conservatively and stayed UNKNOWN on Adwaita faces (gradient exceeds the uniformity window; face-vs-window-background proximity is theme-dependent); verdicts and clusters are unaffected, richer mapping is T07. One capture-wobble observation for T12: early in the session two calibration processes captured button.push.default with different edge-pixel counts (822 vs 782) while ring/square/tint counts matched to the pixel; afterwards 10+ consecutive processes were byte-identical (sha 2fbe8a0f...) across Xvfb displays :97/:98/:99, and both wobble states produced identical verdict tables, so calibration conclusions stand; looks like a GTK/fontconfig warm-up effect. SPI.md still describes ExactDiffer as the current implementation and DiffResult's javadoc still says clusters may be empty "while T06 does not exist yet"; spi/ files are worker-untouchable, left for the orchestrator to refresh. Alpha channels are ignored as before (captures are opaque). ClusterDiffer.DEFAULT_TOLERANCE is the proposed default for T20's `run`.
CHECK 0 backend-classpath: PASS
...
CHECK 12 xgrab-agrees-with-copyarea-at-zoom200: PASS
CHECK 13 catalog-discovered-and-wellformed: PASS
      catalog triple-rendered 67 specimens
CHECK 14 catalog-triple-render-deterministic: PASS
SELFTEST-OK: 15/15 checks passed (73.4 s)
```

Three consecutive green runs (74.1 / 73.7 / 73.4 s) plus an earlier full green at 115.1 s; every line of CHECK 14 prints deterministic for all 67 specimens. Sibling distinctness measured with ImageMagick AE between probe captures: table.headers.default vs hidden 965 px, vs gridlines 254 px; alignment left/center 3085 px, center/right 3106 px; list single default/selected 3951 px, multi default/selected 7901 px, single/disabled 21801 px; tree collapsed/expanded 1796 px over the common 200x160 crop, expanded/node.selected 8231 px, expanded/checked 4163 px (200x200 crop). Probe tooling lives in `/tmp/opencode/oracle-t15/` (captures via `CaptureRuntime`, analyzers print numbers only).
Known gaps:
1. GTK scrollbars paint no pixels on this stack. Under Yaru/GTK3/Xvfb the vertical scrollbar of an overflowing List renders white-on-white at rest in every combination tried: default overlay scrolling, V_SCROLL + `setScrollbarsMode(SWT.NONE)` classic mode (SWT reports the bar visible while pixels stay pure white, right strip x=140..158 has exactly 2 unique colors), and after programmatic scrolling to index 20. Per the brief's drop-with-evidence rule the specimen was renamed `list.overflow.scrollbar` -> `list.overflow.items`: it keeps overflow clipping coverage deterministically, and real scrollbar pixels will join the comparison only on stacks where GTK paints them.
2. Header band quirk (runtime-level, needs an owner): with `gtk_tree_view_get_headers_visible=true`, the header paints only when the hosting shell opens after the control exists, and then in Yaru's orange selection color (#E95420, full-width band). Hosted into an already-open shell, which is what the reused-shell CaptureRuntime does for every specimen after the first, GTK collapses the header entirely and item rows shift up ~26px; a `setHeaderVisible(false)->(true)` toggle does not restore it. Consequence in this environment: `table.headers.default` vs `hidden` differ by one extra visible row (AE=965), not by a header band. Specimen-side unfixable within this task's boundaries; suggest the T04 owner or a follow-up brief investigate `CaptureRuntime.host`.
3. Cold-process variance for first captures (pre-existing, affects any family captured cold): when a Table/Tree is among the very first captures of a fresh process, renderings differ across processes. Measured: `tree.collapsed.default` produced three shas over five cold single-capture runs (clean, plus black unpainted stripes of x=188..199 and x=128..199); `table.headers.default` alternated between two shas over eight runs. After five or more prior captures in the same process, rendering stabilizes to one sha across processes (verified over twelve warm runs and four full-catalog sweep rounds with zero variance). The selftest always warms up through earlier checks and the button family first, so CHECK 14 is reliable as shipped, but a harness change that ever captures an item widget cold would flake. Same owner suggestion as gap 2.
4. `list.single.default` and `list.multi.default` are byte-identical on GTK (AE=0): SINGLE vs MULTI changes rendering only once a selection exists. Kept deliberately: they are distinct API surface and the cross-backend comparison may still separate them elsewhere.
Open questions: none.
CHECK 13 catalog-discovered-and-wellformed: PASS
CHECK 14 catalog-triple-render-deterministic: PASS
      catalog triple-rendered 62 specimens
SELFTEST-OK: 15/15 checks passed (71.5 s)
EXIT=0
```

Seven further consecutive full runs green after that (run3 71.7 s, run4 72.6 s, run5 73.2 s, run6 72.6 s, run7 74.7 s, run8 81.3 s, run9 109.6 s), each printing SELFTEST-OK with CHECK 13 and CHECK 14 PASS over all 62 specimens. Targeted stress beyond the check: every text/combo specimen captured 9 times per process in two independent processes; all 20 ids produce exactly 1 unique hash within the mapped-shell path, matching what CHECK 14 exercises. Faithfulness spot-checked numerically (never reading images into context): entry-region ink at threshold 85% is 0 px for `text.single.empty` vs 327 for `.content`, 415 for `border.empty` vs 742 for `border.content` (+327, same glyphs), 476 for `combo.readonly.empty` vs 1015 for `.selected`, 509 for `combo.editable.empty` vs 1180 for `.text`.
Known gaps:
1. Foundation flake, not introduced by T14: two of ten full runs during this task had failures outside the catalog checks, always involving `button.push.default`, the reference specimen of CHECKS 1-4 and 8-12 (run10 logged CHECKS 2, 3, 9, 10 FAIL with "two separate processes captured different PNG bytes: be37fde1aa… vs 2fbe8a0f94…"). Mechanism is the same one measured for Spinner above, one level up: the FIRST capture of a fresh process races GTK's lazy style computation, and Adwaita styles some nodes after the first paint, so the control window keeps an unstyled frame nothing ever damages afterwards. 10 sequential single-capture processes all produced the dominant hash; the failure needs load timing to lose the race. Suggested fix owner: capture runtime (T04 author) or coordinator; smallest candidate is forcing one post-map damage/expose cycle (e.g. a resize round-trip of the host child) before the stability window starts on a freshly opened shell. Ready-to-file as SCR-2 if the orchestrator agrees.
2. Consequence for filtered runs: when one of MY container-ish specimens (`combo.*`, `text.single.message/search/multi.*`) is captured as the very first capture of a fresh process, the same race applies (measured: round 0 differs from rounds 1-8 by a 2x36 px column, entry background white vs themed `#F4F4F4`). Under CHECK 14's real conditions this never occurs because buttons/clabels warm the process first, and all six full runs passed CHECK 14 including these families. Until gap 1 is fixed, `run --widget Combo`-style single-family runs (T20) should not be trusted for first-capture verdicts.
3. GTK facts worth keeping: this SWT build's GTK Spinner constructor uses `gtk_adjustment_new(0, 0, 100, 1, 10, 0)`, so default range is 0..100 (not Windows' 0..100000) and `setMinimum(v)` is silently ignored while v exceeds the current maximum; order setMaximum before setMinimum.
Open questions: whether the orchestrator wants StyledText basic coverage added to this brief's scope (PLAN.md lists it for T14, the brief does not); whether SCR-2 should be opened against `impl/CaptureRuntime` for the first-open styling race.

### T16 handoff, 2026-08-24
Branch: oracle/T16, single commit on top of f18ff94365 (merged T15); this record ships inside that commit.
Scope delivered:
Container-widget catalog families, contributed as six `SpecimenModule` classes in `org.eclipse.swt.visualoracle.catalog` (discovered automatically; no shared file touched): `TabFolderModule` (5 specimens), `ExpandBarModule` (4), `CoolBarModule` (4), `ToolBarModule` (8), `GroupModule` (7), `SashModule` (3). Catalog grows 67 -> 98. Coverage per brief: two tabs first selected (`tabfolder.two.first`), two tabs second selected (`tabfolder.two.second`), three tabs second selected (`tabfolder.three.second`, covers the three-tab count and the non-default selection together), tab images (`tabfolder.images.default`), `SWT.BOTTOM` (`tabfolder.bottom.default`); expand bar all collapsed vs first expanded vs both expanded (`expandbar.collapsed.default|expanded.first|expanded.both`) and header images with second expanded (`expandbar.images.expanded`); cool bar one item (`coolbar.one.row`), two items (`coolbar.two.items`), explicit two-row wrap via `setWrapIndices(1)` (`coolbar.wrapped.rows`) and `setLocked(true)` against identical unlocked geometry (`coolbar.locked.default`); tool bar push text (`toolbar.push.text`), images (`toolbar.push.images`), a DROP_DOWN item (`toolbar.dropdown.item`), CHECK items one selected (`toolbar.check.items`), a 20 px SEPARATOR between groups (`toolbar.separator.between`), `SWT.FLAT` with image+text+separator (`toolbar.flat.style`), `SWT.VERTICAL` (`toolbar.vertical.style`) and a disabled middle item among enabled ones (`toolbar.disabled.item`); group titled (`group.text.default`), untitled (`group.empty.default`), group-in-group (`group.nested.child`), and every border style Group accepts beyond its default: SHADOW_ETCHED_OUT/SHADOW_IN/SHADOW_OUT as same-geometry variants plus `SWT.BORDER` (`group.shadow.*`, `group.border.style`); sash both orientations and SMOOTH (`sash.horizontal.default`, `sash.vertical.default`, `sash.smooth.horizontal`). All hosted content is fixed-text `Label`s (TabFolder pages, ExpandBar item bodies, CoolBar items, Group children); all text is fixed ASCII; all geometry is fixed pixels (no `computeSize`/`pack` anywhere, so font matching cannot move layouts between backends); all state (tab selection, expansion, check, lock, wrap, enablement) is applied in `create` before realization.
Caret decision, per family: none of TabFolder, ExpandBar, CoolBar, ToolBar, Group or Sash owns a caret, so there was nothing to freeze. Hover/prelight chrome on ToolBar/TabFolder is excluded by never moving the pointer during capture, and anything still animating fails loudly in the capture runtime's stability window instead of producing a frame. Focus dependence follows the shell's deterministic auto-focus on its lone child (T13 finding); non-default tab selections and the checked tool item are tagged `Tag.FOCUS_SENSITIVE`.
Out of scope, deliberately: diff engine (T06), capture runtime changes (the cold-capture finding below belongs there, reported not fixed since `impl/` is outside this task), environment control (T05), run/triage (T20), other widget families.
Verified with:

```
$ tools/oracle/build-harness.sh && tools/oracle/oracle selftest
CHECK 13 catalog-discovered-and-wellformed: PASS
CHECK 14 catalog-triple-render-deterministic: PASS
      catalog triple-rendered 98 specimens
SELFTEST-OK: 33/33 checks passed (115.8 s)
EXIT=0
```

Three consecutive green runs (115.8 / 126.9 / 117.3 s), each printing `deterministic (<w>x<h>)` for all 98 specimens including these 31; the third ran at load average 7-14 with unrelated pytest/PDF jobs saturating cores. All runs headless via the wrapper (Wayland vars unset, GDK_BACKEND=x11, LIBGL_ALWAYS_SOFTWARE=1, Xvfb 1600x1200x24). Sibling distinctness via ImageMagick AE between probe captures: tabfolder.two.first vs .two.second 1339 px, .images vs .three.second 2780 px, .two.first vs .bottom 2822 px; coolbar.two.items vs .locked.default 88 px (locking does render differently here); toolbar.separator.between vs .disabled.item 1395 px; group.text.default vs .empty.default 3192 px. Probe tooling lives in `/tmp/opencode/oracle-t16/` (captures via CaptureRuntime, analyzers print numbers only).
Known gaps:
1. Deliberate AE=0 sets kept like T15's list single/multi: on this Yaru/GTK3 stack `group.shadow.etched.out`, `group.shadow.in`, `group.shadow.out` and `group.border.style` are pixel-identical to `group.text.default` (AE=0 each, same fixed geometry), and `sash.smooth.horizontal` is identical to `sash.horizontal.default` (SMOOTH changes drag feedback only). They stay as distinct API surface for cross-backend comparison rather than being dropped.
2. The emulated CoolBar stretches the last item of a row to fill remaining row width and recomputes from minimum widths when wrapping, so "items wider than the bar" does not wrap; `coolbar.wrapped.rows` therefore pins the wrap explicitly through `CoolBar.setWrapIndices(new int[] {1})`, public API of the emulated widget, instead of width arithmetic.
3. Pre-existing cold-capture variance, measured on this task's families too: the very first captures of a fresh process can catch a stale-damage state that later captures do not. Reproduced minimally with `RepeatProbe`: four consecutive in-process captures of `coolbar.one.row` gave shas r0=6b3bd84f..., r1=r2=r3=ba6ec19f... with AE(r0,r1)=1294 px, bbox 218x72+10+0, i.e. an unpainted black strip of stale layout damage at the right end (label edge at x=210 vs x=226), stable within each state across the 250 ms observation window. Which capture flaked depended on system load, not on specimen type (button.push.default flaked once as first capture; expandbar.collapsed.default once; coolbar.one.row also once as tenth capture under load average ~7), and standalone warm sequences were byte-stable across processes. The selftest is unaffected because CHECK 14 always runs after dozens of earlier captures; a harness change that ever captures any widget cold would flake. Same owner suggestion as T15's gaps 2/3: investigate allocation/damage sequencing in `CaptureRuntime.host`.
4. Baseline note from session start: the first two full selftest runs today failed pre-existing CHECKs 2/3/9/10 identically (same sha pair be37fde1... vs 2fbe8a0f..., the latter being T06's recorded stable sha) while standalone probes were byte-stable; a third run passed without any code change, matching T06's GTK/fontconfig warm-up observation and the load correlation above. Recorded here so the orchestrator does not mistake a cold-machine first run for a regression.
Open questions: none.
