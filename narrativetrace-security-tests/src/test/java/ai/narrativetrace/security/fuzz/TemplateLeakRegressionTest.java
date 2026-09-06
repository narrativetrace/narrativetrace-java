/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.fuzz;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.template.TemplateParser;
import ai.narrativetrace.security.corpus.HostileGraphs;
import ai.narrativetrace.security.oracle.Oracles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The deterministic half of {@link TemplateFuzzTest}: the leak it found, pinned so it cannot come
 * back without a budgeted fuzz run.
 *
 * <p>INTENT: {@code fuzzTemplate} found a template resolution that printed a {@code @NotTraced}
 * value in full on 2026-09-02. The input it found was 580 bytes of brace noise and the leak fired
 * on one of fifty-nine identical {@code {a}} placeholders, because it depended on that one render
 * degrading. Replaying those bytes is therefore necessary but not sufficient — the cases below name
 * the two ways a render degrades <em>deterministically</em>, so the bug class is pinned by
 * construction rather than by luck.
 *
 * <p><b>@edgeCase</b> The bug was never in the placeholder grammar. It was the inference "the safe
 * rendering carries no redaction marker" ⇒ "nothing is hidden, so the value's own {@code
 * toString()} may stand". The marker is equally absent when the renderer never saw the whole value:
 * truncated at the field or collection cap, cut at the depth cap, stopped at a cycle, or degraded
 * to a type marker by its own last-resort catch. Two of those are reachable on demand, and both are
 * below.
 */
class TemplateLeakRegressionTest {

  /** Every fixture the fuzz target resolves against, so a regression anywhere is visible. */
  private static final List<String> FIXTURES =
      List.of(
          "card",
          "user",
          "order",
          "deep",
          "unicode",
          "wide",
          "chain",
          "password-scalar",
          "jwt-scalar",
          "newline-scalar");

  /**
   * The shortest template that still leaked before the fix: a whole-object placeholder whose
   * redacted component sits past the renderer's field cap.
   */
  @Test
  void theMinimizedTemplateDoesNotLeakThroughAFieldCappedRender() {
    var sentinel = Oracles.freshSentinel();

    var resolved = TemplateParser.resolve("{wide}", HostileGraphs.templateValues("wide", sentinel));

    assertThat(resolved).doesNotContain(sentinel);
  }

  /** The same leak reached through the other cap: a redacted leaf below the depth limit. */
  @Test
  void theMinimizedTemplateDoesNotLeakThroughADepthCappedRender() {
    var sentinel = Oracles.freshSentinel();

    var resolved =
        TemplateParser.resolve("{chain}", HostileGraphs.templateValues("chain", sentinel));

    assertThat(resolved).doesNotContain(sentinel);
  }

  /**
   * A whole-object placeholder never narrates through the value's own {@code toString()}, whichever
   * fixture it names. This is the invariant the fix installed; the two cases above are the
   * instances that were failing.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "card",
        "user",
        "order",
        "deep",
        "unicode",
        "wide",
        "chain",
        "password-scalar",
        "jwt-scalar",
        "newline-scalar"
      })
  void aWholeObjectPlaceholderNeverLeaksWhicheverFixtureItNames(String fixture) {
    var sentinel = Oracles.freshSentinel();
    var values = HostileGraphs.templateValues(fixture, sentinel);
    var key = values.keySet().iterator().next();

    var resolved = TemplateParser.resolve("audit {" + key + "}", values);

    assertThat(resolved)
        .as("fixture %s leaked through {%s}", fixture, key)
        .doesNotContain(sentinel);
  }

  /**
   * The 580 bytes the fuzzer actually found, replayed against every fixture. Kept beside the
   * minimized cases because it is the only artifact that proves the reported finding itself is
   * closed, and because it is also the Tier B seed corpus entry of the same name.
   */
  @Test
  void theReportedFuzzInputDoesNotLeakAgainstAnyFixture() {
    var template = new String(reportedInput(), StandardCharsets.UTF_8);

    for (var fixture : FIXTURES) {
      var sentinel = Oracles.freshSentinel();

      var resolved =
          TemplateParser.resolve(template, HostileGraphs.templateValues(fixture, sentinel));

      assertThat(resolved)
          .as("fixture %s leaked through the reported input", fixture)
          .doesNotContain(sentinel);
    }
  }

  /** The reported input, read from the Tier B seed corpus so the two can never drift apart. */
  private static byte[] reportedInput() {
    var resource =
        "/ai/narrativetrace/security/fuzz/TemplateFuzzTestInputs/"
            + "resolvingNeverLeaksARedactedValue/degraded-render-falls-back-to-toString";
    try (var stream = TemplateLeakRegressionTest.class.getResourceAsStream(resource)) {
      if (stream == null) {
        throw new IllegalStateException("seed corpus entry missing: " + resource);
      }
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
