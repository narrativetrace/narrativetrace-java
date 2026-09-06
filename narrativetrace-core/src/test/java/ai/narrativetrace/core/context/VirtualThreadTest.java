/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
  void basicTraceOnVirtualThread() throws Exception {
    var context = new ThreadLocalNarrativeContext();

    ExecutorService executor = virtualThreadExecutor();
    try {
      var tree =
          executor
              .submit(
                  () -> {
                    context.enterMethod(new MethodSignature("VirtualService", "handle", List.of()));
                    context.exitMethodWithReturn("vt-result");
                    return context.captureTrace();
                  })
              .get();

      assertThat(tree.roots()).hasSize(1);
      assertThat(tree.roots().get(0).signature().className()).isEqualTo("VirtualService");
      assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("handle");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void snapshotPropagationToVirtualThread() throws Exception {
    var context = new ThreadLocalNarrativeContext();

    // Trace on parent thread
    context.enterMethod(new MethodSignature("ParentService", "parentMethod", List.of()));
    context.exitMethodWithReturn("parent-result");

    var snapshot = context.snapshot();

    ExecutorService executor = virtualThreadExecutor();
    try {
      var childTree =
          executor
              .submit(
                  () -> {
                    try (var scope = snapshot.activate()) {
                      context.enterMethod(
                          new MethodSignature("ChildService", "childMethod", List.of()));
                      context.exitMethodWithReturn("child-result");
                      return context.captureTrace();
                    }
                  })
              .get();

      // Child thread captures its own trace independently
      assertThat(childTree.roots()).hasSize(1);
      assertThat(childTree.roots().get(0).signature().className()).isEqualTo("ChildService");

      // The virtual thread ran under the parent's snapshot, so its story joins the parent's
      // trace as a second root — a virtual carrier thread changes nothing about adoption.
      var parentTree = context.captureTrace();
      assertThat(parentTree.roots()).hasSize(2);
      assertThat(parentTree.roots().get(0).signature().className()).isEqualTo("ParentService");
      assertThat(parentTree.roots().get(1).signature().className()).isEqualTo("ChildService");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void thousandConcurrentVirtualThreadsNoCrossContamination() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    var snapshot = context.snapshot();

    ExecutorService executor = virtualThreadExecutor();
    try {
      List<Future<ai.narrativetrace.api.tree.TraceTree>> futures = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        int index = i;
        futures.add(
            executor.submit(
                () -> {
                  try (var scope = snapshot.activate()) {
                    context.enterMethod(
                        new MethodSignature("Service" + index, "method" + index, List.of()));
                    context.exitMethodWithReturn("result-" + index);
                    return context.captureTrace();
                  }
                }));
      }

      for (int i = 0; i < 1000; i++) {
        var tree = futures.get(i).get();
        assertThat(tree.roots()).hasSize(1);
        assertThat(tree.roots().get(0).signature().className()).isEqualTo("Service" + i);
        assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("method" + i);
      }
    } finally {
      executor.shutdown();
    }
  }
}
