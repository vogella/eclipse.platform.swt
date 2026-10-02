/*******************************************************************************
 * Copyright (c) 2000, 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.cocoa;

public class NSNumberFormatter extends NSFormatter {

public NSNumberFormatter() {
	super();
}

public NSNumberFormatter(long id) {
	super(id);
}

public NSNumberFormatter(id id) {
	super(id);
}

public NSString decimalSeparator() {
	long result = OS.objc_msgSend(this.id, OS.sel_decimalSeparator);
	return result != 0 ? new NSString(result) : null;
}

public void setMaximum(NSNumber maximum) {
	OS.objc_msgSend(this.id, OS.sel_setMaximum_, maximum != null ? maximum.id : 0);
}

public void setMinimum(NSNumber minimum) {
	OS.objc_msgSend(this.id, OS.sel_setMinimum_, minimum != null ? minimum.id : 0);
}

}
