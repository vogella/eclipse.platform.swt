# Visual Oracle CLI

The agent-facing command line of the SWT Visual Oracle.
It exists so an agent can change a renderer, run one command, and get a
machine-readable verdict without a person looking at an image.

Entry point: `tools/oracle/oracle <verb> [args]`.
The wrapper compiles the harness, resolves the backend classpaths, and runs
graphical verbs headless under Xvfb with the Wayland variables unset and
`GDK_BACKEND=x11`, so callers never apply that incantation themselves.

Verbs:

| Verb | State | Behaviour |
|---|---|---|
| `selftest` | implemented | End-to-end pipeline proof, 57 checks, non-zero exit on any failure. |
| `run` | implemented | Render a filtered specimen set through two backends, diff, classify, verdict. |
| `triage` | implemented | Rank the differences of a previous run for attention. |
| `version` | implemented | Tool and result-schema version. |
| `help` | implemented | Usage. |

## Conventions

**Streams.**
Machine-readable output goes to stdout.
Human-readable progress goes to stderr, always; the two are never mixed into
one stream.
`--format text` switches stdout itself to a human-readable table for the verbs
that support it.

**Exit codes**, for every verb:

| Code | Meaning |
|---|---|
| 0 | Success; see per-verb semantics below for what success means. |
| 1 | Failure: differences above tolerance, failed captures, or a runtime error. |
| 2 | Usage error: unknown flag, missing value, bad number, empty selection, unreadable input. |

(The skeleton's code 3, "verb reserved", is gone now that `run` and `triage`
are delivered.)

**Result documents.**
`run` writes a schema-v1 result document (`result.json`) plus PNG evidence;
the frozen contract for that document lives in `RESULT-SCHEMA.md`.
Paths inside it are relative to the directory containing the document.

**Scratch layout.**
Default output and working directories live under `/tmp/opencode/oracle-T20/`:
`runs/` for run outputs, `children/` for child-process working directories.
Pass `--out DIR` to place a run's evidence somewhere durable instead.

## `oracle run`

```text
oracle run [--prefix ID]... [--family NAME]... [--tag TAG]...
           [--reference BACKEND] [--candidate BACKEND]
           [--dpi N] [--theme ID] [--direction LTR|RTL]
           [--font-family NAME] [--font-size N]
           [--max-channel-delta N] [--max-changed-fraction F]
           [--batch-size N] [--children N] [--child-timeout-seconds N]
           [--out DIR] [--format json|text] [--list]
```

Renders each selected specimen twice, once through the reference backend (the
oracle, normally `native`) and once through the candidate backend (the side
under test, normally `skija-proto`), then compares the two captures pixel by
pixel and reports a verdict per specimen.

Per decision D6, one JVM serves exactly one backend and one environment, so
`run` does not render in its own process at all: it launches child processes
through `impl/ChildProcessLauncher`, one per backend and batch of specimens,
merges their schema-v1 documents at the JSON level (`impl/ResultMerger`), and
compares the referenced PNG evidence with the diff engine
(`impl/ClusterDiffer`). Each child runs under its own Xvfb display.

### Selection filters

| Flag | Selects | Default |
|---|---|---|
| `--prefix ID` | specimens whose id starts with `ID`; an exact id is a valid prefix | nothing |
| `--family NAME` | the widget family (first dot-separated id segment), e.g. `button` | nothing |
| `--tag TAG` | specimens carrying the tag; one of `TEXT_HEAVY`, `ANIMATED`, `FOCUS_SENSITIVE`, `NATIVE_POPUP` | nothing |

Repeated flags of one kind union; different kinds intersect
(`--family button --tag FOCUS_SENSITIVE` = focused button specimens).
With no filter flags at all, the whole catalog is selected; this is the common
CI case and is intentionally not behind a guard.

An empty result is a usage error (exit 2), because a typo'd family name would
otherwise look like a green run over zero specimens.

The catalog currently carries 149 specimens over 19 families; `--list` prints
what a selection would run without capturing anything.

State-based selection (`--state hover`, as sketched in `PLAN.md` decision D5)
does not exist yet because states live inside specimen ids and the state
coverage generator is task T18; until then select states with `--prefix`, e.g.
`--prefix button.push.disabled`.

### Environment, backends, tolerance

| Flag | Meaning | Default |
|---|---|---|
| `--dpi N` | zoom percentage of the rendering environment | 100 |
| `--theme ID` | GTK theme id; empty means platform default | platform default |
| `--direction LTR\|RTL` | base text direction | LTR |
| `--font-family NAME` | base font family; empty means system font | system font |
| `--font-size N` | points if positive, pixels if negative (FontData convention) | -1 |
| `--reference BACKEND` | oracle side | `native` |
| `--candidate BACKEND` | side under test | `skija-proto` |
| `--max-channel-delta N` | per-channel difference below which pixels agree | 8 |
| `--max-changed-fraction F` | changed share separating WITHIN_TOLERANCE from DIFFERENT | 0.5 |

One invocation pins **one** environment; comparing across DPI scales or themes
means several invocations (CI wiring is T21).
`skia-canvas` becomes selectable when its adapter lands (T11).
Tolerance defaults come from `ClusterDiffer.DEFAULT_TOLERANCE`; verdict
semantics are defined in `RESULT-SCHEMA.md`.

### Comparing two SWT versions

`native-baseline` and `native-candidate` are stock SWT like `native`, but built from another source instead of this worktree.
`ORACLE_BASELINE` (default `master`) and `ORACLE_CANDIDATE` (required) each take a git ref of this repository or the path of an SWT checkout, whose uncommitted changes are included.
A ref is extracted once per commit into the build cache together with its own natives.

Both sides are stock SWT, so a correct change to non-rendering code must be bit-identical; use zero tolerance so that any changed pixel counts:

```text
ORACLE_BASELINE=origin/master ORACLE_CANDIDATE=~/git/eclipse.platform.swt \
  tools/oracle/oracle run --reference native-baseline --candidate native-candidate \
  --max-channel-delta 0 --max-changed-fraction 0
```

The harness itself is still compiled against this worktree's SWT, so a candidate that changes the internals the harness calls (`GTK`, `GDK`, `DPIUtil`, `Control.handle`) fails at activation rather than rendering.

| Flag | Meaning | Default |
|---|---|---|
| `--batch-size N` | specimens per child process | 25 |
| `--children N` | concurrent child processes | 4 |
| `--child-timeout-seconds N` | wall-clock bound per child, killed and recorded as failure data beyond it | 240 |
| `--out DIR` | output directory for `result.json` and logs | `<scratch>/runs/<timestamp>-<pid>` |

### Verdict and exit code

A specimen's outcome is one of:

* `EQUAL`, `WITHIN_TOLERANCE`, `DIFFERENT`: compared, verdict from the diff
  engine.
* `UNSUPPORTED`: the candidate backend cannot render this specimen. This is
  migration coverage data, never counted as a defect, and never blocks exit 0.
* `FAILED`: the capture attempt crashed or the child process died. Blocks exit 0.

**Exit code 0 iff no selected specimen is DIFFERENT and none FAILED.**

### stdout JSON (`--format json`, default)

Field order is stable; agents may rely on it.

```json
{
  "verb": "run",
  "tool": "oracle-harness/0.1.0",
  "selection": {
    "criteria": {"prefixes": [], "families": ["link"], "tags": []},
    "matched": 4
  },
  "referenceBackend": "native",
  "candidateBackend": "skija-proto",
  "resultDocument": "/tmp/opencode/oracle-T20/runs/run-20260824-101500-123/result.json",
  "environment": {"zoomPercent": 100, "theme": "", "direction": "LTR",
                  "fontFamily": "", "fontSize": -1},
  "tolerance": {"maxChannelDelta": 8, "maxChangedFraction": 0.5},
  "pass": false,
  "verdict": "FAIL",
  "totals": {"selected": 4, "compared": 4, "equal": 0, "withinTolerance": 0,
             "different": 4, "unsupported": 0, "failed": 0},
  "specimens": [
    {"specimen": "link.markup", "state": "DIFFERENT", "changedPixels": 10494,
     "changedFraction": 0.9938, "maxChannelDelta": 250,
     "probableDefectClass": "UNKNOWN", "clusters": 1}
  ]
}
```

* `pass` mirrors `verdict == "PASS"`; agents should branch on `pass`.
* `resultDocument` names the schema-v1 document with full comparison detail
  (cluster rectangles, image paths); parse it for evidence, not the summary.
* `specimens[]` holds one row per selected specimen, ordered by id.
  Compared rows carry `changedPixels`, `changedFraction`, `maxChannelDelta`,
  `probableDefectClass`, `clusters`.
  Uncompared rows carry `backend` and `message` instead, e.g.
  `{"specimen": "clabel.default", "state": "UNSUPPORTED",
    "backend": "skija-proto", "message": "..."}`.
* With `--list`, the report omits `resultDocument`, `environment`,
  `tolerance`, `pass` and `verdict`, sets `"dryRun": true`, and `specimens` is
  a plain array of ids.

## `oracle triage`

```text
oracle triage [--from PATH] [--top N] [--format json|text]
```

Ranks what a person or agent should look at first after a run.

**What the ranking optimises for:** fewest investigations per defect fixed.
Comparisons with verdict `DIFFERENT` are grouped by widget family and probable
defect class, because a widget whose every specimen differs in the same way is
one defect, not forty, and must not crowd out forty distinct single-specimen
defects. Groups are ordered by affected specimen count (descending), then by
how completely the group covers its family, then by severity (largest
`changedFraction` in the group), with family and class names breaking ties, so
the ranking is deterministic. Specimens inside a group are ordered by
`changedPixels`. Every group carries a generated `hypothesis` sentence saying
whether the evidence suggests one shared defect or isolated ones.

Failed captures are listed separately (they block verification but say nothing
about the renderer); unsupported specimens appear only as coverage counts.

### Flags

| Flag | Meaning | Default |
|---|---|---|
| `--from PATH` | result document to rank: a `result.json` or a directory holding one | newest run under `/tmp/opencode/oracle-T20/runs` |
| `--top N` | report only the first N groups | all |
| `--format json\|text` | stdout format | json |

### Exit codes

0 when a ranking was produced (even if it lists differences; the ranking is
the answer), 2 when `--from` points at nothing, 1 when the document exists but
is unreadable or not a valid schema-v1 result document.

### Determinism

Identical input produces byte-identical stdout: no timestamps, no volatile
ordering. The ranking is also independent of the order of rows inside the
input document.

### stdout JSON

```json
{
  "verb": "triage",
  "tool": "oracle-harness/0.1.0",
  "source": "/tmp/opencode/oracle-T20/runs/run-20260824-101500-123/result.json",
  "rankingOptimisesFor": "fewest investigations per defect fixed: ...",
  "totals": {"comparisons": 4, "equal": 0, "withinTolerance": 0, "different": 4,
             "groups": 1, "unsupportedCaptures": 0, "failedCaptures": 0},
  "groups": [
    {"rank": 1, "family": "link", "defectClass": "UNKNOWN", "affected": 4,
     "comparableInFamily": 4, "coverage": 1.0, "totalChangedPixels": 44169,
     "hypothesis": "every link comparison differs (...); one shared defect in the link
rendering path likely explains all 4 specimens",
     "specimens": [{"specimen": "link.multiline", "changedPixels": 12736,
                    "changedFraction": 0.995, "maxChannelDelta": 250}]}
  ],
  "failedCaptures": [],
  "unsupportedCaptures": []
}
```

Groups are sorted as described above; `rank` is 1-based.
With `--format text`, stdout is a small table of the same data.

## Worked example: iterating on a renderer unattended

An agent is asked to improve the `skija-proto` fork's `Link` rendering.
Baseline first, scoped to the affected family:

```text
$ tools/oracle/oracle run --family link --out /tmp/opencode/oracle-T20/agent-link
[run] selected 4 specimen(s), environment zoom=100 theme=<default> direction=LTR font=<system>
[run] launching 2 child process(es), ...
[run] children finished in 23.4 s
```

stdout (trimmed):

```json
{"pass": false, "verdict": "FAIL",
 "totals": {"selected": 4, "compared": 4, "equal": 0, "withinTolerance": 0,
            "different": 4, "unsupported": 0, "failed": 0}}
```

All four specimens differ, so the agent asks triage whether this is one defect
or four:

```text
$ tools/oracle/oracle triage --from /tmp/opencode/oracle-T20/agent-link --format text
#1 link/UNKNOWN: 4 of 4 specimens differ (whole family)
     link.multiline   12736 px 99.50%
     link.markup      10494 px 99.38%
     link.plain       10474 px 99.19%
     link.disabled    10465 px 99.10%
```

One family-wide defect: essentially every pixel differs (measured independently
with ImageMagick, AE 10554-10560 of 10560 per specimen), native paints a
near-white `#FAFAFA` surface with black text and red `#BA2501` markup, the fork
paints flat `#E3E3E3` almost everywhere with blue `#005299` content.
That is a background/theme integration defect in the fork's Link renderer, not
four unrelated drawing bugs.

The agent fixes the fork's Link painting, saves the change, and re-runs the
same command (the wrapper rebuilds the fork's classes because their fingerprint
changed). Iteration continues until `pass` is `true` and `totals.different`
is 0; each cycle is one command and a JSON read, with no screenshots.

## Known limitations

* One environment per invocation; matrix runs over DPI/theme arrive with T21.
* The first specimen captured by a fresh child process can catch GTK's lazy
  first-open styling (a pre-existing capture-runtime finding recorded by T14
  and T16); treat a spurious DIFFERENT on the very first specimen of a batch
  with suspicion and re-run before investigating.
* The two quarantined scrollbar specimens remain part of runs; the quarantine
  governs determinism judging only, and their diff numbers can legitimately
  alternate between two faithful renderings.
* `run` needs the same JDK everywhere and Linux GTK today, matching the
  backends' own constraints (ADR-002).
