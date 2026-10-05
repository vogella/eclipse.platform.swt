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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.RECT;
import org.eclipse.swt.visualoracle.spi.BackendUnavailableException;
import org.eclipse.swt.visualoracle.spi.CaptureFailedException;
import org.eclipse.swt.visualoracle.spi.CapturedImage;
import org.eclipse.swt.visualoracle.spi.Direction;
import org.eclipse.swt.visualoracle.spi.RenderEnv;
import org.eclipse.swt.visualoracle.spi.Theme;
import org.eclipse.swt.visualoracle.spi.UnsupportedEnvironmentException;
import org.eclipse.swt.widgets.Control;

/**
 * Win32 support: children run on the interactive desktop, the platform
 * default theme is the only one, and zoom is pinned with
 * {@code -Dswt.autoScale}.
 *
 * Capture has two paths. {@code GC.copyArea} (primary) BitBlts from the
 * control's window DC, so it needs the window visible, unobscured and on
 * screen. {@code Control.print} (PrintWindow with PW_RENDERFULLCONTENT, after
 * SWT's own reparenting fixes) renders the window itself and does not. The
 * environment variable {@value #CAPTURE_VARIABLE} selects the primary,
 * {@code copyarea} (default) or {@code print}; the grab fallback is always
 * the print path.
 */
public final class Win32PlatformSupport implements PlatformSupport {

	/** Selects the primary capture path of every process that inherits it: copyarea or print. */
	public static final String CAPTURE_VARIABLE = "ORACLE_WIN32_CAPTURE";
	/** Names the bash that runs the build scripts; set by tools/oracle/platform.sh. */
	private static final String BASH_VARIABLE = "ORACLE_BASH";
	/** Without this SWT refuses a fixed swt.autoScale value while monitor-specific scaling is on. */
	private static final String FIXED_ZOOM_PROPERTY = "-Dswt.autoScale.updateOnRuntime=false";
	private static final String REFERENCE_SPECIMEN = "button.push.default";

	@Override
	public String id() {
		return "win32";
	}

	@Override
	public String fragment() {
		return "win32.win32." + PlatformSupport.architecture();
	}

	@Override
	public void suppressFocus(Control control) {
		// the Edit control creates its caret when it gets focus, after the FocusIn event
		control.addListener(SWT.FocusIn, e -> control.getDisplay().asyncExec(OS::DestroyCaret));
		// no focus rectangles and no underlined mnemonics until a key is pressed
		long shell = control.getShell().handle;
		OS.SendMessage(shell, OS.WM_CHANGEUISTATE, OS.UIS_SET | ((OS.UISF_HIDEFOCUS | OS.UISF_HIDEACCEL) << 16), 0);
	}

	@Override
	public void requireRealizedHandle(Control probe) {
		long hwnd = probe.handle;
		RECT client = new RECT();
		// real native calls: an invalid handle fails GetClientRect, a hidden window IsWindowVisible
		if (hwnd == 0 || !OS.GetClientRect(hwnd, client) || !OS.IsWindowVisible(hwnd))
			throw new BackendUnavailableException(
					"the probe control owns no visible native window (HWND " + hwnd + "); SWT natives are not working");
		if (client.right - client.left <= 0 || client.bottom - client.top <= 0)
			throw new BackendUnavailableException("the probe control's native window has an empty client area");
	}

	@Override
	public CapturedImage grabPrimary(Control control) {
		return printPrimary() ? printGrab(control) : null;
	}

	@Override
	public boolean supportsGrabFallback() {
		return true;
	}

	@Override
	public CapturedImage grabFallback(Control control, Path scratchDir) {
		return printGrab(control);
	}

	private static boolean printPrimary() {
		String mode = System.getenv(CAPTURE_VARIABLE);
		if (mode == null || mode.isBlank() || mode.equalsIgnoreCase("copyarea"))
			return false;
		if (mode.equalsIgnoreCase("print"))
			return true;
		throw new UnsupportedEnvironmentException(CAPTURE_VARIABLE + " must be copyarea or print, found: " + mode);
	}

	/** Renders the control into an image through its own window procedure, device pixels at the process zoom. */
	private static CapturedImage printGrab(Control control) {
		Point size = control.getSize();
		Image image = new Image(control.getDisplay(), size.x, size.y);
		try {
			GC gc = new GC(image);
			try {
				if (!control.print(gc))
					throw new CaptureFailedException("Control.print reported failure for "
							+ control.getClass().getSimpleName());
			} finally {
				gc.dispose();
			}
			return new BasicCapturedImage(image.getImageData(DPIUtil.getDeviceZoom()));
		} finally {
			image.dispose();
		}
	}

	@Override
	public List<String> scriptCommand(Path script, String... args) {
		String bash = System.getenv(BASH_VARIABLE);
		List<String> command = new ArrayList<>();
		command.add(bash == null || bash.isBlank() ? "bash" : bash);
		command.add(script.toString().replace('\\', '/'));
		command.addAll(List.of(args));
		return command;
	}

	@Override
	public void applyTheme(Theme theme, Map<String, String> variables, Set<String> removedVariables,
			List<String> jvmProperties) {
		if (!theme.isPlatformDefault())
			throw new UnsupportedEnvironmentException("visual oracle: Win32 pins only the platform default theme,"
					+ " not '" + theme.id() + "'");
		if (!jvmProperties.contains(FIXED_ZOOM_PROPERTY))
			jvmProperties.add(FIXED_ZOOM_PROPERTY);
	}

	@Override
	public Theme currentTheme() {
		return Theme.PLATFORM_DEFAULT;
	}

	@Override
	public Optional<Theme> alternateTheme() {
		return Optional.empty();
	}

	@Override
	public List<NamedCheck> selfTestChecks(SelfTestHost host) {
		return List.of(new NamedCheck("win32-capture-paths-measured", () -> checkCapturePaths(host)));
	}

	// ------------------------------------------------------ capture measurement

	/** What one capture child produced, read back from its PNG. */
	private record Measurement(int width, int height, int distinctColors, int inkPixels, ImageData data) {
		boolean blank() {
			return distinctColors <= 1 || inkPixels == 0;
		}
	}

	/**
	 * Measures both capture paths and prints one {@code WIN32-CAPTURE} line per
	 * path and zoom plus a decision line, so the CI log shows which path to
	 * make primary. Only a broken copyArea path fails the check; the print
	 * path is reported as data.
	 */
	private static void checkCapturePaths(SelfTestHost host) throws Exception {
		List<String> problems = new ArrayList<>();
		for (String strategy : List.of("COPY_AREA", "X11_GRAB")) {
			String label = label(strategy);
			try {
				ProbeResult first = host.runProbe(strategy, null);
				ProbeResult second = host.runProbe(strategy, null);
				boolean deterministic = first.sha256().equals(second.sha256());
				System.out.println("      WIN32-CAPTURE path=" + label + " zoom=100 processes=2 size="
						+ first.width() + "x" + first.height() + " deterministicAcrossProcesses=" + deterministic
						+ " sha=" + first.sha256());
				if (!deterministic && strategy.equals("COPY_AREA"))
					problems.add("copyArea differs between two processes: " + first.sha256() + " vs " + second.sha256());
			} catch (Exception | AssertionError e) {
				System.out.println("      WIN32-CAPTURE path=" + label + " zoom=100 probe=FAILED " + e);
				if (strategy.equals("COPY_AREA"))
					problems.add("copyArea probe failed: " + e);
			}
		}

		Point preferred = host.preferredSize();
		List<ChildProcessLauncher.ChildRequest> requests = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		for (int zoom : new int[] { 100, 200 }) {
			RenderEnv env = new RenderEnv(zoom, Theme.PLATFORM_DEFAULT, Direction.LTR, "", -1);
			for (CaptureRuntime.Strategy strategy : CaptureRuntime.Strategy.values()) {
				requests.add(new ChildProcessLauncher.ChildRequest(NativeBackend.ID, env, List.of(REFERENCE_SPECIMEN),
						strategy, false));
				keys.add(strategy.name() + "@" + zoom);
			}
		}
		Path scratch = Path.of("/tmp/swt-visual-oracle/selftest/win32-capture-"
				+ Long.toString(System.currentTimeMillis(), 36));
		List<ChildProcessLauncher.ChildOutcome> outcomes =
				new ChildProcessLauncher(ChildProcessLauncher.configFromSystemProperties(scratch)).run(requests);

		Map<String, Measurement> measured = new LinkedHashMap<>();
		for (int i = 0; i < outcomes.size(); i++) {
			String key = keys.get(i);
			String strategy = key.substring(0, key.indexOf('@'));
			int zoom = Integer.parseInt(key.substring(key.indexOf('@') + 1));
			ChildProcessLauncher.ChildOutcome outcome = outcomes.get(i);
			String label = label(strategy);
			Measurement m = outcome.succeeded() ? measure(outcome) : null;
			if (m == null) {
				System.out.println("      WIN32-CAPTURE path=" + label + " zoom=" + zoom + " child=FAILED "
						+ outcome.failureReason() + " " + outcome.stderrTail());
				if (strategy.equals("COPY_AREA"))
					problems.add("copyArea child at zoom " + zoom + " failed: " + outcome.failureReason());
				continue;
			}
			measured.put(key, m);
			int expectedWidth = Math.round(preferred.x * zoom / 100f);
			int expectedHeight = Math.round(preferred.y * zoom / 100f);
			boolean sizeOk = m.width() == expectedWidth && m.height() == expectedHeight;
			System.out.println("      WIN32-CAPTURE path=" + label + " zoom=" + zoom + " size=" + m.width() + "x"
					+ m.height() + " expected=" + expectedWidth + "x" + expectedHeight + " sizeOk=" + sizeOk
					+ " nonBlank=" + !m.blank() + " distinctColors=" + m.distinctColors() + " inkPixels="
					+ m.inkPixels());
			if (strategy.equals("COPY_AREA") && (!sizeOk || m.blank()))
				problems.add("copyArea at zoom " + zoom + " is " + (m.blank() ? "blank" : "the wrong size"));
		}

		for (int zoom : new int[] { 100, 200 }) {
			Measurement copy = measured.get("COPY_AREA@" + zoom);
			Measurement print = measured.get("X11_GRAB@" + zoom);
			if (copy != null && print != null && copy.width() == print.width() && copy.height() == print.height())
				System.out.println("      WIN32-CAPTURE copyArea-vs-print zoom=" + zoom + " differingPixels="
						+ differing(copy.data(), print.data()));
		}

		Measurement copy100 = measured.get("COPY_AREA@100");
		Measurement print100 = measured.get("X11_GRAB@100");
		boolean copyUsable = problems.isEmpty() && copy100 != null && !copy100.blank();
		boolean printUsable = print100 != null && !print100.blank();
		System.out.println("      WIN32-CAPTURE-DECISION primary="
				+ (copyUsable ? "copyarea" : printUsable ? "print" : "none")
				+ " copyAreaUsable=" + copyUsable + " printUsable=" + printUsable);
		if (!problems.isEmpty())
			throw new AssertionError(String.join("\n", problems));
	}

	private static String label(String strategy) {
		return strategy.equals("COPY_AREA") ? "copyArea" : "print";
	}

	private static Measurement measure(ChildProcessLauncher.ChildOutcome outcome) {
		if (!(outcome.resultDocument().get("captures") instanceof List<?> captures))
			return null;
		for (Object entry : captures) {
			if (!(entry instanceof Map<?, ?> capture) || !"CAPTURED".equals(capture.get("status")))
				continue;
			ImageData data = new ImageData(outcome.directory().resolve(String.valueOf(capture.get("image")))
					.normalize().toString());
			Set<Integer> colors = new HashSet<>();
			int background = data.getPixel(0, 0);
			int ink = 0;
			for (int y = 0; y < data.height; y++)
				for (int x = 0; x < data.width; x++) {
					int pixel = data.getPixel(x, y);
					colors.add(pixel);
					if (pixel != background)
						ink++;
				}
			return new Measurement(data.width, data.height, colors.size(), ink, data);
		}
		return null;
	}

	private static int differing(ImageData a, ImageData b) {
		int changed = 0;
		for (int y = 0; y < a.height; y++)
			for (int x = 0; x < a.width; x++)
				if (a.getPixel(x, y) != b.getPixel(x, y))
					changed++;
		return changed;
	}
}
