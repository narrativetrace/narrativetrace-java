/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.export.RequestContext;
import ai.narrativetrace.api.export.RequestContextProvider;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class NarrativeTraceFilterTest {

  private final ThreadLocalNarrativeContext context = new ThreadLocalNarrativeContext();

  @AfterEach
  void tearDown() {
    MDC.clear();
    context.reset();
  }

  @Test
  void implementsFilter() {
    var filter = new NarrativeTraceFilter(context, (tree, req) -> {});

    assertThat(filter).isInstanceOf(Filter.class);
  }

  @Test
  void callsChainDoFilter() throws Exception {
    var filter = new NarrativeTraceFilter(context, (tree, req) -> {});
    var request = new StubHttpServletRequest("GET", "/api/test");
    var response = new StubHttpServletResponse();
    var chainRequest = new AtomicReference<ServletRequest>();
    FilterChain chain = (req, res) -> chainRequest.set(req);

    filter.doFilter(request, response, chain);

    assertThat(chainRequest.get()).isSameAs(request);
  }

  @Test
  void resetsContextBeforeRequest() throws Exception {
    context.enterMethod(new MethodSignature("Stale", "leftover", List.of()));
    context.exitMethodWithReturn("\"stale\"");

    var contextEmptyInsideChain = new AtomicBoolean(false);
    FilterChain chain = (req, res) -> contextEmptyInsideChain.set(context.captureTrace().isEmpty());

    var filter = new NarrativeTraceFilter(context, (tree, req) -> {});
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    assertThat(contextEmptyInsideChain).isTrue();
  }

  @Test
  void resetsContextAfterRequest() throws Exception {
    FilterChain chain =
        (req, res) -> {
          context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
          context.exitMethodWithReturn("\"ok\"");
        };

    var filter = new NarrativeTraceFilter(context, (tree, req) -> {});
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    assertThat(context.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void capturesTraceAndCallsExporter() throws Exception {
    var exportedTrees = new ArrayList<TraceTree>();
    FilterChain chain =
        (req, res) -> {
          context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
          context.exitMethodWithReturn("\"ok\"");
        };

    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> exportedTrees.add(tree));
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    assertThat(exportedTrees).hasSize(1);
    assertThat(exportedTrees.get(0).isEmpty()).isFalse();
  }

  @Test
  void requestContextHasCorrectStatusCode() throws Exception {
    var exportedContexts = new ArrayList<RequestContext>();
    FilterChain chain =
        (req, res) -> {
          context.enterMethod(new MethodSignature("Svc", "create", List.of()));
          context.exitMethodWithReturn("\"created\"");
          ((StubHttpServletResponse) res).setStatus(201);
        };

    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> exportedContexts.add(reqCtx));
    var request = new StubHttpServletRequest("POST", "/api/orders");
    var response = new StubHttpServletResponse();
    filter.doFilter(request, response, chain);

    assertThat(exportedContexts).hasSize(1);
    assertThat(exportedContexts.get(0).statusCode()).isEqualTo(201);
  }

  @Test
  void requestContextHasNonNegativeDuration() throws Exception {
    var exportedContexts = new ArrayList<RequestContext>();
    FilterChain chain =
        (req, res) -> {
          context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
          context.exitMethodWithReturn("\"ok\"");
        };

    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> exportedContexts.add(reqCtx));
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    assertThat(exportedContexts.get(0).durationMillis()).isGreaterThanOrEqualTo(0L);
  }

  @Test
  void skipsExportForEmptyTraces() throws Exception {
    var exportCalled = new AtomicBoolean(false);
    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> exportCalled.set(true));

    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), (req, res) -> {});

    assertThat(exportCalled).isFalse();
  }

  @Test
  void handlesNonHttpRequests() throws Exception {
    var exportCalled = new AtomicBoolean(false);
    var chainCalled = new AtomicBoolean(false);
    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> exportCalled.set(true));

    ServletRequest plainRequest = new StubServletRequest();
    ServletResponse plainResponse = new StubServletResponse();
    FilterChain chain = (req, res) -> chainCalled.set(true);

    filter.doFilter(plainRequest, plainResponse, chain);

    assertThat(chainCalled).isTrue();
    assertThat(exportCalled).isFalse();
  }

  @Test
  void throwingRequestContextProviderDoesNotFailTheRequest() throws Exception {
    RequestContextProvider<HttpServletRequest> provider =
        request -> {
          throw new IllegalStateException("auth backend down");
        };
    var chainRan = new AtomicBoolean(false);
    FilterChain chain = (req, res) -> chainRan.set(true);
    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> {}, provider);

    // Must not throw — observability failure must never become a request failure
    filter.doFilter(
        new StubHttpServletRequest("GET", "/api/orders"), new StubHttpServletResponse(), chain);

    assertThat(chainRan).isTrue();
  }

  @Test
  void concurrentRequestsExportIsolatedTraces() throws Exception {
    var exported = new java.util.concurrent.ConcurrentHashMap<String, TraceTree>();
    var filter =
        new NarrativeTraceFilter(
            context,
            (tree, reqCtx) -> exported.put(tree.roots().get(0).signature().className(), tree));
    var aEntered = new java.util.concurrent.CountDownLatch(1);
    var bFinished = new java.util.concurrent.CountDownLatch(1);

    var requestA = new Thread(() -> runRequest(filter, "SvcA", aEntered, bFinished));
    requestA.start();
    awaitOrFail(aEntered);
    runRequest(filter, "SvcB", null, null); // full request B lifecycle while A is in flight
    bFinished.countDown();
    requestA.join(5000);

    assertThat(exported).containsKeys("SvcA", "SvcB");
    assertThat(exported.get("SvcA").roots()).hasSize(1);
    assertThat(exported.get("SvcB").roots()).hasSize(1);
  }

  private void runRequest(
      NarrativeTraceFilter filter,
      String serviceName,
      java.util.concurrent.CountDownLatch entered,
      java.util.concurrent.CountDownLatch proceed) {
    FilterChain chain =
        (req, res) -> {
          context.enterMethod(new MethodSignature(serviceName, "handle", List.of()));
          if (entered != null) {
            entered.countDown();
            awaitOrFail(proceed);
          }
          context.exitMethodWithReturn("\"ok\"");
        };
    try {
      filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void awaitOrFail(java.util.concurrent.CountDownLatch latch) {
    try {
      assertThat(latch.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @Test
  void exporterFailureDoesNotPropagateToRequest() throws Exception {
    FilterChain chain =
        (req, res) -> {
          context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
          context.exitMethodWithReturn("\"ok\"");
        };

    var filter =
        new NarrativeTraceFilter(
            context,
            (tree, reqCtx) -> {
              throw new RuntimeException("exporter blew up");
            });

    // Must not throw — exporter failure must not become a request failure
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    // Context must still be cleaned up
    assertThat(context.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void filterPopulatesHttpFields() throws Exception {
    var exportedTrees = new ArrayList<TraceTree>();
    FilterChain chain =
        (req, res) -> {
          context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
          context.exitMethodWithReturn("\"ok\"");
        };

    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> exportedTrees.add(tree));
    var request = new StubHttpServletRequest("POST", "/api/orders", "client-ip-1");
    filter.doFilter(request, new StubHttpServletResponse(), chain);

    var tree = exportedTrees.get(0);
    var root = tree.roots().get(0);
    assertThat(root.spanContext()).isNotNull();
    assertThat(root.spanContext().httpMethod()).isEqualTo("POST");
    assertThat(root.spanContext().httpRoute()).hasToString("/api/orders");
    assertThat(root.spanContext().clientIp()).hasToString("client-ip-1");
  }

  @Test
  void filterUsesProviderForUserFields() throws Exception {
    var exportedTrees = new ArrayList<TraceTree>();
    RequestContextProvider<HttpServletRequest> provider =
        request -> new RequestContextProvider.UserContext("user-42", "session-abc", "tenant-xyz");
    FilterChain chain =
        (req, res) -> {
          context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
          context.exitMethodWithReturn("\"ok\"");
        };

    var filter =
        new NarrativeTraceFilter(context, (tree, reqCtx) -> exportedTrees.add(tree), provider);
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    var root = exportedTrees.get(0).roots().get(0);
    assertThat(root.spanContext().enduserId()).hasToString("user-42");
    assertThat(root.spanContext().sessionId()).hasToString("session-abc");
    assertThat(root.spanContext().tenantId()).hasToString("tenant-xyz");
  }

  @Test
  void filterHandlesNullUserContextFields() throws Exception {
    var exportedTrees = new ArrayList<TraceTree>();
    RequestContextProvider<HttpServletRequest> provider =
        request -> new RequestContextProvider.UserContext(null, null, null);
    FilterChain chain =
        (req, res) -> {
          context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
          context.exitMethodWithReturn("\"ok\"");
        };

    var filter =
        new NarrativeTraceFilter(context, (tree, reqCtx) -> exportedTrees.add(tree), provider);
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    var root = exportedTrees.get(0).roots().get(0);
    assertThat(root.spanContext().enduserId()).isNull();
    assertThat(root.spanContext().sessionId()).isNull();
    assertThat(root.spanContext().tenantId()).isNull();
  }

  @Test
  void filterConstructorAcceptsProvider() {
    RequestContextProvider<HttpServletRequest> provider = request -> null;
    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> {}, provider);

    assertThat(filter).isInstanceOf(Filter.class);
  }

  @Test
  void cleansUpWhenChainThrows() {
    context.enterMethod(new MethodSignature("Stale", "leftover", List.of()));
    context.exitMethodWithReturn("\"stale\"");

    FilterChain chain =
        (req, res) -> {
          context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
          context.exitMethodWithReturn("\"ok\"");
          throw new RuntimeException("chain error");
        };

    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> {});

    assertThatThrownBy(
            () ->
                filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("chain error");

    assertThat(context.captureTrace().isEmpty()).isTrue();
  }

  @Test
  void mdcContainsTraceIdDuringChainExecution() throws Exception {
    var capturedMdc = new AtomicReference<String>();
    FilterChain chain = (req, res) -> capturedMdc.set(MDC.get("traceId"));

    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> {});
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    assertThat(capturedMdc.get()).isNotNull().matches("[0-9a-f]{32}");
  }

  @Test
  void mdcContainsHttpFieldsDuringChainExecution() throws Exception {
    var capturedMdc = new HashMap<String, String>();
    FilterChain chain =
        (req, res) -> {
          capturedMdc.put("httpMethod", MDC.get("httpMethod"));
          capturedMdc.put("httpRoute", MDC.get("httpRoute"));
          capturedMdc.put("clientIp", MDC.get("clientIp"));
        };

    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> {});
    var request = new StubHttpServletRequest("POST", "/api/orders", "client-ip-1");
    filter.doFilter(request, new StubHttpServletResponse(), chain);

    assertThat(capturedMdc.get("httpMethod")).isEqualTo("POST");
    assertThat(capturedMdc.get("httpRoute")).isEqualTo("/api/orders");
    assertThat(capturedMdc.get("clientIp")).isEqualTo("client-ip-1");
  }

  @Test
  void mdcContainsIdentityFieldsFromProvider() throws Exception {
    var capturedMdc = new HashMap<String, String>();
    RequestContextProvider<HttpServletRequest> provider =
        request -> new RequestContextProvider.UserContext("user-42", "session-abc", "tenant-xyz");
    FilterChain chain =
        (req, res) -> {
          capturedMdc.put("enduserId", MDC.get("enduserId"));
          capturedMdc.put("sessionId", MDC.get("sessionId"));
          capturedMdc.put("tenantId", MDC.get("tenantId"));
        };

    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> {}, provider);
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    assertThat(capturedMdc.get("enduserId")).isEqualTo("user-42");
    assertThat(capturedMdc.get("sessionId")).isEqualTo("session-abc");
    assertThat(capturedMdc.get("tenantId")).isEqualTo("tenant-xyz");
  }

  @Test
  void mdcClearedAfterRequest() throws Exception {
    FilterChain chain = (req, res) -> {};
    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> {});
    filter.doFilter(
        new StubHttpServletRequest("GET", "/test", "client-ip-2"),
        new StubHttpServletResponse(),
        chain);

    assertThat(MDC.get("traceId")).isNull();
    assertThat(MDC.get("httpMethod")).isNull();
    assertThat(MDC.get("httpRoute")).isNull();
    assertThat(MDC.get("clientIp")).isNull();
  }

  @Test
  void mdcClearedAfterChainException() {
    FilterChain chain =
        (req, res) -> {
          throw new RuntimeException("boom");
        };
    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> {});

    try {
      filter.doFilter(
          new StubHttpServletRequest("POST", "/fail", "client-ip-3"),
          new StubHttpServletResponse(),
          chain);
    } catch (Exception ignored) { // NOPMD
    }

    assertThat(MDC.get("traceId")).isNull();
    assertThat(MDC.get("httpMethod")).isNull();
  }

  @Test
  void mdcOmitsNullIdentityFields() throws Exception {
    var capturedMdc = new HashMap<String, String>();
    RequestContextProvider<HttpServletRequest> provider =
        request -> new RequestContextProvider.UserContext(null, null, null);
    FilterChain chain =
        (req, res) -> {
          capturedMdc.put("enduserId", MDC.get("enduserId"));
          capturedMdc.put("sessionId", MDC.get("sessionId"));
          capturedMdc.put("tenantId", MDC.get("tenantId"));
        };

    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> {}, provider);
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    assertThat(capturedMdc.get("enduserId")).isNull();
    assertThat(capturedMdc.get("sessionId")).isNull();
    assertThat(capturedMdc.get("tenantId")).isNull();
  }

  @Test
  void nonHttpRequestDoesNotSetMdc() throws Exception {
    MDC.put("traceId", "should-not-be-cleared");
    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> {});
    filter.doFilter(new StubServletRequest(), new StubServletResponse(), (req, res) -> {});

    // Non-HTTP requests pass through without touching MDC
    assertThat(MDC.get("traceId")).isEqualTo("should-not-be-cleared");
  }

  @Test
  void mdcTraceIdMatchesContextTraceId() throws Exception {
    var capturedMdcTraceId = new AtomicReference<String>();
    var exportedTrees = new ArrayList<TraceTree>();
    FilterChain chain =
        (req, res) -> {
          capturedMdcTraceId.set(MDC.get("traceId"));
          context.enterMethod(new MethodSignature("Svc", "handle", List.of()));
          context.exitMethodWithReturn("\"ok\"");
        };

    var filter = new NarrativeTraceFilter(context, (tree, reqCtx) -> exportedTrees.add(tree));
    filter.doFilter(new StubHttpServletRequest(), new StubHttpServletResponse(), chain);

    var spanTraceId = exportedTrees.get(0).roots().get(0).spanContext().traceId().toString();
    assertThat(capturedMdcTraceId.get()).isEqualTo(spanTraceId);
  }
}
