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
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.eclipse.swt.internal.ole.win32.*;

/**
 * Java port of com_custom.cpp: the WebView2 callback, host and options objects and PathToPIDL.
 * Each object is a native block holding its vtable pointer; the vtables are shared upcall stubs
 * that find the Java object by the <code>this</code> pointer, as COMObject does.
 */
public final class FFMComCustom {

	static final int S_OK = 0;
	static final int E_FAIL = 0x80004005;
	static final int E_NOINTERFACE = 0x80004002;
	static final int E_OUTOFMEMORY = 0x8007000E;
	static final int DISP_E_BADINDEX = 0x8002000B;

	static final long PTR = ADDRESS.byteSize();
	static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

	static final SymbolLookup OLE32 = FFM.library("ole32.dll");
	static final SymbolLookup OLEAUT32 = FFM.library("oleaut32.dll");
	static final SymbolLookup SHELL32 = FFM.library("shell32.dll");

	static final MethodHandle CoTaskMemAlloc = FFM.downcall(OLE32, "CoTaskMemAlloc", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
	static final MethodHandle CoTaskMemFree = FFM.downcall(OLE32, "CoTaskMemFree", FunctionDescriptor.ofVoid(JAVA_LONG));
	static final MethodHandle CreateBindCtx = FFM.downcall(OLE32, "CreateBindCtx", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG));
	static final MethodHandle CreateDispTypeInfo = FFM.downcall(OLEAUT32, "CreateDispTypeInfo", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG));
	static final MethodHandle SHParseDisplayName = FFM.downcall(SHELL32, "SHParseDisplayName", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG));

	/* Calls through a vtable, the function address comes first */
	static final MethodHandle CALL_P = FFM.LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
	static final MethodHandle CALL_PP = FFM.LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
	static final MethodHandle CALL_PPP = FFM.LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG));
	static final MethodHandle CALL_PPIP = FFM.LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG));
	static final MethodHandle CALL_INVOKE = FFM.LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_SHORT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));

	/* IUnknown, IDispatch, ITypeInfo and IBindCtx slots */
	static final int ADD_REF = 1, RELEASE = 2;
	static final int TYPEINFO_GET_IDS_OF_NAMES = 10, TYPEINFO_INVOKE = 11;
	static final int BINDCTX_SET_BIND_OPTIONS = 6, BINDCTX_REGISTER_OBJECT_PARAM = 9;

	static final byte[] IID_IUnknown = guid(0x00000000, 0x0000, 0x0000, 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46);
	static final byte[] IID_IDispatch = guid(0x00020400, 0x0000, 0x0000, 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46);
	static final byte[] IID_IFileSystemBindData = guid(0x01E18D10, 0x4D8B, 0x11D2, 0x85, 0x5D, 0x00, 0x60, 0x08, 0x05, 0x93, 0x67);
	static final byte[] IID_ICoreWebView2EnvironmentOptions = guid(0x2FDE08A8, 0x1E9A, 0x4766, 0x8C, 0x05, 0x95, 0xA9, 0xCE, 0xB9, 0xD1, 0xC5);

	static final int SIZEOF_WIN32_FIND_DATAW = 592;
	static final int SIZEOF_BIND_OPTS = 16;
	static final int STGM_CREATE = 0x1000;
	static final int CC_STDCALL = 4;
	static final short DISPATCH_METHOD = 1;
	static final short VT_I4 = 3, VT_BSTR = 8;
	static final int LOCALE_NEUTRAL = 0;

	/** The live objects by their native address, like the ObjectMap of COMObject. */
	static final Map<Long, ComObject> OBJECTS = new ConcurrentHashMap<>();

	private FFMComCustom() {
	}

	/* ---------------------------------------------------------------- natives of COM */

	public static long CreateSwtWebView2Callback(ICoreWebView2SwtCallback handler) {
		if (handler == null) return 0;
		return new SwtWebView2Callback(handler).address;
	}

	public static long CreateSwtWebView2Host(ICoreWebView2SwtHost host) {
		if (host == null) return 0;
		long pTypeInfo = TypeInfoData.create();
		if (pTypeInfo == 0) return 0;
		return new SwtWebView2Host(host, pTypeInfo).address;
	}

	public static long CreateSwtWebView2Options() {
		return new SwtWebView2Options().address;
	}

	/**
	 * An extended SHParseDisplayName that uses a bind context to create a simple PIDL
	 * when the normal creation fails, most likely for a file that does not exist.
	 */
	public static int PathToPIDL(char[] pszName, long[] ppidl) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment name = FFM.copyIn(arena, pszName);
			MemorySegment pidl = FFM.copyIn(arena, ppidl);
			int hr = pathToPIDL(arena, name.address(), pidl.address());
			FFM.copyOut(pidl, ppidl);
			FFM.checkCallbackException();
			return hr;
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static int pathToPIDL(Arena arena, long pszName, long ppidl) throws Throwable {
		if (ppidl == 0) return E_FAIL;
		FFM.segment(ppidl, PTR).set(JAVA_LONG, 0, 0);

		MemorySegment sfgao = arena.allocate(JAVA_INT);
		int hr = (int) SHParseDisplayName.invokeExact(pszName, 0L, ppidl, 0, sfgao.address());
		if (hr == S_OK) return hr;

		FileSysBindData pfsbd = new FileSysBindData();
		if (pfsbd.address == 0) return E_OUTOFMEMORY;
		pfsbd.setFindData(arena.allocate(SIZEOF_WIN32_FIND_DATAW).address());

		MemorySegment ppbc = arena.allocate(JAVA_LONG);
		hr = (int) CreateBindCtx.invokeExact(0, ppbc.address());
		if (hr == S_OK) {
			long pbc = ppbc.get(JAVA_LONG, 0);
			MemorySegment bo = arena.allocate(SIZEOF_BIND_OPTS);
			bo.set(JAVA_INT, 0, SIZEOF_BIND_OPTS);
			bo.set(JAVA_INT, 8, STGM_CREATE);
			hr = (int) CALL_PP.invokeExact(function(pbc, BINDCTX_SET_BIND_OPTIONS), pbc, bo.address());
			if (hr == S_OK) {
				MemorySegment key = arena.allocateFrom("File System Bind Data", StandardCharsets.UTF_16LE);
				hr = (int) CALL_PPP.invokeExact(function(pbc, BINDCTX_REGISTER_OBJECT_PARAM), pbc, key.address(), pfsbd.address);
				if (hr == S_OK) {
					sfgao.set(JAVA_INT, 0, 0);
					hr = (int) SHParseDisplayName.invokeExact(pszName, pbc, ppidl, 0, sfgao.address());
				}
			}
			comRelease(pbc);
		}
		pfsbd.release();
		return hr;
	}

	/* ---------------------------------------------------------------- COM objects */

	/** A native object whose first field is its vtable pointer, like a C++ object with virtual methods. */
	abstract static class ComObject {
		final long address;
		int refCount = 1;

		ComObject(long vtable) {
			long block = alloc(PTR);
			if (block != 0) {
				FFM.segment(block, PTR).set(JAVA_LONG, 0, vtable);
				OBJECTS.put(block, this);
			}
			address = block;
		}

		abstract boolean implementsInterface(MemorySegment riid);

		int addRef() {
			return ++refCount;
		}

		int release() {
			int rc = --refCount;
			if (rc == 0) delete();
			return rc;
		}

		/** The destructor. */
		void dispose() {
		}

		final void delete() {
			OBJECTS.remove(address);
			try {
				dispose();
			} finally {
				free(address);
			}
		}
	}

	static <T extends ComObject> T object(long self, Class<T> type) {
		ComObject object = OBJECTS.get(self);
		return type.isInstance(object) ? type.cast(object) : null;
	}

	static int QueryInterface(long self, long riid, long ppv) {
		try {
			FFM.segment(ppv, PTR).set(JAVA_LONG, 0, 0);
			ComObject object = OBJECTS.get(self);
			if (object == null) return E_FAIL;
			if (object.implementsInterface(FFM.segment(riid, 16))) {
				FFM.segment(ppv, PTR).set(JAVA_LONG, 0, self);
				object.addRef();
				return S_OK;
			}
			return E_NOINTERFACE;
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	static int AddRef(long self) {
		try {
			ComObject object = OBJECTS.get(self);
			return object == null ? 0 : object.addRef();
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return 0;
		}
	}

	static int Release(long self) {
		try {
			ComObject object = OBJECTS.get(self);
			return object == null ? 0 : object.release();
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return 0;
		}
	}

	/** Whether JNI would have an exception pending on this thread, which makes the C++ glue return early. */
	static boolean exceptionPending() {
		return FFM.pendingException != null && FFM.pendingThread == Thread.currentThread();
	}

	/* ---------------------------------------------------------------- CFileSysBindData */

	/** IFileSystemBindData, with an interlocked reference count like the C++ class. */
	static final class FileSysBindData extends ComObject {
		static final long VTABLE = vtable(
			"SetFindData", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
			"GetFindData", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));

		final AtomicInteger interlockedCount = new AtomicInteger(1);
		final byte[] findData = new byte[SIZEOF_WIN32_FIND_DATAW];

		FileSysBindData() {
			super(VTABLE);
		}

		@Override
		boolean implementsInterface(MemorySegment riid) {
			return is(riid, IID_IUnknown) || is(riid, IID_IFileSystemBindData);
		}

		@Override
		int addRef() {
			return interlockedCount.incrementAndGet();
		}

		@Override
		int release() {
			int rc = interlockedCount.decrementAndGet();
			if (rc == 0) delete();
			return rc;
		}

		void setFindData(long pfd) {
			MemorySegment.copy(FFM.segment(pfd, SIZEOF_WIN32_FIND_DATAW), JAVA_BYTE, 0, findData, 0, SIZEOF_WIN32_FIND_DATAW);
		}
	}

	static int SetFindData(long self, long pfd) {
		try {
			FileSysBindData object = object(self, FileSysBindData.class);
			if (object == null) return E_FAIL;
			object.setFindData(pfd);
			return S_OK;
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	static int GetFindData(long self, long pfd) {
		try {
			FileSysBindData object = object(self, FileSysBindData.class);
			if (object == null) return E_FAIL;
			MemorySegment.copy(object.findData, 0, FFM.segment(pfd, SIZEOF_WIN32_FIND_DATAW), JAVA_BYTE, 0, SIZEOF_WIN32_FIND_DATAW);
			return S_OK;
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	/* ---------------------------------------------------------------- SwtWebView2Callback */

	/** Stand-in for all WebView2 *EventHandler and *CompletedHandler interfaces. */
	static final class SwtWebView2Callback extends ComObject {
		static final long VTABLE = vtable(
			"CallbackInvoke", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG));

		final ICoreWebView2SwtCallback callback;

		SwtWebView2Callback(ICoreWebView2SwtCallback callback) {
			super(VTABLE);
			this.callback = callback;
		}

		@Override
		boolean implementsInterface(MemorySegment riid) {
			return is(riid, IID_IUnknown);
		}
	}

	static int CallbackInvoke(long self, long arg0, long arg1) {
		SwtWebView2Callback object = object(self, SwtWebView2Callback.class);
		if (object == null || exceptionPending()) return E_FAIL;
		try {
			return object.callback.Invoke(arg0, arg1);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	/* ---------------------------------------------------------------- SwtWebView2Host */

	/** The host object exposed to WebView2 via AddHostObjectToScript, an IDispatch with one method, CallJava. */
	static final class SwtWebView2Host extends ComObject {
		static final long VTABLE = vtable(
			"GetTypeInfoCount", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
			"GetTypeInfo", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG),
			"GetIDsOfNames", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG),
			"DispatchInvoke", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_SHORT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG),
			"CallJava", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));

		final ICoreWebView2SwtHost host;
		final long pTypeInfo;

		SwtWebView2Host(ICoreWebView2SwtHost host, long pTypeInfo) {
			super(VTABLE);
			this.host = host;
			this.pTypeInfo = pTypeInfo;
			if (address == 0) dispose();
		}

		@Override
		boolean implementsInterface(MemorySegment riid) {
			return is(riid, IID_IUnknown) || is(riid, IID_IDispatch);
		}

		@Override
		void dispose() {
			comRelease(pTypeInfo);
		}
	}

	/**
	 * The type info CreateDispTypeInfo builds from a static description of CallJava. It does not
	 * support parameters, so they are described but only the method is found by name.
	 */
	static final class TypeInfoData {
		static final MemorySegment INTERFACE = describe();

		static MemorySegment describe() {
			Arena arena = Arena.global();
			String[] names = {"index", "token", "args"};
			short[] types = {VT_I4, VT_BSTR, VT_BSTR};
			MemorySegment params = arena.allocate(16L * names.length, 8);
			for (int i = 0; i < names.length; i++) {
				params.set(JAVA_LONG, 16L * i, arena.allocateFrom(names[i], StandardCharsets.UTF_16LE).address());
				params.set(JAVA_SHORT, 16L * i + 8, types[i]);
			}
			MemorySegment method = arena.allocate(40, 8);
			method.set(JAVA_LONG, 0, arena.allocateFrom("CallJava", StandardCharsets.UTF_16LE).address());
			method.set(JAVA_LONG, 8, params.address());
			method.set(JAVA_INT, 16, 1); // dispid
			method.set(JAVA_INT, 20, 7); // iMeth, the vtable slot of CallJava
			method.set(JAVA_INT, 24, CC_STDCALL);
			method.set(JAVA_INT, 28, names.length);
			method.set(JAVA_SHORT, 32, DISPATCH_METHOD);
			method.set(JAVA_SHORT, 34, VT_BSTR);
			MemorySegment iface = arena.allocate(16, 8);
			iface.set(JAVA_LONG, 0, method.address());
			iface.set(JAVA_INT, 8, 1);
			return iface;
		}

		static long create() {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment ppTypeInfo = arena.allocate(JAVA_LONG);
				int hr = (int) CreateDispTypeInfo.invokeExact(INTERFACE.address(), LOCALE_NEUTRAL, ppTypeInfo.address());
				long pTypeInfo = ppTypeInfo.get(JAVA_LONG, 0);
				if (hr != S_OK) {
					if (pTypeInfo != 0) comRelease(pTypeInfo);
					return 0;
				}
				return pTypeInfo;
			} catch (Throwable t) {
				throw FFM.rethrow(t);
			}
		}
	}

	static int GetTypeInfoCount(long self, long pctinfo) {
		try {
			FFM.segment(pctinfo, JAVA_INT.byteSize()).set(JAVA_INT, 0, 1);
			return S_OK;
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	static int GetTypeInfo(long self, int iTInfo, int lcid, long ppTInfo) {
		try {
			SwtWebView2Host object = object(self, SwtWebView2Host.class);
			MemorySegment result = FFM.segment(ppTInfo, PTR);
			result.set(JAVA_LONG, 0, 0);
			if (object == null) return E_FAIL;
			if (iTInfo != 0) return DISP_E_BADINDEX;
			comAddRef(object.pTypeInfo);
			result.set(JAVA_LONG, 0, object.pTypeInfo);
			return S_OK;
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	static int GetIDsOfNames(long self, long riid, long rgszNames, int cNames, int lcid, long rgDispId) {
		try {
			SwtWebView2Host object = object(self, SwtWebView2Host.class);
			if (object == null) return E_FAIL;
			long pTypeInfo = object.pTypeInfo;
			return (int) CALL_PPIP.invokeExact(function(pTypeInfo, TYPEINFO_GET_IDS_OF_NAMES), pTypeInfo, rgszNames, cNames, rgDispId);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	static int DispatchInvoke(long self, int dispIdMember, long riid, int lcid, short wFlags, long pDispParams, long pVarResult, long pExcepInfo, long puArgErr) {
		try {
			SwtWebView2Host object = object(self, SwtWebView2Host.class);
			if (object == null) return E_FAIL;
			long pTypeInfo = object.pTypeInfo;
			return (int) CALL_INVOKE.invokeExact(function(pTypeInfo, TYPEINFO_INVOKE), pTypeInfo, self, dispIdMember, wFlags, pDispParams, pVarResult, pExcepInfo, puArgErr);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	static long CallJava(long self, int index, long token, long args) {
		SwtWebView2Host object = object(self, SwtWebView2Host.class);
		if (object == null || exceptionPending()) return 0;
		try {
			return object.host.CallJava(index, token, args);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return 0;
		}
	}

	/* ---------------------------------------------------------------- SwtWebView2Options */

	/** ICoreWebView2EnvironmentOptions, keeping its strings in CoTaskMemAlloc memory like the C++ class. */
	static final class SwtWebView2Options extends ComObject {
		static final long VTABLE = vtable(
			"get_AdditionalBrowserArguments", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
			"put_AdditionalBrowserArguments", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
			"get_Language", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
			"put_Language", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
			"get_TargetCompatibleBrowserVersion", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
			"put_TargetCompatibleBrowserVersion", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
			"get_AllowSingleSignOnUsingOSPrimaryAccount", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG),
			"put_AllowSingleSignOnUsingOSPrimaryAccount", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT));

		/* LPWSTR args, language and version */
		final long[] strings = new long[3];
		int allowSSO;

		SwtWebView2Options() {
			super(VTABLE);
		}

		@Override
		boolean implementsInterface(MemorySegment riid) {
			return is(riid, IID_IUnknown) || is(riid, IID_ICoreWebView2EnvironmentOptions);
		}

		@Override
		void dispose() {
			for (long string : strings) free(string);
		}

		int get(int which, long value) {
			return copyString(strings[which], value);
		}

		int put(int which, long value) {
			free(strings[which]);
			strings[which] = 0;
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment copy = arena.allocate(JAVA_LONG);
				int hr = copyString(value, copy.address());
				strings[which] = copy.get(JAVA_LONG, 0);
				return hr;
			}
		}
	}

	static final int ARGS = 0, LANGUAGE = 1, VERSION = 2;

	static int getString(long self, int which, long value) {
		try {
			SwtWebView2Options object = object(self, SwtWebView2Options.class);
			return object == null ? E_FAIL : object.get(which, value);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	static int putString(long self, int which, long value) {
		try {
			SwtWebView2Options object = object(self, SwtWebView2Options.class);
			return object == null ? E_FAIL : object.put(which, value);
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	static int get_AdditionalBrowserArguments(long self, long value) {
		return getString(self, ARGS, value);
	}

	static int put_AdditionalBrowserArguments(long self, long value) {
		return putString(self, ARGS, value);
	}

	static int get_Language(long self, long value) {
		return getString(self, LANGUAGE, value);
	}

	static int put_Language(long self, long value) {
		return putString(self, LANGUAGE, value);
	}

	static int get_TargetCompatibleBrowserVersion(long self, long value) {
		return getString(self, VERSION, value);
	}

	static int put_TargetCompatibleBrowserVersion(long self, long value) {
		return putString(self, VERSION, value);
	}

	static int get_AllowSingleSignOnUsingOSPrimaryAccount(long self, long allow) {
		try {
			SwtWebView2Options object = object(self, SwtWebView2Options.class);
			if (object == null) return E_FAIL;
			FFM.segment(allow, JAVA_INT.byteSize()).set(JAVA_INT, 0, object.allowSSO);
			return S_OK;
		} catch (Throwable t) {
			FFM.callbackFailed(t);
			return E_FAIL;
		}
	}

	static int put_AllowSingleSignOnUsingOSPrimaryAccount(long self, int allow) {
		SwtWebView2Options object = object(self, SwtWebView2Options.class);
		if (object == null) return E_FAIL;
		object.allowSSO = allow;
		return S_OK;
	}

	/** Copies a wide string into CoTaskMemAlloc memory, or stores NULL for NULL. */
	static int copyString(long pszSrc, long ppszDest) {
		MemorySegment dest = FFM.segment(ppszDest, PTR);
		if (pszSrc == 0) {
			dest.set(JAVA_LONG, 0, 0);
			return S_OK;
		}
		MemorySegment src = MemorySegment.ofAddress(pszSrc).reinterpret(Long.MAX_VALUE);
		long length = 0;
		while (src.get(JAVA_CHAR, length * 2) != 0) length++;
		long cbSize = (length + 1) * 2;
		long copy = alloc(cbSize);
		dest.set(JAVA_LONG, 0, copy);
		if (copy == 0) return E_OUTOFMEMORY;
		MemorySegment.copy(src, 0, FFM.segment(copy, cbSize), 0, cbSize);
		return S_OK;
	}

	/* ---------------------------------------------------------------- helpers */

	/** Builds a vtable of IUnknown followed by the given methods, as name and descriptor pairs, all static in this class. */
	static long vtable(Object... methods) {
		Object[] all = new Object[6 + methods.length];
		all[0] = "QueryInterface";
		all[1] = FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG);
		all[2] = "AddRef";
		all[3] = FunctionDescriptor.of(JAVA_INT, JAVA_LONG);
		all[4] = "Release";
		all[5] = FunctionDescriptor.of(JAVA_INT, JAVA_LONG);
		System.arraycopy(methods, 0, all, 6, methods.length);
		int count = all.length / 2;
		MemorySegment vtable = Arena.global().allocate(PTR * count, PTR);
		try {
			for (int i = 0; i < count; i++) {
				String name = (String) all[2 * i];
				FunctionDescriptor descriptor = (FunctionDescriptor) all[2 * i + 1];
				MethodHandle target = LOOKUP.findStatic(FFMComCustom.class, name, descriptor.toMethodType());
				vtable.setAtIndex(JAVA_LONG, i, FFM.LINKER.upcallStub(target, descriptor, Arena.global()).address());
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return vtable.address();
	}

	/** The function in slot <code>index</code> of the vtable of the COM object at <code>object</code>. */
	static MemorySegment function(long object, int index) {
		long vtable = FFM.segment(object, PTR).get(JAVA_LONG, 0);
		return MemorySegment.ofAddress(FFM.segment(vtable + PTR * index, PTR).get(JAVA_LONG, 0));
	}

	static int comAddRef(long object) throws Throwable {
		return (int) CALL_P.invokeExact(function(object, ADD_REF), object);
	}

	static int comRelease(long object) {
		try {
			return (int) CALL_P.invokeExact(function(object, RELEASE), object);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static long alloc(long size) {
		try {
			return (long) CoTaskMemAlloc.invokeExact(size);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static void free(long pointer) {
		try {
			CoTaskMemFree.invokeExact(pointer);
		} catch (Throwable t) {
			throw FFM.rethrow(t);
		}
	}

	static boolean is(MemorySegment riid, byte[] iid) {
		return MemorySegment.mismatch(riid, 0, 16, MemorySegment.ofArray(iid), 0, 16) == -1;
	}

	/** The in-memory layout of a GUID on little endian Windows. */
	static byte[] guid(int data1, int data2, int data3, int... data4) {
		byte[] guid = new byte[16];
		MemorySegment segment = MemorySegment.ofArray(guid);
		segment.set(JAVA_INT_UNALIGNED, 0, data1);
		segment.set(JAVA_SHORT_UNALIGNED, 4, (short) data2);
		segment.set(JAVA_SHORT_UNALIGNED, 6, (short) data3);
		for (int i = 0; i < 8; i++) guid[8 + i] = (byte) data4[i];
		return guid;
	}
}
