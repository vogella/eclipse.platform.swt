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
package org.eclipse.swt.tools.ffm;

import static org.junit.platform.engine.discovery.DiscoverySelectors.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.junit.platform.engine.*;
import org.junit.platform.engine.TestExecutionResult.*;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;

/**
 * Runs JUnit test classes and writes one line per test with its outcome, so that runs on the JNI and the FFM build can be diffed.
 * Arguments: output file followed by test class names.
 */
public class FFMTestRunner {

	public static void main(String[] args) throws IOException {
		List<DiscoverySelector> selectors = new ArrayList<>();
		for (int i = 1; i < args.length; i++) selectors.add(selectClass(args[i]));
		LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request().selectors(selectors).build();
		Map<String, String> results = new TreeMap<>();
		PrintStream progress = new PrintStream(new FileOutputStream(args[0] + ".progress"), true);
		TestExecutionListener listener = new TestExecutionListener() {
			@Override
			public void executionStarted(TestIdentifier test) {
				if (test.isTest()) progress.println("START " + test.getUniqueId());
			}

			@Override
			public void executionSkipped(TestIdentifier test, String reason) {
				if (test.isTest()) results.put(test.getUniqueId(), "SKIPPED");
			}

			@Override
			public void executionFinished(TestIdentifier test, TestExecutionResult result) {
				if (!test.isTest() && result.getStatus() == Status.SUCCESSFUL) return;
				String outcome = result.getStatus().toString();
				if (result.getStatus() != Status.SUCCESSFUL) {
					outcome += result.getThrowable().map(t -> " " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()).lines().findFirst().orElse("")).orElse("");
				}
				results.put(test.getUniqueId(), outcome);
				progress.println("END   " + outcome);
			}
		};
		Launcher launcher = LauncherFactory.create();
		launcher.execute(request, listener);
		List<String> lines = new ArrayList<>();
		Map<String, Integer> counts = new TreeMap<>();
		results.forEach((id, outcome) -> {
			lines.add(id + "\t" + outcome);
			counts.merge(outcome.split(" ")[0], 1, Integer::sum);
		});
		Files.write(Paths.get(args[0]), lines);
		System.out.println("Results " + counts + " written to " + args[0]);
		System.exit(0);
	}
}
