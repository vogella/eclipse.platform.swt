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

import java.io.PrintStream;
import java.util.Arrays;

/**
 * The {@code oracle} command line entry point.
 *
 * Verbs:
 *   selftest   run the end-to-end harness selftest (implemented here)
 *   version    print tool and schema versions
 *   help       print usage
 *   run        render specimens, compare backends, report a verdict
 *   triage     rank the differences of a previous run
 *
 * Exit codes: 0 success, 1 selftest failure or runtime error, 2 usage error.
 * Per-verb exit codes are documented in docs/visual-oracle/CLI.md.
 */
public final class OracleCli {

	public static final String VERSION = "0.1.0";

	private final PrintStream stdout;
	private final PrintStream stderr;

	public static void main(String[] args) {
		System.exit(new OracleCli(System.out, System.err).dispatch(args));
	}

	public OracleCli(PrintStream stdout, PrintStream stderr) {
		this.stdout = stdout;
		this.stderr = stderr;
	}

	/** Dispatches one invocation; returns the process exit code. */
	public int dispatch(String[] args) {
		if (args.length == 0) {
			printUsage(stdout);
			return 2;
		}
		String[] rest = Arrays.copyOfRange(args, 1, args.length);
		switch (args[0]) {
			case "selftest":
				return new SelfTest(stdout).run();
			case "--version":
			case "version":
				stdout.println("oracle-harness " + VERSION + ", result schema v"
						+ org.eclipse.swt.visualoracle.result.RunResult.CURRENT_SCHEMA_VERSION);
				return 0;
			case "help":
			case "-h":
			case "--help":
				printUsage(stdout);
				return 0;
			case "run":
				return new RunVerb(stdout, stderr, rest).dispatch();
			case "triage":
				return new TriageVerb(stdout, stderr, rest).dispatch();
			default:
				stderr.println("oracle: unknown verb '" + args[0] + "'");
				printUsage(stderr);
				return 2;
		}
	}

	private void printUsage(PrintStream out) {
		out.println("usage: oracle <verb> [args]");
		out.println();
		out.println("verbs:");
		out.println("  selftest    prove the pipeline end to end; exits non-zero on any failure");
		out.println("  run         render specimens through two backends and compare; JSON verdict on stdout");
		out.println("  triage      rank the differences of a previous run for attention");
		out.println("  version     print tool and result schema versions");
		out.println("  help        this text");
	}
}
