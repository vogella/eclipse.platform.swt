# ADR-002: Build targets and build recipe for the visual oracle backends

Date: 2026-08-23
Status: accepted
Task: T02

## Context

The oracle harness renders the same widget through several backends and compares the results.
Before harness code exists, the project needs one reproducible, fast command that produces a working classpath for each backend.

Maven and Tycho need a p2 target platform and take minutes.
Plain `javac` compiles the whole GTK SWT bundle (488 source files) in about five seconds, so the recipe uses `javac` directly and reads the source folder list from each bundle's own `build.properties`.

## Decision

### Backend sources

| backend | host sources | extra sources | Skija dependency |
|---|---|---|---|
| `native` | this worktree, GTK Linux x86_64 fragment's `source..` folders from `binaries/org.eclipse.swt.gtk.linux.x86_64/build.properties` | none | none |
| `skia-canvas` | same as `native` | `bundles/org.eclipse.swt.skia/src` (PR 3231, `SWT.SKIA` canvas) | Maven Central, pinned: `skija-shared` 0.143.17, `skija-linux-x64` 0.143.17, `types` 0.2.0 |
| `skija-proto` | full clone of fork `https://github.com/swt-initiative31/prototype-skija`, branch `master`; its own `build.properties` source folders | none | jars committed inside the fork at `binaries/org.eclipse.swt.gtk.linux.x86_64/lib/`: skija-shared 0.116.3, skija-linux-x64 0.116.3, types 0.1.1 |

The prototype-skija fork declares its Skija dependency as `jars.extra.classpath = lib/types-0.1.1.jar, lib/skija-linux-x64-0.116.3.jar, lib/skija-shared-0.116.3.jar` in its `build.properties`, with the jars committed to that repository.
No download is needed for it; the same plain `javac` approach compiles all 591 of its files without errors, so it was not necessary to force a different mechanism.
Note the version split: the fork uses Skija 0.116.3 while PR 3231's fragment expects 0.143.x.
Each backend gets its own class output directory, its own classpath, and runs against its own natives, so the versions never mix.

### Pinned artifacts

The three Maven Central jars for `skia-canvas` are downloaded into the cache on first use and verified against embedded sha256 checksums:

```
6213e04a09853ff4a2a2ddc63554981bc5f57aa82cc795f4cd9167aca7edb042  skija-shared-0.143.17.jar
8cb4ad7d9952016cc90fca1831711380ac202fae6afb0f40519be4f9b456bc67  skija-linux-x64-0.143.17.jar
38d94d00770c4f261ffb50ee68d5da853c416c8fe7c57842f0e28049fc26cca8  types-0.2.0.jar
```

Jars are never vendored into this repository.

### Build outputs and cache layout

Everything lives under `${ORACLE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/swt-visual-oracle}`:

```
maven/io/github/humbleui/...      downloaded jars
checkouts/prototype-skija         git clone of the fork
build/<worktree-hash>/<backend>/  class output directories plus fingerprint stamps
verify-probes/<backend>/          compiled probe programs
```

The worktree hash keeps parallel oracle worktrees from clobbering each other's outputs.

### Idempotence

Before compiling, `build.sh` fingerprints every file below the declared source folders (relative path, size, mtime), mixed with the pinned jar checksums and, for `skija-proto`, the fork's git HEAD.
A matching fingerprint stamp makes the run a no-op that only prints the classpath (about 0.4 seconds).
Changing any source, any jar, or bumping the recipe version string inside `build.sh` invalidates it.

### Non-Java resources are part of the build

For every source folder, non-Java files are copied into the class output directory with the source folder prefix stripped, exactly what Tycho does for `bin.includes`.
This is load-bearing twice over:

* Without the CSS files from `Eclipse SWT PI/gtk/org/eclipse/swt/internal/gtk/`, `new Display()` throws `NullPointerException` in `Device.overrideThemeValues`.
* The skia fragment declares `resources/` as a source folder; copying it puts `META-INF/services/org.eclipse.swt.internal.canvasext.IExternalCanvasFactory` at the classpath root where the `ServiceLoader` finds `SkiaCanvasFactory`.
  If the service file is missing, `ExternalCanvasHandler` silently falls back to native rendering, which would make the harness compare native against native and report perfect agreement.

### Runtime requirements

Run with `-Djava.library.path=<checkout>/binaries/org.eclipse.swt.gtk.linux.x86_64` for `native` and `skia-canvas`, and against the fork's own binaries directory for `skija-proto`, because JNI library versions must match the Java sources they were generated from.
The `.so` files arrive via git LFS; `build.sh` fails cleanly if it finds an unresolved LFS pointer instead of an ELF binary.
Builds and runs must use the same JDK, 21 or newer; probe programs pass `--enable-native-access=ALL-UNNAMED` because SWT loads native libraries through `System.loadLibrary`.
Graphics runs always go through Xvfb with `WAYLAND_DISPLAY` and `XDG_SESSION_TYPE` unset, `GDK_BACKEND=x11` and `LIBGL_ALWAYS_SOFTWARE=1`; software GL is enough for the `SWT.SKIA` GLX surface.

### Checkout refresh

* `native` and `skia-canvas` build from the current worktree, so refreshing means ordinary git operations on that worktree; nothing to do.
* `skija-proto`: the cached clone is used as-is once it exists, which makes repeated builds reproducible within a machine.
  To refresh it to a new upstream `master`, either set `ORACLE_REFRESH=1` (fetch, hard reset, clean) or delete `checkouts/prototype-skija`.
  The resolved commit is printed on every build so results can be attributed.

### Unavailable backends

`build.sh <id>` exits non-zero with a specific message when a backend cannot be built: missing `javac`, wrong OS or architecture (all three backends currently require Linux x86_64 because of the prebuilt GTK binaries), unresolved git LFS pointers, network failures during the one-time jar download or fork clone, or checksum mismatches.
`build.sh --all` builds every backend that works on the machine, reports each unavailable one on stderr with its reason, and still exits non-zero if any failed, so scripts notice rather than silently comparing fewer backends.
On this machine all three backends are available.

## Consequences

T03 and the backend adapter tasks (T09, T10, T11) get a working classpath per backend in seconds and can assume each backend is genuinely active by using `tools/oracle/verify-backend.sh <id>` before any comparison run.

## Verified with

```
tools/oracle/build.sh --all                 # all three backends built, exit 0
tools/oracle/build.sh native                # cold: ~7 s, warm: ~0.4 s
tools/oracle/verify-backend.sh native       # NATIVE-ACTIVE=true
tools/oracle/verify-backend.sh skia-canvas  # "External canvas activated."
tools/oracle/verify-backend.sh skija-proto  # PROBE-GC=org.eclipse.swt.graphics.SkijaGC
```
