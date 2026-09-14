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
package org.eclipse.swt.visualoracle.catalog;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataAtSizeProvider;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.ImageFileNameProvider;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.Tag;
import org.eclipse.swt.visualoracle.spi.UnsupportedSpecimenException;

/**
 * The image family: how icons reach the screen at the current zoom, through
 * every image source SWT offers, derived images, scaled drawing, and icons
 * hosted by item widgets.
 */
public class ImageModule implements SpecimenModule {

	@Override
	public String family() {
		return "image";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new IconLabel("image.data.multi", ImageModule::dataMulti),
				new IconLabel("image.data.single", ImageModule::dataSingle),
				new IconLabel("image.file.multi", ImageModule::fileMulti),
				new IconLabel("image.file.single", ImageModule::fileSingle),
				new IconLabel("image.atsize", ImageModule::atSize),
				new IconLabel("image.gcdrawer", ImageModule::gcDrawer),
				new IconLabel("image.disabled", d -> derived(d, SWT.IMAGE_DISABLE)),
				new IconLabel("image.gray", d -> derived(d, SWT.IMAGE_GRAY)),
				new DrawScaled(), new DrawAtSize(), new ToolBarIcons(), new TableIcons(), new ButtonIcon());
	}

	static Image dataMulti(Display display) {
		return new Image(display, (ImageDataProvider) CatalogIcons::multiResolution);
	}

	static Image dataSingle(Display display) {
		return new Image(display, (ImageDataProvider) zoom -> zoom == 100
				? CatalogIcons.data(CatalogIcons.POINTS, CatalogIcons.POINTS)
				: null);
	}

	static Image fileMulti(Display display) {
		return new Image(display, (ImageFileNameProvider) zoom -> switch (zoom) {
			case 100 -> CatalogIcons.png(1);
			case 200 -> CatalogIcons.png(2);
			default -> null;
		});
	}

	static Image fileSingle(Display display) {
		return new Image(display, (ImageFileNameProvider) zoom -> zoom == 100 ? CatalogIcons.png(1) : null);
	}

	static Image atSize(Display display) {
		return new Image(display, new ImageDataAtSizeProvider() {
			@Override
			public ImageData getImageData(int width, int height) {
				return CatalogIcons.data(width, height);
			}

			@Override
			public Point getDefaultSize() {
				return new Point(CatalogIcons.POINTS, CatalogIcons.POINTS);
			}
		});
	}

	static Image gcDrawer(Display display) {
		return new Image(display, (gc, width, height) -> {
			gc.setAntialias(SWT.ON);
			gc.setBackground(display.getSystemColor(SWT.COLOR_RED));
			gc.fillRectangle(width / 2, height / 2, width / 4, height / 4);
			gc.setForeground(display.getSystemColor(SWT.COLOR_BLUE));
			gc.drawOval(1, 1, width - 3, height - 3);
			gc.drawLine(0, 0, width - 1, height - 1);
		}, CatalogIcons.POINTS, CatalogIcons.POINTS);
	}

	static Image derived(Display display, int flag) {
		Image source = dataMulti(display);
		try {
			return new Image(display, source, flag);
		} finally {
			source.dispose();
		}
	}

	/** Refuses the specimen when this SWT lacks destination-size drawImage, which a paint listener cannot report. */
	static void requireDrawImageAtSize() {
		try {
			GC.class.getMethod("drawImage", Image.class, int.class, int.class, int.class, int.class);
		} catch (NoSuchMethodException e) {
			throw new UnsupportedSpecimenException("this SWT predates GC.drawImage(Image, int, int, int, int)");
		}
	}

	/** A fixed opaque background, so alpha blending shows up in the pixels. */
	static Color backdrop() {
		return new Color(255, 236, 190);
	}

	static void disposeWith(Control control, Image... images) {
		control.addDisposeListener(e -> {
			for (Image image : images)
				image.dispose();
		});
	}

	static final class IconLabel extends FamilySpecimen {
		private final Function<Display, Image> source;

		IconLabel(String id, Function<Display, Image> source) {
			super(id, 32, 32);
			this.source = source;
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Label label = new Label(parent, SWT.CENTER);
			label.setBackground(backdrop());
			Image image = source.apply(parent.getDisplay());
			disposeWith(label, image);
			label.setImage(image);
			ctx.configure(label);
			return label;
		}
	}

	/** Upscaling with and without interpolation, next to an unscaled copy. */
	public static final class DrawScaled extends FamilySpecimen {
		public DrawScaled() {
			super("image.draw.scaled", 120, 44);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Canvas canvas = new Canvas(parent, SWT.NONE);
			canvas.setBackground(backdrop());
			Image image = dataSingle(parent.getDisplay());
			disposeWith(canvas, image);
			canvas.addPaintListener(e -> {
				GC gc = e.gc;
				gc.drawImage(image, 2, 14);
				gc.setInterpolation(SWT.NONE);
				gc.drawImage(image, 0, 0, 16, 16, 24, 2, 40, 40);
				gc.setInterpolation(SWT.HIGH);
				gc.drawImage(image, 0, 0, 16, 16, 72, 2, 40, 40);
			});
			ctx.configure(canvas);
			return canvas;
		}
	}

	/** The destination-size drawImage for an at-size source and a fixed-resolution source. */
	public static final class DrawAtSize extends FamilySpecimen {
		public DrawAtSize() {
			super("image.draw.atsize", 100, 44);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			requireDrawImageAtSize();
			Canvas canvas = new Canvas(parent, SWT.NONE);
			canvas.setBackground(backdrop());
			Display display = parent.getDisplay();
			Image atSize = atSize(display);
			Image multi = dataMulti(display);
			disposeWith(canvas, atSize, multi);
			canvas.addPaintListener(e -> {
				e.gc.drawImage(atSize, 2, 2, 40, 40);
				e.gc.drawImage(multi, 52, 2, 40, 40);
			});
			ctx.configure(canvas);
			return canvas;
		}
	}

	/** Enabled items from both source kinds, plus a disabled item whose image SWT derives. */
	public static final class ToolBarIcons extends FamilySpecimen {
		public ToolBarIcons() {
			super("image.toolbar", 120, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			ToolBar toolBar = new ToolBar(parent, SWT.FLAT);
			Display display = parent.getDisplay();
			Image multi = dataMulti(display);
			Image file = fileSingle(display);
			disposeWith(toolBar, multi, file);
			new ToolItem(toolBar, SWT.PUSH).setImage(multi);
			new ToolItem(toolBar, SWT.PUSH).setImage(file);
			ToolItem disabled = new ToolItem(toolBar, SWT.PUSH);
			disabled.setImage(multi);
			disabled.setEnabled(false);
			ctx.configure(toolBar);
			return toolBar;
		}
	}

	public static final class TableIcons extends FamilySpecimen {
		public TableIcons() {
			super("image.table", 140, 60);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Table table = new Table(parent, SWT.BORDER);
			Display display = parent.getDisplay();
			Image multi = fileMulti(display);
			Image single = dataSingle(display);
			disposeWith(table, multi, single);
			TableItem first = new TableItem(table, SWT.NONE);
			first.setText("Multi");
			first.setImage(multi);
			TableItem second = new TableItem(table, SWT.NONE);
			second.setText("Single");
			second.setImage(single);
			ctx.configure(table);
			return table;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}

	public static final class ButtonIcon extends FamilySpecimen {
		public ButtonIcon() {
			super("image.button", 120, 40);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			Button button = new Button(parent, SWT.PUSH);
			Image image = dataMulti(parent.getDisplay());
			disposeWith(button, image);
			button.setImage(image);
			button.setText("Open");
			ctx.configure(button);
			return button;
		}

		@Override
		public Set<Tag> tags() {
			return Set.of(Tag.TEXT_HEAVY);
		}
	}
}
