/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.render.ProseRenderer;
import ai.narrativetrace.core.render.RedactionPolicy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The regression test whose absence let a credential leak for the library's whole life.
 *
 * <p>INTENT: The name deny-list reached field names and record-component names, and was never asked
 * about a <em>parameter</em> name. A method taking {@code String password} therefore printed it in
 * full, while the README said the opposite. The old suite could not catch it because every
 * parameter-shaped fixture in it carried {@code @NotTraced} — those tests looked like proof that
 * parameter redaction worked, and only ever proved the annotation worked.
 *
 * <p><b>@llmNote</b> Every fixture here is deliberately <em>un</em>annotated. If a future change
 * adds {@code @NotTraced} to any parameter below to make a failure go away, it restores exactly the
 * blind spot this file exists to close.
 */
class ParameterNameRedactionTest {

  /** No annotations anywhere — the deny-list is the only thing that can redact these. */
  interface LoginService {
    String login(String username, String password);
  }

  interface PayoutService {
    String pay(String policyId, String paymentToken);
  }

  interface ProbeService {
    String submit(String reference, Object password);
  }

  /** Counts every attempt to render it, so "never rendered" is provable rather than assumed. */
  static final class RenderProbe {
    private final AtomicInteger renders = new AtomicInteger();

    int renders() {
      return renders.get();
    }

    @Override
    public String toString() {
      renders.incrementAndGet();
      return "super-secret-value";
    }
  }

  @Test
  @DisplayName("a parameter named password is redacted with no annotation anywhere")
  void redactsPasswordByNameAlone() {
    var context = new ThreadLocalNarrativeContext();
    LoginService service =
        NarrativeTraceProxy.trace((user, pass) -> "ok", LoginService.class, context);

    service.login("jsmith", "hunter2");

    var text = new IndentedTextRenderer().render(context.captureTrace());
    assertThat(text).contains(RedactionPolicy.MARKER).doesNotContain("hunter2");
    assertThat(text).as("the parameter name itself stays visible").contains("password");
    assertThat(text).as("its innocent neighbour is untouched").contains("jsmith");
  }

  @Test
  @DisplayName("the audited leak: a camel-case compound of a deny-listed word")
  void redactsPaymentTokenByNameAlone() {
    var context = new ThreadLocalNarrativeContext();
    PayoutService service =
        NarrativeTraceProxy.trace((policy, token) -> "TXN-1", PayoutService.class, context);

    service.pay("POL-001", "secret_payment_token");

    var text = new IndentedTextRenderer().render(context.captureTrace());
    assertThat(text).contains(RedactionPolicy.MARKER).doesNotContain("secret_payment_token");
    assertThat(text).as("the policy id is not a credential").contains("POL-001");
  }

  @ParameterizedTest
  @ValueSource(strings = {"markdown", "prose", "indented"})
  @DisplayName("redaction holds in every rendered output format, not just one")
  void redactsAcrossEveryRenderer(String format) {
    var context = new ThreadLocalNarrativeContext();
    LoginService service =
        NarrativeTraceProxy.trace((user, pass) -> "ok", LoginService.class, context);

    service.login("jsmith", "hunter2");
    var tree = context.captureTrace();

    var rendered =
        switch (format) {
          case "markdown" -> new MarkdownRenderer().render(tree);
          case "prose" -> new ProseRenderer().render(tree);
          default -> new IndentedTextRenderer().render(tree);
        };

    assertThat(rendered).doesNotContain("hunter2");
    assertThat(rendered).contains(RedactionPolicy.MARKER);
  }

  @Test
  @DisplayName("a denied value is never rendered at all — not rendered and then discarded")
  void deniedValueIsNeverFormatted() {
    var context = new ThreadLocalNarrativeContext();
    var probe = new RenderProbe();
    ProbeService service =
        NarrativeTraceProxy.trace((reference, secret) -> "ok", ProbeService.class, context);

    service.submit("REF-1", probe);

    assertThat(probe.renders())
        .as("a secret formatted and then replaced still existed as a string in memory")
        .isZero();
    var text = new IndentedTextRenderer().render(context.captureTrace());
    assertThat(text).contains(RedactionPolicy.MARKER).doesNotContain("super-secret-value");
  }

  @Test
  @DisplayName("the deny-list decides at capture, so the event itself carries no cleartext")
  void theEventNeverCarriesTheValue() {
    var context = new ThreadLocalNarrativeContext();
    LoginService service =
        NarrativeTraceProxy.trace((user, pass) -> "ok", LoginService.class, context);

    service.login("jsmith", "hunter2");

    var tree = context.captureTrace();
    var everyCapturedValue =
        tree.roots().stream()
            .flatMap(node -> node.signature().parameters().stream())
            .map(parameter -> parameter.renderedValue())
            .toList();

    assertThat(everyCapturedValue)
        .as("a renderer cannot leak what capture never recorded")
        .doesNotContain("hunter2")
        .contains(RedactionPolicy.MARKER);
  }
}
