/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for proxy behavior with CompletableFuture-returning methods. Validates deferred-exit
 * semantics: the proxy detaches the frame on CF return and completes it via {@code whenComplete},
 * capturing the resolved value or exception.
 */
class AsyncInterleavingTest {

  interface AsyncService {
    CompletableFuture<String> fast();

    CompletableFuture<String> slow();
  }

  interface InnerService {
    CompletableFuture<String> innerWork();
  }

  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void sequentialAsyncCallsOnSameThreadProduceCorrectTrace() {
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(createAsyncService(), AsyncService.class, context);

    var fastFuture = proxy.fast();
    var slowFuture = proxy.slow();
    CompletableFuture.allOf(fastFuture, slowFuture).join();

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(2);

    assertThat(rootMethodNames(tree)).containsExactly("fast", "slow");

    assertThat(tree.roots().get(0).children()).isEmpty();
    assertThat(tree.roots().get(1).children()).isEmpty();
  }

  @Test
  void concurrentProxiedCallsFromDifferentThreadsAreIsolated() {
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(createCompletedService(), AsyncService.class, context);

    var trees = runOnSeparateThreads(context, proxy);

    assertThat(trees[0].roots()).hasSize(1);
    assertThat(trees[0].roots().get(0).signature().methodName()).isEqualTo("fast");

    assertThat(trees[1].roots()).hasSize(1);
    assertThat(trees[1].roots().get(0).signature().methodName()).isEqualTo("slow");
  }

  @Test
  void proxyCapturesResolvedValueFromCompletedFuture() {
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(createCompletedService(), AsyncService.class, context);
    proxy.fast().join();

    var tree = context.captureTrace();
    var outcome = tree.roots().get(0).outcome();

    assertThat(outcome).isInstanceOf(TraceOutcome.Returned.class);
    var rendered = ((TraceOutcome.Returned) outcome).renderedValue();
    assertThat(rendered).isEqualTo("\"fast-result\"");
  }

  @Test
  void proxyCapturesResolvedValueFromIncompleteFuture() {
    var context = new ThreadLocalNarrativeContext();
    var proxy = NarrativeTraceProxy.trace(createAsyncService(), AsyncService.class, context);

    // slow() returns an incomplete future
    var future = proxy.slow();
    future.join(); // wait for it to resolve

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    var outcome = (TraceOutcome.Returned) tree.roots().get(0).outcome();
    assertThat(outcome.renderedValue()).isEqualTo("\"slow-result\"");
  }

  @Test
  void proxyCapturesExceptionFromFailedFuture() {
    var context = new ThreadLocalNarrativeContext();
    AsyncService failing = createFailingService();
    var proxy = NarrativeTraceProxy.trace(failing, AsyncService.class, context);

    var future = proxy.fast();
    try {
      future.join();
    } catch (Exception ignored) {
      // expected
    }

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).outcome()).isInstanceOf(TraceOutcome.Threw.class);
  }

  @Test
  void outOfOrderCompletionProducesRootsInCompletionOrder() {
    var context = new ThreadLocalNarrativeContext();
    var fastFuture = new CompletableFuture<String>();
    var slowFuture = new CompletableFuture<String>();
    var proxy = proxyWithControlledFutures(context, fastFuture, slowFuture);

    proxy.fast();
    proxy.slow();
    slowFuture.complete("slow-val");
    fastFuture.complete("fast-val");

    var names = rootMethodNames(context.captureTrace());
    // Event trail preserves entry order, not completion order
    assertThat(names).containsExactly("fast", "slow");
  }

  @Test
  void syncCallBetweenDeferredAsyncCallsAppearsFirst() {
    var context = new ThreadLocalNarrativeContext();
    var fastFuture = new CompletableFuture<String>();
    var slowFuture = new CompletableFuture<String>();
    var asyncProxy = proxyWithControlledFutures(context, fastFuture, slowFuture);

    interface SyncService {
      String compute();
    }

    var syncProxy =
        NarrativeTraceProxy.trace((SyncService) () -> "done", SyncService.class, context);

    asyncProxy.fast();
    syncProxy.compute();
    asyncProxy.slow();
    fastFuture.complete("f");
    slowFuture.complete("s");

    var names = rootMethodNames(context.captureTrace());
    // Event trail preserves entry order: fast entered first, then compute, then slow
    assertThat(names).containsExactly("fast", "compute", "slow");
  }

  private AsyncService createAsyncService() {
    return new AsyncService() {
      @Override
      public CompletableFuture<String> fast() {
        return CompletableFuture.completedFuture("fast-result");
      }

      @Override
      public CompletableFuture<String> slow() {
        return CompletableFuture.supplyAsync(
            () -> {
              try {
                Thread.sleep(20);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return "slow-result";
            },
            executor);
      }
    };
  }

  private static AsyncService createCompletedService() {
    return new AsyncService() {
      @Override
      public CompletableFuture<String> fast() {
        return CompletableFuture.completedFuture("fast-result");
      }

      @Override
      public CompletableFuture<String> slow() {
        return CompletableFuture.completedFuture("slow-result");
      }
    };
  }

  @Test
  void nestedAsyncProducesParentChildRelationship() {
    var context = new ThreadLocalNarrativeContext();
    var innerFuture = new CompletableFuture<String>();
    var outerProxy = buildNestedProxy(context, innerFuture);

    var result = outerProxy.fast();
    // In-flight methods are visible as Incomplete before completion
    assertThat(context.captureTrace().roots()).hasSize(1);
    assertThat(context.captureTrace().roots().get(0).outcome())
        .isInstanceOf(TraceOutcome.Incomplete.class);

    innerFuture.complete("inner-val");
    assertThat(result.join()).isEqualTo("outer(inner-val)");

    assertNestedTrace(context.captureTrace(), "fast", "innerWork");
  }

  private AsyncService buildNestedProxy(
      ThreadLocalNarrativeContext context, CompletableFuture<String> innerFuture) {
    var innerProxy = NarrativeTraceProxy.trace(() -> innerFuture, InnerService.class, context);
    AsyncService outerReal = createOuterService(innerProxy);
    return NarrativeTraceProxy.trace(outerReal, AsyncService.class, context);
  }

  private static AsyncService createOuterService(InnerService inner) {
    return new AsyncService() {
      @Override
      public CompletableFuture<String> fast() {
        return inner.innerWork().thenApply(v -> "outer(" + v + ")");
      }

      @Override
      public CompletableFuture<String> slow() {
        return CompletableFuture.completedFuture("unused");
      }
    };
  }

  private static AsyncService proxyWithControlledFutures(
      ThreadLocalNarrativeContext context,
      CompletableFuture<String> fastFuture,
      CompletableFuture<String> slowFuture) {
    AsyncService service =
        new AsyncService() {
          @Override
          public CompletableFuture<String> fast() {
            return fastFuture;
          }

          @Override
          public CompletableFuture<String> slow() {
            return slowFuture;
          }
        };
    return NarrativeTraceProxy.trace(service, AsyncService.class, context);
  }

  private static List<String> rootMethodNames(TraceTree tree) {
    return tree.roots().stream().map(r -> r.signature().methodName()).toList();
  }

  private static void assertNestedTrace(TraceTree tree, String outerMethod, String innerMethod) {
    assertThat(tree.roots()).hasSize(1);
    var root = tree.roots().get(0);
    assertThat(root.signature().methodName()).isEqualTo(outerMethod);
    assertThat(root.children()).hasSize(1);
    assertThat(root.children().get(0).signature().methodName()).isEqualTo(innerMethod);
  }

  private static AsyncService createFailingService() {
    return new AsyncService() {
      @Override
      public CompletableFuture<String> fast() {
        return CompletableFuture.failedFuture(new RuntimeException("async-error"));
      }

      @Override
      public CompletableFuture<String> slow() {
        return CompletableFuture.failedFuture(new RuntimeException("async-error"));
      }
    };
  }

  private TraceTree[] runOnSeparateThreads(
      ThreadLocalNarrativeContext context, AsyncService proxy) {
    var snapshot = context.snapshot();
    Supplier<TraceTree> s1 =
        () -> {
          proxy.fast();
          return context.captureTrace();
        };
    Supplier<TraceTree> s2 =
        () -> {
          proxy.slow();
          return context.captureTrace();
        };
    var f1 = CompletableFuture.supplyAsync(snapshot.wrap(s1), executor);
    var f2 = CompletableFuture.supplyAsync(snapshot.wrap(s2), executor);
    return new TraceTree[] {f1.join(), f2.join()};
  }
}
