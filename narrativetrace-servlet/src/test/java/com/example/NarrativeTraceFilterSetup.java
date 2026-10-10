/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

// The framework table's servlet row embeds the region below as its wiring snippet — the doctor's
// config.servlet-filter fix line, llms-full.md's integration table. NarrativeTraceFilterSetupTest
// proves it wires: the container is handed the filter, mapped to every request.
// snippet:begin wiring
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import ai.narrativetrace.servlet.Slf4jTraceExporter;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class NarrativeTraceFilterSetup implements ServletContextListener {
  @Override
  public void contextInitialized(ServletContextEvent event) {
    var filter =
        new NarrativeTraceFilter(new ThreadLocalNarrativeContext(), new Slf4jTraceExporter());
    event
        .getServletContext()
        .addFilter("narrativetrace", filter)
        .addMappingForUrlPatterns(null, false, "/*");
  }
}
// snippet:end wiring
