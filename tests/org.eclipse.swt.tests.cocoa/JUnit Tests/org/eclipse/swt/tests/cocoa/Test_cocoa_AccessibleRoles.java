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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.Accessible;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.internal.cocoa.NSArray;
import org.eclipse.swt.internal.cocoa.NSString;
import org.eclipse.swt.internal.cocoa.OS;
import org.eclipse.swt.internal.cocoa.id;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Characterization test for the mapping of ACC.ROLE_* values to the role,
 * subrole and role description that VoiceOver sees, and for the stability of
 * the child delegates handed out for child IDs.
 */
public class Test_cocoa_AccessibleRoles {

	private Shell shell;
	private Composite composite;
	private Accessible accessible;
	private int currentRole;

	@BeforeEach
	public void setUp() {
		shell = new Shell(Display.getDefault());
		composite = new Composite(shell, 0);
		accessible = composite.getAccessible();
		accessible.addAccessibleControlListener(new AccessibleControlAdapter() {
			@Override
			public void getRole(AccessibleControlEvent e) {
				e.detail = currentRole;
			}
		});
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
	}

	private static String[] role(NSString role, NSString subrole) {
		return new String[] { role.getString(), subrole == null ? null : subrole.getString() };
	}

	private static Map<Integer, String[]> expectedRoles() {
		Map<Integer, String[]> m = new HashMap<>();
		m.put(ACC.ROLE_CLIENT_AREA, role(OS.NSAccessibilityGroupRole, null));
		m.put(ACC.ROLE_WINDOW, role(OS.NSAccessibilityWindowRole, null));
		m.put(ACC.ROLE_MENUBAR, role(OS.NSAccessibilityMenuBarRole, null));
		m.put(ACC.ROLE_MENU, role(OS.NSAccessibilityMenuRole, null));
		m.put(ACC.ROLE_MENUITEM, role(OS.NSAccessibilityMenuItemRole, null));
		m.put(ACC.ROLE_SEPARATOR, role(OS.NSAccessibilitySplitterRole, null));
		m.put(ACC.ROLE_TOOLTIP, role(OS.NSAccessibilityHelpTagRole, null));
		m.put(ACC.ROLE_SCROLLBAR, role(OS.NSAccessibilityScrollBarRole, null));
		m.put(ACC.ROLE_DIALOG, role(OS.NSAccessibilityWindowRole, OS.NSAccessibilityDialogSubrole));
		m.put(ACC.ROLE_LABEL, role(OS.NSAccessibilityStaticTextRole, null));
		m.put(ACC.ROLE_PUSHBUTTON, role(OS.NSAccessibilityButtonRole, null));
		m.put(ACC.ROLE_CHECKBUTTON, role(OS.NSAccessibilityCheckBoxRole, null));
		m.put(ACC.ROLE_RADIOBUTTON, role(OS.NSAccessibilityRadioButtonRole, null));
		m.put(ACC.ROLE_SPLITBUTTON, role(OS.NSAccessibilityMenuButtonRole, null));
		m.put(ACC.ROLE_COMBOBOX, role(OS.NSAccessibilityComboBoxRole, null));
		// the composite has no SWT.MULTI style
		m.put(ACC.ROLE_TEXT, role(OS.NSAccessibilityTextFieldRole, null));
		m.put(ACC.ROLE_TOOLBAR, role(OS.NSAccessibilityToolbarRole, null));
		m.put(ACC.ROLE_LIST, role(OS.NSAccessibilityOutlineRole, null));
		m.put(ACC.ROLE_LISTITEM, role(OS.NSAccessibilityStaticTextRole, null));
		m.put(ACC.ROLE_COLUMN, role(OS.NSAccessibilityColumnRole, null));
		m.put(ACC.ROLE_ROW, role(OS.NSAccessibilityRowRole, OS.NSAccessibilityTableRowSubrole));
		m.put(ACC.ROLE_TABLE, role(OS.NSAccessibilityTableRole, null));
		m.put(ACC.ROLE_TABLECELL, role(OS.NSAccessibilityStaticTextRole, null));
		m.put(ACC.ROLE_TABLECOLUMNHEADER, role(OS.NSAccessibilityGroupRole, null));
		m.put(ACC.ROLE_TABLEROWHEADER, role(OS.NSAccessibilityGroupRole, null));
		m.put(ACC.ROLE_TREE, role(OS.NSAccessibilityOutlineRole, null));
		m.put(ACC.ROLE_TREEITEM, role(OS.NSAccessibilityOutlineRole, OS.NSAccessibilityOutlineRowSubrole));
		m.put(ACC.ROLE_TABFOLDER, role(OS.NSAccessibilityTabGroupRole, null));
		m.put(ACC.ROLE_TABITEM, role(OS.NSAccessibilityRadioButtonRole, null));
		m.put(ACC.ROLE_PROGRESSBAR, role(OS.NSAccessibilityProgressIndicatorRole, null));
		m.put(ACC.ROLE_SLIDER, role(OS.NSAccessibilitySliderRole, null));
		m.put(ACC.ROLE_LINK, role(OS.NSAccessibilityLinkRole, null));
		m.put(ACC.ROLE_CANVAS, role(OS.NSAccessibilityGroupRole, null));
		m.put(ACC.ROLE_GRAPHIC, role(OS.NSAccessibilityImageRole, null));
		m.put(ACC.ROLE_GROUP, role(OS.NSAccessibilityGroupRole, null));
		m.put(ACC.ROLE_CHECKMENUITEM, role(OS.NSAccessibilityMenuButtonRole, null));
		m.put(ACC.ROLE_RADIOMENUITEM, role(OS.NSAccessibilityMenuButtonRole, null));
		for (int r : new int[] { ACC.ROLE_FOOTER, ACC.ROLE_HEADER, ACC.ROLE_FORM, ACC.ROLE_PAGE, ACC.ROLE_SECTION }) {
			m.put(r, role(OS.NSAccessibilityGroupRole, null));
		}
		m.put(ACC.ROLE_HEADING, role(OS.NSAccessibilityTextAreaRole, null));
		m.put(ACC.ROLE_PARAGRAPH, role(OS.NSAccessibilityTextAreaRole, null));
		for (int r : new int[] { ACC.ROLE_CLOCK, ACC.ROLE_DATETIME, ACC.ROLE_CALENDAR, ACC.ROLE_ALERT,
				ACC.ROLE_ANIMATION, ACC.ROLE_DOCUMENT, ACC.ROLE_SPINBUTTON, ACC.ROLE_STATUSBAR }) {
			m.put(r, role(OS.NSAccessibilityUnknownRole, null));
		}
		return m;
	}

	private static Set<Integer> allRoleConstants() throws IllegalAccessException {
		Set<Integer> roles = new HashSet<>();
		for (Field f : ACC.class.getFields()) {
			if (f.getName().startsWith("ROLE_") && Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
				roles.add(f.getInt(null));
			}
		}
		return roles;
	}

	private static String string(id value) {
		return value == null ? null : new NSString(value.id).getString();
	}

	@Test
	public void test_everyRoleConstantHasAMapping() throws Exception {
		assertEquals(allRoleConstants(), expectedRoles().keySet());
	}

	@Test
	public void test_roleSubroleAndRoleDescription() throws Exception {
		for (Map.Entry<Integer, String[]> entry : expectedRoles().entrySet()) {
			currentRole = entry.getKey();
			String[] expected = entry.getValue();
			String name = "ACC role 0x" + Integer.toHexString(currentRole);
			for (int pass = 0; pass < 2; pass++) { // repeated queries must give the same answer
				assertEquals(expected[0], string(accessible.internal_accessibilityAttributeValue(
						OS.NSAccessibilityRoleAttribute, ACC.CHILDID_SELF)), name + " role");
				assertEquals(expected[1], string(accessible.internal_accessibilityAttributeValue(
						OS.NSAccessibilitySubroleAttribute, ACC.CHILDID_SELF)), name + " subrole");
				String description = string(accessible.internal_accessibilityAttributeValue(
						OS.NSAccessibilityRoleDescriptionAttribute, ACC.CHILDID_SELF));
				assertNotNull(description, name + " role description");
				String expectedDescription;
				if (currentRole == ACC.ROLE_TABITEM) {
					expectedDescription = new NSString(
							OS.NSAccessibilityRoleDescription(NSString.stringWith("AXTab").id, 0)).getString();
				} else {
					NSString role = NSString.stringWith(expected[0]);
					NSString subrole = expected[1] == null ? null : NSString.stringWith(expected[1]);
					expectedDescription = new NSString(
							OS.NSAccessibilityRoleDescription(role.id, subrole == null ? 0 : subrole.id))
									.getString();
				}
				assertEquals(expectedDescription, description, name + " role description");
			}
		}
	}

	@Test
	public void test_noRoleListenerAnswerMeansNoRoleOverride() {
		currentRole = -1;
		assertNull(accessible.internal_accessibilityAttributeValue(OS.NSAccessibilityRoleAttribute,
				ACC.CHILDID_SELF));
		assertNull(accessible.internal_accessibilityAttributeValue(OS.NSAccessibilitySubroleAttribute,
				ACC.CHILDID_SELF));
	}

	@Test
	public void test_childIdToDelegateMappingIsStable() {
		accessible.addAccessibleControlListener(new AccessibleControlAdapter() {
			@Override
			public void getChildCount(AccessibleControlEvent e) {
				e.detail = 3;
			}

			@Override
			public void getChildren(AccessibleControlEvent e) {
				e.children = new Object[] { Integer.valueOf(3), Integer.valueOf(1), Integer.valueOf(2) };
			}
		});
		long[] first = childDelegates();
		long[] second = childDelegates();
		assertEquals(3, first.length);
		assertTrue(first[0] != first[1] && first[1] != first[2] && first[0] != first[2],
				"each child ID gets its own delegate");
		assertArrayEquals(first, second, "the same child ID must map to the same delegate");
	}

	private long[] childDelegates() {
		id children = accessible.internal_accessibilityAttributeValue(OS.NSAccessibilityChildrenAttribute,
				ACC.CHILDID_SELF);
		assertNotNull(children);
		NSArray array = new NSArray(children.id);
		long[] ids = new long[(int) array.count()];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = array.objectAtIndex(i).id;
		}
		return ids;
	}
}
