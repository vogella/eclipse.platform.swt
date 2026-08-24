import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;

/**
 * Renders one scene twice, antialiasing off and on, at several scale factors.
 * The Transform makes SWT's integer coordinates land on fractional device
 * positions, which is exactly what fractional DPI scaling does.
 */
public class AAComparison {

	static final int W = 300, H = 170;

	public static void main(String[] args) throws Exception {
		String outDir = args[0];
		Display display = new Display();
		for (float scale : new float[] {1.0f, 1.25f, 1.5f}) {
			Image combined = renderPair(display, scale);
			save(combined, outDir + "/aa-scale-" + (int) (scale * 100) + ".png");
			combined.dispose();
		}
		display.dispose();
		System.out.println("done");
	}

	static Image renderPair(Display display, float scale) {
		int pw = Math.round(W * scale), ph = Math.round(H * scale);
		Image out = new Image(display, pw * 2 + 12, ph + 22);
		GC g = new GC(out);
		try {
			g.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
			g.fillRectangle(0, 0, pw * 2 + 12, ph + 22);
			g.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
			g.drawString("antialias OFF  (win32 today)", 4, 3, true);
			g.drawString("antialias ON  (gtk today, proposed win32)", pw + 16, 3, true);
			for (int i = 0; i < 2; i++) {
				Image panel = renderScene(display, scale, i == 0 ? SWT.OFF : SWT.ON);
				g.drawImage(panel, i * (pw + 12), 20);
				panel.dispose();
			}
		} finally {
			g.dispose();
		}
		return out;
	}

	static Image renderScene(Display display, float scale, int antialias) {
		Image img = new Image(display, Math.round(W * scale), Math.round(H * scale));
		GC g = new GC(img);
		Transform t = new Transform(display);
		try {
			g.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
			g.fillRectangle(0, 0, Math.round(W * scale), Math.round(H * scale));
			t.scale(scale, scale);
			g.setTransform(t);
			g.setAntialias(antialias);
			g.setForeground(display.getSystemColor(SWT.COLOR_BLACK));
			g.setBackground(display.getSystemColor(SWT.COLOR_BLACK));

			// 1. evenly spaced ticks, 7pt pitch: the spacing case
			for (int k = 0; k < 20; k++) g.fillRectangle(10 + k * 7, 10, 3, 14);

			// 2. curves
			for (int k = 0; k < 4; k++) g.drawOval(10 + k * 34, 34, 27, 27);
			g.fillOval(160, 34, 27, 27);
			g.fillOval(196, 34, 27, 27);

			// 3. rounded rectangle, the CTabFolder tab shape
			g.drawRoundRectangle(10, 70, 90, 26, 12, 12);
			g.drawRoundRectangle(110, 70, 90, 26, 12, 12);

			// 4. diagonal fan
			for (int k = 0; k <= 8; k++) g.drawLine(215, 70, 215 + 70, 70 + k * 3);

			// 5. hairlines that must stay crisp: border plus gridlines
			g.drawRectangle(10, 106, 275, 50);
			for (int k = 1; k < 6; k++) g.drawLine(10, 106 + k * 8, 285, 106 + k * 8);
			for (int k = 1; k < 9; k++) g.drawLine(10 + k * 30, 106, 10 + k * 30, 156);
		} finally {
			t.dispose();
			g.dispose();
		}
		return img;
	}

	static void save(Image image, String path) {
		ImageLoader loader = new ImageLoader();
		loader.data = new ImageData[] { image.getImageData() };
		loader.save(path, SWT.IMAGE_PNG);
		System.out.println("wrote " + path);
	}
}
