/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.export.RequestContext;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.concurrency.support.Await;
import ai.narrativetrace.concurrency.support.Handover;
import ai.narrativetrace.concurrency.support.ServletAssembly;
import ai.narrativetrace.concurrency.support.TraceFacts;
import ai.narrativetrace.core.context.SoakProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * B-211's production claim, end to end: 20 000 requests through the servlet filter, each handing
 * work to a shared pool (ForkGroup, handed over) while ALSO leaving unhanded background work on the
 * same pool threads. The event store and the context's span maps stay bounded by a constant, and
 * every request's capture stays exactly its own work — so the cost of a capture cannot grow with
 * the number of requests served.
 *
 * <p>No timing is asserted: the capture's cost is its input, the events the flush moves and the
 * spans the capture walks, and both are counted. The wall-clock readings in the output are
 * information for a reader, never an input to a verdict.
 */
class BoundedMemorySoakTest {

  private static final int REQUESTS = 20_000;
  private static final int CHECKPOINT_EVERY = 2_500;
  private static final int WORKERS = 4;
  private static final List<String> HANDED_OVER = List.of("sku-a", "sku-b");

  /**
   * The unowned-span limit the soak runs under (the B-211 seam; production's is {@link
   * SoakProbe#unownedSpanLimit()}). Lowered so 20 000 requests cross the release boundary forty
   * times in seconds: at the production limit each capture scans a store a thousand times larger
   * and the same run measured 200 s.
   */
  private static final int LIMIT = 500;

  /**
   * What "bounded" means: a few limits' worth, a constant, whatever the request count — against the
   * ~6 events and ~3 span contexts per request the unfixed code kept for ever (120 000 events and
   * 60 000 span contexts at 20 000 requests).
   */
  private static final int BOUND = 4 * LIMIT;

  @Test
  void twentyThousandRequestsWithHandedOverAndUnhandedPoolWorkLeaveABoundedStoreAndBoundedMaps()
      throws Exception {
    var verifier = new PerRequestVerifier();
    try (var assembly =
        ServletAssembly.withUnownedLimit(Handover.FORK_GROUP, WORKERS, verifier, LIMIT)) {
      var readings = new ArrayList<String>();
      var windowStart = System.nanoTime();
      for (int i = 1; i <= REQUESTS; i++) {
        serveOne(assembly, verifier, i);
        if (i % CHECKPOINT_EVERY == 0) {
          var windowMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - windowStart);
          readings.add(checkpoint(assembly, i) + " windowMillis(information only)=" + windowMillis);
          windowStart = System.nanoTime();
        }
      }
      readings.forEach(System.out::println);
      assertThat(verifier.violations()).as("every request exported exactly its own work").isEmpty();
      assertThat(verifier.exports()).isEqualTo(REQUESTS);
    }
  }

  private static void serveOne(ServletAssembly assembly, PerRequestVerifier verifier, int n)
      throws Exception {
    var id = "REQ-%04d".formatted(n);
    verifier.expect(id);
    assembly.serve(
        id,
        HANDED_OVER,
        () -> {
          // Unhanded background work: a plain submit on the same pool, nobody wraps it.
          Future<String> background =
              assembly.pool.submit(() -> assembly.inventory.reserve("BG-" + n));
          Await.one(background);
        });
  }

  private static String checkpoint(ServletAssembly assembly, int served) throws Exception {
    assembly.pipeline.flush();
    var storeEvents = assembly.pipeline.events().size();
    var retained = SoakProbe.retainedSpans(assembly.context);
    var poolKnown = poolThreadsKnownSpans(assembly);
    assertThat(storeEvents)
        .as("events in the store after %d requests", served)
        .isLessThanOrEqualTo(BOUND);
    assertThat(retained)
        .as("span contexts held after %d requests", served)
        .isLessThanOrEqualTo(BOUND);
    assertThat(poolKnown)
        .as("known spans on the busiest pool thread after %d requests", served)
        .isLessThanOrEqualTo(BOUND);
    return "soak checkpoint requests=%d storeEvents=%d retainedSpanContexts=%d maxPoolThreadKnownSpans=%d"
        .formatted(served, storeEvents, retained, poolKnown);
  }

  /**
   * Every pool thread's own known-span count, read on that thread (a barrier pins four threads).
   */
  private static int poolThreadsKnownSpans(ServletAssembly assembly) {
    var barrier = new CyclicBarrier(WORKERS);
    List<Future<Integer>> counts = new ArrayList<>();
    for (int i = 0; i < WORKERS; i++) {
      counts.add(
          assembly.pool.submit(
              () -> {
                barrier.await(30, TimeUnit.SECONDS);
                return SoakProbe.knownSpansOnThisThread(assembly.context);
              }));
    }
    return counts.stream().mapToInt(Await::one).max().orElse(0);
  }

  /** Checks each export as it arrives and keeps nothing: 20 000 trees would be the leak itself. */
  static final class PerRequestVerifier implements TraceExporter {

    private final ConcurrentLinkedQueue<String> violations = new ConcurrentLinkedQueue<>();
    private final AtomicInteger exports = new AtomicInteger();
    private volatile String expected = "";

    void expect(String requestId) {
      expected = requestId;
    }

    @Override
    public void export(TraceTree tree, RequestContext requestContext) {
      exports.incrementAndGet();
      var place = TraceFacts.only(tree, "place");
      var reserves = TraceFacts.named(TraceFacts.descendantsOf(place), "reserve");
      var all = TraceFacts.named(TraceFacts.all(tree), "reserve");
      boolean ownValues =
          reserves.stream().allMatch(n -> TraceFacts.firstArgument(n).contains(expected + ":"));
      if (reserves.size() != HANDED_OVER.size() || all.size() != reserves.size() || !ownValues) {
        addViolation(expected, reserves.size(), all.size());
      }
    }

    private void addViolation(String id, int nested, int total) {
      if (violations.size() < 10) {
        violations.add(id + ": nested=" + nested + " total=" + total);
      }
    }

    List<String> violations() {
      return List.copyOf(violations);
    }

    int exports() {
      return exports.get();
    }
  }
}
