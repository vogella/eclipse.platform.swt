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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.visualoracle.spi.RenderEnv;

/**
 * The concrete process configuration that realises one {@link RenderEnv} in a
 * child JVM: environment variables, variables to remove, and JVM properties.
 * Produced by {@link SwtRenderEnvs#launch(RenderEnv)}.
 *
 * @param env the environment this configuration realises
 * @param variables environment variables to set in the child process
 * @param removedVariables environment variables to remove, so a platform
 *     default is not defeated by an inherited override
 * @param jvmProperties JVM properties to pass to the child
 */
public record LaunchConfig(RenderEnv env, Map<String, String> variables,
		Set<String> removedVariables, List<String> jvmProperties) {

	public LaunchConfig {
		variables = Map.copyOf(variables);
		removedVariables = Set.copyOf(removedVariables);
		jvmProperties = List.copyOf(jvmProperties);
	}

	/** Applies this configuration to a process about to be started. */
	public void applyTo(ProcessBuilder pb) {
		pb.environment().putAll(variables);
		for (String name : removedVariables)
			pb.environment().remove(name);
	}
}
