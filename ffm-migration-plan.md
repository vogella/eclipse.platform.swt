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

### Not yet handled, stays on JNI

Callbacks (`CALLBACK_*`), macros and `static inline` functions that have no symbol, `flags=const` constants, struct by value parameters, `Object` parameters, `unicode` strings and GTK4.
The generator reports every method it leaves on JNI together with the reason.

### Running side by side

A rewrite step produces a copy of the SWT sources in which each supported `native` declaration becomes a one-line delegation to its `_FFM` counterpart, while the remaining natives stay JNI.
That build runs the regular SWT JUnit tests and can be compared with the Visual Oracle harness (`ORACLE_CANDIDATE`) against stock SWT with zero pixel tolerance.

## Status of phases 1 and 2

### Tooling

All scripts live in `bundles/org.eclipse.swt.tools/ffm` and need clang, gcc, the GTK3 development headers, a JDK 25, `xvfb-run` and an Eclipse installation (`ECLIPSE_HOME`) for JDT Core and JUnit.

* `generate-gtk.sh` dumps the clang AST of `c.c`, `os.c`, `gtk3.c`, `cairo.c` and `atk.c`, compiles and runs the layout probes, and writes the generated classes to `Eclipse SWT PI/gtk-ffm` and the report to `report-gtk`.
* `build-gtk.sh jni|ffm` compiles the GTK bundle with plain javac, either stock plus the FFM classes or with the supported natives delegated to FFM.
* `test-gtk.sh` runs `FFMCrossCheck` and then the SWT JUnit tests on both builds and diffs the outcomes; `SWT_NATIVES` points it at other native libraries.
* `test/.../FFMBench.java` compares the per-call cost of both implementations.

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

What remains of `os_custom.c` is the GTK4 code, so no GTK3 native is left on JNI.
The rewrite therefore also drops the `Library.loadLibrary` calls of the rewritten classes, and the FFM build runs without any SWT native library: the whole JUnit suite produces the same outcomes with `java.library.path` pointing at a directory that does not exist.
That is what finishes the GTK3 port, because symbols resolve through the GTK libraries themselves rather than through the dependency tree of the SWT library.

`FFMMacros` implements the macros that have no symbol to link against: the `GTK_IS_*`, `GDK_IS_*` and `ATK_*_GET_IFACE` type checks through `g_type_check_instance_is_a` and `g_type_interface_peek`, the accessors for `GTypeInstance`, `GObjectClass`, `GValue`, `GList`, `GSList`, `GError` and `XAnyEvent` as reads of public struct fields whose offsets the layout probe provides, and the arithmetic of `PANGO_PIXELS` and `CAIRO_VERSION_ENCODE`.
The Java versions return 0 for a null pointer where the C macros dereference it.

The rewriter learns which natives a hand written class implements from the public static methods of its source, so adding an implementation needs no list to be updated.

`FFMTypes` supplies what the remaining macros gave: the fundamental GTypes as the compile time constants they are, the registered ones through their `get_type` function, the `sizeof` of C types without a Java struct class from the probe, the glib version variables as exported symbols, the `GDK_WINDOWING_*` checks as the presence of the matching display type, and the calls through a function pointer as downcall handles.

### Coverage

1,515 of the 1,603 natives of `C`, `OS`, `GDK`, `GTK`, `Graphene`, `GTK3`, `Cairo` and `ATK` are generated (`report-gtk/summary.txt`).
No native on the GTK3 path goes through JNI any more: the 36 that remain are GTK4 functions the GTK3 headers do not declare.
The 88 that stay JNI are the `flags=const` constants, the GTK4 functions the GTK3 headers do not declare, the calls through a function pointer, the `sizeof` macros of C types without a Java struct class, the remaining custom C of `os_custom.c` and 1 native that has to be entered through JNI (see below).

### Verification

* `FFMCrossCheck` compares 35 struct sizes, 720 struct reads and 240 struct writes of random data through every JNI `memmove` of a struct, and 58 call results covering scalars, unsigned results, doubles, critical and copied arrays, aliased arrays, struct out-parameters, bit-fields, variadic calls with a sentinel, dynamic functions and symbol addresses: 0 mismatches.
* `FFMUtf16` is compared against the C implementation for every offset of six strings covering ASCII, Latin-1, three byte characters, surrogate pairs and every `max` boundary, 254 comparisons.
* The Visual Oracle harness renders all 169 specimens through stock SWT and through the FFM build and compares them at zero tolerance: every specimen is bit-identical at 100% and at 200% zoom.
  At 150% every specimen fails on both sides with `IllegalArgumentException: Argument not valid`, which is a pre-existing limitation of the harness at fractional zoom and unrelated to FFM.
* The same cross check, JUnit and Visual Oracle runs pass unchanged with the callbacks routed through upcall stubs.
* 127 SWT JUnit test classes (widgets, graphics, custom, accessibility, dnd, layout, events, program, printing; 4,150 tests) produce identical outcomes on the JNI build and on the FFM build: 4,059 passed, 78 failed and 11 aborted on both, the failures coming from the headless environment.

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

Two findings were deliberately not acted on: the entry count is incremented even while callbacks are disabled, which nothing on GTK reads, and `AtkTableIface.remove_column_selection` forwards to `atkTable_remove_row_selection`, which faithfully reproduces a bug of the C and deserves its own fix upstream.

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

### Building it

The committed sources keep their `native` declarations: `bundles/org.eclipse.swt.tools/ffm/apply-ffm.sh` turns them into calls of their FFM implementation in the checkout, and a product build runs it before Maven.
`FFMRewriter.java` runs as a source file, so that step needs no compiled tooling.
Rewriting the files in the branch instead made every change to `OS.java`, `GTK.java` or `GDK.java` collide with it, which upstream does several times a week.


The delegations are committed into the sources and the two FFM source folders are listed in the `build.properties` of the GTK fragment, so Tycho produces an SWT that needs no native library: the whole JUnit suite passes against the built jar with `java.library.path` pointing at a directory that does not exist.
That makes the branch usable as a patch in a product build, for example through the `PATCHES` list of the Speed Eclipse tooling.

Because the declarations stay, the generator keeps its input and the comparison harness keeps its JNI reference.

## Open questions

* WebKit, GLX, the AWT bridge and GTK4 are not generated yet; a GTK3 application does not load them, but they still need their JNI libraries when used.

* Raising the BREE of SWT alone is fine: a bundle with a JavaSE-21 BREE resolves against one that requires JavaSE-25, because the execution environment capability comes from the running JVM rather than from the consuming bundle.
* Java baseline: FFM is final from Java 22, SWT requires Java 21, so shipping needs a Java 25 baseline, which is an Eclipse Platform wide decision.
* Native access: OSGi bundles live in the unnamed module, so launchers need `--enable-native-access=ALL-UNNAMED`, which becomes mandatory in a future Java release.
* Where the declarations live once JNI is gone: `native` declarations cannot keep a body, so the final shape is either generated delegating bodies in `OS.java` or a non-compiled declaration file.
* Start-up cost of linking method handles, to be measured with IDE start-up.
* Modified UTF-8 (JNI `GetStringUTFChars`) versus standard UTF-8 (FFM) differs for embedded NUL and supplementary characters.
* Where the native declarations live once they are no longer `native`, since the generator needs them as its input.
