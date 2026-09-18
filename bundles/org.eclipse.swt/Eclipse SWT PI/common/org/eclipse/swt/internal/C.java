/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal;

public class C extends Platform {

	static {
		exitIfNotLoadable();
		/* FFM: no JNI library needed */ //$NON-NLS-1$
	}

	public static final int PTR_SIZEOF = PTR_sizeof ();

/** @param ptr cast=(void *) */
public static final void free(long ptr) { C_FFM.free(ptr); }
/** @param env cast=(const char *) */
public static final long getenv(byte[] env) { return C_FFM.getenv(env); }
/**
 * @param env cast=(const char *)
 * @param value cast=(const char *)
 */
public static final int setenv(byte[] env, byte[] value, int overwrite) { return C_FFM.setenv(env, value, overwrite); }
public static final long malloc(long size) { return C_FFM.malloc(size); }
/**
 * @param dest cast=(void *)
 * @param src cast=(const void *),flags=no_out critical
 * @param size cast=(size_t)
 */
public static final void memmove(long dest, byte[] src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *)
 * @param src cast=(const void *),flags=no_out critical
 * @param size cast=(size_t)
 */
public static final void memmove(long dest, char[] src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *)
 * @param src cast=(const void *),flags=no_out critical
 * @param size cast=(size_t)
 */
public static final void memmove(long dest, double[] src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *)
 * @param src cast=(const void *),flags=no_out critical
 * @param size cast=(size_t)
 */
public static final void memmove(long dest, float[] src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *)
 * @param src cast=(const void *),flags=no_out critical
 * @param size cast=(size_t)
 */
public static final void memmove(long dest, int[] src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *)
 * @param src cast=(const void *),flags=no_out critical
 * @param size cast=(size_t)
 */
public static final void memmove(long dest, long[] src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *)
 * @param src cast=(const void *),flags=no_out critical
 * @param size cast=(size_t)
 */
public static final void memmove(long dest, short[] src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *),flags=no_in critical
 * @param src cast=(const void *),flags=no_out critical
 * @param size cast=(size_t)
 */
public static final void memmove(byte[] dest, char[] src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *),flags=no_in critical
 * @param src cast=(const void *)
 * @param size cast=(size_t)
 */
public static final void memmove(byte[] dest, long src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *)
 * @param src cast=(const void *)
 * @param size cast=(size_t)
 */
public static final void memmove(long dest, long src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *),flags=no_in critical
 * @param src cast=(const void *)
 * @param size cast=(size_t)
 */
public static final void memmove(char[] dest, long src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *),flags=no_in critical
 * @param src cast=(const void *)
 * @param size cast=(size_t)
 */
public static final void memmove(double[] dest, long src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *),flags=no_in critical
 * @param src cast=(const void *)
 * @param size cast=(size_t)
 */
public static final void memmove(float[] dest, long src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *),flags=no_in critical
 * @param src cast=(const void *)
 * @param size cast=(size_t)
 */
public static final void memmove(int[] dest, byte[] src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *),flags=no_in critical
 * @param src cast=(const void *)
 * @param size cast=(size_t)
 */
public static final void memmove(short[] dest, long src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *),flags=no_in critical
 * @param src cast=(const void *)
 * @param size cast=(size_t)
 */
public static final void memmove(int[] dest, long src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param dest cast=(void *),flags=no_in critical
 * @param src cast=(const void *)
 * @param size cast=(size_t)
 */
public static final void memmove(long[] dest, long src, long size) { C_FFM.memmove(dest, src, size); }
/**
 * @param buffer cast=(void *),flags=critical
 * @param num cast=(size_t)
 */
public static final long memset(long buffer, int c, long num) { return C_FFM.memset(buffer, c, num); }
public static final int PTR_sizeof() { return org.eclipse.swt.internal.ffm.FFMMacros.PTR_sizeof(); }
/** @param s cast=(char *) */
public static final int strlen(long s) { return C_FFM.strlen(s); }
}
