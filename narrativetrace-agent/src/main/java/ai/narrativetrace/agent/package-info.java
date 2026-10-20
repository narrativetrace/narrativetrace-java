/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Java agent for bytecode-level tracing built on ASM.
 *
 * <p>{@link ai.narrativetrace.agent.NarrativeTraceAgent} registers a class file transformer that
 * instruments matching classes at load time. The transformer performs a metadata-collection pass
 * and then injects capture calls into method entry, normal return, and exceptional exit paths.
 *
 * <p>INTENT: Use this module when proxy-based tracing is not enough because targets are concrete
 * classes, third-party types, or objects created outside your control.
 *
 * <p><b>@llmNote</b> Classes the agent's own runtime is not visible from load
 * <em>uninstrumented</em>, whatever the package filter says. Injected bytecode calls {@link
 * ai.narrativetrace.agent.AgentRuntime} by name, so weaving a class whose defining loader cannot
 * resolve that type would replace the host's own call with a {@code NoClassDefFoundError}. This
 * covers bootstrap-injected classes (mockito's inline mock maker is the published case) and
 * isolated loaders that do not delegate to the agent's. The agent reports each such loader once on
 * {@code stderr} — see {@code LoaderVisibility}.
 */
package ai.narrativetrace.agent;
