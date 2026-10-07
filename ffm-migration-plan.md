# Moving SWT from JNI to FFM

## Goal

Replace the generated JNI C glue of SWT with Java code that calls the native libraries through the Foreign Function and Memory API (JEP 454).
The widget code keeps calling `OS`, `GTK`, `GDK`, `C`, `Cairo` and friends with unchanged signatures.

## Why not jextract directly

SWT does not bind raw headers.
Thousands of call sites use hand curated declarations such as `static native long gtk_foo(long widget, int[] out)` together with Javadoc metadata (`cast=`, `flags=no_in`, `critical`, `dynamic`, `sentinel`).
jextract generates a different, `MemorySegment` based API per header, produces tens of thousands of unused symbols for `gtk.h` or `windows.h`, cannot parse Objective-C and its output is tied to one platform.

Instead the existing JNI generator (`bundles/org.eclipse.swt.tools/JNI Generation`) gets a second backend that reads the same declarations and emits FFM Java code.
The Java signatures stay the same, so JNI and FFM implementations can live side by side and the migration can go method by method.

## Size (measured on master)

| Platform | Native methods | Hand written C |
| --- | --- | --- |
| GTK | ~1,700 | `os_custom.c` 2,358 lines, 107 `flags=dynamic` |
| Win32 | ~1,050 | `com_custom.cpp`, `os_custom.c`, GDI+ in C++ |
| Cocoa | ~500 | mostly `objc_msgSend` variants |
| Shared | | `callback.c` 2,084 lines, ~60k lines of generated C in total |

## Phases

1. Generator backend, proven on GTK for scalar functions, JNI and FFM side by side.
2. Arrays and structs, with struct layouts validated against the C `sizeof` values.
3. Callbacks: replace `Callback.java` and `callback.c` with upcall stubs.
4. Port the hand written C (`os_custom.c`, the `SWTFixed` GObject subclass, accessibility glue) to Java.
5. Repeat for Win32 (COM vtables, GDI+) and Cocoa (`objc_msgSend`, struct returns, `objc_msgSend_stret` on x86_64).

## Design decisions for phases 1 and 2

### Source of C types

The Java declarations only carry parameter casts, never return types, and the C compiler silently converts between `jint`, `jlong` and the real C types.
FFM needs the exact C ABI types, so the generator reads them from a clang AST dump of the generated JNI C file (`os.c`, `gtk3.c`, `c.c`, `cairo.c`, `atk.c`), compiled with the same flags as the native build.
That makes the FFM descriptors match what the JNI glue does today, including sign or zero extension of `guint` and `gint` results into Java `long`.

`flags=dynamic` functions are not declared in the headers the JNI glue is compiled against.
For those the JNI glue casts the function pointer to the Java types plus the parameter casts, and the FFM backend uses exactly the same types.

### Struct layouts

Struct offsets, sizes, signedness and bit-fields come from a small C probe the generator writes, compiled with the native build flags and run once.
The generated struct helpers read and write Java struct objects to native memory with those offsets, following the same field accessors, casts and exclusions as `*_structs.c`.
The layouts are validated at runtime against the JNI `*_sizeof()` natives and by round-tripping every struct that has a JNI `memmove` through both implementations.

Layouts are generated on Linux x86_64.
The other 64-bit Linux architectures SWT supports use the same LP64 layouts for these types, but that needs confirming on real hardware before shipping.

### Generated code shape

* One `<Class>_FFM` class per natives class (`OS_FFM`, `GTK_FFM`, `GDK_FFM`, `GTK3_FFM`, `C_FFM`, `Cairo_FFM`, `ATK_FFM`) and one `Structs_FFM` per struct package, in separate source folders (`Eclipse SWT PI/common-ffm`, `Eclipse SWT PI/gtk-ffm`) that the Tycho build does not compile, so master keeps building on Java 21.
* One lazily initialized holder class per native function keeps start-up linking proportional to the functions actually used and lets the JIT constant-fold the `MethodHandle`.
* Pointers travel as `JAVA_LONG`, which is ABI identical to a pointer on every 64-bit target SWT supports and avoids wrapping every handle in a `MemorySegment`.
* Primitive arrays flagged `critical` are passed as heap segments with `Linker.Option.critical(true)`, the FFM counterpart of `GetPrimitiveArrayCritical`.
  Other arrays, strings and structs are copied into a confined arena, honouring `no_in` and `no_out`.
* Variadic functions use `Linker.Option.firstVariadicArg` with C default argument promotions, and `flags=sentinel` passes a trailing `NULL`.
* `flags=dynamic` functions resolve optionally and behave like the JNI glue (no call, result 0) when the symbol is missing.
* Symbols resolve through the SWT native library loaded by the class loader, whose dependency tree contains GTK, GDK, GLib, Pango, Cairo and ATK.
* A natives class whose JNI glue dlopens its own library searches that library first: `GLX` uses `libGL.so.1`, `WebKitGTK` the same `libwebkit2gtk-4.1`, `libwebkit2gtk-4.0` or, under GTK4 (`FFM.GTK4`), `libwebkitgtk-6.0` that `webkitgtk.h` picks (`FFMGenerator.LOOKUPS`).

### Not generated

The generator leaves these shapes to hand written code or to JNI and reports every such method with the reason:

* Macros, `static inline` functions and `flags=const` constants have no symbol to link against; `FFMMacros` and `FFMTypes` implement the GTK3 ones in Java.
* Struct by value parameters (`flags=struct`) do not occur on GTK, while Win32 has 21 and Cocoa 132, so they come with phase 5.
* `Object` parameters are JNI references, which FFM cannot pass; only natives whose C code itself uses JNI take them, such as the AWT bridge, which `FFMAwt` implements by hand.
* `flags=unicode` strings are no longer declared by any platform.
* Callbacks (`CALLBACK_*`) are replaced by `FFMCallback` as a whole.

### Running side by side

A rewrite step produces a copy of the SWT sources in which each supported `native` declaration becomes a one-line delegation to its `_FFM` counterpart, while the remaining natives stay JNI.
That build runs the regular SWT JUnit tests and can be compared with the Visual Oracle harness (`ORACLE_CANDIDATE`) against stock SWT with zero pixel tolerance.

## Status of phases 1 and 2

### Tooling

All scripts live in `bundles/org.eclipse.swt.tools/ffm` and need clang, gcc, the GTK3 development headers, a JDK 25, `xvfb-run` and an Eclipse installation (`ECLIPSE_HOME`) for JDT Core and JUnit.

* `generate-gtk.sh` dumps the clang AST of `c.c`, `os.c`, `gtk3.c`, `cairo.c`, `atk.c`, `glx.c` and `webkitgtk.c`, compiles and runs the layout probes, and writes the generated classes to `Eclipse SWT PI/gtk-ffm` and the report to `report-gtk`.
* `build-gtk.sh jni|ffm` compiles the GTK bundle with plain javac, either stock plus the FFM classes or with the supported natives delegated to FFM.
* `test-gtk.sh` runs `FFMCrossCheck` and then the SWT JUnit tests on both builds and diffs the outcomes; `SWT_NATIVES` points it at other native libraries.
* `test/.../FFMBench.java` compares the per-call cost of both implementations.
* `bench-gtk.sh <swt-gtk jar> <checkout apply-ffm.sh ran in>` runs `FFMWorkloadBench` on a product jar and on its JNI twin, alternating fresh JVMs, and prints the cold and warm medians per phase.

### Callbacks

`Callback` no longer needs `callback.c`: `FFMCallback` binds the target method to an upcall stub whose descriptor comes from the same JVM signature string that the JNI code used.
The stub never throws into native code, returns 0 while callbacks are disabled and the error result of the callback when the Java side fails, and maintains the entry count, which is what the trampolines of `callback.c` did.

Two limitations of the C implementation disappear.
It has a fixed pool of trampolines per argument count, which is what `ERROR_NO_MORE_CALLBACKS` reports when it runs out, and it supports exactly two signatures containing doubles, `(JDDJ)` and `(JIDDJ)`, because each one needs its own hand written trampoline.
Upcall stubs have neither limit.

One difference is deliberate: closing the arena of a stub in `unbind` invalidates the function pointer, while a stale pointer into `callback.c` lands in an empty slot and returns harmlessly.
A callback that is still running keeps its stub alive instead, and the remaining risk is native code holding a pointer to a disposed callback, which SWT's dispose discipline rules out.

### Porting os_custom.c

The first pieces of `os_custom.c` are Java now, so the FFM build no longer calls them.
`FFMUtf16` ports the five UTF-16 offset helpers, which are pure arithmetic over the bytes of a UTF-8 string, and `FFMConstructorProc` ports the six GObject constructor overrides, which call the constructor of the super class through a downcall handle and are installed as upcall stubs.
`FFMSwtFixed` ports the GTK3 `SwtFixed` container, the `GtkContainer` every SWT control lives in.
The type is registered from Java with `g_type_register_static`, the `GtkScrollable` interface is added, and the vtable entries of `GObjectClass`, `GtkWidgetClass` and `GtkContainerClass` are upcall stubs whose offsets come from the layout probe.
The private data of the C implementation, the child list with its geometry and the scrollable adjustments, lives in a Java map keyed by the instance.

`FFMAccessible` ports the accessibility bridge, the largest part of the file.
Its 72 ATK functions were uniform C stubs that forwarded to a static method of `AccessibleObject`, so the vtable slots are derived from the C and installed as upcall stubs built from one dispatch handle; only `initialize` and `get_extents` needed to be written out, and nine slots fall back to the implementation of the parent class when a widget has no Java `Accessible`.

`FFMRuntime` ports the last two helpers, the GDK lock functions, whose `GRecMutex` becomes a `ReentrantLock`, and the debug flag that makes GTK abort on a warning.

No native of `os_custom.c` is left on JNI: the GTK4 code is ported as well (see GTK4).
The harness build (`build-gtk.sh ffm`) therefore also drops the `Library.loadLibrary` calls of the rewritten classes and runs without any SWT native library: the whole JUnit suite produces the same outcomes with `java.library.path` pointing at a directory that does not exist.
That is what finishes the GTK3 port, because symbols resolve through the GTK libraries themselves rather than through the dependency tree of the SWT library.

`FFMMacros` implements the macros that have no symbol to link against: the `GTK_IS_*`, `GDK_IS_*` and `ATK_*_GET_IFACE` type checks through `g_type_check_instance_is_a` and `g_type_interface_peek`, the accessors for `GTypeInstance`, `GObjectClass`, `GValue`, `GList`, `GSList`, `GError` and `XAnyEvent` as reads of public struct fields whose offsets the layout probe provides, and the arithmetic of `PANGO_PIXELS` and `CAIRO_VERSION_ENCODE`.
The Java versions return 0 for a null pointer where the C macros dereference it.

The rewriter learns which natives a hand written class implements from the public static methods of its source, so adding an implementation needs no list to be updated.

`FFMTypes` supplies what the remaining macros gave: the fundamental GTypes as the compile time constants they are, the registered ones through their `get_type` function, the `sizeof` of C types without a Java struct class from the probe, the glib version variables as exported symbols, the `GDK_WINDOWING_*` checks as the presence of the matching display type, and the calls through a function pointer as downcall handles.

### OpenGL, WebKit and the AWT bridge

`GLX` and `WebKitGTK` are generated completely, 11 and 136 natives; every WebKit function is `flags=dynamic`, so a missing WebKit makes `WebKit.IsInstalled` report false through a zero version, as with JNI.
`SWT_AWT` needs JNI by definition: JAWT takes a `JNIEnv` and the AWT component as a `jobject`, and the other natives call `sun.awt.X11.XEmbeddedFrame`, which JNI reaches regardless of module encapsulation.
`FFMJni` calls the JNI function table of the running VM through FFM, from `JNI_GetCreatedJavaVMs` and `GetEnv`, and `FFMAwt` implements the six natives on top of it as `swt_awt.c` does.
Java objects cross over through a `ThreadLocal` that the JNI side reads and writes with `CallObjectMethod`, because FFM cannot pass references; the class holding it is loaded through the context class loader, since JNI `FindClass` resolves against the JDK frame of the downcall.
The VM clears a thread's local references whenever any native method returns to Java, which in interpreted code includes memory segment access and `Thread.currentThread()`, so while a reference is live the bridge only makes JNI calls through two prelinked call sites and computes with `long` values; C strings and function pointers are prepared beforehand, and arguments travel in registers through the variadic `Call*Method` functions instead of a `jvalue` array.
Built this way, `libswt-glx`, `libswt-webkit` and `libswt-awt` are no longer needed.

### Coverage

1,515 of the 1,603 natives of `C`, `OS`, `GDK`, `GTK`, `Graphene`, `GTK3`, `Cairo` and `ATK` are generated (`report-gtk/summary.txt`).
No native goes through JNI any more on GTK3 or GTK4 (see GTK4).
The 111 that the generator leaves out are the `flags=const` constants, the calls through a function pointer, the `sizeof` macros of C types without a Java struct class and the custom C of `os_custom.c`, which hand written Java implements.

### Verification

* `FFMCrossCheck` compares 35 struct sizes, 720 struct reads and 240 struct writes of random data through every JNI `memmove` of a struct, and 58 call results covering scalars, unsigned results, doubles, critical and copied arrays, aliased arrays, struct out-parameters, bit-fields, variadic calls with a sentinel, dynamic functions and symbol addresses: 0 mismatches.
* `FFMUtf16` is compared against the C implementation for every offset of six strings covering ASCII, Latin-1, three byte characters, surrogate pairs and every `max` boundary, 254 comparisons.
* The Visual Oracle harness renders all 169 specimens through stock SWT and through the FFM build and compares them at zero tolerance: every specimen is bit-identical at 100% and at 200% zoom.
  At 150% every specimen fails on both sides with `IllegalArgumentException: Argument not valid`, which is a pre-existing limitation of the harness at fractional zoom and unrelated to FFM.
* The same cross check, JUnit and Visual Oracle runs pass unchanged with the callbacks routed through upcall stubs.
* 127 SWT JUnit test classes (widgets, graphics, custom, accessibility, dnd, layout, events, program, printing; 4,150 tests) produce identical outcomes on the JNI build and on the FFM build: 4,059 passed, 78 failed and 11 aborted on both, the failures coming from the headless environment.
* The browser tests (201 passed, 7 aborted) produce the same outcomes on the JNI build and on the FFM build without `libswt-webkit`, `libswt-glx` and `libswt-awt` on the library path.
  A `GLCanvas` and an `SWT_AWT` frame, shell, handle and debug switch behave the same on both builds, also interpreted (`-Xint`), compiled (`-Xcomp`) and under `-Xcheck:jni`, and 5,000 `getAWTHandle` calls under garbage collection pressure return the same handle.

### Review findings addressed

An independent review of the whole branch found these, all fixed:

* Closing the arena of an upcall stub in `Callback.dispose` frees it even while the stub is on a stack, because the JDK keeps the arena alive for downcalls but not for upcalls in flight.
  Retired stubs are now freed only once no callback is running, which the entry count already tracks.
* A callback that threw had its exception swallowed, while the JNI glue left it pending so that it surfaced when the dispatching native call returned to Java.
  The failure is kept for the thread, nested callbacks save and restore it as `callback.c` did, and the generated bindings check after every downcall.
* `AtkObjectClass.ref_state_set` was missing, because the C function is spelled `swt_fixed_accesssible_ref_state_set` and the extraction expected the correct spelling.
  Without it no state a control reports through `AccessibleControlListener.getState` reached AT-SPI.
* `sizeAllocate` and `map` iterated the live child list while allocating children, which sends `SWT.Resize` and can add or remove children; they iterate a snapshot now, as the C cached the next node.
* Only `FFMCallback` guarded against exceptions escaping into native code; every hand written upcall does now, since an exception leaving an upcall stub terminates the VM.
* `FFMAccessible.REGISTERED` was never cleared, so a later accessible at a reused address counted as registered; the entry is dropped when the object is finalized.
* `memmove` into a struct ignored the size argument, and the generated bindings now never read beyond it.
* The clang AST reader accepted implicitly declared functions, whose guessed prototype would have truncated a returned pointer to 32 bits.
* `forall` and the parent class calls built a downcall handle per invocation; they use one handle per descriptor now.

One finding was deliberately not acted on: `AtkTableIface.remove_column_selection` forwards to `atkTable_remove_row_selection`, which faithfully reproduces a bug of the C and deserves its own fix upstream.

### Findings

* `MethodHandle.invokeExact` in the arm of an arrow switch is a value producing expression, so ECJ inferred `Object` as its return type where javac inferred `void`, and the Tycho built jar failed with a `WrongMethodTypeException` that no javac build showed.
  Such calls need a block body.

* A ported GObject type has to bring its dependants with it: `swt_fixed_accessible_new` asserts `SWT_IS_FIXED`, which only accepts the type the C code registers, so it returned NULL and SWT crashed dereferencing it.
  Building the accessible in Java the way that function does, without the assertion, avoids changing C for it.
* `scrollbar.horizontal.scrolled` and `scrollbar.both.scrolled` flip between two faithful renderings under CPU contention, which the harness documents in `DeterminismLint.CATALOG_QUARANTINE` with the same 2,749 changed pixels seen here.
  They are not a JNI versus FFM difference: the same build compared against itself flips as well, so oracle runs that matter have to happen on an idle machine.

* JNI `SetBooleanField` keeps only the lowest bit of the value, while native method results are true for any non-zero low byte, so struct fields and function results need different conversions.
* The JNI glue copies arrays back in reverse parameter order, which `Transform.multiply` relies on by passing the same `double[]` as result and operand of `cairo_matrix_multiply`.
* JNI `FindClass` inside C code resolves against the class loader of the calling Java method, which is a JDK frame during an FFM downcall.
  `os_custom.c` now caches the `AccessibleObject` class when an accessible is registered through JNI, and clears the pending exception if the lookup fails; without it the accessibility callbacks fail and the leftover exceptions crash the VM.
* The shipped `libswt-pi3-gtk` was compiled against Pango headers without the three offset fields of `PangoGlyphItem`, so its `PangoLayoutRun_sizeof()` is 16 while current headers give 32.
  Generated layouts therefore have to come from the same headers as the native build.
* Functions returning function pointers (`XSynchronize`) have a different prototype shape in the AST and were a parser trap.
* GTK releases the GDK lock from nested main loops without holding it, for example when WebKit opens a window from a signal handler.
  `g_rec_mutex_unlock` ignores that, while `ReentrantLock.unlock` throws, and an exception leaving an upcall terminates the VM; `FFMRuntime` now ignores it as well.
  The harness runs had missed it because the browser tests were not part of them; the product build suite, 4,366 tests including the browser, passes.

### Performance

Best of five runs of two million calls on Linux x86_64, JDK 25:

| Call shape | JNI | FFM |
| --- | --- | --- |
| scalar, `gtk_widget_get_visible(long)` | 13.2 ns | 13.0 ns |
| double result, `cairo_get_tolerance(long)` | 12.7 ns | 12.6 ns |
| critical `byte[]`, `memmove(byte[], long, long)` | 24.5 ns | 5.5 ns |
| two copied `int[]`, `pango_layout_get_pixel_size` | 56.6 ns | 53.8 ns |
| three copied `double[]`, `cairo_matrix_transform_point` | 76.7 ns | 88.8 ns |
| struct out-parameter, `gdk_cairo_get_clip_rectangle` | 45.4 ns | 31.0 ns |
| callback with three long arguments | 153.6 ns | 86.0 ns |

The callback row is from a later run on a busier machine, where the JNI numbers of the other rows are about twice as high as shown, so compare it only with its own JNI value.
Copied arrays pay for a confined arena per call; a reusable per-thread allocator is the obvious next optimisation.

A widget workload (a shell with 16 composites of labels, texts, checks, combos, a tree, a table and a `StyledText`, opened, relaid out 20 times, 2,000 GC operations, disposed) compares the product jar with a twin whose six rewritten PI classes are compiled from the unrewritten sources (`bench-gtk.sh`).
Median main thread CPU time of 10 alternating fresh JVMs on 4 pinned cores:

| Phase | cold JNI | cold FFM | warm JNI | warm FFM |
| --- | --- | --- | --- | --- |
| `new Display()` | 130 ms | 468 ms | | |
| create widgets | 322 ms | 935 ms | 179 ms | 221 ms |
| open | 321 ms | 465 ms | 312 ms | 315 ms |
| relayout | 426 ms | 662 ms | 333 ms | 338 ms |
| GC drawing | 50 ms | 87 ms | 38 ms | 37 ms |
| dispose | 69 ms | 94 ms | 60 ms | 66 ms |

Once warm the two are within noise, except widget creation (+23%) and dispose (+11%).
The first use costs about 1.3 s more CPU time, most of it in `java.lang.invoke` and `jdk.internal.foreign` building downcall handles and in the class initialisers of the holder classes.
Full IDE start-up to the workbench window did not show a stable difference: two batches of 10 and 15 alternating pairs gave +430 ms and -749 ms, so machine noise dominates there.
A cold FFM run loads about 3,900 classes against 1,400 for JNI, most of them `LambdaForm` classes the JDK spins while linking and first invoking the downcall handles.

Two remedies were measured on the first iteration of the workload (main thread CPU, median of 5):

* One shared handle per call shape, 202 shapes for 1,390 functions, with the function address passed as the first argument, removed only 200 of the spun classes and made no measurable difference, because the JDK already caches the downcall stub per shape.
  It was not kept.
* An AOT cache from a training run (`-XX:AOTCacheOutput`, JEP 483 and 514) cut the cold FFM cost from 2,633 ms to 1,932 ms, against 1,152 ms for JNI with a cache.
  An AOT cache is not an option for Eclipse, so the cold cost has to come down in the bindings themselves.

### Building it

The committed sources keep their `native` declarations: `bundles/org.eclipse.swt.tools/ffm/apply-ffm.sh` turns them into calls of their FFM implementation in the checkout, and a product build runs it before Maven.
`FFMRewriter.java` runs as a source file, so that step needs no compiled tooling.
The step rewrites `Eclipse SWT PI/gtk` and `Eclipse SWT PI/cairo` in place.
`C.java` and `Callback.java` sit in folders the win32 and cocoa fragments compile too, which have no FFM implementation, so the step moves them out: a rewritten copy goes to `Eclipse SWT PI/gtk-ffm-shared`, which only the GTK fragments list, and the original to `Eclipse SWT PI/jni-shared`, which only the win32 and cocoa fragments list.
The step keeps the `Library.loadLibrary` call of every class that still has a native: a product build merges pull requests on top, and a native one of them adds is not in `report-gtk` yet, so it has to keep working through JNI; the rewriter names such natives in its output.
A class left without natives, such as `C`, `GLX`, `WebKitGTK`, `SWT_AWT` and `OS`, no longer loads its library, so no SWT native library is loaded on GTK3 or GTK4.
All five GTK fragments list the FFM source folders, since they all compile the rewritten GTK sources.
Rewriting the files in the branch instead made every change to `OS.java`, `GTK.java` or `GDK.java` collide with it, which upstream does several times a week.
Because the declarations stay, the generator keeps its input and the comparison harness keeps its JNI reference.

The Speed Eclipse tooling (`eclipse-speed`) applies the branch through its `PATCHES` list, runs `apply-ffm.sh` before Maven and adds `--enable-native-access=ALL-UNNAMED` to `eclipse.ini`.

### Running in a product

A Speed Eclipse IDE built that way (Java 25, GTK 3.24.52, Wayland, two monitors at 200%) is in daily use.
Its loaded classes show 665 generated functions linked after normal use and `FFMSwtFixed` and `FFMAccessible` serving every control; the JNI libraries stay mapped, as described above; at that time `C` and `Callback` still went through them.
Its Error Log shows no entry from SWT, and the only FFM frames in logged stacks are the event loop and PNG encoding, as expected.

## Phase 5: Win32

### Size (measured on master)

| Class | Natives | Notes |
| --- | --- | --- |
| `OS` | 712 | plain C calls into `user32`, `gdi32`, `comctl32` and friends |
| `Gdip` | 188 | 136 are `flags=cpp`, `new` or `delete`: inline C++ wrapper classes without exported symbols |
| `COM` | 146 | 69 `VtblCall` overloads |
| `OsVersion` | 2 | |

About 220 struct classes (128 Win32, 92 OLE), 21 `flags=struct` parameters and 8 `flags=dynamic` functions.
The hand written code is small: `os_custom.c` has 445 lines, `com_custom.cpp` 451.

### What carries over from GTK

* The generator, reading C types from a clang AST dump of `os.c` and `com.c` and struct layouts from a compiled probe.
* `FFMCallback`: window procs, hooks and the vtables of `COMObject` already go through `Callback`, and x64 and aarch64 have a single calling convention.
* The cross check, the side by side JUnit comparison and the build time rewrite.

### What is new

* LLP64: `long` is 32 bits on Windows, and the headers bring unions, packed structs and `A`/`W` macro names.
  The AST dump and the probes have to use `clang-cl` and MSVC against the Windows SDK, for x64 and aarch64.
* Struct by value parameters (21), which GTK did not need.
* `GetLastError`: the JVM can overwrite the last error between a downcall and `OS.GetLastError()` (9 call sites).
  Functions whose error is read need `Linker.Option.captureCallState("GetLastError")`.
* `VtblCall` becomes a read of the function pointer from the vtable plus a downcall handle per descriptor.
* GDI+: the C++ wrappers are inline header code, so each of the 136 natives has to be reimplemented on the flat API that `gdiplus.dll` exports (`GdipCreateBitmapFromHBITMAP`, `GdipDrawLineI`, ...), checked pixel for pixel.
* WebView2: the C++ callback, host and options objects of `com_custom.cpp` become Java COM objects with upcall vtables, as `COMObject` does for OLE.
* `os_custom.c`: the `DllMain` instance handle, the `DPI_AWARENESS_CONTEXT_*` constants and the validated `uxtheme` dark mode ordinals 133, 135 and 140 move to Java.

### Steps

Each step runs in its own worktree so they can proceed in parallel; step 1 is the only one the others build on.

1. Generator backend for Win32 (`generate-win32`): `clang-cl` AST dump, MSVC layout probes, LLP64 types, struct by value, `captureCallState`; generate `OS_FFM` and `COM_FFM` with a coverage report.
2. Runtime: `VtblCall`, `GetLastError` capture and `FFMCallback` on Windows.
3. `os_custom.c` in Java.
4. WebView2 COM objects in Java.
5. GDI+ on the flat API.
6. Integrate, then run the cross check and the JUnit comparison against the JNI build.

The goal is to drop `swt-win32`, `swt-gdip` and `swt-osversion`, and with them the Visual Studio build.

### Status of step 1

`generate-win32.sh` (Git Bash) dumps the `clang-cl` AST and macros of `c.c`, `os.c`, `com.c` and `osversion.c` with the defines of `make_win32.mak`, compiles and runs the layout probes with `clang-cl`, and writes `C_FFM`, `OS_FFM`, `COM_FFM`, `OsVersion_FFM`, the `Structs_FFM` and `Extra_FFM` classes to `Eclipse SWT PI/win32-ffm` and the report to `report-win32`.
`ARCH=arm64` selects the other target; the probes run, so they have to be built for the host.

* Types: `long` is 32 bits when `size_t` is `unsigned long long`, and the `__attribute__((stdcall))` that `clang-cl` prints into function types is dropped.
* Names: a `.macros` dump next to the AST resolves renaming macros, `CreateWindowEx` to `CreateWindowExW` and `MoveMemory` through `RtlMoveMemory` to `memmove`.
* Symbols: each function resolves from the DLL that its import library names, read with `llvm-nm` from the Windows SDK libraries that `make_win32.mak` links, first library wins; a dynamic function from its `name_LIB` macro; the C runtime through the default lookup (`report-win32/symbols.txt`).
  `FFMLibraries` loads a DLL on first use.
* Struct by value: the layout comes from the probed fields with padding; the one primitive case, `ScriptStringOut`, reads the pointer the JNI glue dereferenced.
  A pointer that the C side takes as a pointer sized integer, the `LPARAM` of `SendMessage`, is passed as the address of the copy.
* `GetLastError`: the functions whose error SWT reads (`FFMGenerator.CAPTURE_LAST_ERROR`) capture it, and `OS_FFM.GetLastError` returns what `FFMLastError` holds for the thread.
  The list is curated from the call sites and has to follow them.
* The `sizeof` macros without a Java struct class come from the probe (`Extra_FFM`).

796 of 1,072 natives are generated (`report-win32/summary.txt`); left are the 188 of `Gdip`, the 69 `VtblCall`, 12 `no_gen` natives of `os_custom.c` and `com_custom.cpp`, `PathToPIDL`, and five macros (`IsEqualGUID`, `TreeView_GetItemRect`, `GID_ROTATE_ANGLE_FROM_ARGUMENT`, `PTR_sizeof`, `setenv`) plus one constant.

`test-win32.sh` compiles stock SWT with the FFM classes (`build-win32.sh`) and runs `FFMCrossCheckWin32` against JNI DLLs built from this checkout (`SWT_NATIVES`): all 495 symbols resolve, all 653 downcall handles link, and 138 struct sizes, 1,020 struct reads, 480 struct writes and 51 call results (struct by value with `AlphaBlend`, `WindowFromPoint`, `ChildWindowFromPointEx` and `ScriptStringOut` compared pixel for pixel, `LPARAM` structs, dynamic functions, captured last error) show no mismatch.
The only known difference is `NOTIFYICONDATA`, whose Java `sizeof` is `NOTIFYICONDATA_V2_SIZE`.
In the same run JNI returned 0 from `GetLastError` right after a failing `GetMenuItemCount`, while the capture returned 1401, the error the call set.

`FFMLibraries` loads `comctl32.dll` on first use, after `OS` activated the SWT manifest, so it resolves to version 6 (checked in the integration).

### Runtime (step 2)

* `FFM` resolves symbols on Windows through the DLLs `swt-win32` links against, each loaded when a lookup first reaches it, so that `comctl32` is loaded after `OS` activated the SWT manifest and resolves to version 6.
* `FFMCallback` needs nothing Windows specific; its entry count now follows `callback.c` and only counts callbacks that reach Java, while retired stubs wait for all stubs in flight.
* `FFMCom` implements the 69 `VtblCall` overloads and `VtblCall_put_Bounds`, with one address-less downcall handle per call shape (46).
  A null struct passed by value throws `NullPointerException`, where the JNI glue dereferences NULL.
* `FFMLastError` keeps one `captureCallState("GetLastError")` buffer per thread; the generated `OS_FFM.GetLastError` returns it.
  JNI loses the error in practice: `GetMenuItemCount` on a bad handle followed by `OS.GetLastError()` returns 0, the capturing binding 1401.
* `test-win32-runtime.sh` runs `FFMWin32RuntimeCheck`: window procs, nested and failing callbacks, disabled callbacks, hooks, timer procs, callbacks on native threads and array based COM slots against JNI `Callback`, every `VtblCall` overload against JNI on a recording object, `IShellLinkW` and `IPersistFile` through both, and the captured last error.

### Status of phase 5

All 1,048 natives of `OS`, `COM`, `Gdip` and `OsVersion`, plus `C` and `Callback`, have an FFM implementation, and the FFM build runs without `swt-win32`, `swt-gdip` and `swt-osversion`.
Only `SWT_AWT` (`swt-awt`) and `WGL` (`swt-wgl`) stay on JNI; `apply-ffm.sh win32` does not rewrite them.

* Generated (step 1): 796 natives.
* Hand written, registered through the implementation files of the rewriter, not `HANDWRITTEN`: `FFMCom` (`VtblCall`), `FFMOsCustom` (`os_custom.c`), `FFMComCustom` (`com_custom.cpp`, `PathToPIDL`), `FFMGdipGraphics` and `FFMGdipObjects` (GDI+ on the flat API, every handle a raw `Gp*` pointer; `FFMGdipObjects` names `Gdip` through `IMPLEMENTS` so its `MoveMemory` does not capture `OS.MoveMemory`), `FFMWin32Macros` (`IsEqualGUID`, `TreeView_GetItemRect`, `GID_ROTATE_ANGLE_FROM_ARGUMENT`, `NOTIFYICONDATA_V2_SIZE`, `PTR_sizeof`, `setenv`).
* `FFMResources` replaces what the FFM build loses with `swt.rc`: with `GetLibraryHandle` 0, `CreateActCtx` reads the SWT manifest from a temporary copy of a class path resource, so `comctl32` is still version 6, and `LoadImage` builds the `Text` search and cancel icons 101 to 104 from the `.ico` files with `LookupIconIdFromDirectoryEx` and `CreateIconFromResourceEx`, as a resource load does.
* `build-win32.sh jni|ffm` and `test-win32.sh` mirror the GTK scripts; `apply-ffm.sh gtk win32` switches either or both, with a rewritten `C` and `Callback` per platform in `<platform>-ffm-shared`.
  The win32 fragments list `common-ffm` and `win32-ffm`; the Tycho build of both fragments compiles them with ECJ.

Verification, Windows 11 x64, JDK 25, against JNI DLLs built from this checkout:

* `FFMCrossCheckWin32`: 3,997 checks, 0 mismatches, including the macros and the icons of `FFMResources`, pixel for pixel against `swt-win32` at seven sizes.
* `FFMWin32RuntimeCheck` 480 checks, the `os_custom.c` check, `GdipGraphicsCrossCheck` 137 checks, `FFMGdipObjectsCheck` 16,105 checks: 0 mismatches. `GdipScenes` on the JNI build and on the full FFM build: 30 images and 258 values identical. `FFMWebView2Check` passes on both builds.
* JUnit, 137 classes of `org.eclipse.swt.tests` (widgets, graphics, custom, browser, accessibility, dnd, layout, events, program, printing) and `org.eclipse.swt.tests.win32`, 4,633 tests, JNI build then FFM build without any SWT library: 4,361 against 4,357 passed, 64 aborted and 8 skipped on both.
  The 8 differences are focus and position dependent (`Browser.test_toControl*`/`test_toDisplay*` flip in both directions, `Shell.test_Issue450_NoShellActivateOnSetFocus`) or remote URLs (`Browser_IE.test_setUrl_remote*`); rerunning those classes gave identical outcomes.
  The failures on both builds come from the environment (no SVG rasterizer, off by one pixel positions).

Open:

* `GetLastError` returns what the last capturing call left; `FFMGenerator.CAPTURE_LAST_ERROR` has to follow the call sites.
* Failed GDI+ constructors return 0 where the C++ gave an object without a native one, so SWT raises `ERROR_NO_HANDLES` instead of drawing nothing.
* The callback exception slot is not per thread, which matters more for COM threads than on GTK.
* aarch64 is untested: the layouts, the `uxtheme` validators and the struct by value classification only ran on x64.
* Not measured: performance and the cold start cost on Windows; a product build with `apply-ffm.sh win32` under Tycho and an IDE session.

## GTK4

### What is generated

`generate-gtk.sh` makes a second pass over the C units the GTK4 build compiles (`os.c`, `gtk4.c`, `atk.c`) with the flags of `make_linux.mak` (`pkg-config gtk4 gtk4-x11 gtk4-unix-print`, `-DGTK4` comes from `os.h`) and runs the layout probes against the GTK4 headers.
`c.c`, `cairo.c`, `glx.c` and `webkitgtk.c` do not depend on the GTK version and are read once.
`GTK4_FFM` is generated from the GTK4 compile alone, and the GTK4 only natives of `OS`, `GDK`, `GTK` and `Graphene` are generated from it, including the new `Graphene_FFM`.
`gdk_clipboard_read_async` takes a `String[]` as a NULL terminated `char **`, which `FFM.strings` marshals like `swt_getArrayOfStringsUTF`.

| Natives class | before (FFM, JNI) | after (FFM, JNI) |
| --- | --- | --- |
| `OS` | 370, 55 | 373, 52 |
| `GDK` | 175, 35 | 206, 4 |
| `Graphene` | 0, 3 | 3, 0 |
| `GTK4` | 0, 245 | 244, 1 |
| all classes | 1,609, 147 | 1,890, 111 |

### One class for GTK3 and GTK4

`OS`, `GDK` and `GTK` serve both versions, so each native of these classes is planned from both compiles (`report-gtk/gtk4-differences.txt`).
1,015 natives are declared in both with the same descriptor, 77 only by the GTK3 headers (a GTK4 process cannot link them, as with the JNI `NO_` guards) and 37 only by the GTK4 headers.
No descriptor differs: the headers changed types such as `const` and `gchar`, and a few return types the Java declarations ignore, but no parameter or result changes its ABI class.
The generator still supports the choice: where the two plans differ, the method holds both downcall handles and branches on `FFM.GTK4`.
Eight layouts differ and are chosen at run time in `Structs_FFM` and `Extra_FFM`: `GdkRGBA` (`double` in GTK3, `float` in GTK4, 32 and 16 bytes), `GtkWidgetClass` (824 and 408 bytes, `snapshot` only in GTK4), `GtkCellRendererClass` (264 and 288 bytes, `render` against `snapshot`), the `realize`, `map` and `size_allocate` offsets of `GtkWidgetClass` that the `SwtFixed` ports read, and the sizes of `GtkCellRendererText` and its class.
The size and offset constants become `FFM.GTK4 ? gtk4 : gtk3`, which the JIT folds, and fields that exist in one version only are read and written under that version only.
Types that exist in GTK4 only, such as `GdkPaintableInterface` and the `measure` slot of `GtkWidgetClass`, are -1 under GTK3, and the GTK3 only ones (`GtkContainer*`, the pixbuf and toggle cell renderers, the `get_preferred_*` and `get_accessible` slots of `GtkWidgetClass`) are -1 under GTK4.
A struct passed by value would reject a layout that differs, and none does.

The probe also produces what the hand written ports need beyond the struct classes: the sizes of `GObject`, `GtkWidget`, `GInterfaceInfo` and `GdkPaintableInterface` and the offsets of `GtkWidgetClass.measure`, `GInterfaceInfo.interface_init` and the `GdkPaintableInterface` slots, so no layout is hard coded in Java.
`FFMGtk4CustomCheck` still compares the constants the ports use with the output of `probe-gtk4-custom.c`.

### One GTK version flag and one library lookup

`FFM.GTK4` is the only GTK version flag of the FFM code.
It is decided the way `OS.java` picks `swt-pi4` or `swt-pi3`, from `SWT_GTK4=1` read through the C `getenv`, and each version falls back to the other if its GTK library cannot be loaded.
The rewriter replaces `Library.loadLibrary("swt-pi3")` and `("swt-pi4")` in `OS` with `FFM.loadGtk(false)` and `FFM.loadGtk(true)`, which throw `UnsatisfiedLinkError` when that `libgtk` cannot be opened.
So the static initializer of `OS` keeps its own try and fallback structure and prints the same message, falls back the same way and fails with the same exception type if neither version loads, as the JNI build does.
`FFM` probes each GTK library at most once and only when asked, so the library of the version nobody selected is never mapped.
It needs no SWT native, because it only reads the environment and opens the GTK library.
`FFM.LOOKUP` lists `libgtk-4.so.1` (which contains GDK) under GTK4 and `libgtk-3.so.0` with `libgdk-3.so.0` under GTK3, followed by the version independent libraries, so a process never maps the GTK libraries of the other version.
`FFMGtkVersionCheck` reads `/proc/self/maps` to confirm that.
Every hand written port links through this lookup.
`flags=dynamic` natives behave as before: a symbol the loaded GTK does not have gives no call and the result 0, for example `gtk_accel_group_new` under GTK4.
A non dynamic native that only the other version has throws `UnsatisfiedLinkError` when called, as the JNI `NO_` natives do: the generator wraps the downcall in `FFM.gtk3Only` or `FFM.gtk4Only`, which decide in the static initializer of the holder, so the hot path is unchanged.
This covers every native of `GTK3` and of `GTK4`, the natives the other version's headers lack, and the overload of `gdk_cursor_new_from_name` whose ABI belongs to one version (`(long, String)` is GTK3, `(String, long)` is GTK4), since libgtk-4 has same named symbols with another ABI.

### GTK4 hand written code

The GTK4 parts of `os_custom.c` are Java as well, so a GTK4 build needs no hand written C either.
`FFMSwtFixed` keeps the natives of the GTK3 container and dispatches to `FFMSwtFixed4` under GTK4.
One public static method per native name is what the rewriter keys on, so `swt_fixed_get_type`, `swt_fixed_move`, `swt_fixed_resize` and `swt_fixed_restack` serve both versions, while `swt_fixed_add`, `swt_fixed_remove` and `swt_scaled_paintable_new` exist for GTK4 only and throw `UnsatisfiedLinkError` under GTK3, as the GTK3 JNI library has no such symbols.
`FFMSwtFixed4` and `FFMScaledPaintable` are package private and need no entry in the implementation lists of `build-gtk.sh` and `apply-ffm.sh`.
`dispose` and `finalize` of the ports always chain to the parent class, also when a pending callback exception surfaces from a downcall in between, as the C code does.
`sizeAllocate` skips children that a `Resize` listener removed during the loop and allocates the remaining children when one allocation raises, then lets the first exception surface.
The GTK3 `FFMSwtFixed` reads the GTK3 values of the version dependent `Extra_FFM` offsets, since `FFM.GTK4` is false there.

The GTK4 `SwtFixed` is a plain `GtkWidget` subclass registered with `g_type_register_static` that implements `GtkScrollable`.
Its `GObjectClass` slots (`set_property`, `get_property`, `dispose`, `finalize`) and `GtkWidgetClass` slots (`measure`, `size_allocate(widget, width, height, baseline)`) are upcall stubs, and the `resize` signal is created with the variadic `g_signal_new` and emitted from `size_allocate` before the children are allocated.
Children are kept in a Java list as in GTK3, `swt_fixed_resize` and `swt_fixed_move` only record the geometry, and `dispose` unparents every child so that none outlives its container.
A child that is not in the list is unparented directly, where the C loop would never end.

`FFMScaledPaintable` registers `SwtScaledPaintable` as a `GObject` that implements `GdkPaintable`, with snapshot, intrinsic width, height and aspect ratio, flags and dispose as stubs.
Its instance keeps the texture and the logical size after the `GObject` header, the layout the C struct has.
`content_providers_create_gtype` and `content_providers_create_gvalue` live in `FFMTypes`, because they only need GLib.
`FFMMacros` and `FFMTypes` add `GTK_IS_POPOVER_MENU`, return 0 for `GET_FUNCTION_POINTER_gtk_false` (GTK4 has no `gtk_false`) and take the `sizeof` of the cell renderers from the generated, version dependent constants, where the pixbuf and toggle ones throw under GTK4 as the JNI library has no such natives.

The ports call the generated bindings (`OS_FFM`, `GTK_FFM`, `GTK4_FFM`) wherever one exists, so a replay on the FFM build needs no SWT library.
Every function the ports call by name is a native of `OS`, `GTK`, `GTK3`, `GTK4` or `ATK`, so the generator emits its binding and the JNI C is generated for it too: the type getters (`gtk_widget_get_type`, `gtk_scrollable_get_type`, `gtk_container_get_type`, `gdk_paintable_get_type`), `g_object_class_override_property`, `g_value_set_object`, `g_value_set_enum`, `g_value_get_enum`, `g_type_add_interface_static`, the variadic `g_signal_new` and `g_signal_emit` with the argument count and promotion of their call sites, `g_boxed_type_register_static`, and the GTK3 container, accessible and lock functions.
`gdk_paintable_snapshot` declares `double` for the size, like the C function and the vtable slot, so the scaled paintable forwards the fractional size.
The handles that remain are the calls through a function pointer, which have no symbol, `localeconv` from libc, and the type getters of `FFMMacros`, which are looked up by the name the `GTK_IS_*` macro stands for.
A call through a function pointer uses one address-less handle per call shape that takes the function as its first argument, because linking a handle per call costs about 0.25 to 0.75 µs more per call and several milliseconds on the first calls.
GTK widget code does not call `OS.call` itself; the hand written parent class calls (finalize, realize, the ATK initialize) are the paths that benefit, and a widget workload shows no change beyond the noise.

### Remaining JNI

No native stays on JNI on either version: every native of `OS`, `GDK`, `GTK`, `GTK3`, `GTK4`, `Graphene`, `Cairo`, `ATK`, `GLX`, `WebKitGTK` and `C` is generated or implemented by hand written Java, so the rewriter removes every `Library.loadLibrary` call of the SWT libraries and a GTK4 process loads no `libswt-pi4`.
`SWT_AWT` needs JNI by definition and goes through `FFMAwt`.
The 111 natives of `report-gtk/unsupported.txt` are the ones the generator does not read: the `flags=const` constants, the macros and the calls through a function pointer, and the custom C, which `FFMMacros`, `FFMTypes`, `FFMRuntime`, `FFMSwtFixed`, `FFMAccessible` and `FFMConstructorProc` implement.
The rewriter prints the natives that remain on JNI, which are the ones a merged pull request adds after the report was generated.

### Verification

* `FFMCrossCheck` on GTK3 against JNI libraries built from this branch: 1,422 checks, 0 mismatches, so GTK3 does not regress.
* `FFMCrossCheck` with `SWT_GTK4=1` against `libswt-pi4` built from this branch: 988 checks, 0 mismatches, 0 known differences.
  That is 25 struct sizes (`GdkRGBA` 16, `GtkWidgetClass` 408, `GtkCellRendererClass` 288 among them), 600 struct reads and 180 struct writes through the JNI `memmove`s, and 146 call results.
  The calls cover `float` and `double` out parameters (`gtk_hsv_to_rgb`, `gdk_surface_get_device_position`, `gtk_widget_translate_coordinates`), structs by pointer (`gdk_rgba_parse`, `gdk_popup_layout_new`, `gdk_monitor_get_geometry`, `gtk_style_context_get_padding`), the `Graphene` natives, the string array, the dynamic native that is missing and the GTK3 only native that must not link.
  `xinject.py` injects motion, button, scroll and key events through XTest, and the callbacks compare `gdk_event_get_position`, `_get_surface`, `_get_seat`, `_get_time`, `_get_modifier_state` and the button, scroll and key getters of every event through JNI and FFM: 9 events.
* `test-gtk-select.sh` compares the JNI and the FFM build for GTK3 only, GTK4 only, both and neither installed with `SWT_GTK4` unset, 0 and 1: the same GTK version, the same stderr message and the same exception type.
* `FFMGtkVersionCheck` runs generated natives on the FFM build with a library path that does not exist: GTK3 and GTK4 both initialize and open a window, and `/proc/self/maps` shows the GTK libraries of the selected version only.
* `test-gtk4-custom.sh` runs `FFMGtk4CustomCheck`, which creates the JNI and the FFM implementation in one process under `SWT_GTK4=1`, drives the same scenario through each and compares what it observes: child allocations, measured sizes, the order of `resize` signals, parents after add, remove and dispose, the `GtkScrollable` properties with their notifications, the intrinsic size, aspect ratio, flags, snapshot render node and rendered pixels of scaled paintables, a `Resize` listener that removes a later sibling during size allocation (no GTK critical, the other children still allocated), and every macro and constant.
  Result: 195 observations with 0 mismatches, 270 macros and constants compared, 27 layout values equal to the C probe.
  It then replays the recorded JNI observations on the FFM build with no SWT library on the library path: 0 mismatches.
* The SWT JUnit suite of `test-gtk.sh` (127 classes) runs on the JNI build and on the FFM build without any SWT library, under GTK3 and under `SWT_GTK4=1`; the outcomes are identical on both builds: 4,062 passed, 78 failed, 11 aborted and 2 skipped on GTK3, and 4,043 passed, 95 failed, 13 aborted and 2 skipped on GTK4 (one GTK4 test is flaky on both builds) (the failures of the GTK4 port are the same with JNI).
* All checks run headless: `env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 xvfb-run -a`.

### Open

* The fallback between versions probes the GTK library with `dlopen`, where the JNI loader falls back when `libswt-pi4` or `libswt-pi3` fails to load, which also fails when the SWT library itself is missing.
  `test-gtk-select.sh` runs both builds through every combination of installed GTK versions and `SWT_GTK4` (a missing library is a stub of that name whose own dependency is missing) and the outcomes are identical.
* The layouts were probed on x86_64 only, as for GTK3.
* `FFMCrossCheck` compares the generated natives with the stock JNI ones, so it cannot catch a difference that both share.

## Next steps

1. Settle where the declarations live once they are no longer `native`: generated delegating bodies in `OS.java` and friends, or a non-compiled declaration file that the generator reads.
   The build time rewrite is fine for a fork, but upstream needs one committed shape.
2. Cut the cold cost, about 1.3 s of CPU time on first use (see Performance), without an AOT cache: profile the first iteration at a finer sampling interval to split handle linking, `LambdaForm` spinning and interpreted execution, then attack the largest part.
   Replace the confined arena per copied array with a per-thread allocator for the warm cost.
3. Propose the Java 25 baseline together with the GTK3 port upstream, starting with a discussion rather than a pull request, since both are platform wide decisions.
4. Then Win32 (see Phase 5: Win32) and Cocoa.

## Open questions

* Java baseline: FFM is final from Java 22 and SWT requires Java 21, so shipping needs a Java 25 baseline; the `java25-bree` branch is ready and lands when something needs it.
  Raising the BREE of SWT alone is fine: a bundle with a JavaSE-21 BREE resolves against one that requires JavaSE-25, because the execution environment capability comes from the running JVM rather than from the consuming bundle.
* Native access: OSGi bundles live in the unnamed module, so launchers need `--enable-native-access=ALL-UNNAMED`, which becomes mandatory in a future Java release.
* Modified UTF-8 (JNI `GetStringUTFChars`) versus standard UTF-8 (FFM) differs for embedded NUL and supplementary characters.
