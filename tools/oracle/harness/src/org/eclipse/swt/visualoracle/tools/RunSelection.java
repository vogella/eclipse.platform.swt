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
package org.eclipse.swt.visualoracle.tools;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;
import org.eclipse.swt.visualoracle.spi.Tag;

/**
 * Specimen selection for the {@code run} verb: every flag of one kind unions,
 * different kinds intersect. Pure catalogue filtering, no processes touched,
 * so the selftest can prove that filters select what they claim.
 *
 * Selection is deterministic: the result is ordered by specimen id.
 */
public final class RunSelection {

	private static final String TAG_NAMES = tagNames();

	private static String tagNames() {
		StringBuilder names = new StringBuilder();
		for (Tag tag : Tag.values()) {
			if (names.length() > 0)
				names.append(", ");
			names.append(tag.name());
		}
		return names.toString();
	}

	private final Set<String> prefixes;
	private final Set<String> families;
	private final Set<String> tags;

	/**
	 * @param prefixes specimen id prefixes, exact id included
	 * @param families widget family names (the first dot-separated segment)
	 * @param tags tag names, validated against {@link Tag}
	 */
	public RunSelection(Set<String> prefixes, Set<String> families, Set<String> tags) {
		this.prefixes = Set.copyOf(prefixes);
		this.families = Set.copyOf(families);
		for (String tag : Set.copyOf(tags)) {
			if (tagByName(tag) == null)
				throw new IllegalArgumentException("unknown tag '" + tag + "'; valid tags: "
						+ TAG_NAMES);
		}
		this.tags = Set.copyOf(tags);
	}

	public static Tag tagByName(String name) {
		try {
			return Tag.valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public Set<String> prefixes() {
		return prefixes;
	}

	public Set<String> families() {
		return families;
	}

	public Set<String> tags() {
		return tags;
	}

	public boolean isEmptyCriteria() {
		return prefixes.isEmpty() && families.isEmpty() && tags.isEmpty();
	}

	/**
	 * Applies this selection to the catalog. Unknown family names are not an
	 * error here (they select nothing); callers decide whether an empty
	 * selection is a usage failure.
	 */
	public List<Specimen> applyTo(SpecimenCatalog catalog) {
		List<Specimen> matches = new ArrayList<>();
		for (Specimen specimen : catalog.all()) {
			if (matches(specimen))
				matches.add(specimen);
		}
		matches.sort((a, b) -> a.id().compareTo(b.id()));
		return List.copyOf(matches);
	}

	public boolean matches(Specimen specimen) {
		if (!prefixes.isEmpty() && prefixes.stream().noneMatch(specimen.id()::startsWith))
			return false;
		if (!families.isEmpty() && !families.contains(familyOf(specimen.id())))
			return false;
		if (!tags.isEmpty()) {
			Set<String> specimenTags = new LinkedHashSet<>();
			for (Tag tag : specimen.tags())
				specimenTags.add(tag.name());
			if (tags.stream().noneMatch(specimenTags::contains))
				return false;
		}
		return true;
	}

	/** The widget family of a specimen id: its first dot-separated segment. */
	public static String familyOf(String specimenId) {
		int dot = specimenId.indexOf('.');
		return dot < 0 ? specimenId : specimenId.substring(0, dot);
	}
}
