import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;

/** Renders a denser, more UI-like scene at 150%, AA off vs on, at 1:1. */
public class Scene11 {
	static final int W = 420, H = 210;

	public static void main(String[] a) {
		Display d = new Display();
		float s = 1.5f;
		int pw = Math.round(W * s), ph = Math.round(H * s);
		Image out = new Image(d, pw, ph * 2 + 30);
		GC g = new GC(out);
		g.setBackground(d.getSystemColor(SWT.COLOR_WHITE));
		g.fillRectangle(0, 0, pw, ph * 2 + 30);
		g.setForeground(d.getSystemColor(SWT.COLOR_DARK_GRAY));
		g.drawString("antialias OFF   (what win32 does today)", 4, 2, true);
		g.drawString("antialias ON   (what gtk does today)", 4, ph + 18, true);
		for (int i = 0; i < 2; i++) {
			Image p = scene(d, s, i == 0 ? SWT.OFF : SWT.ON);
			g.drawImage(p, 0, 16 + i * (ph + 16));
			p.dispose();
		}
		g.dispose();
		ImageLoader l = new ImageLoader();
		l.data = new ImageData[] { out.getImageData() };
		l.save(a[0] + "/scene-1to1-150.png", SWT.IMAGE_PNG);
		out.dispose();
		d.dispose();
		System.out.println("wrote scene-1to1-150.png");
	}

	static Image scene(Display d, float s, int aa) {
		Image img = new Image(d, Math.round(W * s), Math.round(H * s));
		GC g = new GC(img);
		Transform t = new Transform(d);
		g.setBackground(d.getSystemColor(SWT.COLOR_WHITE));
		g.fillRectangle(0, 0, Math.round(W * s), Math.round(H * s));
		t.scale(s, s);
		g.setTransform(t);
		g.setAntialias(aa);
		Color fg = d.getSystemColor(SWT.COLOR_BLACK);
		Color blue = d.getSystemColor(SWT.COLOR_DARK_BLUE);
		g.setForeground(fg);

		// tab strip
		for (int k = 0; k < 4; k++) {
			g.setForeground(fg);
			g.drawRoundRectangle(8 + k * 74, 8, 70, 24, 10, 10);
		}
		// toolbar circles and arcs
		for (int k = 0; k < 6; k++) g.drawOval(10 + k * 26, 44, 20, 20);
		g.setForeground(blue);
		for (int k = 0; k < 4; k++) g.drawArc(180 + k * 26, 44, 20, 20, 45, 270);
		g.setForeground(fg);

		// a small line chart, the diagonal case
		int[] ys = { 150, 120, 138, 96, 110, 74, 88, 60 };
		g.setForeground(blue);
		for (int k = 1; k < ys.length; k++) g.drawLine(20 + (k - 1) * 24, ys[k - 1], 20 + k * 24, ys[k]);
		g.setForeground(fg);

		// table grid, the hairline case
		g.drawRectangle(220, 78, 190, 118);
		for (int k = 1; k < 6; k++) g.drawLine(220, 78 + k * 19, 410, 78 + k * 19);
		for (int k = 1; k < 4; k++) g.drawLine(220 + k * 47, 78, 220 + k * 47, 196);

		t.dispose();
		g.dispose();
		return img;
	}
}
