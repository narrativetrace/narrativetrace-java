/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.core.context.NarrativeContext;
import org.junit.ClassRule;
import org.junit.Rule;

/**
 * Convenience base class that wires the {@link NarrativeTraceClassRule} / {@link
 * NarrativeTraceRule} pair with a single {@code extends}, for legacy JUnit 4 suites free to spend
 * their one superclass slot on it.
 *
 * <p>INTENT: Replace the two-rule ceremony
 *
 * <pre>{@code
 * @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();
 * @Rule public NarrativeTraceRule rule = classRule.testRule();
 * }</pre>
 *
 * <p>with {@code extends NarrativeTestCase}, plus a {@link #context()} accessor:
 *
 * <pre>{@code
 * public class OrderServiceTest extends NarrativeTestCase {
 *     @Test
 *     public void placesOrder() {
 *         var service = NarrativeTraceProxy.trace(impl, OrderService.class, context());
 *         service.placeOrder("item-1", 3);
 *     }
 * }
 * }</pre>
 *
 * <p><b>The two rules remain the PRIMARY API.</b> Java allows only single inheritance, and legacy
 * JUnit 4 suites routinely already extend a project's own base (a database fixture, a Spring test
 * base, a shared assertion helper) — taking that slot for NarrativeTrace would make the two
 * mutually exclusive. This is the same reason JUnit itself moved from the JUnit 3 {@code TestCase}
 * inheritance model to composable {@code @Rule}s: composition over a fixed base class stays usable
 * however a suite is already structured. {@code NarrativeTestCase} is the low-ceremony convenience
 * for suites that are free to take the slot, never a replacement for wiring the two rules directly.
 *
 * <p><b>@llmNote</b> A rejected alternative folded both rules into one field implementing both
 * {@code @ClassRule} and {@code @Rule}. That shape needs static state to bridge the class-level and
 * instance-level lifecycles in a single object, and static per-test state is exactly what breaks
 * under a parallel test runner — rejected for that reason, not for being harder to write. The
 * two-field shape kept here has the same per-test isolation the two-rule form already has.
 *
 * <p><b>@llmNote</b> {@link #classRule} is declared once, here, and inherited — not copied — by
 * every subclass: every {@code NarrativeTestCase} subclass running in one JVM shares one static
 * {@link NarrativeTraceClassRule} instance for the process lifetime. This is safe only because
 * {@link NarrativeTraceClassRule#apply} fully drains its per-class accumulator into the static
 * global one and clears itself before the next subclass's tests can populate it again — verified,
 * not assumed, by {@code NarrativeTestCaseInheritanceTest} (two subclasses: each gets fresh
 * per-test contexts, class-end artifacts fire per class, and no trace bleeds between them). Do not
 * add state to {@link NarrativeTraceClassRule} that survives past {@code afterAll()} without
 * re-checking that invariant first.
 *
 * @see NarrativeTraceClassRule
 * @see NarrativeTraceRule
 */
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
// abstract on purpose, with no abstract method to override: it exists to be extended for its
// @ClassRule/@Rule fields, never instantiated directly — see the class doc above.
public abstract class NarrativeTestCase {

  @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();

  @Rule public NarrativeTraceRule narrativeTrace = classRule.testRule();

  /**
   * Returns the current test's {@link NarrativeContext}.
   *
   * @return Context created for the currently running test method.
   */
  protected NarrativeContext context() {
    return narrativeTrace.context();
  }
}
