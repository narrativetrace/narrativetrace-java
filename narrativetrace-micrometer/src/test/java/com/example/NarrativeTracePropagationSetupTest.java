/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The Micrometer row's wiring snippet, run: after the one registration the doctor's fix line and
 * the docs show, a snapshot captured on one thread carries the trace onto another. It registers on
 * the GLOBAL registry exactly as the snippet does, so it removes the accessor again afterwards.
 */
class NarrativeTracePropagationSetupTest {

  @AfterEach
  void unregister() {
    ContextRegistry.getInstance().removeThreadLocalAccessor("narrativetrace");
  }

  @Test
  void aCapturedSnapshotCarriesTheTraceOntoAnotherThread() throws Exception {
    var context = new ThreadLocalNarrativeContext();
    NarrativeTracePropagationSetup.register(context);
    var snapshot = ContextSnapshotFactory.builder().build().captureAll();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      TraceTree childTree =
          executor
              .submit(
                  () -> {
                    try (var scope = snapshot.setThreadLocals()) {
                      context.enterMethod(new MethodSignature("Worker", "process", List.of()));
                      context.exitMethodWithReturn("done");
                      return context.captureTrace();
                    }
                  })
              .get();
      assertThat(childTree.roots()).hasSize(1);
    } finally {
      executor.shutdown();
    }
  }
}
