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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict JSON parser producing plain Java objects: Map (insertion ordered),
 * List, String, Long or Double, Boolean, null.
 *
 * Rejects duplicate keys, trailing content and malformed input; the result
 * JSON is a contract, so anything ambiguous must fail loudly rather than be
 * guessed at.
 */
public final class JsonParser {

	private final String text;
	private int pos;

	private JsonParser(String text) {
		this.text = text;
	}

	/** Parses {@code text} completely; throws {@link JsonException} on any problem. */
	public static Object parse(String text) {
		return new JsonParser(text).parseRoot();
	}

	private Object parseRoot() {
		skipWhitespace();
		Object value = parseValue();
		skipWhitespace();
		if (peek() != -1)
			throw err("trailing content after JSON value");
		return value;
	}

	private int peek() {
		return pos < text.length() ? text.charAt(pos) : -1;
	}

	private int read() {
		return pos < text.length() ? text.charAt(pos++) : -1;
	}

	private void expect(char expected) {
		int c = read();
		if (c != expected)
			throw err("expected '" + expected + "', found '" + describe(c) + "'");
	}

	private void expectLiteral(String rest) {
		for (char expected : rest.toCharArray())
			expect(expected);
	}

	private void skipWhitespace() {
		while (pos < text.length() && Character.isWhitespace(text.charAt(pos)))
			pos++;
	}

	private JsonException err(String message) {
		return new JsonException(message + " at offset " + pos);
	}

	private static String describe(int c) {
		return c == -1 ? "<eof>" : Character.toString((char) c);
	}

	private Object parseValue() {
		skipWhitespace();
		int c = read();
		switch (c) {
			case '{':
				return parseObject();
			case '[':
				return parseArray();
			case '"':
				return parseString();
			case 't':
				expectLiteral("rue");
				return Boolean.TRUE;
			case 'f':
				expectLiteral("alse");
				return Boolean.FALSE;
			case 'n':
				expectLiteral("ull");
				return null;
			default:
				if (c == '-' || (c >= '0' && c <= '9'))
					return parseNumber((char) c);
				throw err("unexpected character '" + describe(c) + "'");
		}
	}

	private Map<String, Object> parseObject() {
		Map<String, Object> map = new LinkedHashMap<>();
		skipWhitespace();
		int c = read();
		if (c == '}')
			return map;
		if (c != '"')
			throw err("expected object key, found '" + describe(c) + "'");
		while (true) {
			String key = parseString();
			if (map.containsKey(key))
				throw err("duplicate object key '" + key + "'");
			skipWhitespace();
			expect(':');
			map.put(key, parseValue());
			skipWhitespace();
			c = read();
			if (c == ',') {
				skipWhitespace();
				if (read() != '"')
					throw err("expected object key after ','");
				continue;
			}
			if (c == '}')
				return map;
			throw err("expected ',' or '}' in object, found '" + describe(c) + "'");
		}
	}

	private List<Object> parseArray() {
		List<Object> list = new ArrayList<>();
		skipWhitespace();
		int c = read();
		if (c == ']')
			return list;
		pos--; // first value starts at the char just read
		while (true) {
			list.add(parseValue());
			skipWhitespace();
			c = read();
			if (c == ',')
				continue;
			if (c == ']')
				return list;
			throw err("expected ',' or ']' in array, found '" + describe(c) + "'");
		}
	}

	private String parseString() {
		StringBuilder sb = new StringBuilder();
		while (true) {
			int c = read();
			switch (c) {
				case '"':
					return sb.toString();
				case '\\': {
					int e = read();
					switch (e) {
						case '"': sb.append('"'); break;
						case '\\': sb.append('\\'); break;
						case '/': sb.append('/'); break;
						case 'b': sb.append('\b'); break;
						case 'f': sb.append('\f'); break;
						case 'n': sb.append('\n'); break;
						case 'r': sb.append('\r'); break;
						case 't': sb.append('\t'); break;
						case 'u': {
							int v = 0;
							for (int i = 0; i < 4; i++) {
								int d = Character.digit(read(), 16);
								if (d < 0)
									throw err("invalid \\u escape digit");
								v = (v << 4) | d;
							}
							sb.append((char) v);
							break;
						}
						default:
							throw err("invalid escape '\\" + describe(e) + "'");
					}
					break;
				}
				default: {
					if (c == -1)
						throw err("unterminated string");
					if (c < 0x20)
						throw err("unescaped control character in string");
					sb.append((char) c);
				}
			}
		}
	}

	private Number parseNumber(char first) {
		StringBuilder sb = new StringBuilder().append(first);
		boolean integral = true;
		while (true) {
			int c = peek();
			if (c >= '0' && c <= '9') {
				sb.append((char) read());
			} else if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
				if (c == '.' || c == 'e' || c == 'E')
					integral = false;
				sb.append((char) read());
			} else {
				break;
			}
		}
		try {
			if (integral)
				return Long.parseLong(sb.toString());
			return Double.parseDouble(sb.toString());
		} catch (NumberFormatException e) {
			throw err("malformed number '" + sb + "'");
		}
	}
}
