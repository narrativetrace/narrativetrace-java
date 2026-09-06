/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Tutorial example: a plain service traced with zero code by the java agent.
 *
 * <p>{@link ai.narrativetrace.examples.agent.GreetingService} carries no NarrativeTrace annotation,
 * import, or interface — the {@code -javaagent} the {@code agentTest} task attaches (see {@code
 * build.gradle.kts}) weaves it at class-load time. {@code AgentNarrationExtension}, over in {@code
 * src/test}, does no tracing itself either: it only resets and prints what the agent already
 * recorded, through the shared runtime registry ({@code ai.narrativetrace.agent.AgentRuntime}).
 *
 * <p>INTENT: Demonstrate the automatic attachment path. See this module's {@code README.md} for how
 * it contrasts with the explicit proxy path ({@code narrativetrace-junit4-example}) and with
 * container integrations (Spring, Micronaut, servlet).
 */
package ai.narrativetrace.examples.agent;
