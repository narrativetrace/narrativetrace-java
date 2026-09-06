/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Tutorial example: tracing a small e-commerce flow in Spring.
 *
 * <p>This package demonstrates the end-to-end developer experience for a realistic service graph:
 * Spring bean wiring, auto-proxy tracing, SLF4J event logging, Micrometer-backed thread
 * propagation, async notifications, success paths, and failure scenarios.
 *
 * <h2>Suggested reading order</h2>
 *
 * <ol>
 *   <li>{@link ai.narrativetrace.examples.ecommerce.ECommerceExample}: walkthrough entry point that
 *       runs all scenarios and prints multiple renderings.
 *   <li>{@link ai.narrativetrace.examples.ecommerce.ECommerceConfig}: infrastructure wiring showing
 *       how the example composes Spring, SLF4J, Micrometer, and NarrativeTrace.
 *   <li>{@link ai.narrativetrace.examples.ecommerce.DefaultOrderService}: orchestration logic that
 *       produces the most interesting traces.
 *   <li>The in-memory and external-service adapters: simple implementations meant to keep the trace
 *       easy to follow.
 * </ol>
 *
 * <h2>What this tutorial teaches</h2>
 *
 * <ul>
 *   <li>How a shared {@code NarrativeContext} is registered in Spring and reused by traced beans.
 *   <li>How async work is captured and why context propagation matters.
 *   <li>How traces reveal domain failures such as payment decline, unknown customer, and
 *       out-of-stock branches.
 *   <li>How the same captured tree can be rendered as indented text, prose, Mermaid markup, and an
 *       ASCII sequence diagram drawn in the console via PlantUML.
 * </ul>
 *
 * <p>INTENT: If you are evaluating NarrativeTrace for a production-style Java/Spring service, start
 * here.
 */
package ai.narrativetrace.examples.ecommerce;
