/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
/**
 * Concurrency specifications for the context's async seams.
 *
 * <p>INTENT: The scenarios in {@code ai.narrativetrace.core.pipeline} stress the event path — the
 * ring, the consumer, the store. These stress the other half of the dual-path core: the per-thread
 * {@code TraceStack}, the live-child registry that makes a worker's spans visible to the thread
 * that will capture them, the adoption ceiling that refuses a batch whole, and {@code reset()},
 * which has to end a request without touching a concurrent one.
 *
 * <p>What they have in common is that the failure is never an exception. A live-child race loses a
 * subtree from a narrative; a partial adoption produces a tree that asserts a call graph which
 * never happened; a reset that reaches too far deletes another request's evidence, and one that
 * reaches too little leaks it for the life of the process. All four are silent, and all four are
 * invisible to a test that runs on one thread.
 *
 * <p>Typical workflow:
 *
 * <ol>
 *   <li>Read the scenario Javadoc for the invariant, which is usually a sentence from ADR-013 or
 *       from {@code TraceStack}'s own documentation.
 *   <li>Check the {@code @Outcome} matrix: acceptable, interesting, forbidden.
 *   <li>Treat a forbidden outcome as a trace that would have been wrong, not merely incomplete.
 * </ol>
 *
 * <p><b>@layer</b> Verification
 *
 * <p><b>@pattern</b> Adoption and lifecycle race validation
 *
 * <p><b>@llmNote</b> These scenarios reach package-private types on purpose — {@code TraceStack} is
 * internal, and the invariants it keeps have no public surface to assert them through. The classes
 * live in the same package for exactly the reason the core's own unit tests do.
 *
 * <p><b>@edgeCase</b> Nothing here starts a thread or a drain loop. jcstress builds a state per
 * iteration, millions of times, so a scenario that allocated a real request context with the
 * default 65,536-slot ring would measure the allocator instead of the race.
 */
package ai.narrativetrace.core.context;
