/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import ai.narrativetrace.api.event.ServiceIdentity;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.pipeline.PipelineBootstrap;

/**
 * Internal factory for the Spring-managed {@link NarrativeContext}.
 *
 * <p>INTENT: The registrar uses this to create a context wired to whatever topology the deployment
 * configured. Composition — narration when the slf4j module is present, discovered extensions,
 * topology selection — belongs to {@link PipelineBootstrap}; this factory only supplies the Spring
 * -specific inputs (logger name and service identity).
 *
 * <p><b>@edgeCase</b> An empty logger name means "do not narrate": the context still captures, so
 * {@code captureTrace()} works as usual.
 */
final class NarrativeTraceContextFactory {

  private NarrativeTraceContextFactory() {}

  static NarrativeContext createContext(String loggerName) {
    return createContext(loggerName, null);
  }

  static NarrativeContext createContext(String loggerName, ServiceIdentity serviceIdentity) {
    return new ThreadLocalNarrativeContext(
        new NarrativeTraceConfig(), PipelineBootstrap.createDefault(loggerName), serviceIdentity);
  }
}
