/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.ffm;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Java port of the {@code CALLBACK_*} natives of os.c. Each returns an upcall stub with the Objective-C
 * signature, structs by value included, which hands the SWT callback at {@code func} the address of a
 * copy of every struct argument and copies the struct it returns from the memory the callback allocated,
 * as the trampolines of os.c did.
 */
public final class FFMCocoaCallbacks {

	/** The natives class whose natives the public static methods of this class implement. */
	static final String IMPLEMENTS = "org.eclipse.swt.internal.cocoa.OS";

	static final Linker LINKER = Linker.nativeLinker();
	static final MethodHandle FREE = LINKER.downcallHandle(LINKER.defaultLookup().findOrThrow("free"), FunctionDescriptor.ofVoid(JAVA_LONG));
	static final MethodHandle CALL = call();

	static final StructLayout POINT = MemoryLayout.structLayout(JAVA_DOUBLE, JAVA_DOUBLE);
	static final StructLayout RECT = MemoryLayout.structLayout(JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE);
	static final StructLayout RANGE = MemoryLayout.structLayout(JAVA_LONG, JAVA_LONG);

	/** The returned struct is copied by the stub before any other callback of the thread can run. */
	static final ThreadLocal<MemorySegment> RESULT = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(32, 8));

	/** One stub per callback and function, since the C trampolines kept a single function per callback. */
	static final Map<String, Long> STUBS = new ConcurrentHashMap<>();

	public static long CALLBACK_cellBaselineOffset(long func) {
		return stub("cellBaselineOffset", "NSPoint;id;SEL", func);
	}

	public static long CALLBACK_NSTextAttachmentCell_cellSize(long func) {
		return stub("NSTextAttachmentCell_cellSize", "NSSize;id;SEL", func);
	}

	public static long CALLBACK_NSTextAttachmentCell_attachment(long func) {
		return stub("NSTextAttachmentCell_attachment", "id*;id;SEL", func);
	}

	public static long CALLBACK_accessibilityHitTest_(long func) {
		return stub("accessibilityHitTest_", "id;id;SEL;NSPoint", func);
	}

	public static long CALLBACK_attributedSubstringFromRange_(long func) {
		return stub("attributedSubstringFromRange_", "NSAttributedString*;id;SEL;NSRange", func);
	}

	public static long CALLBACK_cacheDisplayInRect_toBitmapImageRep_(long func) {
		return stub("cacheDisplayInRect_toBitmapImageRep_", "void;id;SEL;NSRect;NSBitmapImageRep*", func);
	}

	public static long CALLBACK_canDragRowsWithIndexes_atPoint_(long func) {
		return stub("canDragRowsWithIndexes_atPoint_", "BOOL;id;SEL;NSIndexSet*;NSPoint", func);
	}

	public static long CALLBACK_cellSize(long func) {
		return stub("cellSize", "NSSize;id;SEL", func);
	}

	public static long CALLBACK_cellSizeForBounds_(long func) {
		return stub("cellSizeForBounds_", "NSSize;id;SEL;NSRect", func);
	}

	public static long CALLBACK_characterIndexForPoint_(long func) {
		return stub("characterIndexForPoint_", "NSUInteger;id;SEL;NSPoint", func);
	}

	public static long CALLBACK_columnAtPoint_(long func) {
		return stub("columnAtPoint_", "NSInteger;id;SEL;NSPoint", func);
	}

	public static long CALLBACK_draggedImage_endedAt_operation_(long func) {
		return stub("draggedImage_endedAt_operation_", "void;id;SEL;NSImage*;NSPoint;NSDragOperation", func);
	}

	public static long CALLBACK_drawBackgroundInClipRect_(long func) {
		return stub("drawBackgroundInClipRect_", "void;id;SEL;NSRect", func);
	}

	public static long CALLBACK_drawBezelWithFrame_inView_(long func) {
		return stub("drawBezelWithFrame_inView_", "void;id;SEL;NSRect;NSView*", func);
	}

	public static long CALLBACK_drawImage_withFrame_inView_(long func) {
		return stub("drawImage_withFrame_inView_", "void;id;SEL;NSImage*;NSRect;NSView*", func);
	}

	public static long CALLBACK_drawInteriorWithFrame_inView_(long func) {
		return stub("drawInteriorWithFrame_inView_", "void;id;SEL;NSRect;NSView*", func);
	}

	public static long CALLBACK_drawLabel_inRect_(long func) {
		return stub("drawLabel_inRect_", "void;id;SEL;BOOL;NSRect", func);
	}

	public static long CALLBACK_drawRect_(long func) {
		return stub("drawRect_", "void;id;SEL;NSRect", func);
	}

	public static long CALLBACK_drawTitle_withFrame_inView_(long func) {
		return stub("drawTitle_withFrame_inView_", "NSRect;id;SEL;NSAttributedString*;NSRect;NSView*", func);
	}

	public static long CALLBACK_drawViewBackgroundInRect_(long func) {
		return stub("drawViewBackgroundInRect_", "void;id;SEL;NSRect", func);
	}

	public static long CALLBACK_drawWithExpansionFrame_inView_(long func) {
		return stub("drawWithExpansionFrame_inView_", "void;id;SEL;NSRect;NSView*", func);
	}

	public static long CALLBACK_expansionFrameWithFrame_inView_(long func) {
		return stub("expansionFrameWithFrame_inView_", "NSRect;id;SEL;NSRect;NSView*", func);
	}

	public static long CALLBACK_firstRectForCharacterRange_(long func) {
		return stub("firstRectForCharacterRange_", "NSRect;id;SEL;NSRange", func);
	}

	public static long CALLBACK_focusRingMaskBoundsForFrame_inView_(long func) {
		return stub("focusRingMaskBoundsForFrame_inView_", "NSRect;id;SEL;NSRect;NSView*", func);
	}

	public static long CALLBACK_headerRectOfColumn_(long func) {
		return stub("headerRectOfColumn_", "NSRect;id;SEL;NSInteger", func);
	}

	public static long CALLBACK_highlightSelectionInClipRect_(long func) {
		return stub("highlightSelectionInClipRect_", "void;id;SEL;NSRect", func);
	}

	public static long CALLBACK_hitTest_(long func) {
		return stub("hitTest_", "NSView*;id;SEL;NSPoint", func);
	}

	public static long CALLBACK_hitTestForEvent_inRect_ofView_(long func) {
		return stub("hitTestForEvent_inRect_ofView_", "NSCellHitResult;id;SEL;NSEvent*;NSRect;NSView*", func);
	}

	public static long CALLBACK_imageRectForBounds_(long func) {
		return stub("imageRectForBounds_", "NSRect;id;SEL;NSRect", func);
	}

	public static long CALLBACK_markedRange(long func) {
		return stub("markedRange", "NSRange;id;SEL", func);
	}

	public static long CALLBACK_scrollClipView_toPoint_(long func) {
		return stub("scrollClipView_toPoint_", "void;id;SEL;NSClipView*;NSPoint", func);
	}

	public static long CALLBACK_selectedRange(long func) {
		return stub("selectedRange", "NSRange;id;SEL", func);
	}

	public static long CALLBACK_setFrameOrigin_(long func) {
		return stub("setFrameOrigin_", "void;id;SEL;NSPoint", func);
	}

	public static long CALLBACK_setFrameSize_(long func) {
		return stub("setFrameSize_", "void;id;SEL;NSSize", func);
	}

	public static long CALLBACK_setMarkedText_selectedRange_(long func) {
		return stub("setMarkedText_selectedRange_", "void;id;SEL;id;NSRange", func);
	}

	public static long CALLBACK_setNeedsDisplayInRect_(long func) {
		return stub("setNeedsDisplayInRect_", "void;id;SEL;NSRect", func);
	}

	public static long CALLBACK_shouldChangeTextInRange_replacementString_(long func) {
		return stub("shouldChangeTextInRange_replacementString_", "BOOL;id;SEL;NSRange;NSString*", func);
	}

	public static long CALLBACK_sizeOfLabel_(long func) {
		return stub("sizeOfLabel_", "NSSize;id;SEL;BOOL", func);
	}

	public static long CALLBACK_textView_willChangeSelectionFromCharacterRange_toCharacterRange_(long func) {
		return stub("textView_willChangeSelectionFromCharacterRange_toCharacterRange_", "NSRange;id;SEL;NSTextView*;NSRange;NSRange", func);
	}

	public static long CALLBACK_titleRectForBounds_(long func) {
		return stub("titleRectForBounds_", "NSRect;id;SEL;NSRect", func);
	}

	public static long CALLBACK_view_stringForToolTip_point_userData_(long func) {
		return stub("view_stringForToolTip_point_userData_", "NSString*;id;SEL;NSView*;NSToolTipTag;NSPoint;void*", func);
	}

	public static long CALLBACK_webView_setFrame_(long func) {
		return stub("webView_setFrame_", "void;id;SEL;WebView*;NSRect", func);
	}

	/* ---------------------------------------------------------------- stubs */

	/** The signature lists the return type and the argument types, {@code id*} being a returned id behind a pointer. */
	static long stub(String name, String signature, long func) {
		return STUBS.computeIfAbsent(name + '@' + func, key -> new Proc(signature, func).stub());
	}

	static MethodHandle call() {
		try {
			return MethodHandles.lookup().findVirtual(Proc.class, "call", MethodType.methodType(Object.class, Object[].class));
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	static boolean isStruct(String type) {
		return switch (type) {
			case "NSPoint", "NSSize", "NSRect", "NSRange" -> true;
			default -> false;
		};
	}

	static MemoryLayout layout(String type) {
		return switch (type) {
			case "NSPoint", "NSSize" -> POINT;
			case "NSRect" -> RECT;
			case "NSRange" -> RANGE;
			case "BOOL" -> JAVA_BYTE;
			default -> JAVA_LONG;
		};
	}

	static final class Proc {
		final String[] types;
		final long func;
		final MethodHandle target;

		Proc(String signature, long func) {
			this.types = signature.split(";");
			this.func = func;
			MemoryLayout[] arguments = new MemoryLayout[types.length - 1];
			Arrays.fill(arguments, JAVA_LONG);
			// structs travel as pointers to a copy and the result is always read from a register
			target = LINKER.downcallHandle(MemorySegment.ofAddress(func), FunctionDescriptor.of(JAVA_LONG, arguments)).asSpreader(Object[].class, arguments.length);
		}

		long stub() {
			MemoryLayout[] arguments = new MemoryLayout[types.length - 1];
			for (int i = 0; i < arguments.length; i++) arguments[i] = layout(types[i + 1]);
			String returnType = types[0];
			FunctionDescriptor descriptor = returnType.equals("void") ? FunctionDescriptor.ofVoid(arguments) : FunctionDescriptor.of(layout(returnType.replace("*", "")), arguments);
			try {
				MethodHandle handle = CALL.bindTo(this).asCollector(Object[].class, arguments.length).asType(descriptor.toMethodType());
				return LINKER.upcallStub(handle, descriptor, Arena.global()).address();
			} catch (RuntimeException e) {
				throw new IllegalStateException(e);
			}
		}

		Object call(Object[] arguments) {
			try {
				Object[] in = new Object[arguments.length];
				for (int i = 0; i < in.length; i++) {
					Object argument = arguments[i];
					if (argument instanceof MemorySegment struct) {
						in[i] = struct.address();
					} else if (argument instanceof Byte bool) {
						in[i] = (long) (bool & 0xFF);
					} else {
						in[i] = argument;
					}
				}
				return result((long) target.invokeExact(in));
			} catch (Throwable e) {
				FFM.callbackFailed(e);
				return result(0);
			}
		}

		Object result(long value) {
			String type = types[0];
			if (type.equals("void")) return null;
			if (type.equals("BOOL")) return (byte) value;
			boolean pointer = type.equals("id*");
			if (!isStruct(type) && !pointer) return value;
			long size = pointer ? 8 : layout(type).byteSize();
			MemorySegment copy = RESULT.get().asSlice(0, size);
			if (value == 0) {
				copy.fill((byte) 0);
			} else {
				MemorySegment.copy(FFM.segment(value, size), 0, copy, 0, size);
				try {
					FREE.invokeExact(value);
				} catch (Throwable e) {
					throw FFM.rethrow(e);
				}
			}
			return pointer ? (Object) copy.get(JAVA_LONG, 0) : copy;
		}
	}
}
