/*******************************************************************************
 * Copyright (c) 2007, 2012 IBM Corporation and others.
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
package org.eclipse.swt.internal.cocoa;

/**
 * @jniclass flags=no_gen
 */
public class id {

public long id;

public id() {
}

public id(long id) {
	this.id = id;
}

public id(id id) {
	this.id = id != null ? id.id : 0;
}

@Override
public int hashCode() {
	return (int) this.id;
}

@Override
public boolean equals(Object other) {
	return (this.id == ((id)other).id);
}

private static final ClassValue<Long> OBJC_CLASSES = new ClassValue<>() {
	@Override
	protected Long computeValue(Class<?> type) {
		return Long.valueOf(lookupObjcClass(type));
	}
};

private static long lookupObjcClass(Class<?> type) {
	String name = type.getName();
	int index = name.lastIndexOf('.');
	if (index != -1) name = name.substring(index + 1);
	return OS.objc_getClass(name);
}

public long objc_getClass() {
	Class<?> type = getClass();
	long cls = OBJC_CLASSES.get(type).longValue();
	// do not keep a failed lookup, the Objective-C class might be registered later
	if (cls == 0) OBJC_CLASSES.remove(type);
	return cls;
}

@Override
public String toString() {
	return getClass().getName() + "{" + id +  "}";
}
}
