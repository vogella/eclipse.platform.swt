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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.visualoracle.impl.CaptureRuntime;
import org.eclipse.swt.visualoracle.impl.ClusterDiffer;
import org.eclipse.swt.visualoracle.impl.NativeBackend;
import org.eclipse.swt.visualoracle.impl.SwtRenderEnvs;
import org.eclipse.swt.visualoracle.spi.Backend;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.Differ;
import org.eclipse.swt.visualoracle.spi.DiffResult;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenCatalog;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.Tag;
import org.eclipse.swt.visualoracle.spi.Tolerance;
import org.eclipse.swt.visualoracle.spi.Theme;
import org.eclipse.swt.visualoracle.spi.UnsupportedSpecimenException;
import org.eclipse.swt.visualoracle.spi.Verdict;

/**
 * Selftest infrastructure for the native backend adapter.
 *
 * The native backend is the oracle every comparison is measured against, so
 * these checks hold it to the same standard as the Skija adapters: genuine
 * activation evidence instead of assumption, refusal of a foreign renderer,
 * environment fidelity with mismatch refusal, unsupported-as-data coverage,
 * and the closest thing the project has to validating its own oracle, a
 * whole-catalog native-versus-native sweep that must come out EQUAL for every
 * specimen not officially quarantined.
 */
public final class NativeCheck {

	/**
	 * How many captures one specimen may take before a consecutive agreeing
	 * pair must appear; mirrors the determinism lint's cold-start allowance,
	 * because the very first captures of a widget class in a fresh process can
	 * differ from every later one without being instability.
	 */
	private static final int MAX_WARMUP_CAPTURES = 6;

	private NativeCheck() {
	}

	/**
	 * Activation must be proven, not assumed: configure runs the full evidence
	 * chain (paint event, JNI window, stock GC identity, no foreign wrapper,
	 * glyph ink) and the exposed evidence accessors carry it.
	 */
	public static void checkActivation(Display display, PrintStream out) {
		NativeBackend backend = new NativeBackend();
		backend.configure(display);
		require("org.eclipse.swt.graphics.GC".equals(backend.observedGcClassName()),
				"activation did not leave the stock native GC in place, observed: "
						+ backend.observedGcClassName());
		require(backend.environment().equals(SwtRenderEnvs.current(display)),
				"activation did not record the process environment");
		out.println("      activation evidence: GC=" + backend.observedGcClassName()
				+ ", paint+jni+ink proven, env=" + backend.environment());
	}

	/** Wrong zoom, theme or direction are refused; the pinned env is accepted. */
	public static void checkEnvironmentRefusal(Display display, PrintStream out) {
		NativeBackend backend = new NativeBackend();
		backend.configure(display);
		RenderEnv pinned = backend.environment();
		require(pinned.equals(SwtRenderEnvs.current(display)),
				"environment() does not mirror the process environment: " + pinned);

		requireMismatch(backend, withZoom(pinned, pinned.zoomPercent() >= 200 ? 100 : pinned.zoomPercent() * 2),
				"zoom");
		requireMismatch(backend, withTheme(pinned, pinned.theme().id().isEmpty()
				? new Theme("HighContrast") : Theme.PLATFORM_DEFAULT), "theme");
		requireMismatch(backend, withDirection(pinned,
				pinned.direction() == Direction.RTL ? Direction.LTR : Direction.RTL), "direction");

		backend.requireEnvironment(new RenderEnv(pinned.zoomPercent(), pinned.theme(),
				pinned.direction(), "", -1));
		out.println("      refused wrong zoom, theme and direction; accepted the pinned " + pinned);
	}

	/**
	 * Coverage gaps are data, not exceptions: a specimen the native backend
	 * cannot deliver comparable pixels for reports {@code supports == false},
	 * the frozen Capture contract refuses with UnsupportedSpecimenException,
	 * and the run continues around it.
	 */
	public static void checkUnsupportedAsData(Display display, Backend backend, RenderEnv env,
			PrintStream out) {
		Specimen popup = new PopupSpecimen();
		require(!backend.supports(popup),
				"native claims support for a NATIVE_POPUP specimen whose content escapes the captured bounds");
		Specimen sibling = SpecimenCatalog.discover().byId("button.push.default")
				.orElseThrow(() -> new AssertionError("button.push.default missing from catalog"));
		require(backend.supports(sibling), "native stopped supporting ordinary catalog specimens");
		try (CaptureRuntime runtime = new CaptureRuntime()) {
			try {
				runtime.capture(popup, backend, env);
				throw new AssertionError("capture of an unsupported specimen did not refuse");
			} catch (UnsupportedSpecimenException e) {
				String message = String.valueOf(e.getMessage());
				require(message.contains(backend.id()) && message.contains(popup.id()),
						"refusal does not name backend and specimen: " + message);
			}
			CapturedImage stillWorking = runtime.capture(sibling, backend, env);
			require(stillWorking.width() == sibling.preferredSize().x
					&& stillWorking.height() == sibling.preferredSize().y,
					"the run did not continue cleanly past the refusal");
		}
		out.println("      " + popup.id() + " reported as data (supports=false, "
				+ UnsupportedSpecimenException.class.getSimpleName() + "), " + sibling.id() + " unaffected");
	}

	/**
	 * The oracle cross-checked against itself: every catalog specimen
	 * rendered twice natively in this process must compare EQUAL through the
	 * real differ. Anything else is a non-deterministic specimen or a defect
	 * in the harness, and must be named here rather than blamed on a Skija
	 * renderer later. Specimens on the official quarantine list are excluded
	 * from judging and reported as data, consistent with the project's
	 * determinism policy.
	 */
	public static void checkNativeVsNativeSweep(Display display, Backend backend, RenderEnv env,
			PrintStream out) {
		List<Specimen> all = SpecimenCatalog.discover().all();
		Map<String, String> quarantine = new LinkedHashMap<>();
		for (DeterminismLint.Quarantine entry : DeterminismLint.CATALOG_QUARANTINE)
			quarantine.put(entry.specimenId(), entry.reason());

		Differ differ = new ClusterDiffer();
		int equal = 0;
		int excluded = 0;
		List<String> failures = new ArrayList<>();
		try (CaptureRuntime runtime = new CaptureRuntime()) {
			for (Specimen specimen : all) {
				String reason = quarantine.get(specimen.id());
				if (reason != null) {
					out.println("      sweep " + specimen.id() + ": QUARANTINED: " + reason);
					excluded++;
					continue;
				}
				try {
					requireSelfEqual(runtime, differ, backend, env, specimen);
					equal++;
				} catch (RuntimeException e) {
					failures.add(specimen.id() + ": " + e.getMessage());
				}
			}
		}
		require(failures.isEmpty(), "native-vs-native sweep found " + failures.size()
				+ " of " + (all.size() - excluded) + " judged specimens unequal:\n"
				+ String.join("\n", failures));
		out.println("      swept " + equal + " specimens native-vs-native, all EQUAL ("
				+ excluded + " quarantined-excluded, reasons printed above)");
	}

	/**
	 * Captures until two consecutive renders agree byte for byte (the lint's
	 * convergence rule) and requires the real differ to call that independent
	 * pair EQUAL with zero changed pixels and zero channel delta.
	 */
	private static void requireSelfEqual(CaptureRuntime runtime, Differ differ, Backend backend,
			RenderEnv env, Specimen specimen) {
		Point wanted = specimen.preferredSize();
		CapturedImage prev = null;
		DiffResult disagreement = null;
		for (int captures = 1; captures <= MAX_WARMUP_CAPTURES; captures++) {
			CapturedImage image = runtime.capture(specimen, backend, env);
			if (image.width() != wanted.x || image.height() != wanted.y)
				throw new IllegalStateException(specimen.id() + " captured " + image.width() + "x"
						+ image.height() + ", preferred is " + wanted.x + "x" + wanted.y);
			if (prev != null) {
				DiffResult result = differ.compare(prev, image, Tolerance.EXACT);
				if (result.verdict() == Verdict.EQUAL && result.changedPixels() == 0
						&& result.maxChannelDelta() == 0)
					return;
				disagreement = result;
			}
			prev = image;
		}
		String detail = disagreement == null ? "no pair could even be formed"
				: "last comparison verdict=" + disagreement.verdict() + " changedPixels="
						+ disagreement.changedPixels() + " maxChannelDelta="
						+ disagreement.maxChannelDelta();
		throw new IllegalStateException(detail + "; either the specimen is non-deterministic"
				+ " or the harness is defective");
	}

	private static RenderEnv withZoom(RenderEnv env, int zoom) {
		return new RenderEnv(zoom, env.theme(), env.direction(), env.fontFamily(), env.fontSize());
	}

	private static RenderEnv withTheme(RenderEnv env, Theme theme) {
		return new RenderEnv(env.zoomPercent(), theme, env.direction(), env.fontFamily(),
				env.fontSize());
	}

	private static RenderEnv withDirection(RenderEnv env, Direction direction) {
		return new RenderEnv(env.zoomPercent(), env.theme(), direction, env.fontFamily(),
				env.fontSize());
	}

	private static void requireMismatch(NativeBackend backend, RenderEnv requested, String what) {
		try {
			backend.requireEnvironment(requested);
		} catch (org.eclipse.swt.visualoracle.spi.UnsupportedEnvironmentException e) {
			String message = String.valueOf(e.getMessage());
			require(message.contains("environment mismatch"),
					"wrong-" + what + " refusal does not say what happened: " + message);
			require(message.contains(String.valueOf(backend.environment())),
					"wrong-" + what + " refusal does not name the pinned environment: " + message);
			require(message.contains(String.valueOf(requested)),
					"wrong-" + what + " refusal does not name the requested environment: " + message);
			return;
		}
		throw new AssertionError("a wrong-" + what + " environment was accepted");
	}

	/** Stand-in for a widget whose visible content lives in a native popup. */
	private static final class PopupSpecimen implements Specimen {
		@Override
		public String id() {
			return "test.nativepopup.menu";
		}

		@Override
		public Point preferredSize() {
			return new Point(120, 32);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = new Button(parent, SWT.PUSH);
			button.setText("menu");
			ctx.configure(button);
			return button;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.NATIVE_POPUP);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
