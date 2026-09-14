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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageFileNameProvider;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.visualoracle.spi.Specimen;
import org.eclipse.swt.visualoracle.spi.SpecimenContext;
import org.eclipse.swt.visualoracle.spi.SpecimenModule;
import org.eclipse.swt.visualoracle.spi.UnsupportedSpecimenException;

/**
 * The SVG family: SVG icons rasterized through every way SWT loads them, at
 * their own size, re-rasterized at a destination size, derived as disabled,
 * and hosted in a tool bar.
 */
public class SvgModule implements SpecimenModule {

	/** 16 points drawn on a 24 unit grid: fractional scaling, gradient, clip, stroke, opacity, transform. */
	static final String ICON = """
			<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24">
			  <defs>
			    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
			      <stop offset="0" stop-color="#2060ff"/>
			      <stop offset="1" stop-color="#20c080"/>
			    </linearGradient>
			    <clipPath id="c"><circle cx="12" cy="12" r="9"/></clipPath>
			  </defs>
			  <rect x="0.5" y="0.5" width="23" height="23" fill="none" stroke="#202020" stroke-width="1"/>
			  <g clip-path="url(#c)"><rect x="3" y="3" width="18" height="18" fill="url(#g)"/></g>
			  <path d="M4 20 L20 4" stroke="#e00000" stroke-width="1.5" stroke-linecap="round"/>
			  <rect x="13" y="13" width="7" height="7" fill="#ffd000" fill-opacity="0.6" transform="rotate(15 16.5 16.5)"/>
			</svg>
			""";

	/** A 24 by 12 point icon, so a swapped or equalized aspect ratio changes pixels. */
	static final String WIDE = """
			<svg xmlns="http://www.w3.org/2000/svg" width="24" height="12" viewBox="0 0 48 24">
			  <rect x="1" y="1" width="46" height="22" rx="4" fill="#f0f0f0" stroke="#303030" stroke-width="2"/>
			  <circle cx="12" cy="12" r="7" fill="#e04040"/>
			  <circle cx="36" cy="12" r="7" fill="none" stroke="#4040e0" stroke-width="3"/>
			</svg>
			""";

	@Override
	public String family() {
		return "svg";
	}

	@Override
	public List<Specimen> specimens() {
		return List.of(
				new IconLabel("svg.file", 32, 32, d -> new Image(d, iconFile())),
				new IconLabel("svg.provider", 32, 32,
						d -> new Image(d, (ImageFileNameProvider) zoom -> zoom == 100 ? iconFile() : null)),
				new IconLabel("svg.stream", 32, 32,
						d -> new Image(d, new ByteArrayInputStream(ICON.getBytes(StandardCharsets.UTF_8)))),
				new IconLabel("svg.wide", 40, 24,
						d -> new Image(d, CatalogIcons.text("wide.svg", WIDE))),
				new IconLabel("svg.disabled", 32, 32, SvgModule::disabled),
				new DrawAtSize(), new ToolBarIcons());
	}

	static String iconFile() {
		return CatalogIcons.text("icon.svg", ICON);
	}

	static Image disabled(Display display) {
		Image source = new Image(display, iconFile());
		try {
			return new Image(display, source, SWT.IMAGE_DISABLE);
		} finally {
			source.dispose();
		}
	}

	/** Refuses the specimen when the loaded SWT cannot rasterize SVG, so that is coverage data, not a failure. */
	static void requireSvgSupport() {
		ClassLoader loader = SWT.class.getClassLoader();
		try {
			Class<?> rasterizer = Class.forName("org.eclipse.swt.internal.image.SVGRasterizer", false, loader);
			if (ServiceLoader.load(rasterizer, loader).findFirst().isEmpty())
				throw new UnsupportedSpecimenException("no SVG rasterizer is registered with this SWT");
		} catch (ClassNotFoundException e) {
			throw new UnsupportedSpecimenException("this SWT predates SVG support");
		}
	}

	static final class IconLabel extends FamilySpecimen {
		private final Function<Display, Image> source;

		IconLabel(String id, int width, int height, Function<Display, Image> source) {
			super(id, width, height);
			this.source = source;
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			requireSvgSupport();
			Label label = new Label(parent, SWT.CENTER);
			label.setBackground(ImageModule.backdrop());
			Image image = source.apply(parent.getDisplay());
			ImageModule.disposeWith(label, image);
			label.setImage(image);
			ctx.configure(label);
			return label;
		}
	}

	/** Destination-size drawing, which rasterizes the SVG again at that size instead of scaling pixels. */
	public static final class DrawAtSize extends FamilySpecimen {
		public DrawAtSize() {
			super("svg.draw.atsize", 100, 44);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			requireSvgSupport();
			ImageModule.requireDrawImageAtSize();
			Canvas canvas = new Canvas(parent, SWT.NONE);
			canvas.setBackground(ImageModule.backdrop());
			Image image = new Image(parent.getDisplay(), iconFile());
			ImageModule.disposeWith(canvas, image);
			canvas.addPaintListener(e -> {
				e.gc.drawImage(image, 2, 2, 40, 40);
				e.gc.drawImage(image, 52, 10, 24, 24);
				e.gc.drawImage(image, 82, 14, 12, 12);
			});
			ctx.configure(canvas);
			return canvas;
		}
	}

	public static final class ToolBarIcons extends FamilySpecimen {
		public ToolBarIcons() {
			super("svg.toolbar", 90, 36);
		}

		@Override
		public Control create(Composite parent, SpecimenContext ctx) {
			requireSvgSupport();
			ToolBar toolBar = new ToolBar(parent, SWT.FLAT);
			Image image = new Image(parent.getDisplay(), iconFile());
			ImageModule.disposeWith(toolBar, image);
			new ToolItem(toolBar, SWT.PUSH).setImage(image);
			ToolItem disabled = new ToolItem(toolBar, SWT.PUSH);
			disabled.setImage(image);
			disabled.setEnabled(false);
			ctx.configure(toolBar);
			return toolBar;
		}
	}
}
