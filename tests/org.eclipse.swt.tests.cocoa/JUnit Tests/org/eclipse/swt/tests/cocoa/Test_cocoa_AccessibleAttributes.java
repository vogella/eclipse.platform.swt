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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.Accessible;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.internal.cocoa.NSNumber;
import org.eclipse.swt.internal.cocoa.NSObject;
import org.eclipse.swt.internal.cocoa.NSString;
import org.eclipse.swt.internal.cocoa.OS;
import org.eclipse.swt.internal.cocoa.id;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Queries accessibility attributes of Table, Tree and Text the way VoiceOver
 * does. Every attribute is queried with the pointer-identical NSString
 * constant and with a separate, string-equal NSString instance; both must give
 * the same answer.
 */
public class Test_cocoa_AccessibleAttributes {

	private Shell shell;

	@BeforeEach
	public void setUp() {
		shell = new Shell(Display.getDefault());
	}

	@AfterEach
	public void tearDown() {
		shell.dispose();
	}

	private static Map<String, NSString> attributes() {
		Map<String, NSString> m = new LinkedHashMap<>();
		m.put("role", OS.NSAccessibilityRoleAttribute);
		m.put("subrole", OS.NSAccessibilitySubroleAttribute);
		m.put("roleDescription", OS.NSAccessibilityRoleDescriptionAttribute);
		m.put("enabled", OS.NSAccessibilityEnabledAttribute);
		m.put("focused", OS.NSAccessibilityFocusedAttribute);
		m.put("help", OS.NSAccessibilityHelpAttribute);
		m.put("title", OS.NSAccessibilityTitleAttribute);
		m.put("description", OS.NSAccessibilityDescriptionAttribute);
		m.put("value", OS.NSAccessibilityValueAttribute);
		m.put("parent", OS.NSAccessibilityParentAttribute);
		m.put("children", OS.NSAccessibilityChildrenAttribute);
		m.put("window", OS.NSAccessibilityWindowAttribute);
		m.put("topLevelUIElement", OS.NSAccessibilityTopLevelUIElementAttribute);
		m.put("position", OS.NSAccessibilityPositionAttribute);
		m.put("size", OS.NSAccessibilitySizeAttribute);
		m.put("numberOfCharacters", OS.NSAccessibilityNumberOfCharactersAttribute);
		m.put("selectedText", OS.NSAccessibilitySelectedTextAttribute);
		m.put("selectedTextRange", OS.NSAccessibilitySelectedTextRangeAttribute);
		m.put("columns", OS.NSAccessibilityColumnsAttribute);
		m.put("rows", OS.NSAccessibilityRowsAttribute);
		return m;
	}

	private static String describe(id value) {
		if (value == null) {
			return null;
		}
		return new NSObject(value.id).description().getString();
	}

	private static void prepare(Control control, int role) {
		Accessible accessible = control.getAccessible();
		accessible.addAccessibleListener(new AccessibleAdapter() {
			@Override
			public void getName(AccessibleEvent e) {
				e.result = "name";
			}

			@Override
			public void getDescription(AccessibleEvent e) {
				e.result = "description";
			}

			@Override
			public void getHelp(AccessibleEvent e) {
				e.result = "help";
			}
		});
		accessible.addAccessibleControlListener(new AccessibleControlAdapter() {
			@Override
			public void getRole(AccessibleControlEvent e) {
				e.detail = role;
			}
		});
	}

	private void checkBothNameForms(Control control, int role, String roleName) {
		shell.open();
		Accessible accessible = control.getAccessible();
		Map<String, NSString> attributes = attributes();
		for (Map.Entry<String, NSString> entry : attributes.entrySet()) {
			NSString pointerEqual = entry.getValue();
			// A different NSString instance holding the same characters
			NSString stringEqual = NSString.stringWith(pointerEqual.getString());
			assertTrue(pointerEqual.id != stringEqual.id, "need a distinct NSString instance");
			assertTrue(pointerEqual.isEqualToString(stringEqual));
			String name = entry.getKey();
			String byPointer = describe(accessible.internal_accessibilityAttributeValue(pointerEqual, ACC.CHILDID_SELF));
			String byString = describe(accessible.internal_accessibilityAttributeValue(stringEqual, ACC.CHILDID_SELF));
			assertEquals(byPointer, byString, name + " differs between pointer-equal and string-equal names");
		}

		assertEquals(roleName, valueOf(accessible, OS.NSAccessibilityRoleAttribute));
		assertEquals(roleName, valueOf(accessible, NSString.stringWith(OS.NSAccessibilityRoleAttribute.getString())));
		assertEquals("description", valueOf(accessible, OS.NSAccessibilityDescriptionAttribute));
		assertEquals("description",
				valueOf(accessible, NSString.stringWith(OS.NSAccessibilityDescriptionAttribute.getString())));
		assertEquals("help", valueOf(accessible, OS.NSAccessibilityHelpAttribute));
		assertEquals("help", valueOf(accessible, NSString.stringWith(OS.NSAccessibilityHelpAttribute.getString())));
		assertEquals("name", valueOf(accessible, OS.NSAccessibilityTitleAttribute));
		assertEquals("name", valueOf(accessible, NSString.stringWith(OS.NSAccessibilityTitleAttribute.getString())));

		id enabled = accessible.internal_accessibilityAttributeValue(
				NSString.stringWith(OS.NSAccessibilityEnabledAttribute.getString()), ACC.CHILDID_SELF);
		assertNotNull(enabled);
		assertTrue(new NSNumber(enabled.id).boolValue());
		assertNotNull(accessible.internal_accessibilityAttributeValue(
				NSString.stringWith(OS.NSAccessibilityRoleDescriptionAttribute.getString()), ACC.CHILDID_SELF));
	}

	private static String valueOf(Accessible accessible, NSString attribute) {
		id value = accessible.internal_accessibilityAttributeValue(attribute, ACC.CHILDID_SELF);
		return value == null ? null : new NSString(value.id).getString();
	}

	@Test
	public void test_table() {
		Table table = new Table(shell, SWT.MULTI | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		for (int i = 0; i < 2; i++) {
			new TableColumn(table, SWT.NONE).setText("Column " + i);
		}
		for (int i = 0; i < 5; i++) {
			new TableItem(table, SWT.NONE).setText(new String[] { "a" + i, "b" + i });
		}
		prepare(table, ACC.ROLE_TABLE);
		checkBothNameForms(table, ACC.ROLE_TABLE, OS.NSAccessibilityTableRole.getString());
	}

	@Test
	public void test_tree() {
		Tree tree = new Tree(shell, SWT.MULTI);
		for (int i = 0; i < 3; i++) {
			TreeItem parent = new TreeItem(tree, SWT.NONE);
			parent.setText("parent " + i);
			new TreeItem(parent, SWT.NONE).setText("child " + i);
		}
		prepare(tree, ACC.ROLE_TREE);
		checkBothNameForms(tree, ACC.ROLE_TREE, OS.NSAccessibilityOutlineRole.getString());
	}

	@Test
	public void test_text() {
		Text text = new Text(shell, SWT.SINGLE);
		text.setText("hello world");
		prepare(text, ACC.ROLE_TEXT);
		checkBothNameForms(text, ACC.ROLE_TEXT, OS.NSAccessibilityTextFieldRole.getString());
	}

	@Test
	public void test_multiLineText() {
		Text text = new Text(shell, SWT.MULTI);
		text.setText("hello\nworld");
		prepare(text, ACC.ROLE_TEXT);
		checkBothNameForms(text, ACC.ROLE_TEXT, OS.NSAccessibilityTextAreaRole.getString());
	}
}
