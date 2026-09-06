/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.export.RequestContext;
import ai.narrativetrace.api.export.RequestContextProvider;
import ai.narrativetrace.api.export.TraceExporter;
import ai.narrativetrace.core.context.ContextExport;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Request-derived values reach MDC, and MDC is printed by whatever layout the host configured.
 *
 * <p>INTENT: The 2026-09-02 adversarial audit (finding 6) showed route, client IP and end-user id
 * all preserving raw newlines all the way into MDC. A layout that prints MDC without JSON escaping
 * then forges a log line that a line-based parser or SIEM cannot distinguish from a real one
 * (CWE-117), and an unbounded value is unbounded cardinality once it reaches telemetry.
 *
 * <p><b>@llmNote</b> The values here are attacker-supplied in the ordinary case, not the exotic
 * one: the URI is whatever was requested, the client IP is commonly derived from {@code
 * X-Forwarded-For}, and the user context comes from a header a {@code RequestContextProvider} read.
 */
@SuppressWarnings("PMD.AvoidUsingHardCodedIP") // a client IP is the subject; a literal is the point
class FilterContextNormalizationTest {

  private static final String NEWLINE_FORGERY =
      "/orders\n2026-09-02 10:00:00 INFO  [admin] payment approved";
  private static final String ANSI = "/orders\u001b[31m";

  private ThreadLocalNarrativeContext context;
  private NarrativeTraceFilter filter;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    filter = new NarrativeTraceFilter(context, noopExporter());
    MDC.clear();
  }

  private void withProvider(RequestContextProvider<HttpServletRequest> provider) {
    filter = new NarrativeTraceFilter(context, noopExporter(), provider);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
    context.reset();
  }

  private static TraceExporter noopExporter() {
    return (TraceExporter) (tree, requestContext) -> noop(requestContext);
  }

  private static void noop(RequestContext ignored) {
    // exporting is not what this test is about
  }

  /** MDC as it stood while the chain ran — the filter clears it afterwards. */
  private Map<String, String> mdcDuring(StubHttpServletRequest request) throws Exception {
    var captured = new LinkedHashMap<String, String>();
    filter.doFilter(
        request,
        new StubHttpServletResponse(),
        (req, res) -> {
          var snapshot = MDC.getCopyOfContextMap();
          if (snapshot != null) {
            captured.putAll(snapshot);
          }
        });
    return captured;
  }

  @Test
  @DisplayName("a newline in the request URI cannot forge a log line through MDC")
  void aNewlineInTheUriCannotForgeALogLine() throws Exception {
    var mdc = mdcDuring(new StubHttpServletRequest("GET", NEWLINE_FORGERY));

    assertThat(mdc.get("httpRoute")).doesNotContain("\n").doesNotContain("\r");
    assertNoRawControls(mdc);
  }

  @Test
  @DisplayName("an ANSI escape in the request URI cannot reach a terminal through MDC")
  void anAnsiEscapeInTheUriIsNeutralised() throws Exception {
    var mdc = mdcDuring(new StubHttpServletRequest("GET", ANSI));

    assertThat(mdc.get("httpRoute")).doesNotContain("\u001b");
    assertNoRawControls(mdc);
  }

  @Test
  @DisplayName("a newline in the client address cannot forge a log line through MDC")
  void aNewlineInTheClientAddressCannotForgeALogLine() throws Exception {
    var mdc = mdcDuring(new StubHttpServletRequest("GET", "/orders", "10.0.0.1\nforged"));

    assertThat(mdc.get("clientIp")).doesNotContain("\n");
    assertNoRawControls(mdc);
  }

  @Test
  @DisplayName("an over-long route is capped rather than exported whole")
  void anOverLongRouteIsCapped() throws Exception {
    var huge = "/" + "a".repeat(10_000);

    var mdc = mdcDuring(new StubHttpServletRequest("GET", huge));

    assertThat(mdc.get("httpRoute")).hasSizeLessThanOrEqualTo(ContextExport.MAX_LENGTH + 1);
  }

  @Test
  @DisplayName("hostile user-context headers are normalised before MDC")
  void hostileUserContextValuesAreNormalised() throws Exception {
    withProvider(
        request ->
            new RequestContextProvider.UserContext(
                "ada\nforged-user", "sess\u001b[31m", "tenant" + "x".repeat(10_000)));

    var mdc = mdcDuring(new StubHttpServletRequest("GET", "/orders"));

    assertThat(mdc.get("enduserId")).doesNotContain("\n");
    assertThat(mdc.get("sessionId")).doesNotContain("\u001b");
    assertThat(mdc.get("tenantId")).hasSizeLessThanOrEqualTo(ContextExport.MAX_LENGTH + 1);
    assertNoRawControls(mdc);
  }

  @Test
  @DisplayName("an ordinary request still exports exactly what it always did")
  void anOrdinaryRequestIsUnchanged() throws Exception {
    var mdc = mdcDuring(new StubHttpServletRequest("GET", "/orders/42", "10.0.0.1"));

    assertThat(mdc).containsEntry("httpRoute", "/orders/42");
    assertThat(mdc).containsEntry("clientIp", "10.0.0.1");
    assertThat(mdc).containsEntry("httpMethod", "GET");
  }

  private static void assertNoRawControls(Map<String, String> mdc) {
    mdc.forEach(
        (key, value) ->
            assertThat(value.chars().anyMatch(Character::isISOControl))
                .as("MDC entry %s carried a raw control character: %s", key, value)
                .isFalse());
  }
}
