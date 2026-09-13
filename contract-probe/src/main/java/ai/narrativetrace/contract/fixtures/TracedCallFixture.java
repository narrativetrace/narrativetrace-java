/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.contract.fixtures;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * One passing test run through the real, published JUnit 5 extension (installation-guide.md Option
 * C) — reused by every probe that needs to observe an extension-gated default (approval mode,
 * output-on-by-default) rather than a hand-rolled context, since those defaults are read only
 * inside the extension's own lifecycle. Not named {@code *Test}: Gradle's own {@code test} task
 * must never try to run this directly — each probe launches it explicitly (see {@code
 * JUnitLauncherSupport}) after setting the system properties it wants to observe.
 */
@ExtendWith(NarrativeTraceExtension.class)
public class TracedCallFixture {

  public interface Greeter {
    String greet(String name);
  }

  @Test
  void tracedCall(NarrativeContext context) {
    Greeter greeter =
        NarrativeTraceProxy.trace((Greeter) name -> "hello " + name, Greeter.class, context);
    greeter.greet("world");
  }
}
