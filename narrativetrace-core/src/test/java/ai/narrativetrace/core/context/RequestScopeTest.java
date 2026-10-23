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
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.pipeline.BufferedEventConsumer;
import ai.narrativetrace.core.pipeline.DualPathPipeline;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** B-157: a request owns its trace scope; interleaved requests on one thread stay apart. */
class RequestScopeTest {

  private final DualPathPipeline pipeline =
      new DualPathPipeline(null, new BufferedEventConsumer(64, false));
  private final ThreadLocalNarrativeContext context =
      new ThreadLocalNarrativeContext(new NarrativeTraceConfig(), pipeline);

  @AfterEach
  void close() {
    context.reset();
    pipeline.close();
  }

  private void call(String method) {
    var span = context.enterMethod(new MethodSignature("Service", method, List.of()));
    context.exitMethodWithReturn("1", span);
  }

  private List<String> rootsIn(RequestScope scope) {
    try (var active = scope.activate()) {
      return context.captureTrace().roots().stream()
          .map(node -> node.signature().methodName())
          .toList();
    }
  }

  @Test
  void interleavedRequestsOnOneThreadKeepTheirOwnTraces() {
    var a = context.openRequestScope();
    var b = context.openRequestScope();
    try (var active = a.activate()) {
      call("first");
    }
    try (var active = b.activate()) {
      call("second");
    }

    assertThat(rootsIn(a)).containsExactly("first");
    a.end();
    assertThat(rootsIn(b)).as("ending A must not erase B").containsExactly("second");
    b.end();
    assertThat(context.retainedSpanCount()).isZero();
  }

  @Test
  void activationPutsBackExactlyWhatTheThreadHeld() {
    call("own");
    var scope = context.openRequestScope();
    try (var active = scope.activate()) {
      assertThat(context.captureTrace().isEmpty()).isTrue();
    }
    scope.end();

    assertThat(context.captureTrace().roots()).hasSize(1);
  }

  @Test
  void aThreadThatHeldNothingHoldsNothingAfterward() {
    var scope = context.openRequestScope();
    try (var active = scope.activate()) {
      call("work");
    }
    scope.end();
    scope.end();

    assertThat(context.retainedSpanCount()).isZero();
    assertThat(context.currentSpanId()).isNull();
  }

  @Test
  void aContextWithoutPerRequestStateSharesTheThreadAndResetsAtBothEnds() {
    var resets = new int[1];
    NarrativeContext shared =
        (NarrativeContext)
            java.lang.reflect.Proxy.newProxyInstance(
                NarrativeContext.class.getClassLoader(),
                new Class<?>[] {NarrativeContext.class},
                (proxy, method, args) -> {
                  if (method.isDefault()) {
                    return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                  }
                  if ("reset".equals(method.getName())) {
                    resets[0]++;
                  }
                  return null;
                });

    var scope = shared.openRequestScope();
    scope.activate().close();
    scope.end();

    assertThat(resets[0]).isEqualTo(2);
  }
}
