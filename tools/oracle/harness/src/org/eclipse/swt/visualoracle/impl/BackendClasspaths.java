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
package org.eclipse.swt.visualoracle.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Resolves per-backend classpaths and native library paths by delegating to
 * {@code tools/oracle/build.sh}, the single source of truth for backend
 * builds (ADR-002). Each backend links its own SWT classes and natives, so a
 * child process for one backend must never see another backend's classpath;
 * the harness/native split produced here is what keeps the fork's Skija
 * 0.116.3 jars and PR 3231's 0.143.17 jars apart.
 */
public final class BackendClasspaths {

	private static volatile String nativeClasspath;
	private static final java.util.Map<String, String> builtFromSource = new java.util.concurrent.ConcurrentHashMap<>();

	private BackendClasspaths() {
	}

	/** The classpath printed by {@code build.sh <backendId>}; memoized. */
	public static String backendClasspath(String backendId) {
		return switch (backendId) {
			case NativeBackend.ID -> nativeClasspath();
			case SkiaCanvasBackend.ID, SkijaProtoBackend.ID -> runBuild(backendId);
			case NativeBackend.BASELINE_ID, NativeBackend.CANDIDATE_ID ->
				// memoized so every child of a run sees the same build, even if the source changes meanwhile
				builtFromSource.computeIfAbsent(backendId, BackendClasspaths::runBuild);
			default -> throw new IllegalArgumentException("no build recipe for backend '" + backendId + "'");
		};
	}

	/**
	 * The directory whose natives a backend child must load: the worktree's
	 * GTK binaries for native and skia-canvas (the fragment runs on the host
	 * bundle's natives), the fork checkout's binaries for skija-proto.
	 */
	public static Path libraryPathFor(String backendId) {
		return switch (backendId) {
			case NativeBackend.ID, SkiaCanvasBackend.ID ->
				repoRoot().resolve("binaries/org.eclipse.swt.gtk.linux.x86_64");
			case NativeBackend.BASELINE_ID, NativeBackend.CANDIDATE_ID ->
				Path.of(backendClasspath(backendId).split(java.io.File.pathSeparator)[0]).resolveSibling("lib");
			case SkijaProtoBackend.ID -> cacheRoot().resolve(
					"checkouts/prototype-skija/binaries/org.eclipse.swt.gtk.linux.x86_64");
			default -> throw new IllegalArgumentException("no library path known for backend '" + backendId + "'");
		};
	}

	/**
	 * The parent JVM's classpath minus the native backend entries: the harness
	 * classes a child needs so it can load one specific backend's SWT instead
	 * of the parent's. Fails when the split cannot be made unambiguously.
	 */
	public static String harnessClasspath() {
		List<String> nativeEntries = List.of(nativeClasspath().split(java.io.File.pathSeparator));
		String parent = System.getProperty("java.class.path", "");
		List<String> kept = new ArrayList<>();
		for (String entry : parent.split(java.io.File.pathSeparator)) {
			if (entry.isEmpty() || nativeEntries.contains(entry))
				continue;
			kept.add(entry);
		}
		for (String entry : kept) {
			if (Files.isRegularFile(Path.of(entry,
					"org/eclipse/swt/visualoracle/spi/Backend.class")))
				return String.join(java.io.File.pathSeparator, kept);
		}
		throw new IllegalStateException("cannot locate the visual oracle harness classes on the classpath '"
				+ parent + "'; launch through tools/oracle/oracle");
	}

	private static String nativeClasspath() {
		String cached = nativeClasspath;
		if (cached == null)
			cached = nativeClasspath = runBuild(NativeBackend.ID);
		return cached;
	}

	private static String runBuild(String backendId) {
		Path script = repoRoot().resolve("tools/oracle/build.sh");
		if (!Files.isRegularFile(script))
			throw new IllegalStateException("build recipe not found at " + script);
		Process process;
		try {
			process = new ProcessBuilder(script.toString(), backendId).start();
		} catch (IOException e) {
			throw new IllegalStateException("cannot run " + script + " " + backendId + ": " + e, e);
		}
		String stdout;
		try {
			stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!process.waitFor(10, TimeUnit.MINUTES))
				process.destroyForcibly();
			if (process.exitValue() != 0 || stdout.isEmpty())
				throw new IllegalStateException("build.sh " + backendId + " failed with exit code "
						+ process.exitValue() + ":\n" + stderr.strip());
			if (stdout.indexOf('\n') >= 0)
				throw new IllegalStateException("build.sh " + backendId
						+ " printed more than its classpath on stdout:\n" + stdout);
			return stdout;
		} catch (IOException e) {
			throw new IllegalStateException("reading build.sh output failed: " + e, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while building backend '" + backendId + "'", e);
		}
	}

	/** The repository root holding tools/oracle/build.sh. */
	public static Path repoRoot() {
		Path candidate = Path.of(System.getProperty("oracle.repoRoot", ""));
		if (Files.isRegularFile(candidate.resolve("tools/oracle/build.sh")))
			return candidate.toAbsolutePath().normalize();
		Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
		while (dir != null) {
			if (Files.isRegularFile(dir.resolve("tools/oracle/build.sh")))
				return dir;
			dir = dir.getParent();
		}
		throw new IllegalStateException("cannot locate repository root; set -Doracle.repoRoot");
	}

	/** Mirrors the cache location documented in tools/oracle/build.sh. */
	private static Path cacheRoot() {
		String override = System.getenv("ORACLE_CACHE_DIR");
		if (override != null && !override.isBlank())
			return Path.of(override);
		String xdg = System.getenv("XDG_CACHE_HOME");
		Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg)
				: Path.of(System.getProperty("user.home", ".")).resolve(".cache");
		return base.resolve("swt-visual-oracle");
	}
}
