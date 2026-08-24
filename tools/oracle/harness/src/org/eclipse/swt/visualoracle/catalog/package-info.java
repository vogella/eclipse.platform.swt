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
/**
 * The specimen catalog: one {@code *Module} class per widget family, each
 * discovered automatically by {@code SpecimenCatalog}.
 *
 * Add a family by adding one module class and its specimen classes here.
 * Never add a central registration list: several agents extend this package in
 * parallel and a shared list would make all of them conflict.
 */
package org.eclipse.swt.visualoracle.catalog;
