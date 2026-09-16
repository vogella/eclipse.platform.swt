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

### Coverage

1,456 of the 1,603 natives of `C`, `OS`, `GDK`, `GTK`, `Graphene`, `GTK3`, `Cairo` and `ATK` are generated (`report-gtk/summary.txt`).
The 147 that stay JNI are 93 macros or custom C functions without a declaration, 34 `flags=const` constants, 10 `no_gen` hand written natives, 9 calls through function pointers and 1 native that has to be entered through JNI (see below).

### Verification

* `FFMCrossCheck` compares 35 struct sizes, 720 struct reads and 240 struct writes of random data through every JNI `memmove` of a struct, and 58 call results covering scalars, unsigned results, doubles, critical and copied arrays, aliased arrays, struct out-parameters, bit-fields, variadic calls with a sentinel, dynamic functions and symbol addresses: 0 mismatches.
* The Visual Oracle harness renders all 169 specimens through stock SWT and through the FFM build and compares them at zero tolerance: every specimen is bit-identical at 100% and at 200% zoom.
  At 150% every specimen fails on both sides with `IllegalArgumentException: Argument not valid`, which is a pre-existing limitation of the harness at fractional zoom and unrelated to FFM.
* 127 SWT JUnit test classes (widgets, graphics, custom, accessibility, dnd, layout, events, program, printing; 4,150 tests) produce identical outcomes on the JNI build and on the FFM build: 4,059 passed, 78 failed and 11 aborted on both, the failures coming from the headless environment.

### Findings

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

Copied arrays pay for a confined arena per call; a reusable per-thread allocator is the obvious next optimisation.

## Open questions

* Java baseline: FFM is final from Java 22, SWT requires Java 21, so shipping needs a Java 25 baseline, which is an Eclipse Platform wide decision.
* Native access: OSGi bundles live in the unnamed module, so launchers need `--enable-native-access=ALL-UNNAMED`, which becomes mandatory in a future Java release.
* Where the declarations live once JNI is gone: `native` declarations cannot keep a body, so the final shape is either generated delegating bodies in `OS.java` or a non-compiled declaration file.
* Start-up cost of linking method handles, to be measured with IDE start-up.
* Modified UTF-8 (JNI `GetStringUTFChars`) versus standard UTF-8 (FFM) differs for embedded NUL and supplementary characters.
