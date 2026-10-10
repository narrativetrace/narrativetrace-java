/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

// The framework table's Spring Web row embeds the region below as its wiring snippet — the
// doctor's config.spring-web-filter fix line, llms-full.md's integration table.
// NarrativeTraceWebConfigTest proves it wires: the request filter is a bean.
// snippet:begin wiring
import ai.narrativetrace.spring.EnableNarrativeTrace;
import ai.narrativetrace.spring.web.NarrativeTraceWebConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableNarrativeTrace(basePackages = "com.example")
@Import(NarrativeTraceWebConfiguration.class)
public class NarrativeTraceWebConfig {}
// snippet:end wiring
