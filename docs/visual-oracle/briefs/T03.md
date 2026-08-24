# T03: Harness skeleton and SPI freeze

You are working on the SWT Visual Oracle, a harness that renders each SWT widget twice, once through the native backend and once through a Skija backend, and diffs the two images so that renderer work can be verified by machine instead of by a person looking at screenshots.

Read these before you start, they are the specification and this brief does not restate them:

- `docs/visual-oracle/PLAN.md`, in particular "Architecture", "SPI sketch" and the T03 row of the task table
- `docs/visual-oracle/TRACKING.md`, sections "Work protocol" and "Quality bar"
- `docs/visual-oracle/adr/ADR-001-capture-strategy.md`, which decided how capture works
- `docs/visual-oracle/adr/ADR-002-build-target.md`, which decided how the backends are built

Two tasks are already merged and you build on them, you do not redo them.
`tools/oracle/build.sh <backend>` prints a ready classpath for `native`, `skia-canvas` and `skija-proto`.
`tools/oracle/verify-backend.sh <backend>` asserts a backend is genuinely active.

## Why this task is different

Every later task depends on the interfaces you define here, and they are worked in parallel by separate agents that cannot negotiate with you or with each other.
An interface that is wrong or missing costs a coordinated rewrite across a dozen branches.
Spend your effort on getting the shapes right, not on implementing behind them.

## Scope

Create the harness skeleton: module layout, the SPI, the result model, and a CLI whose `selftest` proves the whole pipeline runs end to end.

1. **Source layout.** `tools/oracle/harness/src/...`, plain Java compiled with `javac`, no Maven, no Tycho, matching how the rest of this project builds. Pick a package root and use it consistently.

2. **The SPI.** Implement the interfaces sketched in `PLAN.md` under "SPI sketch": `Specimen`, `Backend`, `Capture`, `RenderEnv`, `Differ`, plus the result types they mention. Treat the sketch as intent, not as gospel: it was written before T01 and T02 existed, so where their findings contradict it, follow the findings and say so in your handoff. Two things the sketch does not yet account for and that you must handle: a backend cannot render every widget, and `RenderEnv` combinations mostly require a separate process because SWT reads zoom, theme and text direction once at `Display` creation.

3. **Result model and JSON.** A stable, documented serialisation. Agents parse this, so treat it as a contract: field names, enum values, and what a consumer may rely on. Include a schema conformance check in the selftest.

4. **Reference implementations, minimal on purpose.** Enough to make the pipeline run and no more: one `Specimen` (a `Button` is fine), the `native` `Backend`, a `Capture` using `GC.copyArea` as ADR-001 decided, and a `Differ` that reports exact equality plus a changed-pixel count. T04 and T06 replace the capture runtime and the diff engine with real ones. Do not build those here, and do not design the SPI around your minimal versions.

5. **CLI.** An `oracle` entry point supporting `selftest`. Full `run` and `triage` verbs belong to T20; define where they will attach, do not implement them.

6. **Build script.** `tools/oracle/build-harness.sh` that compiles the harness against a backend classpath obtained from `build.sh`. Do not modify `build.sh` or `verify-backend.sh`; they belong to a merged task. If you need a change there, follow the SPI change request process in `TRACKING.md` instead of editing them.

7. **One gap left open by T02, now in your scope.** `verify-backend.sh` was verified to say yes when a backend is active, but never to say no when it is not. A false "active" verdict would make the harness compare native against native and report perfect agreement, which is the most dangerous failure this project has. Add a negative test proving it fails when the Skia canvas is disabled. `ExternalCanvasHandler` honours the system property `org.eclipse.swt.external.canvas:disabled`. Put the test wherever it fits your layout; it must run as part of `selftest`.

## What selftest must prove

That the pipeline works end to end, unattended, exiting non-zero on any failure:

- the harness compiles against a real backend classpath
- a specimen is created, rendered on the `native` backend, and captured
- capturing the same specimen twice produces identical images, so the pipeline is deterministic
- the differ reports equality for that pair, and a non-zero changed-pixel count for a deliberately altered pair
- the result serialises to JSON and validates against the documented schema
- `verify-backend.sh skia-canvas` fails when the external canvas is disabled

## Running anything graphical

Always, without exception:

```bash
env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 LIBGL_ALWAYS_SOFTWARE=1 \
  xvfb-run -a -s "-screen 0 1600x1200x24" <command>
```

Unsetting the Wayland variables is mandatory: with them set, `xvfb-run` silently renders on the real compositor and the run is not headless.
`selftest` must apply this itself, so that a caller does not have to remember it.

## Rules that earlier tasks in this project learned the hard way

**Scratch goes in `/tmp/opencode/oracle-t03/` and nowhere else outside this worktree.** The permission layer auto-rejects other external paths non-interactively, and your run stops with no useful error. Note that the source folder lists in `build.properties` contain `../../` prefixes, so `cp --parents` with those paths escapes the worktree.

**Never read PNG or other image files into your context.** Use ImageMagick from the shell for pixel facts, for example `compare -metric AE a.png b.png null:` and `identify`, and quote only the numbers. A previous task in this project lost six sessions to provider stream errors, most likely from large image payloads.

**Commit early, amend after.** Make your first commit as soon as anything coherent exists, then amend as you go. A previous task did 49 tool calls before its first commit and lost all of it to one dropped connection.

**Do not weaken a check to make it pass.** If `selftest` fails, fix the cause. Never delete or soften an assertion, and never mark something verified that you did not run.

**Do not push and do not open a pull request.**

## Check your own work

```bash
tools/oracle/build-harness.sh && tools/oracle/oracle selftest
```

Your work is not finished until this exits zero from a clean checkout, with every item in "What selftest must prove" actually exercised.
Run it, read the output, fix the cause, run it again.

## Definition of done

- `tools/oracle/build-harness.sh` and `tools/oracle/oracle selftest` both exit zero
- the SPI is documented in `docs/visual-oracle/SPI.md`: each interface, what implementers may assume, and what is guaranteed to stay stable
- the JSON result schema is documented and enforced by the selftest
- exactly one commit, subject starting `T03: `, containing this `TASK.md`
- a handoff record appended to `docs/visual-oracle/TRACKING.md` using the template in its "Handoff records" section, with the real `selftest` output pasted into the "Verified with" block

If you cannot complete part of it, commit what is green, and say precisely what is missing and why in the handoff record.
A truthful partial result is worth more than a passing check that hides a gap.
