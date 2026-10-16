/*******************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.cocoa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.dnd.Transfer;
import org.junit.jupiter.api.Test;

/**
 * Characterization test for {@link Transfer#registerType(String)}.
 */
public class Test_cocoa_TransferRegisterType {

	@Test
	public void test_registerTypeReturnsStableIds() {
		String name = "org.eclipse.swt.tests.cocoa.type." + System.nanoTime();
		int id = Transfer.registerType(name);
		assertTrue(id > 0, "type 0 is not used");
		assertEquals(id, Transfer.registerType(name));
		assertEquals(id, Transfer.registerType(new String(name)), "lookup is by value, not identity");
	}

	@Test
	public void test_registerTypeReturnsDistinctIdsForDistinctNames() {
		String prefix = "org.eclipse.swt.tests.cocoa.many." + System.nanoTime() + ".";
		int count = 50; // more than the initial table capacity, to force growth
		int[] ids = new int[count];
		Set<Integer> unique = new HashSet<>();
		for (int i = 0; i < count; i++) {
			ids[i] = Transfer.registerType(prefix + i);
			assertTrue(unique.add(ids[i]), "duplicate id for " + i);
		}
		for (int i = 0; i < count; i++) {
			assertEquals(ids[i], Transfer.registerType(prefix + i));
		}
		assertNotEquals(Transfer.registerType(prefix + "a"), Transfer.registerType(prefix + "b"));
	}

	@Test
	public void test_registerTypeIsCaseSensitive() {
		String name = "org.eclipse.swt.tests.cocoa.Case." + System.nanoTime();
		assertNotEquals(Transfer.registerType(name), Transfer.registerType(name.toLowerCase()));
	}
}
