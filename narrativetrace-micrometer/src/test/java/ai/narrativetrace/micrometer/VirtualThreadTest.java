/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

@EnabledForJreRange(min = JRE.JAVA_21)
class VirtualThreadTest {

  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static ExecutorService virtualThreadExecutor() throws Exception {
    return (ExecutorService)
        Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
  }

  @Test
  void contextSnapshotFactoryPropagationViaVirtualThread() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var accessor = new NarrativeTraceThreadLocalAccessor(context);

    var registry = new ContextRegistry();
    registry.registerThreadLocalAccessor(accessor);

    var factory = ContextSnapshotFactory.builder().contextRegistry(registry).build();

    // Capture snapshot on main thread
    var micrometerSnapshot = factory.captureAll();

    ExecutorService executor = virtualThreadExecutor();
    try {
      var childTree =
          executor
              .submit(
                  () -> {
                    try (var scope = micrometerSnapshot.setThreadLocals()) {
                      context.enterMethod(
                          new MethodSignature("VirtualService", "process", List.of()));
                      context.exitMethodWithReturn("done");
                      return context.captureTrace();
                    }
                  })
              .get();

      assertThat(childTree.roots()).hasSize(1);
      assertThat(childTree.roots().get(0).signature().className()).isEqualTo("VirtualService");
      assertThat(childTree.roots().get(0).signature().methodName()).isEqualTo("process");
    } finally {
      executor.shutdown();
    }
  }
}
