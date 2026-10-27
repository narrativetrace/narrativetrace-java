/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * B-207: the renderer's limits are clamped to zero when negative, with one diagnostic.
 *
 * <p>INTENT: a limit is a size, and a negative size has no meaning. Left alone it threw inside
 * {@code substring} (caught into a failure marker) for the string cap and silently changed which
 * branch ran for the others. The contract: every limit below zero behaves exactly as zero, and the
 * constructor says so once on the library's diagnostic channel, naming the limits it clamped.
 *
 * <p><b>@llmNote</b> The depth cap and the node budget are fixed constants in Java ({@code
 * RenderWalk.MAX_DEPTH}, {@code RenderWalk.MAX_NODES}); no caller can set them, so there is no
 * negative value to clamp. The three configurable limits are the string cap, the element cap and
 * the field cap.
 */
class ValueRendererLimitClampTest {

  record Pair(String first, String second, String third) {}

  @SuppressWarnings("PMD.UnusedPrivateField") // read by introspection
  static final class Bean {
    private final String a = "one";
    private final String b = "two";
    private final String c = "three";
  }

  private static final String LONG_TEXT = "a text that is longer than nothing";

  private static List<Object> battery() {
    var map = new LinkedHashMap<String, Object>();
    map.put("k1", "v1");
    map.put("k2", "v2");
    return List.of(
        LONG_TEXT,
        "",
        List.of(1, 2, 3),
        new int[] {1, 2, 3},
        map,
        new Pair("x", "y", "z"),
        new Bean(),
        new Object[] {"p", "q"});
  }

  private static String rendered(ValueRenderer renderer) {
    var out = new StringBuilder();
    for (var value : battery()) {
      out.append(renderer.render(value)).append('\n');
      out.append(renderer.renderStructured(value)).append('\n');
    }
    return out.toString();
  }

  private static String captureErr(Supplier<?> action) {
    var original = System.err;
    var captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      action.get();
    } finally {
      System.setErr(original);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }

  private static void assertOneDiagnosticNaming(String err, String... limits) {
    var lines = err.lines().filter(l -> l.contains("narrative-trace")).toList();
    assertThat(lines).as("exactly one diagnostic line").hasSize(1);
    for (var limit : limits) {
      assertThat(lines.get(0)).contains(limit);
    }
  }

  @Test
  void aNegativeStringCapRendersExactlyAsZeroAndSaysSoOnce() {
    ValueRenderer[] clamped = new ValueRenderer[1];
    var err = captureErr(() -> clamped[0] = new ValueRenderer(-1, 5, 5));

    assertThat(rendered(clamped[0])).isEqualTo(rendered(new ValueRenderer(0, 5, 5)));
    assertThat(clamped[0].render(LONG_TEXT)).isEqualTo("\"…\"");
    assertOneDiagnosticNaming(err, "maxStringLength");
    assertThat(err).doesNotContain("maxCollectionItems").doesNotContain("maxObjectFields");
  }

  @Test
  void aNegativeElementCapRendersExactlyAsZeroAndSaysSoOnce() {
    ValueRenderer[] clamped = new ValueRenderer[1];
    var err = captureErr(() -> clamped[0] = new ValueRenderer(200, -3, 5));

    assertThat(rendered(clamped[0])).isEqualTo(rendered(new ValueRenderer(200, 0, 5)));
    assertOneDiagnosticNaming(err, "maxCollectionItems");
    assertThat(err).doesNotContain("maxStringLength").doesNotContain("maxObjectFields");
  }

  @Test
  void aNegativeFieldCapRendersExactlyAsZeroAndSaysSoOnce() {
    ValueRenderer[] clamped = new ValueRenderer[1];
    var err = captureErr(() -> clamped[0] = new ValueRenderer(200, 5, -2));

    assertThat(rendered(clamped[0])).isEqualTo(rendered(new ValueRenderer(200, 5, 0)));
    assertOneDiagnosticNaming(err, "maxObjectFields");
    assertThat(err).doesNotContain("maxStringLength").doesNotContain("maxCollectionItems");
  }

  @Test
  void severalNegativeLimitsShareOneDiagnosticThatNamesEach() {
    var err = captureErr(() -> new ValueRenderer(-1, -1, -1));

    assertOneDiagnosticNaming(err, "maxStringLength", "maxCollectionItems", "maxObjectFields");
  }

  @Test
  void validLimitsSayNothing() {
    assertThat(captureErr(() -> new ValueRenderer(0, 0, 0))).isEmpty();
    assertThat(captureErr(() -> new ValueRenderer())).isEmpty();
  }
}
