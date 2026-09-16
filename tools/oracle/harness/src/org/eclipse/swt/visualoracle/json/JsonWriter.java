/*******************************************************************************
 * Copyright (c) 2026 SWT Visual Oracle contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.visualoracle.json;

import java.util.List;
import java.util.Map;

/**
 * Serialises plain Java objects (Map, List, String, Long/Integer/Double,
 * Boolean, null) to pretty-printed JSON. Map iteration order defines member
 * order, so using insertion ordered maps yields deterministic output.
 */
public final class JsonWriter {

	private final StringBuilder out = new StringBuilder();
	private int indent;

	private JsonWriter() {
	}

	/** Returns {@code value} as pretty-printed JSON text. */
	public static String write(Object value) {
		JsonWriter w = new JsonWriter();
		w.writeIndented(value);
		return w.out.toString();
	}

	private void writeIndented(Object value) {
		writeValue(value);
		out.append('\n');
	}

	private void writeValue(Object value) {
		if (value == null) {
			out.append("null");
		} else if (value instanceof String s) {
			writeString(s);
		} else if (value instanceof Boolean b) {
			out.append(b.booleanValue() ? "true" : "false");
		} else if (value instanceof Double d) {
			if (d.isNaN() || d.isInfinite())
				throw new JsonException("cannot serialise " + d + " as JSON");
			out.append(d.toString());
		} else if (value instanceof Number n) {
			out.append(n.toString());
		} else if (value instanceof Map<?, ?> map) {
			writeObject(map);
		} else if (value instanceof List<?> list) {
			writeArray(list);
		} else {
			throw new JsonException("unsupported JSON value type: " + value.getClass().getName());
		}
	}

	private void writeObject(Map<?, ?> map) {
		if (map.isEmpty()) {
			out.append("{}");
			return;
		}
		open('{');
		boolean first = true;
		for (Map.Entry<?, ?> e : map.entrySet()) {
			if (!first)
				out.append(',');
			first = false;
			newline();
			writeString(String.valueOf(e.getKey()));
			out.append(": ");
			writeValue(e.getValue());
		}
		close('}');
	}

	private void writeArray(List<?> list) {
		if (list.isEmpty()) {
			out.append("[]");
			return;
		}
		open('[');
		boolean first = true;
		for (Object item : list) {
			if (!first)
				out.append(',');
			first = false;
			newline();
			writeValue(item);
		}
		close(']');
	}

	private void open(char c) {
		out.append(c);
		indent++;
	}

	private void close(char c) {
		indent--;
		newline();
		out.append(c);
	}

	private void newline() {
		out.append('\n');
		out.append("  ".repeat(indent));
	}

	private void writeString(String s) {
		out.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"': out.append("\\\""); break;
				case '\\': out.append("\\\\"); break;
				case '\b': out.append("\\b"); break;
				case '\f': out.append("\\f"); break;
				case '\n': out.append("\\n"); break;
				case '\r': out.append("\\r"); break;
				case '\t': out.append("\\t"); break;
				default:
					if (c < 0x20)
						out.append(String.format("\\u%04x", (int) c));
					else
						out.append(c);
			}
		}
		out.append('"');
	}
}
