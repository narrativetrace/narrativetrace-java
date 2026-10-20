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
 * import, or interface — the {@code -javaagent} the {@code agentTest}/{@code agentTestJunit4} tasks
 * attach (see {@code build.gradle.kts}) weaves it at class-load time. The test fixtures over in
 * {@code src/test} carry no tracing code either, only {@code @ExtendWith(NarrativeTraceExtension
 * .class)} (JUnit 5) or the linked {@code NarrativeTraceClassRule}/{@code NarrativeTraceRule} pair
 * (JUnit 4) — the ordinary way any test opts into per-test files. Since §176, that is sufficient on
 * its own: both integrations route the agent's shared capture (the {@code
 * ai.narrativetrace.agent.AgentRuntime} registry) into their own lifecycle-owned context, so a
 * class the agent wove writes the same Markdown/structural/JSON/diagram files a proxy-traced test
 * always has.
 *
 * <p>INTENT: Demonstrate the automatic attachment path. See this module's {@code README.md} for how
 * it contrasts with the explicit proxy path ({@code narrativetrace-junit4-example}) and with
 * container integrations (Spring, Micronaut, servlet).
 */
package ai.narrativetrace.examples.agent;
