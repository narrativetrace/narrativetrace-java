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
 */
package ai.narrativetrace.agent;
