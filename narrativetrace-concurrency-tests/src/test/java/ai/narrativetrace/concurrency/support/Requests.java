/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import ai.narrativetrace.concurrency.shop.Checkout;
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Drives requests: one id per request, N of them from N threads at once. */
public final class Requests {

  /** The calls every request makes: the worker-side refs are {@code <requestId>:<sku>}. */
  public static final List<String> SKUS = List.of("sku-a", "sku-b", "sku-c");

  /** What a cell does to serve one request. */
  @FunctionalInterface
  public interface Serve {
    void request(String requestId) throws Exception;
  }

  private Requests() {}

  /** One request through the filter; the chain is the "controller": meet the others, then place. */
  public static void throughFilter(
      NarrativeTraceFilter filter, Overlap overlap, Checkout checkout, String requestId)
      throws Exception {
    throughFilter(filter, overlap, checkout, requestId, () -> {});
  }

  /** As above, with {@code alsoInChain} run in the chain after the checkout returned. */
  public static void throughFilter(
      NarrativeTraceFilter filter,
      Overlap overlap,
      Checkout checkout,
      String requestId,
      Runnable alsoInChain)
      throws Exception {
    throughFilter(filter, overlap, checkout, requestId, SKUS, alsoInChain);
  }

  /** As above, handing over work for {@code skus} instead of the matrix's three. */
  public static void throughFilter(
      NarrativeTraceFilter filter,
      Overlap overlap,
      Checkout checkout,
      String requestId,
      List<String> skus,
      Runnable alsoInChain)
      throws Exception {
    var request = new MockHttpServletRequest("GET", "/orders/" + requestId);
    request.setRemoteAddr("edge-proxy");
    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          overlap.meet();
          checkout.place(requestId, skus);
          alsoInChain.run();
        });
  }

  public static String id(int n) {
    return "REQ-%04d".formatted(n);
  }

  public static List<String> ids(int from, int count) {
    var ids = new ArrayList<String>();
    for (int i = 0; i < count; i++) {
      ids.add(id(from + i));
    }
    return ids;
  }

  /** Starts one thread per id, joins them all, and fails if any request threw. */
  public static void concurrently(List<String> ids, Serve serve) {
    var failures = new ConcurrentLinkedQueue<Throwable>();
    var threads = new ArrayList<Thread>();
    for (var id : ids) {
      var thread =
          new Thread(
              () -> {
                try {
                  serve.request(id);
                } catch (Throwable t) { // NOPMD - collected and rethrown on the test thread
                  failures.add(t);
                }
              },
              "request-" + id);
      threads.add(thread);
      thread.start();
    }
    join(threads);
    if (!failures.isEmpty()) {
      var first = failures.peek();
      throw new AssertionError("a request failed: " + failures, first);
    }
  }

  private static void join(List<Thread> threads) {
    for (var thread : threads) {
      try {
        thread.join(TimeUnit.SECONDS.toMillis(60));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      if (thread.isAlive()) {
        throw new AssertionError("request thread " + thread.getName() + " did not finish");
      }
    }
  }
}
