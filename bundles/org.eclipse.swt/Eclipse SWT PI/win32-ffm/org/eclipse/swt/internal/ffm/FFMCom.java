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

import static java.lang.foreign.MemoryLayout.*;
import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.*;
import java.lang.invoke.*;

import org.eclipse.swt.internal.ole.win32.*;
import org.eclipse.swt.internal.win32.*;

/**
 * Implements the <code>VtblCall</code> natives of {@link COM}: the function pointer is read from the
 * vtable of the object, <code>*(*(this) + index)</code>, and called through one downcall handle per
 * descriptor. Arrays and structs are copied in and back out like the JNI glue in com.c does.
 */
public final class FFMCom {

	static final MemorySegment MEMORY = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);

	private FFMCom() {
	}

	static MemorySegment function(long object, int index) {
		long vtbl = MEMORY.get(JAVA_LONG_UNALIGNED, object);
		return MemorySegment.ofAddress(MEMORY.get(JAVA_LONG_UNALIGNED, vtbl + index * ADDRESS.byteSize()));
	}

	/** An address-less handle returning HRESULT, with the object as first argument. */
	static MethodHandle handle(MemoryLayout... parameters) {
		MemoryLayout[] layouts = new MemoryLayout[parameters.length + 1];
		layouts[0] = JAVA_LONG;
		System.arraycopy(parameters, 0, layouts, 1, parameters.length);
		return FFM.LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, layouts));
	}

	/* ---------------------------------------------------------------- struct layouts (Windows x64 and arm64) */

	static final StructLayout RECT_LAYOUT = structLayout(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
	static final StructLayout POINT_LAYOUT = structLayout(JAVA_INT, JAVA_INT);
	static final StructLayout SIZE_LAYOUT = structLayout(JAVA_INT, JAVA_INT);
	/** hwnd, message, wParam, lParam, time, pt */
	static final StructLayout MSG_LAYOUT = structLayout(JAVA_LONG, JAVA_INT, paddingLayout(4), JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, paddingLayout(4));
	static final StructLayout GUID_LAYOUT = structLayout(JAVA_INT, JAVA_SHORT, JAVA_SHORT, sequenceLayout(8, JAVA_BYTE));
	static final StructLayout PROPERTYKEY_LAYOUT = structLayout(GUID_LAYOUT, JAVA_INT);
	/** rgvarg, rgdispidNamedArgs, cArgs, cNamedArgs */
	static final StructLayout DISPPARAMS_LAYOUT = structLayout(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT);
	/** wCode, wReserved, bstrSource, bstrDescription, bstrHelpFile, dwHelpContext, pvReserved, pfnDeferredFillIn, scode */
	static final StructLayout EXCEPINFO_LAYOUT = structLayout(JAVA_SHORT, JAVA_SHORT, paddingLayout(4), JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, paddingLayout(4), JAVA_LONG, JAVA_LONG, JAVA_INT, paddingLayout(4));
	/** cfFormat, ptd, dwAspect, lindex, tymed */
	static final StructLayout FORMATETC_LAYOUT = structLayout(JAVA_SHORT, paddingLayout(6), JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, paddingLayout(4));
	/** tymed, union, pUnkForRelease */
	static final StructLayout STGMEDIUM_LAYOUT = structLayout(JAVA_INT, paddingLayout(4), JAVA_LONG, JAVA_LONG);
	static final StructLayout CAUUID_LAYOUT = structLayout(JAVA_INT, paddingLayout(4), JAVA_LONG);
	/** cb, hAccel, cAccel, dwFlags */
	static final StructLayout CONTROLINFO_LAYOUT = structLayout(JAVA_INT, paddingLayout(4), JAVA_LONG, JAVA_SHORT, paddingLayout(2), JAVA_INT);
	static final StructLayout OLECMD_LAYOUT = structLayout(JAVA_INT, JAVA_INT);
	static final StructLayout LICINFO_LAYOUT = structLayout(JAVA_INT, JAVA_INT, JAVA_INT);
	static final StructLayout TF_DA_COLOR_LAYOUT = structLayout(JAVA_INT, JAVA_INT);
	/** crText, crBk, lsStyle, fBoldLine, crLine, bAttr */
	static final StructLayout TF_DISPLAYATTRIBUTE_LAYOUT = structLayout(TF_DA_COLOR_LAYOUT, TF_DA_COLOR_LAYOUT, JAVA_INT, JAVA_INT, TF_DA_COLOR_LAYOUT, JAVA_INT);

	/* ---------------------------------------------------------------- struct copies, as get/set*Fields in *_structs.c */

	static MemorySegment in(Arena arena, RECT o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(RECT_LAYOUT);
		s.set(JAVA_INT, 0, o.left);
		s.set(JAVA_INT, 4, o.top);
		s.set(JAVA_INT, 8, o.right);
		s.set(JAVA_INT, 12, o.bottom);
		return s;
	}

	static void out(MemorySegment s, RECT o) {
		if (o == null) return;
		o.left = s.get(JAVA_INT, 0);
		o.top = s.get(JAVA_INT, 4);
		o.right = s.get(JAVA_INT, 8);
		o.bottom = s.get(JAVA_INT, 12);
	}

	static MemorySegment in(Arena arena, POINT o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(POINT_LAYOUT);
		s.set(JAVA_INT, 0, o.x);
		s.set(JAVA_INT, 4, o.y);
		return s;
	}

	static void out(MemorySegment s, POINT o) {
		if (o == null) return;
		o.x = s.get(JAVA_INT, 0);
		o.y = s.get(JAVA_INT, 4);
	}

	static MemorySegment in(Arena arena, SIZE o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(SIZE_LAYOUT);
		s.set(JAVA_INT, 0, o.cx);
		s.set(JAVA_INT, 4, o.cy);
		return s;
	}

	static void out(MemorySegment s, SIZE o) {
		if (o == null) return;
		o.cx = s.get(JAVA_INT, 0);
		o.cy = s.get(JAVA_INT, 4);
	}

	static MemorySegment in(Arena arena, MSG o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(MSG_LAYOUT);
		s.set(JAVA_LONG, 0, o.hwnd);
		s.set(JAVA_INT, 8, o.message);
		s.set(JAVA_LONG, 16, o.wParam);
		s.set(JAVA_LONG, 24, o.lParam);
		s.set(JAVA_INT, 32, o.time);
		s.set(JAVA_INT, 36, o.x);
		s.set(JAVA_INT, 40, o.y);
		return s;
	}

	static void out(MemorySegment s, MSG o) {
		if (o == null) return;
		o.hwnd = s.get(JAVA_LONG, 0);
		o.message = s.get(JAVA_INT, 8);
		o.wParam = s.get(JAVA_LONG, 16);
		o.lParam = s.get(JAVA_LONG, 24);
		o.time = s.get(JAVA_INT, 32);
		o.x = s.get(JAVA_INT, 36);
		o.y = s.get(JAVA_INT, 40);
	}

	static MemorySegment in(Arena arena, GUID o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(GUID_LAYOUT);
		s.set(JAVA_INT, 0, o.Data1);
		s.set(JAVA_SHORT, 4, o.Data2);
		s.set(JAVA_SHORT, 6, o.Data3);
		MemorySegment.copy(o.Data4, 0, s, JAVA_BYTE, 8, 8);
		return s;
	}

	static void out(MemorySegment s, GUID o) {
		if (o == null) return;
		o.Data1 = s.get(JAVA_INT, 0);
		o.Data2 = s.get(JAVA_SHORT, 4);
		o.Data3 = s.get(JAVA_SHORT, 6);
		MemorySegment.copy(s, JAVA_BYTE, 8, o.Data4, 0, 8);
	}

	static MemorySegment in(Arena arena, PROPERTYKEY o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(PROPERTYKEY_LAYOUT);
		MemorySegment.copy(o.fmtid, 0, s, JAVA_BYTE, 0, 16);
		s.set(JAVA_INT, 16, o.pid);
		return s;
	}

	static void out(MemorySegment s, PROPERTYKEY o) {
		if (o == null) return;
		MemorySegment.copy(s, JAVA_BYTE, 0, o.fmtid, 0, 16);
		o.pid = s.get(JAVA_INT, 16);
	}

	static MemorySegment in(Arena arena, DISPPARAMS o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(DISPPARAMS_LAYOUT);
		s.set(JAVA_LONG, 0, o.rgvarg);
		s.set(JAVA_LONG, 8, o.rgdispidNamedArgs);
		s.set(JAVA_INT, 16, o.cArgs);
		s.set(JAVA_INT, 20, o.cNamedArgs);
		return s;
	}

	static void out(MemorySegment s, DISPPARAMS o) {
		if (o == null) return;
		o.rgvarg = s.get(JAVA_LONG, 0);
		o.rgdispidNamedArgs = s.get(JAVA_LONG, 8);
		o.cArgs = s.get(JAVA_INT, 16);
		o.cNamedArgs = s.get(JAVA_INT, 20);
	}

	static MemorySegment in(Arena arena, EXCEPINFO o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(EXCEPINFO_LAYOUT);
		s.set(JAVA_SHORT, 0, o.wCode);
		s.set(JAVA_SHORT, 2, o.wReserved);
		s.set(JAVA_LONG, 8, o.bstrSource);
		s.set(JAVA_LONG, 16, o.bstrDescription);
		s.set(JAVA_LONG, 24, o.bstrHelpFile);
		s.set(JAVA_INT, 32, o.dwHelpContext);
		s.set(JAVA_LONG, 40, o.pvReserved);
		s.set(JAVA_LONG, 48, o.pfnDeferredFillIn);
		s.set(JAVA_INT, 56, o.scode);
		return s;
	}

	static void out(MemorySegment s, EXCEPINFO o) {
		if (o == null) return;
		o.wCode = s.get(JAVA_SHORT, 0);
		o.wReserved = s.get(JAVA_SHORT, 2);
		o.bstrSource = s.get(JAVA_LONG, 8);
		o.bstrDescription = s.get(JAVA_LONG, 16);
		o.bstrHelpFile = s.get(JAVA_LONG, 24);
		o.dwHelpContext = s.get(JAVA_INT, 32);
		o.pvReserved = s.get(JAVA_LONG, 40);
		o.pfnDeferredFillIn = s.get(JAVA_LONG, 48);
		o.scode = s.get(JAVA_INT, 56);
	}

	static MemorySegment in(Arena arena, FORMATETC o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(FORMATETC_LAYOUT);
		s.set(JAVA_SHORT, 0, (short) o.cfFormat);
		s.set(JAVA_LONG, 8, o.ptd);
		s.set(JAVA_INT, 16, o.dwAspect);
		s.set(JAVA_INT, 20, o.lindex);
		s.set(JAVA_INT, 24, o.tymed);
		return s;
	}

	static void out(MemorySegment s, FORMATETC o) {
		if (o == null) return;
		o.cfFormat = Short.toUnsignedInt(s.get(JAVA_SHORT, 0)); // CLIPFORMAT is a WORD
		o.ptd = s.get(JAVA_LONG, 8);
		o.dwAspect = s.get(JAVA_INT, 16);
		o.lindex = s.get(JAVA_INT, 20);
		o.tymed = s.get(JAVA_INT, 24);
	}

	static MemorySegment in(Arena arena, STGMEDIUM o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(STGMEDIUM_LAYOUT);
		s.set(JAVA_INT, 0, o.tymed);
		s.set(JAVA_LONG, 8, o.unionField);
		s.set(JAVA_LONG, 16, o.pUnkForRelease);
		return s;
	}

	static void out(MemorySegment s, STGMEDIUM o) {
		if (o == null) return;
		o.tymed = s.get(JAVA_INT, 0);
		o.unionField = s.get(JAVA_LONG, 8);
		o.pUnkForRelease = s.get(JAVA_LONG, 16);
	}

	static MemorySegment in(Arena arena, CAUUID o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(CAUUID_LAYOUT);
		s.set(JAVA_INT, 0, o.cElems);
		s.set(JAVA_LONG, 8, o.pElems);
		return s;
	}

	static void out(MemorySegment s, CAUUID o) {
		if (o == null) return;
		o.cElems = s.get(JAVA_INT, 0);
		o.pElems = s.get(JAVA_LONG, 8);
	}

	static MemorySegment in(Arena arena, CONTROLINFO o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(CONTROLINFO_LAYOUT);
		s.set(JAVA_INT, 0, o.cb);
		s.set(JAVA_LONG, 8, o.hAccel);
		s.set(JAVA_SHORT, 16, o.cAccel);
		s.set(JAVA_INT, 20, o.dwFlags);
		return s;
	}

	static void out(MemorySegment s, CONTROLINFO o) {
		if (o == null) return;
		o.cb = s.get(JAVA_INT, 0);
		o.hAccel = s.get(JAVA_LONG, 8);
		o.cAccel = s.get(JAVA_SHORT, 16);
		o.dwFlags = s.get(JAVA_INT, 20);
	}

	static MemorySegment in(Arena arena, OLECMD o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(OLECMD_LAYOUT);
		s.set(JAVA_INT, 0, o.cmdID);
		s.set(JAVA_INT, 4, o.cmdf);
		return s;
	}

	static void out(MemorySegment s, OLECMD o) {
		if (o == null) return;
		o.cmdID = s.get(JAVA_INT, 0);
		o.cmdf = s.get(JAVA_INT, 4);
	}

	static MemorySegment in(Arena arena, LICINFO o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(LICINFO_LAYOUT);
		s.set(JAVA_INT, 0, o.cbLicInfo);
		s.set(JAVA_INT, 4, o.fRuntimeKeyAvail ? 1 : 0);
		s.set(JAVA_INT, 8, o.fLicVerified ? 1 : 0);
		return s;
	}

	static void out(MemorySegment s, LICINFO o) {
		if (o == null) return;
		o.cbLicInfo = s.get(JAVA_INT, 0);
		// JNI SetBooleanField keeps the lowest bit of the (jboolean) cast
		o.fRuntimeKeyAvail = (s.get(JAVA_INT, 4) & 1) != 0;
		o.fLicVerified = (s.get(JAVA_INT, 8) & 1) != 0;
	}

	static void in(MemorySegment s, long offset, TF_DA_COLOR o) {
		if (o == null) return;
		s.set(JAVA_INT, offset, o.type);
		s.set(JAVA_INT, offset + 4, o.cr);
	}

	static void out(MemorySegment s, long offset, TF_DA_COLOR o) {
		if (o == null) return;
		o.type = s.get(JAVA_INT, offset);
		o.cr = s.get(JAVA_INT, offset + 4);
	}

	static MemorySegment in(Arena arena, TF_DISPLAYATTRIBUTE o) {
		if (o == null) return MemorySegment.NULL;
		MemorySegment s = arena.allocate(TF_DISPLAYATTRIBUTE_LAYOUT);
		in(s, 0, o.crText);
		in(s, 8, o.crBk);
		s.set(JAVA_INT, 16, o.lsStyle);
		s.set(JAVA_INT, 20, o.fBoldLine ? 1 : 0);
		in(s, 24, o.crLine);
		s.set(JAVA_INT, 32, o.bAttr);
		return s;
	}

	static void out(MemorySegment s, TF_DISPLAYATTRIBUTE o) {
		if (o == null) return;
		out(s, 0, o.crText);
		out(s, 8, o.crBk);
		o.lsStyle = s.get(JAVA_INT, 16);
		o.fBoldLine = (s.get(JAVA_INT, 20) & 1) != 0;
		out(s, 24, o.crLine);
		o.bAttr = s.get(JAVA_INT, 32);
	}

	/* ---------------------------------------------------------------- one handle per call shape: I int, J long, D double, P pointer, R RECT by value */

	private static final class H {
		static final MethodHandle MH = handle();
	}
	private static final class H_D {
		static final MethodHandle MH = handle(JAVA_DOUBLE);
	}
	private static final class H_I {
		static final MethodHandle MH = handle(JAVA_INT);
	}
	private static final class H_IIIPJPJ {
		static final MethodHandle MH = handle(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG);
	}
	private static final class H_IIJP {
		static final MethodHandle MH = handle(JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS);
	}
	private static final class H_IJ {
		static final MethodHandle MH = handle(JAVA_INT, JAVA_LONG);
	}
	private static final class H_IJIP {
		static final MethodHandle MH = handle(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS);
	}
	private static final class H_IJJ {
		static final MethodHandle MH = handle(JAVA_INT, JAVA_LONG, JAVA_LONG);
	}
	private static final class H_IJP {
		static final MethodHandle MH = handle(JAVA_INT, JAVA_LONG, ADDRESS);
	}
	private static final class H_IP {
		static final MethodHandle MH = handle(JAVA_INT, ADDRESS);
	}
	private static final class H_IPIIPJPP {
		static final MethodHandle MH = handle(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS);
	}
	private static final class H_IPIP {
		static final MethodHandle MH = handle(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS);
	}
	private static final class H_IPJIJP {
		static final MethodHandle MH = handle(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_LONG, ADDRESS);
	}
	private static final class H_IPJJ {
		static final MethodHandle MH = handle(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG);
	}
	private static final class H_IPP {
		static final MethodHandle MH = handle(JAVA_INT, ADDRESS, ADDRESS);
	}
	private static final class H_IPPP {
		static final MethodHandle MH = handle(JAVA_INT, ADDRESS, ADDRESS, ADDRESS);
	}
	private static final class H_IPPPP {
		static final MethodHandle MH = handle(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS);
	}
	private static final class H_J {
		static final MethodHandle MH = handle(JAVA_LONG);
	}
	private static final class H_JI {
		static final MethodHandle MH = handle(JAVA_LONG, JAVA_INT);
	}
	private static final class H_JIIP {
		static final MethodHandle MH = handle(JAVA_LONG, JAVA_INT, JAVA_INT, ADDRESS);
	}
	private static final class H_JIP {
		static final MethodHandle MH = handle(JAVA_LONG, JAVA_INT, ADDRESS);
	}
	private static final class H_JJ {
		static final MethodHandle MH = handle(JAVA_LONG, JAVA_LONG);
	}
	private static final class H_JJIP {
		static final MethodHandle MH = handle(JAVA_LONG, JAVA_LONG, JAVA_INT, ADDRESS);
	}
	private static final class H_JJJ {
		static final MethodHandle MH = handle(JAVA_LONG, JAVA_LONG, JAVA_LONG);
	}
	private static final class H_JJJJJ {
		static final MethodHandle MH = handle(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG);
	}
	private static final class H_JJP {
		static final MethodHandle MH = handle(JAVA_LONG, JAVA_LONG, ADDRESS);
	}
	private static final class H_JJPI {
		static final MethodHandle MH = handle(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT);
	}
	private static final class H_JJPJP {
		static final MethodHandle MH = handle(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS);
	}
	private static final class H_JP {
		static final MethodHandle MH = handle(JAVA_LONG, ADDRESS);
	}
	private static final class H_JPJ {
		static final MethodHandle MH = handle(JAVA_LONG, ADDRESS, JAVA_LONG);
	}
	private static final class H_P {
		static final MethodHandle MH = handle(ADDRESS);
	}
	private static final class H_PI {
		static final MethodHandle MH = handle(ADDRESS, JAVA_INT);
	}
	private static final class H_PIIIP {
		static final MethodHandle MH = handle(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS);
	}
	private static final class H_PIIJJ {
		static final MethodHandle MH = handle(ADDRESS, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG);
	}
	private static final class H_PIIPP {
		static final MethodHandle MH = handle(ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
	}
	private static final class H_PIPJ {
		static final MethodHandle MH = handle(ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG);
	}
	private static final class H_PJ {
		static final MethodHandle MH = handle(ADDRESS, JAVA_LONG);
	}
	private static final class H_PJI {
		static final MethodHandle MH = handle(ADDRESS, JAVA_LONG, JAVA_INT);
	}
	private static final class H_PJIIIP {
		static final MethodHandle MH = handle(ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS);
	}
	private static final class H_PJIIP {
		static final MethodHandle MH = handle(ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, ADDRESS);
	}
	private static final class H_PP {
		static final MethodHandle MH = handle(ADDRESS, ADDRESS);
	}
	private static final class H_PPJPP {
		static final MethodHandle MH = handle(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS);
	}
	private static final class H_PPP {
		static final MethodHandle MH = handle(ADDRESS, ADDRESS, ADDRESS);
	}
	private static final class H_PPPPP {
		static final MethodHandle MH = handle(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS);
	}
	private static final class H_R {
		static final MethodHandle MH = handle(RECT_LAYOUT);
	}
	private static final class H_RJJ {
		static final MethodHandle MH = handle(RECT_LAYOUT, JAVA_LONG, JAVA_LONG);
	}

	/* ---------------------------------------------------------------- the natives */

	public static int VtblCall(int fnNumber, long ppVtbl) {
		try {
			int rc = (int) H.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0) {
		try {
			int rc = (int) H_I.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0) {
		try {
			int rc = (int) H_J.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, long arg1, int arg2, long[] arg3) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg3 = FFM.copyIn(arena, arg3);
			int rc = (int) H_JJIP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, arg2, lparg3);
			FFM.copyOut(lparg3, arg3);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, long arg1, int arg2, long[] arg3) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg3 = FFM.copyIn(arena, arg3);
			int rc = (int) H_IJIP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, arg2, lparg3);
			FFM.copyOut(lparg3, arg3);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, int arg1, int arg2, long[] arg3) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg3 = FFM.copyIn(arena, arg3);
			int rc = (int) H_JIIP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, arg2, lparg3);
			FFM.copyOut(lparg3, arg3);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0, int arg1, int arg2, int[] arg3, int[] arg4) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			MemorySegment lparg3 = FFM.copyIn(arena, arg3);
			MemorySegment lparg4 = FFM.copyIn(arena, arg4);
			int rc = (int) H_PIIPP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1, arg2, lparg3, lparg4);
			FFM.copyOut(lparg4, arg4);
			FFM.copyOut(lparg3, arg3);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int[] arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long[] arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, long[] arg1, int[] arg2) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			MemorySegment lparg2 = FFM.copyIn(arena, arg2);
			int rc = (int) H_IPP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1, lparg2);
			FFM.copyOut(lparg2, arg2);
			FFM.copyOut(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, TF_DISPLAYATTRIBUTE arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, long arg1, long arg2) {
		try {
			int rc = (int) H_IJJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, arg2);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, long arg1, long arg2) {
		try {
			int rc = (int) H_JJJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, arg2);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, long arg1) {
		try {
			int rc = (int) H_IJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, long arg1) {
		try {
			int rc = (int) H_JJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0, int arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			int rc = (int) H_PI.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0, long arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			int rc = (int) H_PJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0, long[] arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			int rc = (int) H_PP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, lparg1);
			FFM.copyOut(lparg1, arg1);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, PROPERTYKEY arg0, long arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_PJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, int[] arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			int rc = (int) H_IP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1);
			FFM.copyOut(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, int[] arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			int rc = (int) H_JP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1);
			FFM.copyOut(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, long[] arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			int rc = (int) H_IP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1);
			FFM.copyOut(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0, char[] arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			int rc = (int) H_PP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, lparg1);
			FFM.copyOut(lparg1, arg1);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, long arg1, POINT arg2, int arg3) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg2 = in(arena, arg2);
			int rc = (int) H_JJPI.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, lparg2, arg3);
			out(lparg2, arg2);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int[] arg0, GUID arg1, long[] arg2) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			MemorySegment lparg1 = in(arena, arg1);
			MemorySegment lparg2 = FFM.copyIn(arena, arg2);
			int rc = (int) H_PPP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, lparg1, lparg2);
			FFM.copyOut(lparg2, arg2);
			out(lparg1, arg1);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, POINT arg1, long arg2) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = in(arena, arg1);
			int rc = (int) H_JPJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1, arg2);
			out(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, POINT arg0, int arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_PI.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0, int arg1, int arg2, int arg3, long[] arg4) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			MemorySegment lparg4 = FFM.copyIn(arena, arg4);
			int rc = (int) H_PIIIP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1, arg2, arg3, lparg4);
			FFM.copyOut(lparg4, arg4);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0, long arg1, int arg2, int arg3, long[] arg4) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			MemorySegment lparg4 = FFM.copyIn(arena, arg4);
			int rc = (int) H_PJIIP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1, arg2, arg3, lparg4);
			FFM.copyOut(lparg4, arg4);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0, long arg1, int arg2, int arg3, int arg4, long[] arg5) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			MemorySegment lparg5 = FFM.copyIn(arena, arg5);
			int rc = (int) H_PJIIIP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1, arg2, arg3, arg4, lparg5);
			FFM.copyOut(lparg5, arg5);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0, char[] arg1, long arg2, char[] arg3, long[] arg4) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			MemorySegment lparg3 = FFM.copyIn(arena, arg3);
			MemorySegment lparg4 = FFM.copyIn(arena, arg4);
			int rc = (int) H_PPJPP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, lparg1, arg2, lparg3, lparg4);
			FFM.copyOut(lparg4, arg4);
			FFM.copyOut(lparg3, arg3);
			FFM.copyOut(lparg1, arg1);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, long[] arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			int rc = (int) H_JP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1);
			FFM.copyOut(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, int arg1, long[] arg2) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg2 = FFM.copyIn(arena, arg2);
			int rc = (int) H_JIP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, lparg2);
			FFM.copyOut(lparg2, arg2);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, long arg1, long[] arg2) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg2 = FFM.copyIn(arena, arg2);
			int rc = (int) H_JJP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, lparg2);
			FFM.copyOut(lparg2, arg2);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, long arg1, int[] arg2) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg2 = FFM.copyIn(arena, arg2);
			int rc = (int) H_IJP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, lparg2);
			FFM.copyOut(lparg2, arg2);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, int arg1, int[] arg2) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg2 = FFM.copyIn(arena, arg2);
			int rc = (int) H_JIP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, lparg2);
			FFM.copyOut(lparg2, arg2);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, int arg1, long arg2, SIZE arg3) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg3 = in(arena, arg3);
			int rc = (int) H_IIJP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, arg2, lparg3);
			out(lparg3, arg3);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, long arg1, GUID arg2, long arg3, long[] arg4) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg2 = in(arena, arg2);
			MemorySegment lparg4 = FFM.copyIn(arena, arg4);
			int rc = (int) H_JJPJP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, lparg2, arg3, lparg4);
			FFM.copyOut(lparg4, arg4);
			out(lparg2, arg2);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, GUID arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = in(arena, arg1);
			int rc = (int) H_IP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1);
			out(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, GUID arg1, long arg2, long arg3) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = in(arena, arg1);
			int rc = (int) H_IPJJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1, arg2, arg3);
			out(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, GUID arg1, int arg2, int arg3, DISPPARAMS arg4, long arg5, EXCEPINFO arg6, int[] arg7) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = in(arena, arg1);
			MemorySegment lparg4 = in(arena, arg4);
			MemorySegment lparg6 = in(arena, arg6);
			MemorySegment lparg7 = FFM.copyIn(arena, arg7);
			int rc = (int) H_IPIIPJPP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1, arg2, arg3, lparg4, arg5, lparg6, lparg7);
			FFM.copyOut(lparg7, arg7);
			out(lparg6, arg6);
			out(lparg4, arg4);
			out(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, MSG arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, MSG arg1, long arg2, int arg3, long arg4, RECT arg5) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = in(arena, arg1);
			MemorySegment lparg5 = in(arena, arg5);
			int rc = (int) H_IPJIJP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1, arg2, arg3, arg4, lparg5);
			out(lparg5, arg5);
			out(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, SIZE arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = in(arena, arg1);
			int rc = (int) H_IP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1);
			out(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, int arg1) {
		try {
			int rc = (int) H_JI.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, CAUUID arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, CONTROLINFO arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, FORMATETC arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, FORMATETC arg0, STGMEDIUM arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			MemorySegment lparg1 = in(arena, arg1);
			int rc = (int) H_PP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, lparg1);
			out(lparg1, arg1);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, GUID arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, GUID arg0, long[] arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			int rc = (int) H_PP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, lparg1);
			FFM.copyOut(lparg1, arg1);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, GUID arg0, GUID arg1, long[] arg2) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			MemorySegment lparg1 = in(arena, arg1);
			MemorySegment lparg2 = FFM.copyIn(arena, arg2);
			int rc = (int) H_PPP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, lparg1, lparg2);
			FFM.copyOut(lparg2, arg2);
			out(lparg1, arg1);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, GUID arg0, long arg1, int arg2, int arg3, int[] arg4) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			MemorySegment lparg4 = FFM.copyIn(arena, arg4);
			int rc = (int) H_PJIIP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1, arg2, arg3, lparg4);
			FFM.copyOut(lparg4, arg4);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, GUID arg0, int arg1, int arg2, long arg3, long arg4) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_PIIJJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1, arg2, arg3, arg4);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, GUID arg0, int arg1, OLECMD arg2, long arg3) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			MemorySegment lparg2 = in(arena, arg2);
			int rc = (int) H_PIPJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1, lparg2, arg3);
			out(lparg2, arg2);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, GUID arg1, GUID arg2, GUID arg3) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = in(arena, arg1);
			MemorySegment lparg2 = in(arena, arg2);
			MemorySegment lparg3 = in(arena, arg3);
			int rc = (int) H_IPPP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1, lparg2, lparg3);
			out(lparg3, arg3);
			out(lparg2, arg2);
			out(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, LICINFO arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, RECT arg0, long arg1, int arg2) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_PJI.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1, arg2);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, long arg0, long arg1, long arg2, long arg3, long arg4) {
		try {
			int rc = (int) H_JJJJJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, arg2, arg3, arg4);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, RECT arg0, RECT arg1) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			MemorySegment lparg1 = in(arena, arg1);
			int rc = (int) H_PP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, lparg1);
			out(lparg1, arg1);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, RECT arg0) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_P.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, long[] arg1, long[] arg2, int[] arg3, long[] arg4) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			MemorySegment lparg2 = FFM.copyIn(arena, arg2);
			MemorySegment lparg3 = FFM.copyIn(arena, arg3);
			MemorySegment lparg4 = FFM.copyIn(arena, arg4);
			int rc = (int) H_IPPPP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1, lparg2, lparg3, lparg4);
			FFM.copyOut(lparg4, arg4);
			FFM.copyOut(lparg3, arg3);
			FFM.copyOut(lparg2, arg2);
			FFM.copyOut(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, long[] arg1, int arg2, int[] arg3) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			MemorySegment lparg3 = FFM.copyIn(arena, arg3);
			int rc = (int) H_IPIP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, lparg1, arg2, lparg3);
			FFM.copyOut(lparg3, arg3);
			FFM.copyOut(lparg1, arg1);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, int arg0, int arg1, int arg2, DISPPARAMS arg3, long arg4, EXCEPINFO arg5, long arg6) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg3 = in(arena, arg3);
			MemorySegment lparg5 = in(arena, arg5);
			int rc = (int) H_IIIPJPJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0, arg1, arg2, lparg3, arg4, lparg5, arg6);
			out(lparg5, arg5);
			out(lparg3, arg3);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, char[] arg0, char[] arg1, char[] arg2, char[] arg3, long[] arg4) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = FFM.copyIn(arena, arg0);
			MemorySegment lparg1 = FFM.copyIn(arena, arg1);
			MemorySegment lparg2 = FFM.copyIn(arena, arg2);
			MemorySegment lparg3 = FFM.copyIn(arena, arg3);
			MemorySegment lparg4 = FFM.copyIn(arena, arg4);
			int rc = (int) H_PPPPP.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, lparg1, lparg2, lparg3, lparg4);
			FFM.copyOut(lparg4, arg4);
			FFM.copyOut(lparg3, arg3);
			FFM.copyOut(lparg2, arg2);
			FFM.copyOut(lparg1, arg1);
			FFM.copyOut(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, double arg0) {
		try {
			int rc = (int) H_D.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall(int fnNumber, long ppVtbl, RECT arg0, long arg1, long arg2) {
		if (arg0 == null) throw new NullPointerException(); // the JNI glue dereferences NULL
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_RJJ.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0, arg1, arg2);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}

	public static int VtblCall_put_Bounds(int fnNumber, long ppVtbl, RECT arg0) {
		if (arg0 == null) throw new NullPointerException(); // the JNI glue dereferences NULL
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment lparg0 = in(arena, arg0);
			int rc = (int) H_R.MH.invokeExact(function(ppVtbl, fnNumber), ppVtbl, lparg0);
			out(lparg0, arg0);
			FFM.checkCallbackException();
			return rc;
		} catch (Throwable e) {
			throw FFM.rethrow(e);
		}
	}
}
