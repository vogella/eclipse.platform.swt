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
| T01 | Capture strategy spike and ADR | FOUNDATION | READY | | none | Blocks T04, decide before writing capture code |
| T02 | Target selection and build recipe | FOUNDATION | READY | | none | Blocks everything |
| T03 | Harness skeleton and SPI freeze | FOUNDATION | BLOCKED | | T02 | Orchestrator reviews line by line |
| T04 | Capture runtime | CORE | BLOCKED | | T01, T03 | |
| T05 | Environment control | CORE | BLOCKED | | T03 | |
| T06 | Diff engine | CORE | BLOCKED | | T03 | |
| T07 | Defect classification | CORE | BLOCKED | | T06 | |
| T08 | Result model and JSON schema | CORE | BLOCKED | | T03 | |
| T09 | Backend adapter: native | BACKENDS | BLOCKED | | T03 | |
| T10 | Backend adapter: prototype-skija | BACKENDS | BLOCKED | | T02, T03 | |
| T11 | Backend adapter: SWT.SKIA canvas | BACKENDS | BLOCKED | | T02, T03 | |
| T12 | Determinism lint | QUALITY | BLOCKED | | T04 | |
| T13 | Catalog: buttons and labels | CATALOG | BLOCKED | | T03 | |
| T14 | Catalog: text entry | CATALOG | BLOCKED | | T03 | |
| T15 | Catalog: item widgets | CATALOG | BLOCKED | | T03 | XL, split on claim |
| T16 | Catalog: containers | CATALOG | BLOCKED | | T03 | |
| T17 | Catalog: range widgets | CATALOG | BLOCKED | | T03 | Freeze animation |
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

Project not started.
T01 and T02 are `READY` and can be dispatched immediately and simultaneously.
Everything else is blocked pending the SPI freeze in T03.

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

