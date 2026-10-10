/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

// The framework table's Spring row embeds the region below as its wiring snippet — the doctor's
// config.spring-enabled fix line, llms-full.md's integration table. NarrativeTraceConfigTest
// proves it wires: a bean under the base package comes back traced.
// snippet:begin wiring
import ai.narrativetrace.spring.EnableNarrativeTrace;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableNarrativeTrace(basePackages = "com.example")
public class NarrativeTraceConfig {}
// snippet:end wiring
