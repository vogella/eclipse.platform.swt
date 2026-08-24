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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.visualoracle.json.JsonParser;
import org.eclipse.swt.visualoracle.json.ResultSchemaValidator;
import org.eclipse.swt.visualoracle.result.CaptureEntry;
import org.eclipse.swt.visualoracle.result.CaptureStatus;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.tools.CaptureChild;

/**
 * Spawns one child JVM per (backend, environment) pair, drives
 * {@link CaptureChild} inside it, and collects what it produced.
 *
 * Failure isolation: a child that exits non-zero, dies or hangs is recorded
 * as failure data (reason plus stderr tail) while the remaining children
 * continue; every child runs under its own Xvfb display because children
 * sharing the parent's display would interfere.
 *
 * Parallelism is bounded by {@link Config#maxConcurrent}; the default comes
 * from measurement on the reference machine (see TRACKING.md handoff for
 * T05) and is overridable via {@link #PARALLELISM_PROPERTY}.
 */
public final class ChildProcessLauncher {

	/** System property overriding how many children may run at once. */
	public static final String PARALLELISM_PROPERTY = "oracle.children.parallelism";
	/** System property overriding the per-child wall-clock timeout. */
	public static final String TIMEOUT_PROPERTY = "oracle.child.timeoutSeconds";
	/**
	 * Measured sustainable concurrency on the reference machine (8 CPUs,
	 * software GL): batches above this get slower per child instead of
	 * faster and start flaking on frame timing. See the T05 handoff record.
	 */
	public static final int DEFAULT_PARALLELISM = 4;
	public static final int DEFAULT_TIMEOUT_SECONDS = 240;
	private static final int KILL_GRACE_SECONDS = 10;
	private static final int STDERR_TAIL_CHARS = 2000;

	/** Backends a child can be launched for; each needs its own classpath. */
	private static final java.util.Set<String> SUPPORTED_BACKENDS = java.util.Set.of(
			NativeBackend.ID, SkijaProtoBackend.ID);

	private final Config config;

	public ChildProcessLauncher(Config config) {
		this.config = config;
		if (config.maxConcurrent() < 1)
			throw new IllegalArgumentException("maxConcurrent must be >= 1");
		if (config.timeoutSeconds() < 1)
			throw new IllegalArgumentException("timeoutSeconds must be >= 1");
	}

	/**
	 * Launch configuration resolved from system properties with the measured
	 * defaults; {@code scratchDir} is where child working directories go.
	 */
	public static Config configFromSystemProperties(Path scratchDir) {
		return new Config(
				Math.max(1, Integer.getInteger(PARALLELISM_PROPERTY, DEFAULT_PARALLELISM)),
				Math.max(1, Integer.getInteger(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT_SECONDS)),
				scratchDir);
	}

	public record Config(int maxConcurrent, int timeoutSeconds, Path scratchDir) {
	}

	/** One requested child: a backend, an environment and specimens to run. */
	public record ChildRequest(String backendId, RenderEnv env, List<String> specimenIds,
			CaptureRuntime.Strategy strategy, boolean hangForTest) {

		public ChildRequest {
			specimenIds = List.copyOf(specimenIds);
			if (specimenIds.isEmpty())
				throw new IllegalArgumentException("a child request needs at least one specimen");
		}

		public static ChildRequest of(RenderEnv env, String... specimenIds) {
			return new ChildRequest(NativeBackend.ID, env, List.of(specimenIds),
					CaptureRuntime.Strategy.COPY_AREA, false);
		}
	}

	/**
	 * What became of one launched child.
	 *
	 * @param request the request this outcome belongs to
	 * @param directory the child's working directory (logs, result document)
	 * @param exitCode process exit code; null when killed after a timeout
	 * @param timedOut true when the child was killed after the timeout
	 * @param failureReason null when the child produced a valid result document
	 * @param resultDocument parsed schema-v1 document; null when the child
	 *     failed before writing one
	 * @param stderrTail tail of the child's stderr
	 * @param wallMillis wall-clock duration of the launch
	 */
	public record ChildOutcome(ChildRequest request, Path directory, Integer exitCode, boolean timedOut,
			String failureReason, Map<String, Object> resultDocument, String stderrTail, long wallMillis) {

		public boolean succeeded() {
			return failureReason == null && resultDocument != null;
		}

		/**
		 * FAILED schema entries standing in for a child that never produced a
		 * result document; each requested specimen gets one carrying the
		 * failure reason.
		 */
		public List<CaptureEntry> failureEntries() {
			if (resultDocument != null)
				throw new IllegalStateException("child produced a result document; it has real entries");
			List<CaptureEntry> entries = new ArrayList<>();
			for (String id : request.specimenIds())
				entries.add(CaptureEntry.skipped(id, request.backendId(), CaptureStatus.FAILED,
						failureReason == null ? "child failed" : failureReason));
			return entries;
		}
	}

	/** Runs every request, at most {@link Config#maxConcurrent} at a time. */
	public List<ChildOutcome> run(List<ChildRequest> requests) {
		for (ChildRequest request : requests)
			if (!SUPPORTED_BACKENDS.contains(request.backendId()))
				throw new IllegalArgumentException("no child adapter for backend '" + request.backendId() + "'");
		// Resolve backend builds up front: a missing or failing recipe must
		// fail the batch immediately instead of once per child, and build
		// time must not eat into any child's timeout.
		for (ChildRequest request : requests)
			if (SkijaProtoBackend.ID.equals(request.backendId())) {
				BackendClasspaths.harnessClasspath();
				BackendClasspaths.backendClasspath(request.backendId());
			}
		long start = System.nanoTime();
		Path batchDir = config.scratchDir().resolve("batch-" + Long.toString(start, 36));
		List<Callable<ChildOutcome>> tasks = new ArrayList<>();
		for (int i = 0; i < requests.size(); i++) {
			final ChildRequest request = requests.get(i);
			final int index = i;
			tasks.add(() -> launchOne(request, batchDir, index));
		}
		return runBounded(config.maxConcurrent(), tasks);
	}

	private ChildOutcome launchOne(ChildRequest request, Path batchDir, int index) {
		long start = System.nanoTime();
		Path dir = batchDir.resolve("child-" + index + "-" + describe(request.env()));
		LaunchConfig launch = SwtRenderEnvs.launch(request.env());
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			return new ChildOutcome(request, dir, null, false,
					"cannot create child directory " + dir + ": " + e, null, "", elapsed(start));
		}

		ProcessBuilder pb = new ProcessBuilder(buildCommand(request, dir));
		pb.environment().remove("DISPLAY");
		pb.environment().remove("WAYLAND_DISPLAY");
		pb.environment().remove("XDG_SESSION_TYPE");
		pb.environment().put("GDK_BACKEND", "x11");
		pb.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
		launch.applyTo(pb);
		pb.redirectOutput(dir.resolve("child.out.log").toFile());
		pb.redirectError(dir.resolve("child.err.log").toFile());

		Process process;
		try {
			process = pb.start();
		} catch (IOException e) {
			return new ChildOutcome(request, dir, null, false,
					"cannot start child process: " + e, null, "", elapsed(start));
		}

		boolean timedOut = false;
		Integer exitCode = null;
		try {
			if (process.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS)) {
				exitCode = process.exitValue();
			} else {
				timedOut = true;
				killProcessTree(process);
				process.waitFor(KILL_GRACE_SECONDS, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			killProcessTree(process);
			return new ChildOutcome(request, dir, exitCode, true,
					"launcher interrupted while waiting for the child", null, "", elapsed(start));
		}

		Map<String, Object> document = readAndValidateDocument(dir);
		String stderr = readTail(dir.resolve("child.err.log"));

		if (timedOut)
			return new ChildOutcome(request, dir, null, true,
					"child timed out after " + config.timeoutSeconds() + " s and was killed",
					document, stderr, elapsed(start));
		if (document != null)
			return new ChildOutcome(request, dir, exitCode, false, null, document, stderr, elapsed(start));
		String reason = "child exited with code " + exitCode + " and wrote no usable result document";
		return new ChildOutcome(request, dir, exitCode, false, reason, null, stderr, elapsed(start));
	}

	/**
	 * Kills the child and everything it started. Because the child runs under
	 * {@code setsid}, its pid is also its process group id, so a negative pid
	 * signals the whole group: the xvfb-run wrapper, the JVM under it and the
	 * Xvfb server. Falls back to the direct child if the group kill is
	 * unavailable, which at worst restores the previous leaky behaviour rather
	 * than failing the run.
	 */
	private static void killProcessTree(Process process) {
		long pid = process.pid();
		try {
			new ProcessBuilder("kill", "-KILL", "-" + pid).start().waitFor(5, TimeUnit.SECONDS);
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException)
				Thread.currentThread().interrupt();
		}
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
	}

	private List<String> buildCommand(ChildRequest request, Path dir) {
		LaunchConfig launch = SwtRenderEnvs.launch(request.env());
		boolean protoBackend = SkijaProtoBackend.ID.equals(request.backendId());
		List<String> command = new ArrayList<>();
		// setsid puts the child in its own process group. Process.destroyForcibly()
		// kills only the direct child, which is the xvfb-run wrapper; the JVM and the
		// Xvfb server it starts are grandchildren and survive, leaking one JVM and one
		// X server per timed-out child. Killing the whole group is what actually reaps them.
		command.add("setsid");
		command.add("xvfb-run");
		command.add("-a");
		command.add("-s");
		command.add("-screen 0 1600x1200x24");
		command.add(javaCommand());
		command.add("--enable-native-access=ALL-UNNAMED");
		if (protoBackend)
			command.add("-Djava.library.path=" + BackendClasspaths.libraryPathFor(SkijaProtoBackend.ID));
		else {
			String libPath = System.getProperty("java.library.path", "");
			if (!libPath.isEmpty())
				command.add("-Djava.library.path=" + libPath);
		}
		command.add("-Doracle.repoRoot=" + repoRoot());
		command.addAll(launch.jvmProperties());
		command.add("-cp");
		// One backend's SWT classes per process (ADR-002): a skija-proto child
		// gets the harness classes plus the fork build, never the parent's
		// native backend classes.
		command.add(protoBackend
				? BackendClasspaths.harnessClasspath() + java.io.File.pathSeparator
						+ BackendClasspaths.backendClasspath(SkijaProtoBackend.ID)
				: System.getProperty("java.class.path"));
		command.add(CaptureChild.class.getName());
		command.add("--out");
		command.add(dir.toString());
		command.add("--strategy");
		command.add(request.strategy().name());
		command.add("--backend");
		command.add(request.backendId());
		if (request.hangForTest())
			command.add("--hang");
		command.addAll(request.specimenIds());
		return command;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readAndValidateDocument(Path dir) {
		Path json = dir.resolve("result.json");
		if (!Files.isRegularFile(json))
			return null;
		Object parsed;
		try {
			parsed = JsonParser.parse(Files.readString(json, StandardCharsets.UTF_8));
		} catch (Exception e) {
			return null;
		}
		List<String> errors = ResultSchemaValidator.validate(parsed);
		if (!errors.isEmpty())
			return null;
		return (Map<String, Object>) parsed;
	}

	private static String describe(RenderEnv env) {
		String tag = "z" + env.zoomPercent() + "-" + env.direction().name().toLowerCase();
		if (!env.theme().isPlatformDefault())
			tag += "-theme-" + env.theme().id().replaceAll("[^A-Za-z0-9._-]", "_");
		return tag;
	}

	private static String javaCommand() {
		return ProcessHandle.current().info().command().orElse("java");
	}

	private static Path repoRoot() {
		Path candidate = Path.of(System.getProperty("oracle.repoRoot", ""));
		if (Files.isRegularFile(candidate.resolve("tools/oracle/verify-backend.sh")))
			return candidate.toAbsolutePath().normalize();
		Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
		while (dir != null) {
			if (Files.isRegularFile(dir.resolve("tools/oracle/verify-backend.sh")))
				return dir;
			dir = dir.getParent();
		}
		throw new IllegalStateException("cannot locate repository root; set -Doracle.repoRoot");
	}

	private static String readTail(Path file) {
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			return text.length() <= STDERR_TAIL_CHARS
					? text.strip()
					: text.substring(text.length() - STDERR_TAIL_CHARS).strip();
		} catch (IOException e) {
			return "";
		}
	}

	private static long elapsed(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

	/**
	 * Runs tasks with at most {@code maxConcurrent} in flight, preserving
	 * input order in the result. Public so the selftest can prove the bound
	 * actually bites.
	 */
	public static <T> List<T> runBounded(int maxConcurrent, List<Callable<T>> tasks) {
		ExecutorService pool = Executors.newFixedThreadPool(maxConcurrent, runnable -> {
			Thread thread = new Thread(runnable, "oracle-child-launcher");
			thread.setDaemon(true);
			return thread;
		});
		try {
			List<Future<T>> futures = pool.invokeAll(tasks);
			List<T> results = new ArrayList<>(futures.size());
			for (Future<T> future : futures) {
				try {
					results.add(future.get());
				} catch (Exception e) {
					throw new IllegalStateException("bounded task failed", e);
				}
			}
			return results;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while running bounded tasks", e);
		} finally {
			pool.shutdownNow();
		}
	}
}
